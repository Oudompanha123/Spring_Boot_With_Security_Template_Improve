package com.spring.app.domain.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * The {@link RefreshTokenStore} backed by the {@code refresh_tokens} table.
 *
 * <p>The default, and the reason is durability of <em>revocation</em>. Revoking is the one write
 * here that must not be lost: losing an issue just forces a re-login, while losing a revoke brings
 * a logged-out token back to life. A committed row in Postgres cannot be lost that way; a Redis
 * {@code DEL} can be, under snapshotting or {@code appendfsync everysec}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JpaRefreshTokenStore implements RefreshTokenStore {

    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    public void issue(Long userId, String tokenId, Instant expiresAt) {
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .tokenId(tokenId)
                .expiresAt(expiresAt)
                .revoked(false)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredRefreshToken> find(String tokenId) {
        return refreshTokenRepository.findByTokenId(tokenId)
                .map(row -> new StoredRefreshToken(
                        row.getUserId(), row.getTokenId(), row.getExpiresAt(), row.isRevoked()));
    }

    @Override
    @Transactional
    public void revoke(String tokenId) {
        // An explicit update rather than loading the entity and letting dirty checking notice:
        // the port hands back a record, so there is no managed instance to mutate.
        refreshTokenRepository.revokeByTokenId(tokenId);
    }

    @Override
    @Transactional
    public int revokeAllFor(Long userId) {
        return refreshTokenRepository.revokeAllByUserId(userId);
    }

    @Override
    @Transactional
    public int purgeExpiredBefore(Instant cutoff) {
        return refreshTokenRepository.deleteExpiredBefore(cutoff);
    }
}
