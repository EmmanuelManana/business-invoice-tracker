package com.invoicetracker.api.web;

/** A conditional request header (If-Match) is required but was missing. Maps to 428. */
public class PreconditionRequiredException extends RuntimeException {

    public PreconditionRequiredException(String message) {
        super(message);
    }
}
