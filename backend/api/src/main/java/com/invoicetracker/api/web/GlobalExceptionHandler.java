package com.invoicetracker.api.web;

import com.invoicetracker.application.common.AuthenticationFailedException;
import com.invoicetracker.application.common.ConflictException;
import com.invoicetracker.application.common.NotFoundException;
import com.invoicetracker.application.common.PreconditionFailedException;
import com.invoicetracker.domain.common.BusinessRuleViolationException;
import com.invoicetracker.domain.common.InvalidStateTransitionException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every failure to an RFC 7807 problem response with a stable {@code code}. Unexpected errors
 * are logged with full detail but returned to the client as a generic 500 that leaks nothing.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemTypes.problem(HttpStatus.NOT_FOUND, "not_found", "Not found", e.getMessage());
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    ProblemDetail authenticationFailed(AuthenticationFailedException e) {
        return ProblemTypes.problem(
                HttpStatus.UNAUTHORIZED, "authentication_failed", "Authentication failed", e.getMessage());
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    ProblemDetail unauthenticated(org.springframework.security.core.AuthenticationException e) {
        return ProblemTypes.problem(
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Authentication required",
                "A valid access token is required.");
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return ProblemTypes.problem(HttpStatus.CONFLICT, e.code(), "Conflict", e.getMessage());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    ProblemDetail invalidTransition(InvalidStateTransitionException e) {
        ProblemDetail problem =
                ProblemTypes.problem(HttpStatus.CONFLICT, e.code(), "Invalid status transition", e.getMessage());
        problem.setProperty("currentStatus", e.from());
        problem.setProperty("requestedStatus", e.to());
        return problem;
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ProblemDetail businessRule(BusinessRuleViolationException e) {
        return ProblemTypes.problem(
                HttpStatus.UNPROCESSABLE_ENTITY, e.code(), "Business rule violated", e.getMessage());
    }

    @ExceptionHandler(PreconditionFailedException.class)
    ResponseEntity<ProblemDetail> preconditionFailed(PreconditionFailedException e) {
        ProblemDetail problem = ProblemTypes.problem(
                HttpStatus.PRECONDITION_FAILED, "version_mismatch", "Resource was modified", e.getMessage());
        problem.setProperty("currentVersion", e.currentVersion());
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                .eTag(Etags.of(e.currentVersion()))
                .body(problem);
    }

    @ExceptionHandler(PreconditionRequiredException.class)
    ProblemDetail preconditionRequired(PreconditionRequiredException e) {
        return ProblemTypes.problem(
                HttpStatus.PRECONDITION_REQUIRED, "precondition_required", "Precondition required", e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail optimisticLock(OptimisticLockingFailureException e) {
        log.info("Optimistic lock conflict: {}", e.getMessage());
        return ProblemTypes.problem(
                HttpStatus.CONFLICT,
                "concurrent_modification",
                "Concurrent modification",
                "The resource was changed by another request. Reload and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return ProblemTypes.problem(
                HttpStatus.CONFLICT,
                "conflict",
                "Conflict",
                "The request conflicts with existing data (it may already have been processed).");
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail badRequest(Exception e) {
        String detail = e instanceof MethodArgumentTypeMismatchException m
                ? "Invalid value for parameter '%s'".formatted(m.getName())
                : e.getMessage();
        return ProblemTypes.problem(HttpStatus.BAD_REQUEST, "bad_request", "Bad request", detail);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ProblemTypes.problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal_error",
                "Internal server error",
                "Something went wrong. Quote the correlation id when reporting this problem.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field",
                        fe.getField(),
                        "message",
                        fe.getDefaultMessage() == null ? "is invalid" : fe.getDefaultMessage()))
                .toList();
        ProblemDetail problem = ProblemTypes.problem(
                HttpStatus.BAD_REQUEST, "validation_failed", "Validation failed", "One or more fields are invalid.");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            @NonNull HandlerMethodValidationException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        ProblemDetail problem = ProblemTypes.problem(
                HttpStatus.BAD_REQUEST,
                "validation_failed",
                "Validation failed",
                "One or more parameters are invalid.");
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        ProblemDetail problem = ProblemTypes.problem(
                HttpStatus.BAD_REQUEST,
                "malformed_request",
                "Malformed request",
                "The request body could not be read. Check the JSON syntax and field types.");
        return ResponseEntity.badRequest().body(problem);
    }

    /** Adds code and correlation id to problems produced by Spring MVC itself (404 route, 405, ...). */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode statusCode,
            @NonNull WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            ProblemTypes.decorate(problem);
            if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
                problem.setProperty(
                        "code", HttpStatus.valueOf(statusCode.value()).name().toLowerCase());
            }
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
