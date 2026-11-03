package com.invoicetracker.domain.common;

/** Thrown when a request is well-formed but breaks a business rule (e.g. overpaying an invoice). */
public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(String code, String message) {
        super(code, message);
    }
}
