package com.spring.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.app.exception.ErrorCode;
import com.spring.app.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 403 handler for the filter chain: the caller is authenticated, but the rules in
 * {@code authorizeHttpRequests} refused the request.
 *
 * <p>This covers denials decided <em>before</em> the controller runs. Denials decided by method
 * security ({@code @PreAuthorize}) are thrown inside the dispatch and handled by
 * {@code GlobalApiExceptionHandler} instead. Both paths deliberately produce the same {@code A002}
 * body, because a client should not have to care which mechanism said no.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException e) throws IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principal = authentication == null ? "anonymous" : authentication.getName();

        log.warn("Access denied: principal={}, {} {}", principal, request.getMethod(), request.getRequestURI());

        ErrorResponse body = ErrorResponse.of(ErrorCode.ACCESS_DENIED, request.getRequestURI());

        response.setStatus(ErrorCode.ACCESS_DENIED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
