package com.invoicetracker.application.port;

import com.invoicetracker.domain.user.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    RefreshToken save(RefreshToken token);

    /** Revokes every active token of a user (used when token replay is detected). */
    int revokeAllForUser(UUID userId, Instant now);
}
