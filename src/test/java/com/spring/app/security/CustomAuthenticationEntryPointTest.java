package com.spring.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.app.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 401 path in isolation: which code wins, and that the body is always JSON.
 */
class CustomAuthenticationEntryPointTest {

    private final CustomAuthenticationEntryPoint entryPoint =
            new CustomAuthenticationEntryPoint(new ObjectMapper());

    @Test
    @DisplayName("no token at all is A001 / 401 JSON")
    void noCredentials() throws IOException {
        MockHttpServletResponse response = commence(
                request("GET", "/api/v1/me", null),
                new InsufficientAuthenticationException("full authentication required"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString())
                .contains("\"code\":\"A001\"")
                .contains("\"status\":401")
                .contains("\"path\":\"/api/v1/me\"");
    }

    @Test
    @DisplayName("a rejected token wins over the generic exception: A004 for expired")
    void expiredTokenAttributeWins() throws IOException {
        // ExceptionTranslationFilter only ever reports the generic "not authenticated"; the
        // attribute set by JwtAuthenticationFilter is what carries the real reason.
        MockHttpServletResponse response = commence(
                request("GET", "/api/v1/me", ErrorCode.TOKEN_EXPIRED),
                new InsufficientAuthenticationException("full authentication required"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"A004\"");
    }

    @Test
    @DisplayName("A005 for an invalid token, distinct from A004")
    void invalidTokenAttribute() throws IOException {
        MockHttpServletResponse response = commence(
                request("POST", "/api/v1/books", ErrorCode.TOKEN_INVALID),
                new InsufficientAuthenticationException("full authentication required"));

        assertThat(response.getContentAsString())
                .contains("\"code\":\"A005\"")
                .doesNotContain("A004");
    }

    @Test
    @DisplayName("with no attribute, the exception decides: a locked account is A006 / 403")
    void authenticationExceptionIsMapped() throws IOException {
        MockHttpServletResponse response = commence(
                request("POST", "/api/v1/auth/login", null),
                new LockedException("locked"));

        // Same mapping the advice uses, so both failure paths agree on code and status.
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"A006\"");
    }

    @Test
    @DisplayName("an unknown account does not leak through the entry point either")
    void unknownAccountIsNotDisclosed() throws IOException {
        MockHttpServletResponse response = commence(
                request("POST", "/api/v1/auth/login", null),
                new UsernameNotFoundException("no such user"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("\"code\":\"A003\"")
                .doesNotContain("U001")
                // No exception class name, no framework message.
                .doesNotContain("UsernameNotFound")
                .doesNotContain("no such user");
    }

    @Test
    @DisplayName("the response body carries no timestamp")
    void bodyHasNoTimestamp() throws IOException {
        MockHttpServletResponse response = commence(
                request("POST", "/api/v1/auth/login", null),
                new InsufficientAuthenticationException("nope"));

        // A varying field would break the byte-identical guarantee for failed logins.
        assertThat(response.getContentAsString()).doesNotContain("timestamp");
    }

    private MockHttpServletRequest request(String method, String uri, ErrorCode tokenError) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        if (tokenError != null) {
            request.setAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE, tokenError);
        }
        return request;
    }

    private MockHttpServletResponse commence(MockHttpServletRequest request,
                                             AuthenticationException authException) throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(request, response, authException);
        return response;
    }
}
