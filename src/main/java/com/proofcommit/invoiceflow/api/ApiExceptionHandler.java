package com.proofcommit.invoiceflow.api;

import com.proofcommit.invoiceflow.invoice.InvoiceNotFoundException;
import com.proofcommit.invoiceflow.invoice.InvoiceRuleException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvoiceNotFoundException.class)
    ResponseEntity<Problem> notFound(InvoiceNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Invoice not found", exception.getMessage(), request, null);
    }

    @ExceptionHandler(InvoiceRuleException.class)
    ResponseEntity<Problem> ruleViolation(InvoiceRuleException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Invoice rule violated", exception.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Problem> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
            .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return problem(HttpStatus.BAD_REQUEST, "Request validation failed", "Correct the invalid fields", request, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Problem> malformed(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request", "Request body must be valid JSON", request, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Problem> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure for {} {}", request.getMethod(), request.getRequestURI(), exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "The request could not be completed", request, null);
    }

    private static ResponseEntity<Problem> problem(
        HttpStatus status, String title, String detail, HttpServletRequest request, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new Problem(
            status.value(), title, detail, request.getRequestURI(), Instant.now(), fields));
    }

    public record Problem(
        int status, String title, String detail, String instance, Instant timestamp, Map<String, String> fields) {
    }
}
