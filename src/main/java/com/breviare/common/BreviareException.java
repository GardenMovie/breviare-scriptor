package com.breviare.common;

import org.springframework.http.HttpStatus;

public class BreviareException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BreviareException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BreviareException notFound(String message) {
        return new BreviareException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static BreviareException gone(String message) {
        return new BreviareException(HttpStatus.GONE, "GONE", message);
    }

    public static BreviareException conflict(String message) {
        return new BreviareException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static BreviareException forbidden(String message) {
        return new BreviareException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static BreviareException rateLimited(String message) {
        return new BreviareException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", message);
    }

    public static BreviareException serviceUnavailable(String message) {
        return new BreviareException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
    }

    public static BreviareException badRequest(String message) {
        return new BreviareException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    public static BreviareException unprocessableEntity(String message) {
        return new BreviareException(HttpStatus.UNPROCESSABLE_ENTITY, "REJECTED", message);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
