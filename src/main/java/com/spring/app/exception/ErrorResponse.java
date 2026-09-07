package com.spring.app.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * The single error body this API returns — from the filter chain, from method security and from
 * {@code @RestControllerAdvice} alike, so a client only ever has to parse one shape.
 *
 * <p>Three things are deliberately absent:
 *
 * <ul>
 *   <li><b>No exception class name, stack trace or SQL.</b> They describe the server's internals,
 *       which is reconnaissance for an attacker and noise for a client.</li>
 *   <li><b>No timestamp.</b> A failed login must be byte-identical whether the email is unknown or
 *       the password is wrong, and a per-response timestamp would make every body unique. The
 *       server clock belongs in the logs, where it is already recorded next to the request id.</li>
 *   <li><b>No echo of the submitted values.</b> {@link #fieldErrors} carries messages only, never
 *       the rejected input, so a bad password never travels back out.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Error response returned by every failing endpoint")
public class ErrorResponse {

    @Schema(description = "Stable machine-readable error code", example = "A003")
    private String code;

    @Schema(description = "Human-readable, non-sensitive description", example = "Invalid email or password")
    private String message;

    @Schema(description = "HTTP status code", example = "401")
    private Integer status;

    @Schema(description = "Request path that produced the error", example = "/api/v1/auth/login")
    private String path;

    @Schema(description = "Per-field validation messages; present only for validation failures")
    private Map<String, String> fieldErrors;

    /**
     * The code's own message. Use this for every code whose message takes no arguments — which is
     * all of them on the authentication and authorization paths.
     */
    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return of(errorCode, path, errorCode.getMessage());
    }

    /**
     * An explicit message, for a code whose message is a template that has already been rendered.
     * The caller is responsible for that text being safe to return; see
     * {@link ErrorCode#exception(Object...)}.
     */
    public static ErrorResponse of(ErrorCode errorCode, String path, String message) {
        return ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(message)
                .status(errorCode.getStatus().value())
                .path(path)
                .build();
    }
}
