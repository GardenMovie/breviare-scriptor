package com.breviare.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (a, b) -> a));
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(new ApiResponse.ApiError("VALIDATION_ERROR", "Request validation failed", fields)));
    }

    @ExceptionHandler(BreviareException.class)
    public ResponseEntity<ApiResponse<?>> handleBreviare(BreviareException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.error(new ApiResponse.ApiError(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiResponse<?>> handleErrorResponse(ErrorResponseException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status)
                .body(ApiResponse.error(new ApiResponse.ApiError(status.name(), status.getReasonPhrase())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(new ApiResponse.ApiError("INTERNAL_ERROR", "An unexpected error occurred")));
    }
}
