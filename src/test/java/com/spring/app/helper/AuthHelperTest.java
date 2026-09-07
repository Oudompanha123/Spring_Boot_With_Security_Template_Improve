package com.spring.app.helper;

import com.spring.app.enums.Role;
import com.spring.app.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/** The static accessors over {@code SecurityContextHolder}. */
class AuthHelperTest {

    @AfterEach
    void clearContext() {
        // Static state: leaking a principal into the next test would make failures look random.
        SecurityContextHolder.clearContext();
    }

    private void authenticate(CustomUserDetails principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private CustomUserDetails principal() {
        return CustomUserDetails.fromToken(42L, "jane@example.com", "jane_doe", Role.MANAGER);
    }

    @Test
    @DisplayName("the display name and the login identifier are different values")
    void usernameIsTheDisplayNameNotTheEmail() {
        authenticate(principal());

        assertThat(AuthHelper.currentUsername()).contains("jane_doe");
        assertThat(AuthHelper.currentEmail()).contains("jane@example.com");

        // The distinction this pins down: UserDetails.getUsername() has to return the value the
        // UserDetailsService loads by, which here is the email. Anyone reaching for "the username"
        // through that method gets the identifier; currentUsername() gives the human-facing name.
        assertThat(AuthHelper.currentUsername()).isNotEqualTo(AuthHelper.currentEmail());
        assertThat(principal().getUsername()).isEqualTo(principal().getEmail());
    }

    @Test
    @DisplayName("id, email, username and role all come from the principal")
    void readsThePrincipal() {
        authenticate(principal());

        assertThat(AuthHelper.currentUserId()).contains(42L);
        assertThat(AuthHelper.currentEmail()).contains("jane@example.com");
        assertThat(AuthHelper.currentUsername()).contains("jane_doe");
        assertThat(AuthHelper.currentRole()).contains(Role.MANAGER);
        assertThat(AuthHelper.isAuthenticated()).isTrue();
        assertThat(AuthHelper.hasRole(Role.MANAGER)).isTrue();
        assertThat(AuthHelper.hasRole(Role.ADMIN)).isFalse();
        assertThat(AuthHelper.isSelf(42L)).isTrue();
        assertThat(AuthHelper.isSelf(43L)).isFalse();
        assertThat(AuthHelper.isSelf(null)).isFalse();
    }

    @Test
    @DisplayName("with no authentication every accessor is empty, none of them throw")
    void anonymousContext() {
        assertThat(AuthHelper.currentPrincipal()).isEmpty();
        assertThat(AuthHelper.currentUserId()).isEmpty();
        assertThat(AuthHelper.currentEmail()).isEmpty();
        assertThat(AuthHelper.currentUsername()).isEmpty();
        assertThat(AuthHelper.currentRole()).isEmpty();
        assertThat(AuthHelper.isAuthenticated()).isFalse();
        assertThat(AuthHelper.hasRole(Role.USER)).isFalse();
        assertThat(AuthHelper.isSelf(42L)).isFalse();
    }

    @Test
    @DisplayName("a principal of another type reads as absent rather than blowing up")
    void foreignPrincipalType() {
        // What @WithMockUser and the anonymous filter put in the context: a String principal, or a
        // token this helper knows nothing about. Returning empty keeps callers from having to
        // guard every call, and keeps a test-only principal from being mistaken for a real user.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("some-user", null,
                        AuthorityUtils.createAuthorityList("ROLE_USER")));

        assertThat(AuthHelper.currentUsername()).isEmpty();
        assertThat(AuthHelper.currentUserId()).isEmpty();
        assertThat(AuthHelper.isAuthenticated()).isFalse();
    }

    @Test
    @DisplayName("an anonymous token is not treated as authenticated")
    void anonymousTokenIsNotAUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(AuthHelper.isAuthenticated()).isFalse();
        assertThat(AuthHelper.currentUsername()).isEmpty();
    }
}
