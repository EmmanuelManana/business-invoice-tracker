package com.invoicetracker.application.common;

/** Credentials or a refresh token were invalid. The message is deliberately generic. */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
