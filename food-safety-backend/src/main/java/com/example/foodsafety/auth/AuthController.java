package com.example.foodsafety.auth;

import com.example.foodsafety.auth.AuthRequestModels.AuthTokenResponse;
import com.example.foodsafety.auth.AuthRequestModels.AuthUserResponse;
import com.example.foodsafety.auth.AuthRequestModels.DeleteAccountRequest;
import com.example.foodsafety.auth.AuthRequestModels.EmailRequest;
import com.example.foodsafety.auth.AuthRequestModels.LoginRequest;
import com.example.foodsafety.auth.AuthRequestModels.MessageResponse;
import com.example.foodsafety.auth.AuthRequestModels.PasswordChangeRequest;
import com.example.foodsafety.auth.AuthRequestModels.PasswordForgotRequest;
import com.example.foodsafety.auth.AuthRequestModels.PasswordResetRequest;
import com.example.foodsafety.auth.AuthRequestModels.RegisterRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "bitewise_refresh";

    private final AuthService authService;
    private final CurrentUserService currentUser;
    private final boolean secureCookie;
    private final String cookieSameSite;

    public AuthController(
            AuthService authService,
            CurrentUserService currentUser,
            @Value("${app.auth.cookie-secure:true}") boolean secureCookie,
            @Value("${app.auth.cookie-same-site:Lax}") String cookieSameSite
    ) {
        if (!java.util.Set.of("Strict", "Lax", "None").contains(cookieSameSite)) {
            throw new IllegalArgumentException("AUTH_COOKIE_SAME_SITE must be Strict, Lax, or None");
        }
        if ("None".equals(cookieSameSite) && !secureCookie) {
            throw new IllegalArgumentException("AUTH_COOKIE_SECURE must be true when AUTH_COOKIE_SAME_SITE is None");
        }
        this.authService = authService;
        this.currentUser = currentUser;
        this.secureCookie = secureCookie;
        this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/register")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @GetMapping("/verify-email")
    public MessageResponse verifyEmail(@RequestParam String token) {
        return authService.verifyEmail(token);
    }

    @PostMapping("/verification/resend")
    public MessageResponse resendVerification(@Valid @RequestBody EmailRequest request) {
        return authService.resendVerification(request.email());
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.AuthSessionIssue issue = authService.login(request);
        setRefreshCookie(response, issue.refreshToken());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return issue.response();
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            @org.springframework.web.bind.annotation.RequestHeader("X-Bitewise-Refresh") String refreshRequestMarker,
            HttpServletResponse response
    ) {
        if (!"1".equals(refreshRequestMarker)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Refresh request header is required");
        }
        AuthService.AuthSessionIssue issue = authService.refresh(refreshToken);
        setRefreshCookie(response, issue.refreshToken());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return issue.response();
    }

    @PostMapping("/logout")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken, currentUser.id());
        clearRefreshCookie(response);
    }

    @PostMapping("/logout-all")
    public MessageResponse logoutAll(HttpServletResponse response) {
        MessageResponse message = authService.logoutAll(currentUser.id());
        clearRefreshCookie(response);
        return message;
    }

    @PostMapping("/password/forgot")
    public MessageResponse requestPasswordReset(@Valid @RequestBody PasswordForgotRequest request) {
        return authService.requestPasswordReset(request);
    }

    @PostMapping("/password/reset")
    public MessageResponse resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        return authService.resetPassword(request);
    }

    @PostMapping("/password/change")
    public MessageResponse changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        return authService.changePassword(currentUser.id(), request);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/me")
    public MessageResponse deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            HttpServletResponse response
    ) {
        MessageResponse message = authService.deleteAccount(currentUser.id(), request.password());
        clearRefreshCookie(response);
        return message;
    }

    @GetMapping("/me")
    public AuthUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getClaimAsString("user_id"));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token has no valid user identity");
        }
        return authService.getCurrentUser(userId);
    }

    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(cookieSameSite)
                .path("/api/auth")
                .maxAge(Duration.ofDays(14))
                .build()
                .toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(cookieSameSite)
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build()
                .toString());
    }
}
