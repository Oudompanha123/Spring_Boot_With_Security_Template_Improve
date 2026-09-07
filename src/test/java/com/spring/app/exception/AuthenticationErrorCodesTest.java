package com.spring.app.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/** The shared authentication-failure mapping used by the entry point and the advice. */
class AuthenticationErrorCodesTest {

    @Test
    @DisplayName("an unknown account maps to A003, never to U001")
    void unknownAccountIsIndistinguishableFromAWrongPassword() {
        ErrorCode unknownAccount = AuthenticationErrorCodes.of(new UsernameNotFoundException("no such user"));
        ErrorCode wrongPassword = AuthenticationErrorCodes.of(new BadCredentialsException("bad password"));

        // Same code for both, which is what keeps the two failures indistinguishable.
        assertThat(unknownAccount).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(wrongPassword).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(unknownAccount).isNotEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("a locked account maps to A006 / 403")
    void lockedAccount() {
        ErrorCode code = AuthenticationErrorCodes.of(new LockedException("locked"));

        assertThat(code).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
        assertThat(code.getStatus().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("a disabled account maps to A007 / 403")
    void disabledAccount() {
        ErrorCode code = AuthenticationErrorCodes.of(new DisabledException("disabled"));

        assertThat(code).isEqualTo(ErrorCode.ACCOUNT_DISABLED);
        assertThat(code.getStatus().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("a provider failure is a 500, not a 401")
    void providerFailureIsServerError() {
        ErrorCode wrapped = AuthenticationErrorCodes.of(
                new InternalAuthenticationServiceException("database down"));
        ErrorCode direct = AuthenticationErrorCodes.of(new AuthenticationServiceException("boom"));

        // Blaming the caller's credentials for a server outage sends them to fix the wrong thing.
        assertThat(wrapped).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(direct).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(wrapped.getStatus().value()).isEqualTo(500);
    }

    @Test
    @DisplayName("no usable credentials maps to A001")
    void insufficientAuthentication() {
        ErrorCode code = AuthenticationErrorCodes.of(
                new InsufficientAuthenticationException("full authentication required"));

        assertThat(code).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(code.getStatus().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("account states this model does not have fall back to A001")
    void unmodelledAccountStates() {
        // CustomUserDetails reports both as non-expired, so these cannot occur here; a code of
        // their own would document a rule that does not exist.
        assertThat(AuthenticationErrorCodes.of(new AccountExpiredException("expired")))
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(AuthenticationErrorCodes.of(new CredentialsExpiredException("expired")))
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a null exception still yields a code")
    void nullIsTolerated() {
        assertThat(AuthenticationErrorCodes.of(null)).isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
