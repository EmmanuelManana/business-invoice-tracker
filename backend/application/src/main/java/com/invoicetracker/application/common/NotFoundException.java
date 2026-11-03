package com.invoicetracker.application.common;

import java.util.UUID;

/**
 * The requested resource does not exist <em>for the current tenant</em>. Resources owned by
 * another business also produce this exception so that their existence is not revealed.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, UUID id) {
        super("%s %s was not found".formatted(resource, id));
    }
}
