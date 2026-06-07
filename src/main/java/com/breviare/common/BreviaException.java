package com.breviare.common;

import org.springframework.http.HttpStatus;

public class BreviaException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BreviaException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BreviaException notFound(String message) {
        return new BreviaException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static BreviaException gone(String message) {
        return new BreviaException(HttpStatus.GONE, "GONE", message);
    }

    public static BreviaException conflict(String message) {
        return new BreviaException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static BreviaException forbidden(String message) {
        return new BreviaException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static BreviaException rateLimited(String message) {
        return new BreviaException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", message);
    }

    public static BreviaException serviceUnavailable(String message) {
        return new BreviaException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
    }

    public static BreviaException badRequest(String message) {
        return new BreviaException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
