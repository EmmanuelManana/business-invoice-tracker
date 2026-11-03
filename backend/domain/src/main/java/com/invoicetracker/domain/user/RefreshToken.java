package com.invoicetracker.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A long-lived refresh token. Only a SHA-256 hash of the token is stored, so a database leak does
 * not expose usable tokens. Tokens are single-use: every refresh revokes the old token and links it
 * to its replacement, which lets us detect replay of a stolen token.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant revokedAt;

    private UUID replacedBy;

    @Version
    private Long version;

    protected RefreshToken() {}

    public static RefreshToken issue(UUID userId, String tokenHash, Instant expiresAt, Instant now) {
        var token = new RefreshToken();
        token.id = UUID.randomUUID();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        token.createdAt = now;
        return token;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isActive(Instant now) {
        return !isRevoked() && !isExpired(now);
    }

    public void revoke(Instant now, UUID replacedBy) {
        if (revokedAt == null) {
            this.revokedAt = now;
            this.replacedBy = replacedBy;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedBy() {
        return replacedBy;
    }
}
