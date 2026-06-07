package com.breviare.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> error(ApiError error) {
        return new ApiResponse<>(null, error);
    }

    public record ApiError(String code, String message, Object details) {
        public ApiError(String code, String message) {
            this(code, message, null);
        }
    }
}
