package com.invoicetracker.application.common;

/** The client's copy of a resource is stale (optimistic concurrency check failed). */
public class PreconditionFailedException extends RuntimeException {

    private final long currentVersion;

    public PreconditionFailedException(long expectedVersion, long currentVersion) {
        super(
                "The resource was modified by someone else (expected version %d, current version %d). Reload and try again."
                        .formatted(expectedVersion, currentVersion));
        this.currentVersion = currentVersion;
    }

    public long currentVersion() {
        return currentVersion;
    }
}
