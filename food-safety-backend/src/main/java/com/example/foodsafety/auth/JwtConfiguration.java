package com.example.foodsafety.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfiguration {
    @Bean
    SecretKey jwtSecretKey(@Value("${app.auth.jwt-secret-base64:}") String encodedSecret) {
        if (encodedSecret == null || encodedSecret.isBlank()) {
            throw new IllegalStateException("Set JWT_SECRET_BASE64 to a random Base64-encoded secret of at least 32 bytes");
        }
        try {
            byte[] secretBytes = Base64.getDecoder().decode(encodedSecret);
            if (secretBytes.length < 32) {
                throw new IllegalStateException("JWT_SECRET_BASE64 must decode to at least 32 bytes");
            }
            return new SecretKeySpec(secretBytes, "HmacSHA256");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT_SECRET_BASE64 must be valid Base64", ex);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, AuthUserRepository userRepository) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> accountValidator = jwt -> {
            String userIdClaim = jwt.getClaimAsString("user_id");
            Object authVersionClaim = jwt.getClaim("auth_version");
            if (userIdClaim == null || !(authVersionClaim instanceof Number authVersion)) {
                return invalidAccountToken();
            }
            java.util.UUID userId;
            try {
                userId = java.util.UUID.fromString(userIdClaim);
            } catch (IllegalArgumentException ex) {
                return invalidAccountToken();
            }
            AuthUser user = userRepository.findById(userId).orElse(null);
            return user != null && user.isEmailVerified() && user.getAuthVersion() == authVersion.intValue()
                    ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
                    : invalidAccountToken();
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("bitewise"),
                accountValidator
        ));
        return decoder;
    }

    private static org.springframework.security.oauth2.core.OAuth2TokenValidatorResult invalidAccountToken() {
        return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                new org.springframework.security.oauth2.core.OAuth2Error(
                        "invalid_token",
                        "Account is unavailable or the token has been revoked",
                        null
                )
        );
    }
}
