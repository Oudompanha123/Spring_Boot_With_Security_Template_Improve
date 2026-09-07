package com.spring.app.service.auth;

import com.spring.app.domain.token.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Deletes refresh-token records that can no longer authenticate anything.
 *
 * <p>Not housekeeping-for-its-own-sake: rotation writes one record per refresh, and with a
 * 5-minute access token an active client refreshes roughly twelve times an hour. That is about 96
 * records per user per working day, none of which are ever read again once expired, and nothing
 * else removes them. A store with per-key TTLs would expire them for free; a table needs this.
 *
 * <p>Expired records only. A revoked-but-unexpired record is deliberately left alone until its
 * natural expiry — it is the trail of a rotation, and deleting it early would erase evidence of a
 * token having existed. Either way a client presenting one gets {@code A008}, since an unknown
 * {@code jti} and a revoked {@code jti} are the same answer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupJob {

    private final RefreshTokenStore refreshTokenStore;

    /**
     * Runs every half hour by default. The cadence only needs to keep the table from growing
     * without bound, so it is deliberately unhurried and configurable — set
     * {@code app.refresh-token-cleanup.cron} to change it, or {@code -} to switch it off.
     */
    @Scheduled(cron = "${app.refresh-token-cleanup.cron:0 */30 * * * *}")
    public void purgeExpired() {
        int removed = refreshTokenStore.purgeExpiredBefore(Instant.now());
        if (removed > 0) {
            log.info("Purged {} expired refresh token record(s)", removed);
        } else {
            log.debug("No expired refresh token records to purge");
        }
    }
}
