package com.example.foodsafety.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthRequestModels {
    private AuthRequestModels() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Size(min = 12, max = 128) String password
    ) {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 128) String password
    ) {}

    public record PasswordForgotRequest(
            @NotBlank @Email @Size(max = 320) String email
    ) {}

    public record EmailRequest(
            @NotBlank @Email @Size(max = 320) String email
    ) {}

    public record PasswordResetRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 12, max = 128) String newPassword
    ) {}

    public record PasswordChangeRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword
    ) {}

    public record DeleteAccountRequest(@NotBlank String password) {}

    public record AuthUserResponse(String id, String email, String displayName, boolean emailVerified) {}

    public record AuthTokenResponse(String accessToken, String tokenType, long expiresIn, AuthUserResponse user) {}

    public record MessageResponse(String message) {}
}
