package com.spring.app.exception;

import com.spring.app.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    // ========================= Business Exceptions =========================

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("Business exception [{}]: {} - Path: {}", ex.getErrorCode(), ex.getMessage(), request.getRequestURI());

        Map<String, Object> errorDetails = new LinkedHashMap<>();
        errorDetails.put("errorCode", ex.getErrorCode());
        errorDetails.put("timestamp", LocalDateTime.now());
        errorDetails.put("path", request.getRequestURI());

        if (ex.getDetails() != null) {
            errorDetails.put("details", ex.getDetails());
        }

        return ResponseEntity.status(ex.getHttpStatus())
                .body(new ApiResponse<>(errorDetails, new ApiResponse.Status(ex.getHttpStatus().value(), ex.getMessage())));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        return createErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), ex.getErrorCode(), request);
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceAlreadyExists(ResourceAlreadyExistsException ex, HttpServletRequest request) {
        log.warn("Resource conflict: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        return createErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), ex.getErrorCode(), request);
    }

    // ========================= Security Exceptions =========================

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Authentication failed: {} - Path: {} - IP: {}", ex.getMessage(), request.getRequestURI(), getClientIpAddress(request));
        return createErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), "AUTHENTICATION_FAILED", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication error: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        return createErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication required", "AUTHENTICATION_REQUIRED", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {} - Path: {} - IP: {}", ex.getMessage(), request.getRequestURI(), getClientIpAddress(request));
        return createErrorResponse(HttpStatus.FORBIDDEN, "Access denied", "ACCESS_DENIED", request);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Object>> handleDisabled(DisabledException ex, HttpServletRequest request) {
        log.warn("Account disabled: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        return createErrorResponse(HttpStatus.UNAUTHORIZED, "Account is disabled", "ACCOUNT_DISABLED", request);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponse<Object>> handleLocked(LockedException ex, HttpServletRequest request) {
        log.warn("Account locked: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        return createErrorResponse(HttpStatus.UNAUTHORIZED, "Account is locked", "ACCOUNT_LOCKED", request);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiResponse<Object>> handleJwtException(JwtException ex, HttpServletRequest request) {
        log.warn("JWT error: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        return createErrorResponse(HttpStatus.UNAUTHORIZED, "Invalid or expired token", "INVALID_TOKEN", request);
    }

    // ========================= Validation Exceptions =========================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed: {} - Path: {}", fieldErrors, request.getRequestURI());

        Map<String, Object> errorDetails = new LinkedHashMap<>();
        errorDetails.put("errorCode", "VALIDATION_FAILED");
        errorDetails.put("fieldErrors", fieldErrors);
        errorDetails.put("timestamp", LocalDateTime.now());
        errorDetails.put("path", request.getRequestURI());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(errorDetails, new ApiResponse.Status(400, "Validation failed")));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(
            ConstraintViolationException ex) {

        Map<String, String> errors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.put(violation.getPropertyPath().toString(), violation.getMessage());
        }

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400,"Invalid Fields",Map.of(
                        "fieldError",errors
                )));
    }

    // ========================= HTTP Exceptions =========================

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("Method not supported: {} - Path: {}", ex.getMethod(), request.getRequestURI());
        String message = String.format("HTTP method '%s' is not supported for this endpoint", ex.getMethod());
        return createErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, message, "METHOD_NOT_ALLOWED", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        log.warn("Media type not supported: {} - Path: {}", ex.getContentType(), request.getRequestURI());
        return createErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Media type not supported", "MEDIA_TYPE_NOT_SUPPORTED", request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(NoHandlerFoundException ex, HttpServletRequest request) {
        log.warn("Endpoint not found: {} {} - Path: {}", ex.getHttpMethod(), ex.getRequestURL(), request.getRequestURI());
        return createErrorResponse(HttpStatus.NOT_FOUND, "Endpoint not found", "ENDPOINT_NOT_FOUND", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed JSON request: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        return createErrorResponse(HttpStatus.BAD_REQUEST, "Malformed JSON request", "MALFORMED_JSON", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("File size exceeded: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        return createErrorResponse(HttpStatus.PAYLOAD_TOO_LARGE, "File size exceeds maximum allowed limit", "FILE_SIZE_EXCEEDED", request);
    }

    // ========================= Parameter Exceptions =========================

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing parameter: {} - Path: {}", ex.getParameterName(), request.getRequestURI());
        String message = String.format("Required parameter '%s' is missing", ex.getParameterName());
        return createErrorResponse(HttpStatus.BAD_REQUEST, message, "MISSING_PARAMETER", request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        log.warn("Missing header: {} - Path: {}", ex.getHeaderName(), request.getRequestURI());
        String message = String.format("Required header '%s' is missing", ex.getHeaderName());
        return createErrorResponse(HttpStatus.BAD_REQUEST, message, "MISSING_HEADER", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Type mismatch for parameter: {} - Path: {}", ex.getName(), request.getRequestURI());
        String message = String.format("Invalid value for parameter '%s'", ex.getName());
        return createErrorResponse(HttpStatus.BAD_REQUEST, message, "PARAMETER_TYPE_MISMATCH", request);
    }

    // ========================= Database Exceptions =========================

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Data integrity violation: {} - Path: {}", ex.getMessage(), request.getRequestURI());

        String userMessage = "Data integrity constraint violation";
        if (ex.getMessage() != null) {
            if (ex.getMessage().contains("unique") || ex.getMessage().contains("duplicate")) {
                userMessage = "Duplicate entry - record already exists";
            } else if (ex.getMessage().contains("foreign key")) {
                userMessage = "Related record not found";
            }
        }

        return createErrorResponse(HttpStatus.CONFLICT, userMessage, "DATA_INTEGRITY_VIOLATION", request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataAccessException(DataAccessException ex, HttpServletRequest request) {
        log.error("Database access error: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Error", "INTERNAL_ERROR", request);
    }

    // ========================= Generic Exceptions =========================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        return createErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), "ILLEGAL_ARGUMENT", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex, HttpServletRequest request, WebRequest webRequest) {
        log.error("Unexpected error: {} - Path: {} - User: {}", ex.getMessage(), request.getRequestURI(),
                webRequest.getRemoteUser(), ex);

        String userMessage = "dev".equals(activeProfile) ? ex.getMessage() : "Internal server error";
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Error", "INTERNAL_ERROR", request);
    }

    // ========================= Helper Methods =========================

    private ResponseEntity<ApiResponse<Object>> createErrorResponse(HttpStatus status, String message, String errorCode, HttpServletRequest request) {
        Map<String, Object> errorDetails = new LinkedHashMap<>();
        errorDetails.put("errorCode", errorCode);
        errorDetails.put("timestamp", LocalDateTime.now());
//        errorDetails.put("path", request.getRequestURI());

        return ResponseEntity.status(status)
                .body(new ApiResponse<>(errorDetails, new ApiResponse.Status(status.value(), message)));
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedForHeader = request.getHeader("X-Forwarded-For");
        if (xForwardedForHeader == null) {
            return request.getRemoteAddr();
        } else {
            return xForwardedForHeader.split(",")[0].trim();
        }
    }
}