package com.spring.app.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates every exception that escapes a controller into an {@link ErrorResponse}.
 *
 * <h2>Why this exists alongside the entry point and the denied handler</h2>
 *
 * Spring Security has two distinct failure paths and they are handled in two different places:
 *
 * <ul>
 *   <li><b>Filter chain</b> — a request refused by {@code authorizeHttpRequests} never reaches a
 *       controller, so no {@code @ExceptionHandler} can see it. Those are
 *       {@code CustomAuthenticationEntryPoint} (401) and {@code CustomAccessDeniedHandler} (403).</li>
 *   <li><b>Method security</b> — a {@code @PreAuthorize} denial is thrown while the controller
 *       method is being invoked, inside the dispatch. It is handled <em>here</em>. Registering only
 *       the filter-chain handlers is the classic mistake: method-security denials then fall through
 *       to the servlet container and a JSON API answers with an HTML error page.</li>
 * </ul>
 *
 * <h2>What never appears in a response body</h2>
 *
 * No stack trace, no exception class name, no SQL, no framework message. Those go to the log, where
 * operators can see them; the client gets a stable {@link ErrorCode} and a sentence written for a
 * human. {@code ex.getMessage()} is treated as untrusted for output purposes: it routinely carries
 * SQL fragments ({@link DataIntegrityViolationException}), internal class names, and echoes of
 * whatever the caller submitted.
 */
@RestControllerAdvice
@Slf4j
public class GlobalApiExceptionHandler {

    // ========================= security =========================

