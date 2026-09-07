package com.spring.app.exception;

import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * The one mapping from a Spring Security {@link AuthenticationException} to an {@link ErrorCode}.
 *
 * <p>Authentication can fail in two places, and until they share this mapping they answer
 * differently for the same cause:
 *
 * <ul>
 *   <li><b>Filter chain</b> — {@code CustomAuthenticationEntryPoint} is handed the exception by
 *       {@code ExceptionTranslationFilter}.</li>
 *   <li><b>Dispatch</b> — {@code GlobalApiExceptionHandler} catches whatever
 *       {@code AuthenticationManager.authenticate()} threw inside the login endpoint.</li>
 * </ul>
 *
 * <p>Both now call {@link #of(AuthenticationException)}, so a locked account is {@code A006} no
 * matter which path reported it, and no future edit can drift one path away from the other.
 */
public final class AuthenticationErrorCodes {

    private AuthenticationErrorCodes() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * @param e the failure, possibly {@code null} when the caller has none to offer
     * @return the code to report; never {@code null}
     */
    public static ErrorCode of(AuthenticationException e) {
        if (e == null) {
            return ErrorCode.UNAUTHORIZED;
        }

        // "No such account" and "wrong password" must be the same answer. UsernameNotFoundException
        // is mapped here as well as being hidden by DaoAuthenticationProvider, so the guarantee
        // survives a future provider that does not hide it - and it must never become U001, which
        // would confirm to a caller that an email is unregistered.
        if (e instanceof UsernameNotFoundException || e instanceof BadCredentialsException) {
            return ErrorCode.INVALID_CREDENTIALS;
        }

        // Account state. Both are AccountStatusException subtypes and neither is a subtype of the
        // other, so the order between them does not matter.
        if (e instanceof LockedException) {
            return ErrorCode.ACCOUNT_LOCKED;
        }
        if (e instanceof DisabledException) {
            return ErrorCode.ACCOUNT_DISABLED;
        }

        // The provider itself broke - the database is down, a bean blew up. Nothing is wrong with
        // the credentials, so reporting 401 would send the caller off to fix their password while
        // the real fault is server-side. This is a 500.
        if (e instanceof AuthenticationServiceException) {
            return ErrorCode.INTERNAL_ERROR;
        }

        // Everything else, InsufficientAuthenticationException included: no usable credentials were
        // presented. This is the common case at the entry point.
        //
        // AccountExpiredException and CredentialsExpiredException deliberately land here rather than
        // getting codes of their own: CustomUserDetails reports both as non-expired because this
        // model has no such states, so a distinct code would document a rule that does not exist.
        return ErrorCode.UNAUTHORIZED;
    }
}
