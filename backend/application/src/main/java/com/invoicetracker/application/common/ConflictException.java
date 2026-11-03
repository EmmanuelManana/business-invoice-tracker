package com.invoicetracker.application.common;

/** The request conflicts with the current state of the system (e.g. duplicate email). */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
