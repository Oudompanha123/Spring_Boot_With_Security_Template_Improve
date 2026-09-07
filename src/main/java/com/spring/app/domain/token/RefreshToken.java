package com.spring.app.domain.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * Server-side record of an issued refresh token.
 *
 * <p>Access tokens stay stateless; refresh tokens are deliberately not. Logout has to be able to
 * kill a session, and that is impossible if the only proof of a session is a signature the client
 * holds. Keeping one row per refresh token buys revocation at the cost of a database lookup on the
 * refresh path only — the hot path (every authenticated request) still touches no storage.
 *
 * <p>What is stored is the token's {@code jti}, not the token. The row therefore proves a token was
 * issued and says whether it is still live, but is useless to anyone who obtains it: a database
 * dump cannot be replayed, because the signature it would need is not in the database. The token
 * itself only ever exists in the response that issued it and in the client that holds it.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_tokens_token_id", columnList = "token_id", unique = true),
        @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString // nothing here is a credential: the jti identifies a token without being one
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The {@code jti} of the issued refresh token, not the token itself. */
    @Column(name = "token_id", unique = true, nullable = false, length = 64)
    private String tokenId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    /** A token is usable only while it is neither revoked nor expired. */
    public boolean isActive() {
        return !revoked && !isExpired();
    }
}
