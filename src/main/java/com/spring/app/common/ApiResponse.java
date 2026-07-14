package com.spring.app.common;

import com.spring.app.payload.EmptyJson;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private T data;
    private Status status;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Status {
        private int code;
        private String message;
    }

    // Success response factory methods
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, new Status(200, "Success"));
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(data, new Status(200, message));
    }

    public static <T> ApiResponse<T> success(T data, int code, String message) {
        return new ApiResponse<>(data, new Status(code, message));
    }

    // Error response factory methods - FIXED for empty JSON object
    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> error(int code, String message) {
        return (ApiResponse<T>) new ApiResponse<>(new EmptyJson(), new Status(code, message));
    }

    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> error(String message) {
        return (ApiResponse<T>) new ApiResponse<>(new EmptyJson(), new Status(400, message));
    }

    public static <T> ApiResponse<T> error(int code, String message, T errorData) {
        return new ApiResponse<>(errorData, new Status(code, message));
    }

    // Common HTTP status factory methods
    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> badRequest(String message) {
        return (ApiResponse<T>) new ApiResponse<>(new EmptyJson(), new Status(400, message));
    }

    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> unauthorized(String message) {
        return (ApiResponse<T>) new ApiResponse<>(new EmptyJson(), new Status(401, message));
    }

    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> forbidden(String message) {
        return (ApiResponse<T>) new ApiResponse<>(new EmptyJson(), new Status(403, message));
    }

    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> notFound(String message) {
        return (ApiResponse<T>) new ApiResponse<>(new EmptyJson(), new Status(404, message));
    }

    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> conflict(String message) {
        return (ApiResponse<T>) new ApiResponse<>(new EmptyJson(), new Status(409, message));
    }

    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> internalServerError(String message) {
        return (ApiResponse<T>) new ApiResponse<>(new EmptyJson(), new Status(500, message));
    }

    // Builder pattern for more complex responses
    public static <T> ApiResponseBuilder<T> builder() {
        return new ApiResponseBuilder<>();
    }

    public static class ApiResponseBuilder<T> {
        private T data;
        private int code = 200;
        private String message = "Success";

        public ApiResponseBuilder<T> data(T data) {
            this.data = data;
            return this;
        }

        public ApiResponseBuilder<T> code(int code) {
            this.code = code;
            return this;
        }

        public ApiResponseBuilder<T> message(String message) {
            this.message = message;
            return this;
        }

        public ApiResponse<T> build() {
            return new ApiResponse<>(data, new Status(code, message));
        }
    }
}