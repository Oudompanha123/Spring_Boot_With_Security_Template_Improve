package com.spring.app.helper;

import com.spring.app.enums.Role;
import com.spring.app.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Read-only access to the current principal for code that is not a controller method (and so
 * cannot simply take {@code @AuthenticationPrincipal} as a parameter).
 *
 * <p>Deliberately has no {@code requireRole}-style guards. Authorization decisions belong in
 * {@code SecurityConfig} or in a {@code @PreAuthorize} expression, where they can be read as a
 * policy; scattering imperative checks through the service layer is how rules end up contradicting
 * each other with nobody able to say what the effective policy is.
 */
public final class AuthHelper {

    private AuthHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** The authenticated principal, or empty for an anonymous or non-JWT context. */
    public static Optional<CustomUserDetails> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof CustomUserDetails principal
                ? Optional.of(principal)
                : Optional.empty();
    }

    public static Optional<Long> currentUserId() {
        return currentPrincipal().map(CustomUserDetails::getId);
    }

    public static Optional<String> currentEmail() {
        return currentPrincipal().map(CustomUserDetails::getEmail);
    }

    public static Optional<Role> currentRole() {
        return currentPrincipal().map(CustomUserDetails::getRole);
    }

    public static boolean isAuthenticated() {
        return currentPrincipal().isPresent();
    }

    public static boolean hasRole(Role role) {
        return currentRole().filter(role::equals).isPresent();
    }

    /** True when the current principal owns the given user id. */
    public static boolean isSelf(Long userId) {
        return userId != null && currentUserId().filter(userId::equals).isPresent();
    }
}
