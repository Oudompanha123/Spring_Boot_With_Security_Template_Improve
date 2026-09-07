package com.spring.app.exception;

import lombok.Getter;

/**
 * Application-level failure that already knows the code, status and message it should surface as.
 *
 * <p>Carrying an {@link ErrorCode} instead of a free-text message keeps the mapping in one place:
 * the advice translates the exception without a chain of {@code instanceof} checks, and no call
 * site can invent a status or leak internals into the response.
 *
 * <h2>Two distinct messages</h2>
 *
 * {@link #getMessage()} is for the log and may say anything useful. {@link #getClientMessage()} is
 * what the response body carries and is always either the code's own message or that message with
 * caller-supplied identifiers filled in. Keeping them separate is what lets a call site record
 * "Signup lost the unique-email race" for operators while the client still reads a plain
 * "An account with this email already exists".
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    /** Non-sensitive, already-rendered text for the response body. */
    private final String clientMessage;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), errorCode.getMessage());
    }

    /**
     * @param internalMessage detail for the log only — the client still sees
     *                        {@code errorCode.getMessage()}
     */
    public ApiException(ErrorCode errorCode, String internalMessage) {
        this(errorCode, internalMessage, errorCode.getMessage());
    }

    private ApiException(ErrorCode errorCode, String internalMessage, String clientMessage) {
        super(internalMessage);
        this.errorCode = errorCode;
        this.clientMessage = clientMessage;
    }

    /**
     * Builds an exception whose client message is the code's template rendered with {@code args}.
     * Reached through {@link ErrorCode#exception(Object...)}, which is the form to prefer at call
     * sites; the rendered text is used for the log as well, since it is already specific.
     */
    static ApiException of(ErrorCode errorCode, Object... args) {
        String rendered = errorCode.formatMessage(args);
        return new ApiException(errorCode, rendered, rendered);
    }
}
