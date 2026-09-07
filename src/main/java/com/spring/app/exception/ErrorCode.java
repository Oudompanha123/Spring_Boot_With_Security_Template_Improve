package com.spring.app.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import java.text.MessageFormat;

/**
 * Every error this API can return, as a stable machine-readable code.
 *
 * <p>Clients branch on {@link #getCode()}, never on the message: the code is part of the contract
 * and the message is free to be reworded or localised. Two rules the codes below encode on purpose:
 *
 * <ul>
 *   <li>{@link #INVALID_CREDENTIALS} covers both "no such email" and "wrong password". One code,
 *       one message, one HTTP status — anything finer is a user-enumeration oracle.</li>
 *   <li>{@link #TOKEN_EXPIRED} and {@link #TOKEN_INVALID} stay distinct. Expiry is the normal
 *       lifecycle of a valid token and tells the client to refresh; an invalid signature means the
 *       token was forged or mangled and refreshing will not help.</li>
 * </ul>
 *
 * <h2>Templated messages</h2>
 *
 * A message may carry {@link MessageFormat} placeholders, filled in through
 * {@link #exception(Object...)}:
 *
 * <pre>{@code throw ErrorCode.BOOK_NOT_FOUND.exception(id);   // -> "Book not found: 42" }</pre>
 *
 * Two constraints on that, both enforced by {@code ErrorCodeTest}:
 *
 * <ul>
 *   <li><b>Arguments must never be user-supplied credentials, emails or submitted values.</b> They
 *       land in the response body, so interpolating them would re-open the enumeration and
 *       value-echo holes the rest of this class is built to close. Ids and resource names only.</li>
 *   <li><b>No authentication or authorization code is templated.</b> A failed login must be
 *       byte-identical between an unknown email and a wrong password, and a message that varies
 *       with its input cannot be.</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ----- users -----
    USER_NOT_FOUND("U001", HttpStatus.NOT_FOUND, "User not found"),
    EMAIL_ALREADY_EXISTS("U002", HttpStatus.CONFLICT, "An account with this email already exists"),

    // ----- authentication / authorization -----
    // Never templated: see the class javadoc.
    UNAUTHORIZED("A001", HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource"),
    ACCESS_DENIED("A002", HttpStatus.FORBIDDEN, "You do not have permission to access this resource"),
    INVALID_CREDENTIALS("A003", HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    TOKEN_EXPIRED("A004", HttpStatus.UNAUTHORIZED, "Access token has expired"),
    TOKEN_INVALID("A005", HttpStatus.UNAUTHORIZED, "Access token is invalid"),
    ACCOUNT_LOCKED("A006", HttpStatus.FORBIDDEN, "Account is locked after too many failed login attempts"),
    ACCOUNT_DISABLED("A007", HttpStatus.FORBIDDEN, "Account is disabled"),
    REFRESH_TOKEN_INVALID("A008", HttpStatus.UNAUTHORIZED, "Refresh token is invalid or has been revoked"),
    REFRESH_TOKEN_EXPIRED("A009", HttpStatus.UNAUTHORIZED, "Refresh token has expired"),

    // ----- books -----
    BOOK_NOT_FOUND("B001", HttpStatus.NOT_FOUND, "Book not found: {0}"),

    // ----- request / generic -----
    VALIDATION_FAILED("V001", HttpStatus.BAD_REQUEST, "Request validation failed"),
    MALFORMED_REQUEST("C001", HttpStatus.BAD_REQUEST, "Request body could not be read"),
    METHOD_NOT_ALLOWED("C002", HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not supported for this endpoint"),
    UNSUPPORTED_MEDIA_TYPE("C003", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Media type not supported"),
    PAYLOAD_TOO_LARGE("C004", HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded payload is too large"),
    RESOURCE_NOT_FOUND("C005", HttpStatus.NOT_FOUND, "Resource not found"),
    RESOURCE_CONFLICT("C006", HttpStatus.CONFLICT, "Resource conflict"),
    BUSINESS_RULE_VIOLATION("C007", HttpStatus.BAD_REQUEST, "Request violates a business rule"),
    INTERNAL_ERROR("S001", HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final String code;
    private final HttpStatus status;

    /** The raw message, {@link MessageFormat} placeholders included. */
    private final String message;

    /**
     * Renders {@link #getMessage()} with the given arguments.
     *
     * <p>With no arguments the message is returned untouched rather than run through
     * {@link MessageFormat}. That is not just an optimisation: {@code MessageFormat} treats a single
     * quote as an escape character, so formatting {@code "Doesn't exist"} with no arguments would
     * silently return {@code "Doesnt exist"}. Short-circuiting keeps every parameterless message
     * exactly as written here. (In a message that <em>does</em> take arguments, a literal quote has
     * to be doubled: {@code ''{0}''}.)
     */
    public String formatMessage(Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        // Arguments are rendered as text before formatting, because MessageFormat treats a numeric
        // argument as a quantity and applies the default locale's number format: id 9999 comes out
        // as "9,999" on an English server and "9 999" on a French one. These are identifiers, not
        // quantities, and an error body should not change shape with the server's locale.
        Object[] asText = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            asText[i] = String.valueOf(args[i]);
        }
        return MessageFormat.format(message, asText);
    }

    /**
     * The idiomatic way to fail with this code.
     *
     * <pre>{@code throw ErrorCode.BOOK_NOT_FOUND.exception(id);}</pre>
     *
     * <p>The rendered message goes to the client, so pass identifiers — never a password, a token,
     * an email or any other submitted value. When the detail is for operators rather than callers,
     * use {@code new ApiException(code, internalMessage)} instead: that keeps the detail in the log
     * and still returns this code's own message.
     */
    public ApiException exception(Object... args) {
        return ApiException.of(this, args);
    }
}
