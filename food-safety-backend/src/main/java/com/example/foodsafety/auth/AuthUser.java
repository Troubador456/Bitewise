package com.example.foodsafety.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_users", uniqueConstraints = @UniqueConstraint(name = "uk_auth_users_email", columnNames = "email"))
public class AuthUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "auth_version", nullable = false)
    private int authVersion;

    protected AuthUser() {}

    public AuthUser(String email, String displayName, String passwordHash) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.emailVerified = false;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isEmailVerified() { return emailVerified; }
    public int getAuthVersion() { return authVersion; }

    public void verifyEmail() { this.emailVerified = true; }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.authVersion++;
    }
}
