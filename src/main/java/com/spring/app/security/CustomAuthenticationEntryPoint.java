package com.spring.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.app.exception.AuthenticationErrorCodes;
import com.spring.app.exception.ErrorCode;
import com.spring.app.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 401 handler for the filter chain: an anonymous or badly-authenticated request reaching an
 * endpoint that requires authentication.
 *
 * <p>Registered in {@code exceptionHandling}, it replaces the default behaviour of an HTML
 * Whitelabel page (or a bare {@code WWW-Authenticate} challenge) with the same JSON body every
 * other error in this API uses. A JSON client handed HTML cannot report what went wrong.
 *
 * <p>The specific code is resolved in two steps: {@link JwtAuthenticationFilter#AUTH_ERROR_ATTRIBUTE}
 * first, for a token that was present and rejected ({@code A004} expired, {@code A005} invalid),
 * then the {@link AuthenticationException} itself via {@link AuthenticationErrorCodes} — the same
 * mapping {@code GlobalApiExceptionHandler} uses, so the two failure paths cannot drift apart.
 * With neither available the answer is {@code A001}: no usable credentials were presented.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        ErrorCode errorCode = resolveErrorCode(request, authException);

        log.warn("Unauthenticated request [{}]: {} {}",
                errorCode.getCode(), request.getMethod(), request.getRequestURI());

        ErrorResponse body = ErrorResponse.of(errorCode, request.getRequestURI());

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getWriter(), body);
    }

    private ErrorCode resolveErrorCode(HttpServletRequest request, AuthenticationException authException) {
        // A rejected token is the more specific fact: the caller did present credentials, and
        // whether they were expired or invalid is what the client needs in order to react.
        // ExceptionTranslationFilter only ever reports the generic "not authenticated" here, so
        // without this attribute that distinction would be lost.
        Object attribute = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        if (attribute instanceof ErrorCode errorCode) {
            return errorCode;
        }
        return AuthenticationErrorCodes.of(authException);
    }
}
