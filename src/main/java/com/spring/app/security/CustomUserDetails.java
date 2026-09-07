package com.spring.app.security;

import com.spring.app.domain.user.User;
import com.spring.app.enums.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The authenticated principal.
 *
 * <p>Holds flat values rather than the {@link User} entity so that a principal can be rebuilt from
 * JWT claims with no database round trip, and so a detached entity can never be lazily initialised
 * (or accidentally serialised, password included) from inside the security context.
 *
 * <p>{@link #getId()} is the reason this class exists: controllers and the JPA auditor need the
 * user's primary key, and digging it out of the username string is exactly the kind of guesswork
 * that turns into a bug the day emails become editable.
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String email;

    /**
     * Display name — {@code User.username}. Not the login identifier; see {@link #getUsername()}.
     */
    private final String displayName;

    private final Role role;

    private final String password;
    private final boolean enabled;
    private final boolean locked;

    private CustomUserDetails(Long id, String email, String displayName, Role role,
                              String password, boolean enabled, boolean locked) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.password = password;
        this.enabled = enabled;
        this.locked = locked;
    }

    /** Full principal, password hash included, for the {@code DaoAuthenticationProvider} to check. */
    public static CustomUserDetails from(User user) {
        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.getPassword(),
                user.isEnabled(),
                user.isLocked()
        );
    }

    /**
     * Principal reconstructed from a verified access token.
     *
     * <p>No password (nothing left to verify — the signature already did that) and no live account
     * flags: those were checked when the token was issued. That is the stateless trade-off, and it
     * means a lock or a disable only takes effect on this path once the access token expires. The
     * README spells out the blacklist option for shortening that window.
     */
    public static CustomUserDetails fromToken(Long id, String email, String displayName, Role role) {
        return new CustomUserDetails(id, email, displayName, role, null, true, false);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Exactly one role per user, so exactly one authority. hasRole('ADMIN') matches ROLE_ADMIN.
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    /**
     * The login identifier, i.e. the email — {@code CustomUserDetailsService} loads by email, and
     * Spring Security requires this to return whatever that lookup key is. The human-facing name
     * lives in {@link #getDisplayName()}.
     */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** Identifies the principal in logs without exposing the hash. */
    @Override
    public String toString() {
        return "CustomUserDetails(id=" + id + ", role=" + role + ")";
    }
}
