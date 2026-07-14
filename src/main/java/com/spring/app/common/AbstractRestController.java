package com.spring.app.common;

import com.spring.app.payload.EmptyJson;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * Abstract base controller providing common REST response patterns
 * All controllers can extend this to get consistent response formatting
 */
public abstract class AbstractRestController {

    // ========================= SUCCESS RESPONSES =========================

    /**
     * 200 OK - Success with data
     */
    protected <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * 200 OK - Success with custom message
     */
    protected <T> ResponseEntity<ApiResponse<T>> ok(T data, String message) {
        return ResponseEntity.ok(ApiResponse.success(data, message));
    }

    /**
     * 200 OK - Success with no data (empty object)
     */
    protected ResponseEntity<ApiResponse<EmptyJson>> ok() {
        return ResponseEntity.ok(ApiResponse.success(new EmptyJson()));
    }

    /**
     * 200 OK - Success with a custom message and no data
     */
    protected ResponseEntity<ApiResponse<EmptyJson>> ok(String message) {
        return ResponseEntity.ok(ApiResponse.success(new EmptyJson(), message));
    }

    /**
     * 201 Created - Resource created successfully
     */
    protected <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, HttpStatus.CREATED.value(), "Resource created successfully"));
    }

    /**
     * 201 Created - Resource created with custom message
     */
    protected <T> ResponseEntity<ApiResponse<T>> created(T data, String message) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, HttpStatus.CREATED.value(), message));
    }

    /**
     * 201 Created - Resource created with Location header
     */
    protected <T> ResponseEntity<ApiResponse<T>> created(T data, String locationPath, Object... pathVariables) {
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path(locationPath)
                .buildAndExpand(pathVariables)
                .toUri();

        return ResponseEntity.created(location)
                .body(ApiResponse.success(data, HttpStatus.CREATED.value(), "Resource created successfully"));
    }

    /**
     * 202 Accepted - Request accepted for processing
     */
    protected <T> ResponseEntity<ApiResponse<T>> accepted(T data) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(data, HttpStatus.ACCEPTED.value(), "Request accepted"));
    }

    /**
     * 204 No Content - Success with no response body
     */
    protected ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }

    // ========================= ERROR RESPONSES =========================

    /**
     * 400 Bad Request
     */
    protected <T> ResponseEntity<ApiResponse<T>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.badRequest(message));
    }

    /**
     * 400 Bad Request with validation errors
     */
    protected ResponseEntity<ApiResponse<Map<String, String>>> badRequest(Map<String, String> validationErrors) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), "Validation failed", validationErrors));
    }

    /**
     * 401 Unauthorized
     */
    protected <T> ResponseEntity<ApiResponse<T>> unauthorized(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.unauthorized(message));
    }

    /**
     * 403 Forbidden
     */
    protected <T> ResponseEntity<ApiResponse<T>> forbidden(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.forbidden(message));
    }

    /**
     * 404 Not Found
     */
    protected <T> ResponseEntity<ApiResponse<T>> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.notFound(message));
    }

    /**
     * 409 Conflict
     */
    protected <T> ResponseEntity<ApiResponse<T>> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.conflict(message));
    }

    /**
     * 422 Unprocessable Entity
     */
    protected <T> ResponseEntity<ApiResponse<T>> unprocessableEntity(String message) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(HttpStatus.UNPROCESSABLE_ENTITY.value(), message));
    }

    /**
     * 500 Internal Server Error
     */
    protected <T> ResponseEntity<ApiResponse<T>> internalServerError(String message) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.internalServerError(message));
    }

    // ========================= PAGINATION RESPONSES =========================

    /**
     * 200 OK with paginated data
     */
    protected <T> ResponseEntity<ApiResponse<PaginatedResponse<T>>> okPaginated(Page<T> page) {
        PaginatedResponse<T> paginatedResponse = PaginatedResponse.from(page);
        return ResponseEntity.ok(ApiResponse.success(paginatedResponse));
    }

    /**
     * 200 OK with paginated data and custom message
     */
    protected <T> ResponseEntity<ApiResponse<PaginatedResponse<T>>> okPaginated(Page<T> page, String message) {
        PaginatedResponse<T> paginatedResponse = PaginatedResponse.from(page);
        return ResponseEntity.ok(ApiResponse.success(paginatedResponse, message));
    }

    // ========================= CUSTOM RESPONSES =========================

    /**
     * Custom status code with data
     */
    protected <T> ResponseEntity<ApiResponse<T>> response(HttpStatus status, T data, String message) {
        return ResponseEntity.status(status)
                .body(ApiResponse.success(data, status.value(), message));
    }

    /**
     * Custom headers with response
     */
    protected <T> ResponseEntity<ApiResponse<T>> responseWithHeaders(T data, HttpHeaders headers) {
        return ResponseEntity.ok()
                .headers(headers)
                .body(ApiResponse.success(data));
    }

    /**
     * Response with custom status and headers
     */
    protected <T> ResponseEntity<ApiResponse<T>> response(HttpStatus status, T data, String message, HttpHeaders headers) {
        return ResponseEntity.status(status)
                .headers(headers)
                .body(ApiResponse.success(data, status.value(), message));
    }

    // ========================= BUILDER PATTERN =========================

    /**
     * Response builder for complex responses
     */
    protected ResponseBuilder builder() {
        return new ResponseBuilder();
    }

    public static class ResponseBuilder {
        private HttpStatus status = HttpStatus.OK;
        private Object data;
        private String message = "Success";
        private HttpHeaders headers = new HttpHeaders();

        public ResponseBuilder status(HttpStatus status) {
            this.status = status;
            return this;
        }

        public ResponseBuilder data(Object data) {
            this.data = data;
            return this;
        }

        public ResponseBuilder message(String message) {
            this.message = message;
            return this;
        }

        public ResponseBuilder header(String name, String value) {
            this.headers.add(name, value);
            return this;
        }

        public ResponseBuilder headers(HttpHeaders headers) {
            this.headers.putAll(headers);
            return this;
        }

        @SuppressWarnings("unchecked")
        public <T> ResponseEntity<ApiResponse<T>> build() {
            ApiResponse<T> apiResponse = (ApiResponse<T>) ApiResponse.success(data != null ? data : new EmptyJson(), status.value(), message);
            return ResponseEntity.status(status)
                    .headers(headers)
                    .body(apiResponse);
        }
    }

    // ========================= UTILITY METHODS =========================

    /**
     * Check if data exists, return 404 if null
     */
    protected <T> ResponseEntity<ApiResponse<T>> okOrNotFound(T data, String notFoundMessage) {
        if (data == null) {
            return notFound(notFoundMessage);
        }
        return ok(data);
    }

    /**
     * Conditional response based on success flag
     */
    protected <T> ResponseEntity<ApiResponse<T>> conditionalResponse(boolean success, T data, String successMessage, String errorMessage) {
        if (success) {
            return ok(data, successMessage);
        } else {
            return badRequest(errorMessage);
        }
    }
}