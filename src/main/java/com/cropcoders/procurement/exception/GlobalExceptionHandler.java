package com.cropcoders.procurement.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns exceptions thrown by our controllers into consistent JSON errors. */
@RestControllerAdvice(basePackages = "com.cropcoders.procurement.controller")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException e) {
        int status = e.getStatus().value();
        return ResponseEntity.status(status)
                .body(new ApiError(status, e.getCode(), e.getMessage(), e.getParams(), Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(new ApiError(400, "VALIDATION", "Please check the details you entered.", Map.of(), fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "BAD_REQUEST", "Request body is missing or invalid."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException e) {
        log.warn("Database constraint violated: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(409).body(ApiError.of(409, "CONFLICT", "This conflicts with existing data."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(403).body(ApiError.of(403, "FORBIDDEN", "You do not have access to this."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleOther(Exception e) {
        // Spring's own web errors (wrong HTTP method, missing parameter, ...) already know their status.
        if (e instanceof ErrorResponse springError) {
            int status = springError.getStatusCode().value();
            return ResponseEntity.status(status).body(ApiError.of(status, "REQUEST_ERROR", e.getMessage()));
        }
        log.error("Unexpected error", e);
        return ResponseEntity.internalServerError()
                .body(ApiError.of(500, "SERVER_ERROR", "Something went wrong. Please try again."));
    }
}
