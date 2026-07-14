package com.spring.app.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;
    private final Object details;

    public BusinessException(String message) {
        super(message);
        this.errorCode = "BUSINESS_ERROR";
        this.httpStatus = HttpStatus.BAD_REQUEST;
        this.details = null;
    }

    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = HttpStatus.BAD_REQUEST;
        this.details = null;
    }

    public BusinessException(String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = "BUSINESS_ERROR";
        this.httpStatus = httpStatus;
        this.details = null;
    }

    public BusinessException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.details = null;
    }

    public BusinessException(String message, String errorCode, HttpStatus httpStatus, Object details) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.details = details;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "BUSINESS_ERROR";
        this.httpStatus = HttpStatus.BAD_REQUEST;
        this.details = null;
    }

    public BusinessException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = HttpStatus.BAD_REQUEST;
        this.details = null;
    }

    // Static factory methods for common business exceptions
    public static BusinessException notFound(String resource) {
        return new BusinessException(
                resource + " not found",
                "RESOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND
        );
    }

    public static BusinessException notFound(String resource, Object id) {
        return new BusinessException(
                String.format("%s with id %s not found", resource, id),
                "RESOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND
        );
    }

    public static BusinessException alreadyExists(String resource) {
        return new BusinessException(
                resource + " already exists",
                "RESOURCE_ALREADY_EXISTS",
                HttpStatus.CONFLICT
        );
    }

    public static BusinessException alreadyExists(String resource, String field, Object value) {
        return new BusinessException(
                String.format("%s with %s '%s' already exists", resource, field, value),
                "RESOURCE_ALREADY_EXISTS",
                HttpStatus.CONFLICT
        );
    }

    public static BusinessException invalidState(String message) {
        return new BusinessException(
                message,
                "INVALID_STATE",
                HttpStatus.BAD_REQUEST
        );
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(
                message,
                "FORBIDDEN",
                HttpStatus.FORBIDDEN
        );
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(
                message,
                "UNAUTHORIZED",
                HttpStatus.UNAUTHORIZED
        );
    }

    public static BusinessException validationFailed(String field, String message) {
        return new BusinessException(
                String.format("Validation failed for field '%s': %s", field, message),
                "VALIDATION_FAILED",
                HttpStatus.BAD_REQUEST
        );
    }
}