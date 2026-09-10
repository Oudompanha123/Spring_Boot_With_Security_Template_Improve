package com.spring.app;

import com.jayway.jsonpath.JsonPath;
import com.spring.app.domain.token.RefreshTokenRepository;
import com.spring.app.domain.user.User;
import com.spring.app.domain.user.UserRepository;
import com.spring.app.enums.Role;
import com.spring.app.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the auth rules: signup, the identical-failure guarantee, token rejection
 * codes, refresh rotation, logout revocation and the lockout threshold.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    private static final String EMAIL = "jane@example.com";
    private static final String PASSWORD = "Str0ngPassw0rd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    /** The test-only key, so a test can mint a deliberately malformed but correctly signed token. */
    @org.springframework.beans.factory.annotation.Value("${jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ========================= signup =========================

    @Test
    @DisplayName("signup creates a USER and never echoes the password")
    void signupCreatesUser() throws Exception {
        MvcResult result = mockMvc.perform(signup(EMAIL, "jane_doe", PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(PASSWORD);
        // No hash either: a BCrypt hash is still a credential.
        assertThat(body).doesNotContain("$2a$").doesNotContain("$2b$");

        User stored = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(stored.getRole()).isEqualTo(Role.USER);
        assertThat(stored.getPassword()).isNotEqualTo(PASSWORD).startsWith("$2");
    }

    @Test
    @DisplayName("a duplicate email is 409 with code U002")
    void duplicateEmailIsConflict() throws Exception {
        mockMvc.perform(signup(EMAIL, "jane_doe", PASSWORD)).andExpect(status().isCreated());

        mockMvc.perform(signup(EMAIL, "someone_else", PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("U002"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("a duplicate email differing only in case is still a conflict")
    void duplicateEmailIsCaseInsensitive() throws Exception {
        mockMvc.perform(signup(EMAIL, "jane_doe", PASSWORD)).andExpect(status().isCreated());

        mockMvc.perform(signup("JANE@Example.com", "jane_again", PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("U002"));
    }

    @Test
    @DisplayName("a role in the signup body does not create an admin")
    void roleInBodyIsIgnored() throws Exception {
        String payload = """
                {"email":"sneaky@example.com","username":"sneaky","password":"Str0ngPassw0rd","role":"ADMIN"}
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("USER"));

        assertThat(userRepository.findByEmail("sneaky@example.com").orElseThrow().getRole())
                .isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("invalid signup input is 400 with code V001 and field messages")
    void signupValidation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","username":"x","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                // The rejected values are never echoed back.
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("short"))));
    }

    // ========================= login =========================

    @Test
    @DisplayName("an unknown email and a wrong password give byte-identical bodies")
    void failedLoginsAreIndistinguishable() throws Exception {
        mockMvc.perform(signup(EMAIL, "jane_doe", PASSWORD)).andExpect(status().isCreated());

        MvcResult unknownEmail = mockMvc.perform(login("nobody@example.com", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult wrongPassword = mockMvc.perform(login(EMAIL, "WrongPassw0rd"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // Byte-for-byte: same code, same message, same status, same path, and no timestamp or
        // other varying field that could be used to tell the two cases apart.
        assertThat(unknownEmail.getResponse().getContentAsByteArray())
                .isEqualTo(wrongPassword.getResponse().getContentAsByteArray());
        assertThat(JsonPath.<String>read(unknownEmail.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("A003");
    }

    @Test
    @DisplayName("login returns a usable access token")
    void loginReturnsWorkingToken() throws Exception {
        mockMvc.perform(signup(EMAIL, "jane_doe", PASSWORD)).andExpect(status().isCreated());

        String accessToken = accessToken(login(EMAIL, PASSWORD));

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    @DisplayName("login is case-insensitive on the email")
    void loginAcceptsAnyCasing() throws Exception {
        mockMvc.perform(signup(EMAIL, "jane_doe", PASSWORD)).andExpect(status().isCreated());

        mockMvc.perform(login("Jane@EXAMPLE.com", PASSWORD))
                .andExpect(status().isOk());
    }

    // ========================= token rejection =========================

    @Test
    @DisplayName("a tampered token is 401 with code A005")
    void tamperedTokenIsA005() throws Exception {
        mockMvc.perform(signup(EMAIL, "jane_doe", PASSWORD)).andExpect(status().isCreated());
        String token = accessToken(login(EMAIL, PASSWORD));

        // The first character of the signature, not the last: base64url gives the final character
        // of a 32-byte signature two bits that decode to nothing, so changing it can leave the
        // signature bytes identical and the token valid. See JwtTokenProviderTest for the detail.
        int signatureStart = token.lastIndexOf('.') + 1;
        char first = token.charAt(signatureStart);
        String tampered = token.substring(0, signatureStart)
                + (first == 'A' ? 'B' : 'A')
                + token.substring(signatureStart + 1);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("A005"));
    }

    @Test
    @DisplayName("an expired token is 401 with code A004, distinct from A005")
    void expiredTokenIsA004() throws Exception {
        User user = userRepository.save(User.builder()
                .email(EMAIL).username("jane_doe").password("$2a$12$notusedhere")
                .role(Role.USER).enabled(true).locked(false).failedLoginCount(0)
                .build());

        String expired = tokenProvider.generateAccessToken(user, Duration.ofSeconds(-60));

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A004"));
    }

    @Test
    @DisplayName("login returns the status/res envelope with snake_case tokens, sub and scope")
    void loginResponseShape() throws Exception {
        mockMvc.perform(signup(EMAIL, "jane_doe", PASSWORD)).andExpect(status().isCreated());

        String body = mockMvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(200))
                .andExpect(jsonPath("$.status.message").value("Success"))
                .andExpect(jsonPath("$.res.data.access_token").isNotEmpty())
                .andExpect(jsonPath("$.res.data.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.res.data.token_type").value("Bearer"))
                .andExpect(jsonPath("$.res.data.expires_in").isNumber())
                // Both describe the role: sub is the lowercased authority, scope the bare name.
                .andExpect(jsonPath("$.res.sub").value("role_user"))
                .andExpect(jsonPath("$.res.scope").value("USER"))
                // Not the username, and not the email - neither belongs in a role field.
                .andExpect(jsonPath("$.res.sub").value(org.hamcrest.Matchers.not("jane_doe")))
                // Login alone uses `res`; nothing should still be published under `data`.
                .andExpect(jsonPath("$.data").doesNotExist())
                // snake_case only - no camelCase duplicates left behind.
                .andExpect(jsonPath("$.res.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.res.data.expiresIn").doesNotExist())
                // sub and scope are the whole of the account data: no profile, no email, no id.
                .andExpect(jsonPath("$.res.user").doesNotExist())
                .andExpect(jsonPath("$.res.email").doesNotExist())
                .andExpect(jsonPath("$.res.id").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // The email must not appear anywhere in the body - including inside the tokens, whose
        // payload is only base64, not encryption. The refresh token no longer carries it; the
        // access token does, which is the deliberate trade that keeps /me free of a database hit.
        String refreshToken = JsonPath.read(body, "$.res.data.refresh_token");
        assertThat(decodePayload(refreshToken)).doesNotContain(EMAIL);

        // And the same for refresh, which must not reintroduce it.
        String refreshBody = mockMvc.perform(refresh(refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(refreshBody).doesNotContain("\"email\"");
    }

    @Test
    @DisplayName("the configured TTLs are the ones actually applied")
    void configuredTtlsAreActuallyApplied() throws Exception {
        mockMvc.perform(signup(EMAIL, "jane_doe", PASSWORD)).andExpect(status().isCreated());

        // src/test/resources/application.yml sets 2m / 3h, values chosen not to match the
        // defaults. A misspelled property name would fall back to the default and this fails.
        String body = mockMvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.res.data.expires_in").value(120))
                .andReturn().getResponse().getContentAsString();

        // expiresIn must describe the token it was sent with, not a config value nothing used.
        String accessToken = JsonPath.read(body, "$.res.data.access_token");
        Map<String, Object> claims = readClaims(accessToken);
        long lifetime = ((Number) claims.get("exp")).longValue() - ((Number) claims.get("iat")).longValue();
        assertThat(lifetime).isEqualTo(120);

        Instant expiresAt = refreshTokenRepository.findAll().get(0).getExpiresAt();
        assertThat(expiresAt).isBetween(
                Instant.now().plus(Duration.ofMinutes(170)),
                Instant.now().plus(Duration.ofMinutes(190)));
    }

    @Test
    @DisplayName("the refresh token is a JWT, and only its jti is stored")
    void refreshTokenIsAJwt() throws Exception {
        mockMvc.perform(signup(EMAIL, "jane_doe", PASSWORD)).andExpect(status().isCreated());
        String body = mockMvc.perform(login(EMAIL, PASSWORD)).andReturn().getResponse().getContentAsString();
        String refreshToken = JsonPath.read(body, "$.res.data.refresh_token");

        assertThat(refreshToken.split("\\.")).hasSize(3);

        // The stored row identifies the token without being usable as one: a database dump cannot
        // be replayed, because the signature it would need is not in the database.
        assertThat(refreshTokenRepository.findAll()).hasSize(1);
        String storedTokenId = refreshTokenRepository.findAll().get(0).getTokenId();
        assertThat(refreshToken).doesNotContain(storedTokenId);
        assertThat(storedTokenId).isEqualTo(tokenProvider.parseRefreshToken(refreshToken).getId());
    }

    @Test
    @DisplayName("an access token cannot be exchanged for a new pair")
    void accessTokenCannotBeRefreshed() throws Exception {
        mockMvc.perform(signup(EMAIL, "jane_doe", PASSWORD)).andExpect(status().isCreated());
        String accessToken = accessToken(login(EMAIL, PASSWORD));

        // Both tokens are signed by the same key, so only the typ claim stops the more widely
        // exposed of the two from minting fresh credentials.
        mockMvc.perform(refresh(accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A008"));
    }

    @Test
    @DisplayName("expiry is re-checked on every request, not cached after one pass")
    void expiryIsCheckedOnEveryRequest() throws Exception {
        User user = userRepository.save(User.builder()
                .email(EMAIL).username("jane_doe").password("$2a$12$notusedhere")
                .role(Role.USER).enabled(true).locked(false).failedLoginCount(0)
                .build());

        String valid = tokenProvider.generateAccessToken(user);
        String expired = tokenProvider.generateAccessToken(user, Duration.ofSeconds(-60));

        // A valid call first, so a passing request cannot be what "primes" anything, then the same
        // expired token twice: both refused. Nothing about verification is remembered between
        // requests, which is what makes the 5-minute lifetime an actual bound.
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + valid))
                .andExpect(status().isOk());
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + expired))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("A004"));
        }
        // And the still-valid token keeps working: rejecting one token does not poison the next.
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + valid))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a token with no exp claim is refused, not treated as eternal")
    void tokenWithoutExpiryIsRefused() throws Exception {
        User user = userRepository.save(User.builder()
                .email(EMAIL).username("jane_doe").password("$2a$12$notusedhere")
                .role(Role.USER).enabled(true).locked(false).failedLoginCount(0)
                .build());

        // Correctly signed by this service's key, but with the exp claim left out - which a JWT
        // parser reads as "never expires". A005, because it is malformed rather than expired:
        // A004 would tell the client to refresh, and refreshing would not help.
        String eternal = io.jsonwebtoken.Jwts.builder()
                .issuer("spring-app")
                .subject(String.valueOf(user.getId()))
                .issuedAt(new java.util.Date())
                .claim("typ", "access")
                .claim("email", EMAIL)
                .claim("username", "jane_doe")
                .claim("role", "USER")
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + eternal))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A005"));
    }

    @Test
    @DisplayName("a refresh token cannot be used as an access token")
    void refreshTokenIsNotAnAccessToken() throws Exception {
        mockMvc.perform(signup(EMAIL, "jane_doe", PASSWORD)).andExpect(status().isCreated());
        String body = mockMvc.perform(login(EMAIL, PASSWORD)).andReturn().getResponse().getContentAsString();
        String refreshToken = JsonPath.read(body, "$.res.data.refresh_token");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A005"));
    }

    // ========================= refresh and logout =========================

    @Test
    @DisplayName("refresh returns a new pair and burns the old refresh token")
    void refreshRotatesTheToken() throws Exception {
        mockMvc.perform(signup(EMAIL, "jane_doe", PASSWORD)).andExpect(status().isCreated());
        String loginBody = mockMvc.perform(login(EMAIL, PASSWORD)).andReturn().getResponse().getContentAsString();
        String firstRefresh = JsonPath.read(loginBody, "$.res.data.refresh_token");

        String refreshBody = mockMvc.perform(refresh(firstRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String secondRefresh = JsonPath.read(refreshBody, "$.data.refreshToken");
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        // Replaying the first one now fails: one refresh token, one use.
        mockMvc.perform(refresh(firstRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A008"));
    }

    @Test
    @DisplayName("after logout, the old refresh token is 401")
    void logoutRevokesRefreshToken() throws Exception {
        mockMvc.perform(signup(EMAIL, "jane_doe", PASSWORD)).andExpect(status().isCreated());
        String loginBody = mockMvc.perform(login(EMAIL, PASSWORD)).andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(loginBody, "$.res.data.access_token");
        String refreshToken = JsonPath.read(loginBody, "$.res.data.refresh_token");

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(refresh(refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A008"));
    }

    @Test
    @DisplayName("an unknown refresh token is 401, not 500")
    void unknownRefreshTokenIsRejected() throws Exception {
        mockMvc.perform(refresh("not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A008"));
    }

    @Test
    @DisplayName("logout requires a token")
    void logoutRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
    }

    // ========================= lockout =========================

    @Test
    @DisplayName("five consecutive failures lock the account: 403 with code A006")
    void fiveFailuresLockTheAccount() throws Exception {
        mockMvc.perform(signup(EMAIL, "jane_doe", PASSWORD)).andExpect(status().isCreated());

        for (int attempt = 1; attempt < User.MAX_FAILED_LOGINS; attempt++) {
            mockMvc.perform(login(EMAIL, "WrongPassw0rd"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("A003"));
        }

        // The fifth failure is the one that locks, and says so.
        mockMvc.perform(login(EMAIL, "WrongPassw0rd"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A006"))
                .andExpect(jsonPath("$.status").value(403));

        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().isLocked()).isTrue();

        // And the correct password no longer helps.
        mockMvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A006"));
    }

    @Test
    @DisplayName("a successful login clears the failure streak")
    void successResetsTheCounter() throws Exception {
        mockMvc.perform(signup(EMAIL, "jane_doe", PASSWORD)).andExpect(status().isCreated());

        mockMvc.perform(login(EMAIL, "WrongPassw0rd")).andExpect(status().isUnauthorized());
        mockMvc.perform(login(EMAIL, "WrongPassw0rd")).andExpect(status().isUnauthorized());
        mockMvc.perform(login(EMAIL, PASSWORD)).andExpect(status().isOk());

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    // ========================= helpers =========================

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signup(
            String email, String username, String password) {
        return post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"username\":\"" + username
                        + "\",\"password\":\"" + password + "\"}");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String email, String password) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readClaims(String jwt) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(decodePayload(jwt), Map.class);
    }

    /** A JWT payload is base64url, not ciphertext: anything put in a claim is readable. */
    private String decodePayload(String jwt) {
        String payload = jwt.split("\\.")[1];
        return new String(java.util.Base64.getUrlDecoder().decode(payload),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refresh(String refreshToken) {
        return post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}");
    }

    private String accessToken(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest)
            throws Exception {
        String body = mockMvc.perform(loginRequest)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.res.data.access_token");
    }
}
