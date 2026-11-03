package com.invoicetracker.domain.common;

/** Base type for violations of business rules raised by the domain model. */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Stable, machine-readable error code (exposed to API clients). */
    public String code() {
        return code;
    }
}
