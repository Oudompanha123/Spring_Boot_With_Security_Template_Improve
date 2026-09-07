package com.spring.app.security;

import com.spring.app.exception.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Turns an {@code Authorization: Bearer} header into an authenticated
 * {@link org.springframework.security.core.context.SecurityContext}.
 *
 * <p>The filter never writes a response. A rejected token is recorded on the request as
 * {@link #AUTH_ERROR_ATTRIBUTE} and the chain continues unauthenticated, which leaves two
 * decisions where they belong:
 *
 * <ul>
 *   <li>whether the endpoint needed authentication at all: that belongs to the authorization
 *       rules, so a junk token sent to a public endpoint does not break a public endpoint;</li>
 *   <li>what the error body looks like: that belongs to {@link CustomAuthenticationEntryPoint},
 *       which reads the attribute back and so can distinguish {@code A004} (expired) from
 *       {@code A005} (invalid). Without the attribute both collapse into a generic 401.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute carrying the {@link ErrorCode} for a rejected token. */
    public static final String AUTH_ERROR_ATTRIBUTE = "com.spring.app.AUTH_ERROR";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        try {
            if (StringUtils.hasText(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
                Authentication authentication = tokenProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (ExpiredJwtException e) {
            // Signature was valid, the clock ran out: the client should refresh, not re-login.
            reject(request, ErrorCode.TOKEN_EXPIRED, "expired token");
        } catch (JwtException | IllegalArgumentException e) {
            // Bad signature, tampered payload, wrong issuer, wrong token type, unparseable value.
            reject(request, ErrorCode.TOKEN_INVALID, "invalid token");
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, ErrorCode errorCode, String reason) {
        // Log the reason and the target, never the token itself.
        log.debug("Rejected bearer token ({}): {} {}", reason, request.getMethod(), request.getRequestURI());
        SecurityContextHolder.clearContext();
        request.setAttribute(AUTH_ERROR_ATTRIBUTE, errorCode);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
