package com.spring.app.util;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.spring.app.domain.permission.Permission;
import com.spring.app.domain.role.Role;
import com.spring.app.domain.user.User;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JwtUtil {

    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_FULL_NAME = "fullName";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String RSA_ALGORITHM = "RSA";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    @Getter
    private final long accessTtlSeconds;
    private final int refreshTtlDays;
    private final String issuer;
    private final String audience;

    public JwtUtil(@Value("${jwt.private-key}") Resource privateKeyResource,
                   @Value("${jwt.public-key}") Resource publicKeyResource,
                   @Value("${jwt.access-ttl-seconds}") long accessTtlSeconds,
                   @Value("${jwt.refresh-ttl-days}") int refreshTtlDays,
                   @Value("${jwt.issuer}") String issuer,
                   @Value("${jwt.audience}") String audience,
                   @Value("${jwt.key-id}")
                   String keyID
    ) {

        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlDays = refreshTtlDays;
        this.issuer = issuer;
        this.audience = audience;

        try {
            // Load RSA keys
            PrivateKey privateKey = loadPrivateKey(privateKeyResource);
            PublicKey publicKey = loadPublicKey(publicKeyResource);

            // Setup JWT encoder/decoder
            JWK jwk = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) publicKey)
                    .privateKey((java.security.interfaces.RSAPrivateKey) privateKey)
                    .keyID(keyID) // Add key ID for better key management
                    .build();

            JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
            this.jwtEncoder = new NimbusJwtEncoder(jwkSource);
            this.jwtDecoder = NimbusJwtDecoder.withPublicKey((java.security.interfaces.RSAPublicKey) publicKey)
                    .build();

            log.info("JWT utility initialized successfully with issuer: {}", issuer);
        } catch (Exception e) {
            log.error("Failed to initialize JWT utility", e);
            throw new IllegalStateException("JWT configuration error", e);
        }
    }

    public String generateAccessToken(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        Map<String, Object> claims = buildUserClaims(user);
        return generateAccessToken(user.getUsername(), claims);
    }

    public String generateAccessToken(String username, Map<String, Object> claims) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        Instant now = Instant.now();
        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(Collections.singletonList(audience))
                .subject(claims.get(CLAIM_USER_ID).toString())
                .issuedAt(now)
                .expiresAt(now.plus(accessTtlSeconds, ChronoUnit.SECONDS))
                .notBefore(now) // Add not-before claim for security
                .id(UUID.randomUUID().toString()) // Add unique JWT ID
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claims(claimsMap -> {
                    if (claims != null) {
                        claimsMap.putAll(claims);
                    }
                })
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claimsSet)).getTokenValue();
    }

    public String generateRefreshToken(String username) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        Instant now = Instant.now();
        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(Collections.singletonList(audience))
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plus(refreshTtlDays, ChronoUnit.DAYS))
                .notBefore(now)
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claimsSet)).getTokenValue();
    }

    public Jwt decodeToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BadCredentialsException("Token cannot be null or empty");
        }

        try {
            return jwtDecoder.decode(token);
        } catch (JwtException e) {
            log.warn("Failed to decode JWT token: {}", e.getMessage());
            throw new BadCredentialsException("Invalid token", e);
        }
    }

    public void validateTokenType(Jwt jwt, String expectedType) {
        if (jwt == null) {
            throw new BadCredentialsException("JWT cannot be null");
        }

        String tokenType = jwt.getClaimAsString(CLAIM_TOKEN_TYPE);
        if (!expectedType.equals(tokenType)) {
            log.warn("Token type mismatch. Expected: {}, Got: {}", expectedType, tokenType);
            throw new BadCredentialsException(
                    String.format("Invalid token type. Expected: %s, Got: %s", expectedType, tokenType)
            );
        }
    }

    public boolean isAccessToken(Jwt jwt) {
        return TOKEN_TYPE_ACCESS.equals(jwt.getClaimAsString(CLAIM_TOKEN_TYPE));
    }

    public boolean isRefreshToken(Jwt jwt) {
        return TOKEN_TYPE_REFRESH.equals(jwt.getClaimAsString(CLAIM_TOKEN_TYPE));
    }

    public String extractUsername(Jwt jwt) {
        return jwt != null ? jwt.getSubject() : null;
    }

    public String extractTokenId(Jwt jwt) {
        return jwt != null ? jwt.getId() : null;
    }

    private Map<String, Object> buildUserClaims(User user) {
        Map<String, Object> claims = new HashMap<>();

        // Add basic user info
        claims.put(CLAIM_USER_ID, user.getUserId());
        claims.put(CLAIM_EMAIL, user.getEmail());
        claims.put(CLAIM_FULL_NAME, user.getFullName());
        claims.put("username", user.getUsername());

        // Add roles and permissions if present
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            Set<String> roles = user.getRoles().stream()
                    .map(Role::getRoleName)
                    .collect(Collectors.toSet());
            claims.put(CLAIM_ROLES, roles);

            Set<String> permissions = user.getRoles().stream()
                    .flatMap(role -> role.getPermissions().stream())
                    .map(Permission::getPermissionName)
                    .collect(Collectors.toSet());
            claims.put(CLAIM_PERMISSIONS, permissions);
        }

        return claims;
    }

    private PrivateKey loadPrivateKey(Resource resource) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String keyContent = loadKeyContent(resource);
        String cleanedKey = cleanPemKey(keyContent, "PRIVATE KEY");

        byte[] keyBytes = Base64.getDecoder().decode(cleanedKey);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance(RSA_ALGORITHM).generatePrivate(spec);
    }

    private PublicKey loadPublicKey(Resource resource) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String keyContent = loadKeyContent(resource);
        String cleanedKey = cleanPemKey(keyContent, "PUBLIC KEY");

        byte[] keyBytes = Base64.getDecoder().decode(cleanedKey);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance(RSA_ALGORITHM).generatePublic(spec);
    }

    private String loadKeyContent(Resource resource) throws IOException {
        if (!resource.exists()) {
            throw new IOException("Key resource does not exist: " + resource.getDescription());
        }

        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private String cleanPemKey(String key, String keyType) {
        return key
                .replaceAll("-----BEGIN " + keyType + "-----", "")
                .replaceAll("-----END " + keyType + "-----", "")
                .replaceAll("\\s", "");
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return this.jwtDecoder;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return this.jwtEncoder;
    }
}