package com.spring.app;

import com.spring.app.domain.token.RefreshTokenRepository;
import com.spring.app.domain.token.RefreshTokenStore;
import com.spring.app.service.auth.RefreshTokenCleanupJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The storage port and the cleanup job.
 *
 * <p>Written against {@link RefreshTokenStore}, not the repository, so the same suite would hold
 * for a Redis implementation — which is the point of the port existing.
 */
@SpringBootTest
class RefreshTokenStoreIntegrationTest {

    private static final Long USER_ID = 7L;

    @Autowired
    private RefreshTokenStore store;

    @Autowired
    private RefreshTokenCleanupJob cleanupJob;

    @Autowired
    private RefreshTokenRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("an issued token is found and active")
    void issueAndFind() {
        store.issue(USER_ID, "jti-1", Instant.now().plus(Duration.ofHours(8)));

        RefreshTokenStore.StoredRefreshToken stored = store.find("jti-1").orElseThrow();

        assertThat(stored.userId()).isEqualTo(USER_ID);
        assertThat(stored.tokenId()).isEqualTo("jti-1");
        assertThat(stored.revoked()).isFalse();
        assertThat(stored.isActive()).isTrue();
        assertThat(stored.isExpired()).isFalse();
    }

    @Test
    @DisplayName("an unknown jti is simply absent")
    void unknownTokenId() {
        assertThat(store.find("never-issued")).isEmpty();
    }

    @Test
    @DisplayName("revoking one token leaves the others alone")
    void revokeOne() {
        Instant expiry = Instant.now().plus(Duration.ofHours(8));
        store.issue(USER_ID, "jti-1", expiry);
        store.issue(USER_ID, "jti-2", expiry);

        store.revoke("jti-1");

        assertThat(store.find("jti-1").orElseThrow().isActive()).isFalse();
        assertThat(store.find("jti-2").orElseThrow().isActive()).isTrue();
    }

    @Test
    @DisplayName("logout revokes every live token of one user and reports how many")
    void revokeAllForUser() {
        Instant expiry = Instant.now().plus(Duration.ofHours(8));
        store.issue(USER_ID, "jti-1", expiry);
        store.issue(USER_ID, "jti-2", expiry);
        store.issue(99L, "other-user", expiry);
        store.revoke("jti-1"); // already dead, so it should not be counted again

        assertThat(store.revokeAllFor(USER_ID)).isEqualTo(1);

        assertThat(store.find("jti-2").orElseThrow().revoked()).isTrue();
        assertThat(store.find("other-user").orElseThrow().isActive()).isTrue();
    }

    @Test
    @DisplayName("an expired record is found but not active")
    void expiredIsNotActive() {
        store.issue(USER_ID, "stale", Instant.now().minus(Duration.ofMinutes(1)));

        RefreshTokenStore.StoredRefreshToken stored = store.find("stale").orElseThrow();

        assertThat(stored.isExpired()).isTrue();
        assertThat(stored.isActive()).isFalse();
        assertThat(stored.revoked()).isFalse();
    }

    @Test
    @DisplayName("the cleanup job removes expired records and keeps the rest")
    void cleanupRemovesOnlyExpired() {
        store.issue(USER_ID, "expired-1", Instant.now().minus(Duration.ofHours(1)));
        store.issue(USER_ID, "expired-2", Instant.now().minus(Duration.ofSeconds(1)));
        store.issue(USER_ID, "live", Instant.now().plus(Duration.ofHours(8)));
        store.issue(USER_ID, "revoked-but-live", Instant.now().plus(Duration.ofHours(8)));
        store.revoke("revoked-but-live");

        cleanupJob.purgeExpired();

        assertThat(store.find("expired-1")).isEmpty();
        assertThat(store.find("expired-2")).isEmpty();
        assertThat(store.find("live")).isPresent();
        // Kept on purpose: it is the trail of a rotation, and it stops being readable soon enough
        // on its own expiry.
        assertThat(store.find("revoked-but-live")).isPresent();
    }

    @Test
    @DisplayName("the cleanup job is safe to run when there is nothing to do")
    void cleanupOnEmptyStore() {
        assertThat(store.purgeExpiredBefore(Instant.now())).isZero();

        store.issue(USER_ID, "live", Instant.now().plus(Duration.ofHours(8)));
        cleanupJob.purgeExpired();

        assertThat(store.find("live")).isPresent();
    }
}
