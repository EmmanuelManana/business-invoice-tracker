package com.invoicetracker.api.web;

import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Builds RFC 7807 problem responses with a stable {@code code} and the request's correlation id. */
public final class ProblemTypes {

    private static final String TYPE_BASE = "https://invoice-tracker.example/problems/";

    private ProblemTypes() {}

    public static ProblemDetail problem(HttpStatus status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code.replace('_', '-')));
        problem.setTitle(title);
        problem.setProperty("code", code);
        decorate(problem);
        return problem;
    }

    /** Adds the correlation id so a client error report can be matched to server logs. */
    public static void decorate(ProblemDetail problem) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
    }
}
