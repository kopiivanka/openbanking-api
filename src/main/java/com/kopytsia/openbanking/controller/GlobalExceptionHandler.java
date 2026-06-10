package com.kopytsia.openbanking.controller;

import com.kopytsia.openbanking.exception.AccountNotFoundException;
import com.kopytsia.openbanking.exception.ExternalBankException;
import com.kopytsia.openbanking.exception.CurrencyMismatchException;
import com.kopytsia.openbanking.exception.InsufficientFundsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiError> handleAccountNotFound(AccountNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "account_not_found", e.getMessage()));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiError> handleInsufficientFunds(InsufficientFundsException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of(422, "insufficient_funds", e.getMessage()));
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ApiError> handleCurrencyMismatch(CurrencyMismatchException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of(422, "currency_mismatch", e.getMessage()));
    }

    @ExceptionHandler(ExternalBankException.class)
    public ResponseEntity<ApiError> handleExternal(ExternalBankException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiError.of(502, "external_bank_error", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "malformed_request",
                        "Request body is missing or could not be parsed"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiError.of(415, "unsupported_media_type",
                        "Content-Type " + e.getContentType() + " is not supported"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiError.of(405, "method_not_allowed",
                        "Method " + e.getMethod() + " is not supported for this endpoint"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "validation_failed", "Request validation failed", details));
    }

    /**
     * Re-throw so Spring Security's ExceptionTranslationFilter renders the proper
     * 401/403 via the configured entry point / access-denied handler, instead of
     * being flattened to a generic 500 by the catch-all below.
     */
    @ExceptionHandler({AuthenticationException.class, AccessDeniedException.class})
    public void rethrowSecurityException(RuntimeException e) {
        throw e;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "internal_error", "Unexpected error occurred"));
    }
}
