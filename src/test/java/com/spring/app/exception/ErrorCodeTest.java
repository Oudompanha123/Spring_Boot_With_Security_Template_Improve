package com.spring.app.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The error catalogue itself: message rendering, the exception factory, and the two rules that keep
 * templated messages from re-opening holes the rest of the security code closes.
 */
class ErrorCodeTest {

    /**
     * The complete set of codes whose message takes arguments.
     *
     * <p>This is an allowlist, not a description. Adding a placeholder to any other code fails
     * {@link #templatedCodesAreOnlyTheAllowlistedOnes()}, which forces whoever does it to come here
     * and confirm the arguments are identifiers rather than credentials, emails or submitted values
     * — because those arguments end up in the response body.
     */
    private static final Set<ErrorCode> TEMPLATED = EnumSet.of(ErrorCode.BOOK_NOT_FOUND);

    // ========================= rendering =========================

    @Test
    @DisplayName("arguments are interpolated into a templated message")
    void formatsWithArguments() {
        assertThat(ErrorCode.BOOK_NOT_FOUND.formatMessage(42L)).isEqualTo("Book not found: 42");
    }

    @Test
    @DisplayName("a numeric id is not formatted as a quantity")
    void idsKeepTheirDigits() {
        // Regression: MessageFormat applies the default locale's number format to a numeric
        // argument, so this rendered as "Book not found: 9,999" - and "9 999" under a French
        // locale. An id is an identifier, and the body must not vary with the server's locale.
        assertThat(ErrorCode.BOOK_NOT_FOUND.formatMessage(9999L)).isEqualTo("Book not found: 9999");
        assertThat(ErrorCode.BOOK_NOT_FOUND.formatMessage(1234567)).isEqualTo("Book not found: 1234567");

        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            assertThat(ErrorCode.BOOK_NOT_FOUND.formatMessage(9999L)).isEqualTo("Book not found: 9999");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("with no arguments the message is returned exactly as written")
    void noArgumentsMeansNoFormatting() {
        // MessageFormat eats single quotes as escape characters, so a parameterless message must
        // never go through it. Verified against the raw constant rather than a literal, so this
        // keeps holding if the wording changes.
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.formatMessage()).isEqualTo(code.getMessage());
            assertThat(code.formatMessage((Object[]) null)).isEqualTo(code.getMessage());
        }
    }

    @Test
    @DisplayName("an apostrophe in a parameterless message survives")
    void apostrophesAreNotSwallowed() {
        // The regression this guards: MessageFormat.format("Doesn't", <no args>) -> "Doesnt".
        assertThat(ErrorCode.ACCESS_DENIED.formatMessage())
                .isEqualTo("You do not have permission to access this resource");
    }

    // ========================= the exception factory =========================

    @Test
    @DisplayName("exception(args) carries the code, the status and the rendered message")
    void exceptionFactory() {
        ApiException e = ErrorCode.BOOK_NOT_FOUND.exception(7L);

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BOOK_NOT_FOUND);
        assertThat(e.getErrorCode().getStatus().value()).isEqualTo(404);
        assertThat(e.getClientMessage()).isEqualTo("Book not found: 7");
        // The rendered text is specific enough to be the log message too.
        assertThat(e.getMessage()).isEqualTo("Book not found: 7");
    }

    @Test
    @DisplayName("an internal message stays internal: the client still gets the code's own message")
    void internalMessageIsNotLeaked() {
        ApiException e = new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS, "Signup lost the unique-email race");

        assertThat(e.getMessage()).isEqualTo("Signup lost the unique-email race");
        assertThat(e.getClientMessage()).isEqualTo("An account with this email already exists");
    }

    // ========================= the two rules =========================

    @Test
    @DisplayName("only allowlisted codes have templated messages")
    void templatedCodesAreOnlyTheAllowlistedOnes() {
        Set<ErrorCode> actuallyTemplated = Arrays.stream(ErrorCode.values())
                .filter(code -> code.getMessage().contains("{"))
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(ErrorCode.class)));

        assertThat(actuallyTemplated).isEqualTo(TEMPLATED);
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("no authentication or authorization code is templated")
    void authCodesAreNeverTemplated(ErrorCode code) {
        if (!code.getCode().startsWith("A")) {
            return;
        }
        // A failed login has to be byte-identical between an unknown email and a wrong password.
        // A message that varies with its input cannot be, and a 403 that names the account being
        // locked out would confirm the account exists.
        assertThat(code.getMessage()).doesNotContain("{");
    }

    @Test
    @DisplayName("codes and statuses are unique and consistent")
    void catalogueIsWellFormed() {
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::getCode))
                .doesNotHaveDuplicates();

        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.getCode()).matches("[A-Z][0-9]{3}");
            assertThat(code.getMessage()).isNotBlank();
            assertThat(code.getStatus().isError()).isTrue();
        }
    }
}
