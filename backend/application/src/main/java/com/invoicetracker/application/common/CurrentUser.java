package com.invoicetracker.application.common;

import java.util.Objects;
import java.util.UUID;

/**
 * The authenticated caller. {@code businessId} comes from the verified access token, never from
 * request input, and every use case scopes its queries by it.
 */
public record CurrentUser(UUID userId, UUID businessId, String email) {

    public CurrentUser {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(businessId, "businessId");
    }
}
