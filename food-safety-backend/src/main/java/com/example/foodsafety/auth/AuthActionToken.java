package com.example.foodsafety.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "auth_action_tokens")
public class AuthActionToken {
    @Id
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AuthUser user;

    @Column(name = "token_type", nullable = false, length = 32)
    private String tokenType;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected AuthActionToken() {}

    public AuthActionToken(String tokenHash, AuthUser user, String tokenType, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.tokenType = tokenType;
        this.expiresAt = expiresAt;
    }

    public AuthUser getUser() { return user; }
    public String getTokenType() { return tokenType; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public void consume(Instant now) { consumedAt = now; }
}
