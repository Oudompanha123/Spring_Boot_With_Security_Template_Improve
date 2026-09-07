package com.spring.app.security;

import com.spring.app.domain.user.User;
import com.spring.app.enums.Role;
import com.spring.app.exception.JwtSecretConfigurationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for token minting and verification. No Spring context: the provider is constructed
 * with an explicit secret so the tests exercise the crypto and nothing else.
 */
class JwtTokenProviderTest {

    // Test-only key, long enough for HS256. Not used by any deployment.
    private static final String SECRET = "unit-test-signing-key-0123456789abcdefghijklmnopqrst";
    private static final String OTHER_SECRET = "another-unit-test-key-0123456789abcdefghijklmnopqrst";
    private static final String ISSUER = "spring-app";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(5);
    private static final Duration REFRESH_TTL = Duration.ofHours(8);

    private final JwtTokenProvider provider =
            new JwtTokenProvider(SECRET, ACCESS_TTL, REFRESH_TTL, ISSUER);

    private User user() {
        return User.builder()
                .id(42L)
                .email("jane@example.com")
                .username("jane_doe")
                .password("irrelevant-here")
                .role(Role.MANAGER)
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("a valid token rebuilds the principal, id and role included")
    void roundTrip() {
        String token = provider.generateAccessToken(user());

        Authentication authentication = provider.getAuthentication(token);
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();

        assertThat(principal.getId()).isEqualTo(42L);
        assertThat(principal.getEmail()).isEqualTo("jane@example.com");
        assertThat(principal.getDisplayName()).isEqualTo("jane_doe");
        assertThat(principal.getRole()).isEqualTo(Role.MANAGER);
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_MANAGER");
        // The signature was the credential and must not be retained afterwards.
        assertThat(authentication.getCredentials()).isNull();
    }

    @Test
    @DisplayName("changing a single character invalidates the token")
    void tamperedTokenIsRejected() {
        String token = provider.generateAccessToken(user());
        String tampered = flipFirstSignatureCharacter(token);

        // A JwtException (not ExpiredJwtException) is what the filter maps to A005.
        assertThatThrownBy(() -> provider.getAuthentication(tampered))
                .isInstanceOf(JwtException.class)
                .isNotInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("an expired token fails as expired, distinctly from invalid")
    void expiredTokenIsRejectedAsExpired() {
        String token = provider.generateAccessToken(user(), Duration.ofSeconds(-60));

        // The distinction is the whole reason A004 and A005 are separate codes.
        assertThatThrownBy(() -> provider.getAuthentication(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("every minted token carries an exp claim")
    void mintedTokensAlwaysExpire() {
        // The claim has to be there for expiry to mean anything, so pin it at the source rather
        // than trusting that whoever edits the builder next remembers.
        assertThat(provider.parseRefreshToken(provider.generateRefreshToken(user()).token())
                .getExpiration()).isNotNull();

        String accessToken = provider.generateAccessToken(user());
        Claims accessClaims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(UTF_8)))
                .build().parseSignedClaims(accessToken).getPayload();
        assertThat(accessClaims.getExpiration()).isNotNull();
        assertThat(accessClaims.getExpiration()).isAfter(accessClaims.getIssuedAt());
    }

    @Test
    @DisplayName("a token with no exp claim is rejected, not treated as eternal")
    void tokenWithoutExpiryIsRejected() {
        // JWT makes exp optional, and jjwt only checks it when present: to a parser, a token with
        // no exp simply never expires. Honouring exp when it happens to be there is therefore not
        // the same as enforcing expiry - the check is opt-out by omission unless the claim is
        // required. Nothing here mints such a token, but "validated on every request" has to mean
        // the request is refused when the claim is absent.
        String eternal = Jwts.builder()
                .issuer(ISSUER)
                .subject("42")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .claim("typ", "access")
                .claim("email", "jane@example.com")
                .claim("username", "jane_doe")
                .claim("role", "MANAGER")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> provider.getAuthentication(eternal))
                .isInstanceOf(JwtException.class)
                .isNotInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("every claim the principal is built from is required")
    void principalClaimsAreRequired() {
        // A correctly signed, unexpired, typ=access token missing any one of these must be
        // refused rather than producing a principal with a null field. Dropping sub or role
        // was already caught; email and username were not, and a principal with a null login
        // identifier is what logs and /me would then report.
        for (String omitted : new String[]{"sub", "role", "email", "username"}) {
            String token = accessTokenOmitting(omitted);

            assertThatThrownBy(() -> provider.getAuthentication(token))
                    .describedAs("token missing %s", omitted)
                    .isInstanceOf(JwtException.class)
                    .isNotInstanceOf(ExpiredJwtException.class);
        }
    }

    @Test
    @DisplayName("a blank claim counts as missing")
    void blankClaimsAreRejected() {
        // "" and "   " are not identities. Without the blank check they would sail through a
        // plain null test and become the principal's email.
        assertThatThrownBy(() -> provider.getAuthentication(accessTokenWith("email", "   ")))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> provider.getAuthentication(accessTokenWith("username", "")))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a refresh token with no exp claim is rejected too")
    void refreshTokenWithoutExpiryIsRejected() {
        String eternal = Jwts.builder()
                .issuer(ISSUER)
                .subject("42")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .claim("typ", "refresh")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> provider.parseRefreshToken(eternal))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a token signed with a different key is rejected")
    void foreignSignatureIsRejected() {
        JwtTokenProvider attacker = new JwtTokenProvider(OTHER_SECRET, ACCESS_TTL, REFRESH_TTL, ISSUER);
        String forged = attacker.generateAccessToken(user());

        assertThatThrownBy(() -> provider.getAuthentication(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a token from another issuer is rejected")
    void foreignIssuerIsRejected() {
        JwtTokenProvider otherIssuer = new JwtTokenProvider(SECRET, ACCESS_TTL, REFRESH_TTL, "some-other-service");
        String token = otherIssuer.generateAccessToken(user());

        assertThatThrownBy(() -> provider.getAuthentication(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a secret shorter than HS256 requires is refused at startup")
    void shortSecretIsRefused() {
        assertThatThrownBy(() -> new JwtTokenProvider("too-short", ACCESS_TTL, REFRESH_TTL, ISSUER))
                .isInstanceOf(JwtSecretConfigurationException.class)
                .hasMessageContaining("jwt.secret")
                .hasMessageContaining("32")
                // The message must name the property, never quote the value.
                .hasMessageNotContaining("too-short");
    }

    @Test
    @DisplayName("a missing secret is refused, and says so distinctly from a short one")
    void missingSecretIsRefused() {
        // What ${JWT_SECRET:} resolves to when the variable is unset. It must never be treated as
        // a key, and the message has to be the one JwtSecretFailureAnalyzer turns into an action.
        for (String blank : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> new JwtTokenProvider(blank, ACCESS_TTL, REFRESH_TTL, ISSUER))
                    .isInstanceOf(JwtSecretConfigurationException.class)
                    .hasMessageContaining("No JWT signing secret is configured")
                    .hasMessageContaining("JWT_SECRET");
        }
    }

    @Test
    @DisplayName("a refresh token is a JWT with a unique jti and a longer life")
    void refreshTokenIsAJwt() {
        JwtTokenProvider.IssuedRefreshToken first = provider.generateRefreshToken(user());
        JwtTokenProvider.IssuedRefreshToken second = provider.generateRefreshToken(user());

        assertThat(first.token().split("\\.")).hasSize(3);
        assertThat(first.tokenId()).isNotEqualTo(second.tokenId());
        assertThat(first.expiresAt()).isAfter(Instant.now().plus(Duration.ofHours(7)));
        assertThat(first.expiresAt()).isBefore(Instant.now().plus(Duration.ofHours(9)));

        // The jti handed back must be the one inside the token, or the stored row would point at
        // nothing and every refresh would fail.
        assertThat(provider.parseRefreshToken(first.token()).getId()).isEqualTo(first.tokenId());
        assertThat(provider.parseRefreshToken(first.token()).getSubject()).isEqualTo("42");
    }

    @Test
    @DisplayName("a refresh token carries no profile data")
    void refreshTokenCarriesNoProfile() {
        String token = provider.generateRefreshToken(user()).token();

        // It is only ever exchanged for a new pair, so email/username/role would be a long-lived
        // copy of account data in the client's hands for nothing.
        assertThat(provider.parseRefreshToken(token).get("email", String.class)).isNull();
        assertThat(provider.parseRefreshToken(token).get("username", String.class)).isNull();
        assertThat(provider.parseRefreshToken(token).get("role", String.class)).isNull();
    }

    @Test
    @DisplayName("the two token types are not interchangeable")
    void tokenTypesAreNotInterchangeable() {
        String accessToken = provider.generateAccessToken(user());
        String refreshToken = provider.generateRefreshToken(user()).token();

        // Both are validly signed by the same key, so only the typ claim separates them. Without
        // that check a refresh token would authenticate every endpoint, and an access token -
        // the one handed to every call - could mint fresh pairs forever.
        assertThatThrownBy(() -> provider.getAuthentication(refreshToken))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> provider.parseRefreshToken(accessToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("an expired refresh token fails as expired, not as invalid")
    void expiredRefreshToken() {
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, ACCESS_TTL, Duration.ofSeconds(-60), ISSUER);
        String token = shortLived.generateRefreshToken(user()).token();

        // A negative TTL means it expired before it was ever handed out. A009, not A008.
        assertThatThrownBy(() -> shortLived.parseRefreshToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("a refresh token signed with another key is rejected")
    void forgedRefreshToken() {
        JwtTokenProvider attacker = new JwtTokenProvider(OTHER_SECRET, ACCESS_TTL, REFRESH_TTL, ISSUER);
        String forged = attacker.generateRefreshToken(user()).token();

        assertThatThrownBy(() -> provider.parseRefreshToken(forged))
                .isInstanceOf(JwtException.class);
    }

    /**
     * Changes one character of the signature, at a position where every bit counts.
     *
     * <p>Not the <em>last</em> character, which is the obvious choice and is quietly wrong: an
     * HS256 signature is 32 bytes, and base64url encodes that in 43 characters carrying 258 bits,
     * so the final character has two bits that decode to nothing. Swapping {@code A} for {@code B}
     * there changes only those two bits, the signature bytes come out identical, and the token
     * still verifies. Since the payload carries a random {@code jti}, the final character varies
     * per run and a test written that way fails roughly one run in sixteen.
     */
    /** A complete, correctly signed access token, minus one claim. */
    private String accessTokenOmitting(String claimToOmit) {
        Map<String, Object> claims = new LinkedHashMap<>(Map.of(
                "sub", "42",
                "typ", "access",
                "email", "jane@example.com",
                "username", "jane_doe",
                "role", "MANAGER"));
        claims.remove(claimToOmit);
        return sign(claims);
    }

    /** A complete, correctly signed access token with one claim overridden. */
    private String accessTokenWith(String claim, String value) {
        Map<String, Object> claims = new LinkedHashMap<>(Map.of(
                "sub", "42",
                "typ", "access",
                "email", "jane@example.com",
                "username", "jane_doe",
                "role", "MANAGER"));
        claims.put(claim, value);
        return sign(claims);
    }

    private String sign(Map<String, Object> claims) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer(ISSUER)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ACCESS_TTL)));
        // subject() rather than a "sub" claim: jjwt rejects reserved names passed to claim().
        Object subject = claims.remove("sub");
        if (subject != null) {
            builder.subject(subject.toString());
        }
        claims.forEach(builder::claim);
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(UTF_8)), Jwts.SIG.HS256).compact();
    }

    private String flipFirstSignatureCharacter(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char first = token.charAt(signatureStart);
        char replacement = first == 'A' ? 'B' : 'A';
        return token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
    }
}
