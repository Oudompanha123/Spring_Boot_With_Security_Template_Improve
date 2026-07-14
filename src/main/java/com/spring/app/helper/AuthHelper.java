package com.spring.app.helper;

import com.spring.app.domain.user.User;
import com.spring.app.security.JwtUserPrincipal;
import com.spring.app.security.SecurityUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public final class AuthHelper {

    private AuthHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========================= CORE PRINCIPAL ACCESS =========================

    /**
     * Get the current principal as JwtUserPrincipal if JWT-based.
     */
    public static Optional<JwtUserPrincipal> getCurrentJwtPrincipal() {
        Authentication authentication = getAuthentication();
        if (!isValidAuthentication(authentication)) return Optional.empty();

        // Using your custom JwtAuthenticationTokenWithPrincipal which sets JwtUserPrincipal as principal
        if (authentication instanceof JwtAuthenticationToken) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof JwtUserPrincipal jwtPrincipal) {
                return Optional.of(jwtPrincipal);
            }
        }
        return Optional.empty();
    }

    /**
     * Get the current principal as SecurityUser if form/DB-based.
     */
    public static Optional<SecurityUser> getCurrentSecurityUser() {
        Authentication authentication = getAuthentication();
        if (!isValidAuthentication(authentication)) return Optional.empty();

        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return Optional.of(securityUser);
        }
        return Optional.empty();
    }

    /**
     * Get the current domain User (only when form/DB-based, not from JWT).
     */
    public static Optional<User> getCurrentUser() {
        return getCurrentSecurityUser().map(SecurityUser::user);
    }

    // ========================= FLAT GETTERS =========================

    public static Long getUserId() {
        // Prefer JWT principal
        Optional<JwtUserPrincipal> jwt = getCurrentJwtPrincipal();
        if (jwt.isPresent()) {
            String raw = jwt.get().getUserId();
            Long parsed = tryParseLong(raw);
            if (parsed != null) return parsed;
        }

        // Fallback to SecurityUser (DB)
        return getCurrentUser().map(User::getUserId).orElse(null);
    }

    public static String getUsername() {
        return getCurrentJwtPrincipal().map(JwtUserPrincipal::getUsername)
                .orElseGet(() -> getCurrentUser().map(User::getUsername).orElse(null));
    }

    public static String getEmail() {
        return getCurrentJwtPrincipal().map(JwtUserPrincipal::getEmail)
                .orElseGet(() -> getCurrentUser().map(User::getEmail).orElse(null));
    }

    public static String getFullName() {
        return getCurrentJwtPrincipal().map(JwtUserPrincipal::getFullName)
                .orElseGet(() -> getCurrentUser().map(User::getFullName).orElse(null));
    }

    // ========================= OPTIONAL VARIANTS =========================

    public static Optional<Long> getCurrentUserId() {
        return Optional.ofNullable(getUserId());
    }

    public static Optional<String> getCurrentUsername() {
        return Optional.ofNullable(getUsername());
    }

    public static Optional<String> getCurrentUserEmail() {
        return Optional.ofNullable(getEmail());
    }

    // ========================= ROLE / PERMISSION =========================

    public static boolean hasRole(String roleName) {
        if (roleName == null || roleName.isBlank()) return false;
        return hasAuthority("ROLE_" + roleName);
    }

    public static boolean hasAnyRole(String... roleNames) {
        if (roleNames == null || roleNames.length == 0) return false;
        for (String r : roleNames) if (hasRole(r)) return true;
        return false;
    }

    public static boolean hasAllRoles(String... roleNames) {
        if (roleNames == null || roleNames.length == 0) return true;
        for (String r : roleNames) if (!hasRole(r)) return false;
        return true;
    }

    public static Set<String> getRoles() {
        return getAuthorities().stream()
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .collect(Collectors.toSet());
    }

    public static boolean hasPermission(String permissionName) {
        if (permissionName == null || permissionName.isBlank()) return false;
        return hasAuthority(permissionName);
    }

    public static boolean hasAnyPermission(String... permissionNames) {
        if (permissionNames == null || permissionNames.length == 0) return false;
        for (String p : permissionNames) if (hasPermission(p)) return true;
        return false;
    }

    public static boolean hasAllPermissions(String... permissionNames) {
        if (permissionNames == null || permissionNames.length == 0) return true;
        for (String p : permissionNames) if (!hasPermission(p)) return false;
        return true;
    }

    public static Set<String> getPermissions() {
        return getAuthorities().stream()
                .filter(a -> !a.startsWith("ROLE_"))
                .collect(Collectors.toSet());
    }

    // ========================= AUTH STATUS / TYPE =========================

    public static boolean isAuthenticated() {
        return isValidAuthentication(getAuthentication());
    }

    public static boolean isAnonymous() {
        return !isAuthenticated();
    }

    public static AuthType getAuthType() {
        Authentication authentication = getAuthentication();
        if (authentication == null) return AuthType.NONE;

        if (authentication instanceof JwtAuthenticationToken) {
            return AuthType.JWT;
        } else if (authentication.getPrincipal() instanceof SecurityUser) {
            return AuthType.FORM;
        } else {
            return AuthType.OTHER;
        }
    }

    // ========================= SHORTCUTS =========================

    public static boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public static boolean isManagerOrAdmin() {
        return hasAnyRole("ADMIN", "MANAGER");
    }

    public static boolean canAccessUser(Long targetUserId) {
        if (targetUserId == null) return false;
        if (isAdmin()) return true;
        Long currentUserId = getUserId();
        return currentUserId != null && currentUserId.equals(targetUserId);
    }

    public static void requireAuthentication() {
        if (!isAuthenticated()) throw new SecurityException("Authentication required");
    }

    public static void requireRole(String roleName) {
        requireAuthentication();
        if (!hasRole(roleName)) throw new SecurityException("Role required: " + roleName);
    }

    public static void requirePermission(String permissionName) {
        requireAuthentication();
        if (!hasPermission(permissionName)) throw new SecurityException("Permission required: " + permissionName);
    }

    // ========================= INTERNALS =========================

    private static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static boolean isValidAuthentication(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName());
    }

    private static boolean hasAuthority(String authority) {
        Authentication authentication = getAuthentication();
        if (!isValidAuthentication(authentication)) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(ga -> authority.equals(ga.getAuthority()));
    }

    private static Set<String> getAuthorities() {
        Authentication authentication = getAuthentication();
        if (!isValidAuthentication(authentication)) return Set.of();
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    private static Long tryParseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Unable to parse userId to Long: {}", value);
            return null;
        }
    }

    public enum AuthType { NONE, JWT, FORM, OTHER }
}