    /**
     * Method-security denials, i.e. {@code @PreAuthorize}/{@code @Secured} refusing the call.
     * Spring Security 6 throws {@code AuthorizationDeniedException}, a subclass of
     * {@link AccessDeniedException}, so this one handler covers both.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("Access denied by method security: {} {}", request.getMethod(), request.getRequestURI());
        return build(ErrorCode.ACCESS_DENIED, request);
    }

    /*
     * The authentication handlers below all resolve their code through AuthenticationErrorCodes,
     * which is shared with CustomAuthenticationEntryPoint. They exist as separate handlers only so
     * each cause gets its own log line - the response mapping itself lives in one place.
     */

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException e, HttpServletRequest request) {
        // Deliberately identical whether the email is unknown or the password is wrong: same code,
        // same message, same status. Anything that differs between the two is an oracle that tells
        // an attacker which emails have accounts.
        log.warn("Failed login attempt: {} {}", request.getMethod(), request.getRequestURI());
        return build(AuthenticationErrorCodes.of(e), request);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLocked(LockedException e, HttpServletRequest request) {
        log.warn("Login attempt on a locked account: {}", request.getRequestURI());
        return build(AuthenticationErrorCodes.of(e), request);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(DisabledException e, HttpServletRequest request) {
        log.warn("Login attempt on a disabled account: {}", request.getRequestURI());
        return build(AuthenticationErrorCodes.of(e), request);
    }

    /**
     * The authentication provider itself failed — the database was unreachable, a collaborator
     * threw. Nothing is wrong with the submitted credentials, so this is a 500 with a full stack
     * trace in the log, not a 401 that sends the caller off to check their password.
     */
    @ExceptionHandler(AuthenticationServiceException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationService(AuthenticationServiceException e,
                                                                     HttpServletRequest request) {
        log.error("Authentication provider failed on {} {}", request.getMethod(), request.getRequestURI(), e);
        return build(AuthenticationErrorCodes.of(e), request);
    }

    /** Anything else the authentication machinery throws; usually "no usable credentials". */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e, HttpServletRequest request) {
        log.warn("Authentication failure [{}]: {}", e.getClass().getSimpleName(), request.getRequestURI());
        return build(AuthenticationErrorCodes.of(e), request);
    }

    // ========================= application =========================

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e, HttpServletRequest request) {
        ErrorCode code = e.getErrorCode();
        // Two messages, deliberately: getMessage() may carry internal detail and goes to the log,
        // while getClientMessage() is the vetted text (the code's own, or its template rendered
        // with identifiers) that is safe to return.
        log.warn("Request failed [{}] {} {}: {}",
                code.getCode(), request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, request.getRequestURI(), e.getClientMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException e, HttpServletRequest request) {
        log.warn("Resource not found: {}", request.getRequestURI());
        return build(ErrorCode.RESOURCE_NOT_FOUND, request);
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleResourceExists(ResourceAlreadyExistsException e, HttpServletRequest request) {
        log.warn("Resource conflict: {}", request.getRequestURI());
        return build(ErrorCode.RESOURCE_CONFLICT, request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest request) {
        log.warn("Business rule violation [{}]: {}", e.getErrorCode(), request.getRequestURI());
        return build(ErrorCode.BUSINESS_RULE_VIOLATION, request);
    }

    // ========================= request validation =========================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            fieldErrors.put(field, error.getDefaultMessage());
        });

        // Field names and messages only. The rejected values are never echoed: for a signup that
        // would put the submitted password straight into the response body.
        log.warn("Validation failed on {}: {}", request.getRequestURI(), fieldErrors.keySet());

        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ErrorResponse.builder()
                        .code(ErrorCode.VALIDATION_FAILED.getCode())
                        .message(ErrorCode.VALIDATION_FAILED.getMessage())
                        .status(ErrorCode.VALIDATION_FAILED.getStatus().value())
                        .path(request.getRequestURI())
                        .fieldErrors(fieldErrors)
                        .build());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            fieldErrors.put(String.valueOf(violation.getPropertyPath()), violation.getMessage());
        }
        log.warn("Constraint violation on {}: {}", request.getRequestURI(), fieldErrors.keySet());

        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ErrorResponse.builder()
                        .code(ErrorCode.VALIDATION_FAILED.getCode())
                        .message(ErrorCode.VALIDATION_FAILED.getMessage())
                        .status(ErrorCode.VALIDATION_FAILED.getStatus().value())
                        .path(request.getRequestURI())
                        .fieldErrors(fieldErrors)
                        .build());
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequestParameters(Exception e, HttpServletRequest request) {
        log.warn("Malformed request parameters on {}", request.getRequestURI());
        return build(ErrorCode.VALIDATION_FAILED, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e, HttpServletRequest request) {
        // The framework message quotes the offending JSON, so it stays in the log only.
        log.warn("Unreadable request body on {}", request.getRequestURI());
        return build(ErrorCode.MALFORMED_REQUEST, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return build(ErrorCode.PAYLOAD_TOO_LARGE, request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(NoHandlerFoundException e, HttpServletRequest request) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, request);
    }

    /**
     * A request for a static resource that is not there.
     *
     * <p>Handled by its concrete type because it does not extend
     * {@link ErrorResponseException}: like {@link NoHandlerFoundException}, it extends
     * {@code ServletException} and merely <em>implements</em> Spring's {@code ErrorResponse}
     * interface, which an {@code @ExceptionHandler} cannot target (the interface is not a
     * {@code Throwable}).
     *
     * <p>Worth having: Spring Boot maps unmatched paths to the static-resource handler, so this
     * fires both for a missing file under {@code /api/v1/image/**} and for any unmapped URL. Left
     * to the catch-all it came back as {@code 500 S001} — a client mistake reported as a server
     * failure, logged at ERROR alongside real ones.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("No such resource: {} {}", request.getMethod(), request.getRequestURI());
        return build(ErrorCode.RESOURCE_NOT_FOUND, request);
    }

    /**
     * Framework exceptions that already carry the status they mean.
     *
     * <p>Without this they fall through to {@link #handleUnexpected}, and a request for a static
     * resource that does not exist — a missing image under {@code /api/v1/image/**}, or
     * {@code /swagger-ui.html} when the docs are switched off — is answered with
     * {@code 500 S001} instead of a 404. The catch-all is a backstop for bugs, and a
     * {@code NoResourceFoundException} is not a bug; letting it reach the catch-all reports a
     * client mistake as a server failure and buries a real 500 in the same log line.
     *
     * <p>The status comes from the exception, and the code from the status. The more specific
     * handlers above (validation, method, media type) still win for the cases they name.
     */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ErrorResponse> handleFrameworkError(ErrorResponseException e, HttpServletRequest request) {
        int status = e.getStatusCode().value();
        ErrorCode code = switch (status) {
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 409 -> ErrorCode.RESOURCE_CONFLICT;
            default -> status >= 500 ? ErrorCode.INTERNAL_ERROR : ErrorCode.MALFORMED_REQUEST;
        };

        if (code == ErrorCode.INTERNAL_ERROR) {
            log.error("Framework error {} on {} {}", status, request.getMethod(), request.getRequestURI(), e);
        } else {
            log.warn("Framework error {} [{}] on {} {}",
                    status, code.getCode(), request.getMethod(), request.getRequestURI());
        }
        return build(code, request);
    }

    // ========================= persistence and fallback =========================

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e, HttpServletRequest request) {
        // The exception message contains the failing SQL and constraint name. Log it, never ship it.
        log.error("Data integrity violation on {} {}", request.getMethod(), request.getRequestURI(), e);
        return build(ErrorCode.RESOURCE_CONFLICT, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        // Full detail to the log, a bare S001 to the client.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), e);
        return build(ErrorCode.INTERNAL_ERROR, request);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode errorCode, HttpServletRequest request) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, request.getRequestURI()));
    }
}
