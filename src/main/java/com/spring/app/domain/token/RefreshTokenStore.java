package com.spring.app.domain.token;

import java.time.Instant;
import java.util.Optional;

/**
 * Where issued refresh tokens are recorded, as an interface rather than a repository call.
 *
 * <p>The reason this exists is that the storage choice here is genuinely open. A JPA table gives
 * durable, transactional revocation and lets "who has a live session" be a SQL join; Redis with a
 * per-key TTL would make expiry self-cleaning and keep the writes off the primary database. Both
 * are defensible, and which one is right depends on refresh volume and on how much infrastructure a
 * deployment wants to run.
 *
 * <p>So the service depends on this port and not on {@link RefreshTokenRepository}. Swapping the
 * implementation becomes a new class and a bean choice instead of surgery on the authentication
 * flow, and neither implementation can leak into it: the methods below deal in a
 * {@link StoredRefreshToken} record, never a JPA entity, because an entity is exactly the kind of
 * return type a Redis implementation could not honestly produce.
 *
 * <p>Only the token's {@code jti} is ever stored — never the token. See {@link RefreshToken}.
 */
public interface RefreshTokenStore {

    /** Records a newly issued token so it can later be revoked. */
    void issue(Long userId, String tokenId, Instant expiresAt);

    Optional<StoredRefreshToken> find(String tokenId);

    /** Kills one token: used by rotation, which spends the presented token as it issues the next. */
    void revoke(String tokenId);

    /**
     * Kills every live token of one user, for logout.
     *
     * @return how many were still live
     */
    int revokeAllFor(Long userId);

    /**
     * Deletes records that can no longer authenticate anything.
     *
     * @return how many were removed
     */
    int purgeExpiredBefore(Instant cutoff);

    /**
     * What the store knows about one issued token.
     *
     * <p>A record, not the entity: the port has to be satisfiable by a store that has no rows.
     */
    record StoredRefreshToken(Long userId, String tokenId, Instant expiresAt, boolean revoked) {

        public boolean isExpired() {
            return expiresAt.isBefore(Instant.now());
        }

        /** Usable only while neither revoked nor expired. */
        public boolean isActive() {
            return !revoked && !isExpired();
        }
    }
}
