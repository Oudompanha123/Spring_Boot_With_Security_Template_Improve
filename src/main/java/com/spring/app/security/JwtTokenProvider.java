package com.spring.app.security;

import com.spring.app.domain.user.User;
import com.spring.app.enums.Role;
import com.spring.app.exception.JwtSecretConfigurationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Mints and verifies access tokens, and mints refresh-token values.
 *
 * <p>Access tokens are HS256 JWTs. The signing secret comes from configuration with no usable
 * default (see {@code jwt.secret} → {@code JWT_SECRET}), so a missing or too-short secret is a
 * startup failure rather than a silently insecure deployment signing with a value that is public
 * on GitHub.
 *
 * <p>Refresh tokens are JWTs too, distinguished by {@code typ=refresh} and given a far longer
 * lifetime. A signature alone cannot be revoked, though, and logout has to be able to end a
 * session — so every refresh token is issued with a {@code jti} that is recorded in
 * {@code refresh_tokens}, and the exchange checks that row as well as the signature. Verifying is
 * therefore stateless for access tokens and stateful for refresh tokens, which is the only place
 * that cost is paid. See {@code com.spring.app.domain.token.RefreshToken}.
 *
 * <p>Nothing in this class logs a token, a claim set or a secret.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    static final String CLAIM_TOKEN_TYPE = "typ";
    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_USERNAME = "username";
    static final String CLAIM_ROLE = "role";
    static final String TOKEN_TYPE_ACCESS = "access";
    static final String TOKEN_TYPE_REFRESH = "refresh";

    /** HS256 requires a key of at least 256 bits; anything shorter weakens the signature. */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final String issuer;

    @Getter
    private final Duration accessTtl;

    @Getter
    private final Duration refreshTtl;

    /**
     * TTLs are bound as {@link Duration}, so configuration reads {@code 5m} and {@code 8h} rather
     * than {@code 300} and {@code 28800}. A bare number in a property named {@code ...-seconds} is
     * exactly the kind of value that gets changed by someone who assumed it was minutes.
     */
    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.access-token-ttl:5m}") Duration accessTtl,
                            @Value("${jwt.refresh-token-ttl:8h}") Duration refreshTtl,
                            @Value("${jwt.issuer:spring-app}") String issuer) {

        // Both failure modes below are reported by JwtSecretFailureAnalyzer as a readable
        // APPLICATION FAILED TO START block rather than a nested bean-creation stack trace.
        // Neither message contains the secret or any part of it.
        byte[] secretBytes = secret == null ? new byte[0] : secret.trim().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length == 0) {
            throw new JwtSecretConfigurationException(
                    "No JWT signing secret is configured (property 'jwt.secret', "
                            + "environment variable JWT_SECRET).");
        }
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new JwtSecretConfigurationException(
                    "The configured JWT signing secret is " + secretBytes.length + " bytes; HS256 "
                            + "requires at least " + MIN_SECRET_BYTES + " (property 'jwt.secret', "
                            + "environment variable JWT_SECRET).");
        }

        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.issuer = issuer;

        log.info("JWT provider ready (issuer={}, accessTtl={}, refreshTtl={})",
                issuer, accessTtl, refreshTtl);
    }

    // ========================= issuing =========================

    public String generateAccessToken(User user) {
        return generateAccessToken(user, accessTtl);
    }

    /**
     * Issues an access token with an explicit lifetime.
     *
     * <p>Visible so tests can mint an already-expired token (negative TTL) without sleeping and
     * without reaching into the signing key.
     */
    public String generateAccessToken(User user, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_ROLE, user.getRole().name())
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Mints a refresh token: a signed JWT, like the access token, but carrying {@code typ=refresh}
     * and its own much longer lifetime.
     *
     * <p>The returned {@link IssuedRefreshToken} also exposes the token's {@code jti} and expiry so
     * the caller can record them without parsing back what it just built. Only the {@code jti} is
     * stored server-side — see {@link com.spring.app.domain.token.RefreshToken}.
     */
    public IssuedRefreshToken generateRefreshToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(refreshTtl);
        String tokenId = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .id(tokenId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                // No email, username or role: a refresh token is only ever exchanged for a new
                // pair, and those values are re-read from the database at that point. Claims that
                // nothing consumes are just a longer-lived copy of user data in the client's hands.
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new IssuedRefreshToken(token, tokenId, expiresAt);
    }

    /** The access-token lifetime in seconds, for the {@code expiresIn} field of a token response. */
    public long getAccessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    /** A freshly minted refresh token, with the two facts the caller has to persist. */
    public record IssuedRefreshToken(String token, String tokenId, Instant expiresAt) {
    }

    /**
     * Verifies a refresh token and returns its claims.
     *
     * <p>Signature, issuer, expiry and token type are all checked here; whether the token has been
     * <em>revoked</em> is not, and cannot be — that answer lives in the database. Callers must look
     * up {@link Claims#getId()} before trusting the token.
     *
     * @throws ExpiredJwtException past its expiry — the caller maps this to {@code A009}
     * @throws JwtException        every other rejection, including an access token presented here
     *                             — {@code A008}
     */
    public Claims parseRefreshToken(String token) {
        Claims claims = parseClaims(token);

        if (!TOKEN_TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            // An access token must not be exchangeable for a new pair: it is handed to every
            // endpoint the client calls, so it is the more exposed of the two.
            throw new MalformedJwtException("Token is not a refresh token");
        }
        return claims;
    }

    // ========================= verifying =========================

    /**
     * Verifies an access token and rebuilds the principal from its claims.
     *
     * @throws ExpiredJwtException when the signature is good but the token is past its expiry —
     *                             the caller maps this to {@code A004}
     * @throws JwtException        for every other rejection: bad signature, malformed token, wrong
     *                             issuer, wrong token type, unreadable claims — all {@code A005}
     */
    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);

        if (!TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            // A refresh token (or anything else) must not buy access to protected endpoints.
            throw new MalformedJwtException("Token is not an access token");
        }

        // Every claim the principal is built from is required. Reading them with a plain get()
        // would hand back null and produce a principal with no login identifier: getUsername()
        // returns null, the audit lines in CustomAccessDeniedHandler log "null", and /me answers
        // with a null email. Same reasoning as the exp check below — verification does not assume
        // the token was minted by the builder above.
        CustomUserDetails principal = CustomUserDetails.fromToken(
                parseUserId(claims),
                requireClaim(claims, CLAIM_EMAIL),
                requireClaim(claims, CLAIM_USERNAME),
                parseRole(claims)
        );

        // Credentials are left null: the signature was the credential and it has been consumed.
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    /**
     * Verifies signature, issuer and expiry, for either token type.
     *
     * <p>{@code parseSignedClaims} enforces {@code exp} whenever the claim is present — but JWT
     * makes it optional, and a parser reading a token without {@code exp} concludes that it never
     * expires. Honouring expiry when it happens to be there is therefore not the same as enforcing
     * it: the check is opt-out by omission. The explicit requirement below closes that, so
     * "validated on every request" holds for a token that simply left the claim out.
     *
     * <p>Nothing in this class mints such a token. The point is that this method does not have to
     * trust that — not the next edit to a builder, not another service signing with the same key,
     * not a library default that changes.
     */
    private Claims parseClaims(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (claims.getExpiration() == null) {
            // Deliberately not ExpiredJwtException: the token is not expired, it is malformed.
            // The caller maps this to "invalid" (A005 / A008), which is the accurate answer -
            // telling a client to refresh would not help.
            throw new MalformedJwtException("Token has no exp claim");
        }
        return claims;
    }

    /** Reads a string claim that the principal cannot be built without. */
    private String requireClaim(Claims claims, String name) {
        String value = claims.get(name, String.class);
        if (value == null || value.isBlank()) {
            throw new MalformedJwtException("Token is missing the " + name + " claim");
        }
        return value;
    }

    private Long parseUserId(Claims claims) {
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new MalformedJwtException("Token subject is not a user id");
        }
    }

    private Role parseRole(Claims claims) {
        String role = claims.get(CLAIM_ROLE, String.class);
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException e) {
            // A role that no longer exists must fail closed, not fall back to something permissive.
            throw new MalformedJwtException("Token carries an unknown role");
        }
    }
}
