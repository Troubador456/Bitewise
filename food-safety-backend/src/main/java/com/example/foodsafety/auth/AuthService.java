package com.example.foodsafety.auth;

import com.example.foodsafety.auth.AuthRequestModels.AuthTokenResponse;
import com.example.foodsafety.auth.AuthRequestModels.AuthUserResponse;
import com.example.foodsafety.auth.AuthRequestModels.LoginRequest;
import com.example.foodsafety.auth.AuthRequestModels.PasswordChangeRequest;
import com.example.foodsafety.auth.AuthRequestModels.PasswordForgotRequest;
import com.example.foodsafety.auth.AuthRequestModels.PasswordResetRequest;
import com.example.foodsafety.auth.AuthRequestModels.RegisterRequest;
import com.example.foodsafety.auth.AuthRequestModels.MessageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);
    private static final Duration EMAIL_VERIFICATION_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(30);
    private static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    private static final String PASSWORD_RESET = "PASSWORD_RESET";

    private final AuthUserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final AuthActionTokenRepository actionTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final EmailDeliveryService emailDeliveryService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long accessTokenSeconds;

    public AuthService(
            AuthUserRepository userRepository,
            AuthSessionRepository sessionRepository,
            AuthActionTokenRepository actionTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            EmailDeliveryService emailDeliveryService,
            @Value("${app.auth.access-token-minutes:15}") long accessTokenMinutes
    ) {
        if (accessTokenMinutes < 1 || accessTokenMinutes > 60) {
            throw new IllegalArgumentException("Access token lifetime must be between 1 and 60 minutes");
        }
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.actionTokenRepository = actionTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.emailDeliveryService = emailDeliveryService;
        this.accessTokenSeconds = Duration.ofMinutes(accessTokenMinutes).toSeconds();
    }

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        validateEncodedPassword(request.password());
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        AuthUser user = userRepository.save(new AuthUser(
                email,
                request.displayName().trim(),
                passwordEncoder.encode(request.password())
        ));
        String token = createActionToken(user, EMAIL_VERIFICATION, EMAIL_VERIFICATION_TTL);
        emailDeliveryService.sendVerification(email, token);
        return new MessageResponse("Account created. Check your email to verify the address before logging in.");
    }

    @Transactional
    public MessageResponse verifyEmail(String token) {
        AuthActionToken actionToken = findUsableActionToken(token, EMAIL_VERIFICATION);
        actionToken.consume(Instant.now());
        actionToken.getUser().verifyEmail();
        return new MessageResponse("Email verified. You can now log in.");
    }

    @Transactional
    public MessageResponse resendVerification(String emailAddress) {
        userRepository.findByEmail(normalizeEmail(emailAddress))
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    String token = createActionToken(user, EMAIL_VERIFICATION, EMAIL_VERIFICATION_TTL);
                    try {
                        emailDeliveryService.sendVerification(user.getEmail(), token);
                    } catch (ResponseStatusException ex) {
                        LOGGER.warn("Email verification delivery is unavailable (HTTP {})", ex.getStatusCode().value());
                    }
                });
        return new MessageResponse("If an unverified account exists for that email, verification instructions have been sent.");
    }

    @Transactional
    public AuthSessionIssue login(LoginRequest request) {
        AuthUser user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(AuthService::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verify your email before logging in");
        }
        UUID familyId = UUID.randomUUID();
        String refreshToken = createRefreshSession(user, familyId);
        return new AuthSessionIssue(createAuthResponse(user), refreshToken);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthSessionIssue refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw invalidCredentials();
        }
        AuthSession session = sessionRepository.findByTokenHashForUpdate(hash(refreshToken))
                .orElseThrow(AuthService::invalidCredentials);
        Instant now = Instant.now();
        if (session.getRevokedAt() != null) {
            revokeFamily(session.getFamilyId(), now);
            throw invalidCredentials();
        }
        if (!session.isActiveAt(now) || !session.getUser().isEmailVerified()) {
            session.revoke(now);
            throw invalidCredentials();
        }

        session.revoke(now);
        String replacement = createRefreshSession(session.getUser(), session.getFamilyId());
        return new AuthSessionIssue(createAuthResponse(session.getUser()), replacement);
    }

    @Transactional
    public void logout(String refreshToken, UUID userId) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        sessionRepository.findByTokenHashForUpdate(hash(refreshToken))
                .filter(session -> session.getUser().getId().equals(userId))
                .ifPresent(session -> session.revoke(Instant.now()));
    }

    @Transactional
    public MessageResponse requestPasswordReset(PasswordForgotRequest request) {
        userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(AuthUser::isEmailVerified)
                .ifPresent(user -> {
                    String token = createActionToken(user, PASSWORD_RESET, PASSWORD_RESET_TTL);
                    try {
                        emailDeliveryService.sendPasswordReset(user.getEmail(), token);
                    } catch (ResponseStatusException ex) {
                        LOGGER.warn("Password reset email delivery is unavailable (HTTP {})", ex.getStatusCode().value());
                    }
                });
        return new MessageResponse("If a verified account exists for that email, password-reset instructions have been sent.");
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public MessageResponse resetPassword(PasswordResetRequest request) {
        validateEncodedPassword(request.newPassword());
        AuthActionToken actionToken = findUsableActionToken(request.token(), PASSWORD_RESET);
        Instant now = Instant.now();
        actionToken.consume(now);
        AuthUser user = actionToken.getUser();
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        revokeAllSessions(user.getId(), now);
        return new MessageResponse("Password reset. Log in again with the new password.");
    }

    @Transactional
    public MessageResponse changePassword(UUID userId, PasswordChangeRequest request) {
        validateEncodedPassword(request.newPassword());
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must differ from the current password");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        revokeAllSessions(userId, Instant.now());
        return new MessageResponse("Password changed. Log in again on your devices.");
    }

    @Transactional
    public MessageResponse logoutAll(UUID userId) {
        revokeAllSessions(userId, Instant.now());
        return new MessageResponse("All refresh sessions have been revoked.");
    }

    @Transactional
    public MessageResponse deleteAccount(UUID userId, String password) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is incorrect");
        }
        userRepository.delete(user);
        return new MessageResponse("Account and associated profiles have been deleted.");
    }

    @Transactional(readOnly = true)
    public AuthUserResponse getCurrentUser(UUID userId) {
        return userRepository.findById(userId)
                .map(AuthService::userResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
    }

    private AuthTokenResponse createAuthResponse(AuthUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("bitewise")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTokenSeconds))
                .subject(user.getEmail())
                .claim("user_id", user.getId().toString())
                .claim("auth_version", user.getAuthVersion())
                .claim("email_verified", user.isEmailVerified())
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims
        )).getTokenValue();
        return new AuthTokenResponse(accessToken, "Bearer", accessTokenSeconds, userResponse(user));
    }

    private String createRefreshSession(AuthUser user, UUID familyId) {
        String token = randomToken();
        sessionRepository.save(new AuthSession(
                user,
                familyId,
                hash(token),
                Instant.now().plus(REFRESH_TOKEN_TTL)
        ));
        return token;
    }

    private String createActionToken(AuthUser user, String type, Duration ttl) {
        String token = randomToken();
        actionTokenRepository.save(new AuthActionToken(hash(token), user, type, Instant.now().plus(ttl)));
        return token;
    }

    private AuthActionToken findUsableActionToken(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token is invalid or expired");
        }
        AuthActionToken actionToken = actionTokenRepository.findByTokenHashForUpdate(hash(token))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token is invalid or expired"));
        if (!expectedType.equals(actionToken.getTokenType())
                || actionToken.getConsumedAt() != null
                || !actionToken.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token is invalid or expired");
        }
        return actionToken;
    }

    private void revokeFamily(UUID familyId, Instant now) {
        sessionRepository.findByFamilyIdForUpdate(familyId).forEach(session -> session.revoke(now));
    }

    private void revokeAllSessions(UUID userId, Instant now) {
        List<AuthSession> sessions = sessionRepository.findActiveByUserIdForUpdate(userId);
        sessions.forEach(session -> session.revoke(now));
    }

    private String randomToken() {
        byte[] token = new byte[32];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static void validateEncodedPassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at most 72 UTF-8 bytes for the configured password hashing algorithm");
        }
    }

    private static AuthUserResponse userResponse(AuthUser user) {
        return new AuthUserResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getDisplayName(),
                user.isEmailVerified()
        );
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials or refresh session");
    }

    public record AuthSessionIssue(AuthTokenResponse response, String refreshToken) {}
}
