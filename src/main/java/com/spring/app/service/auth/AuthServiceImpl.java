package com.spring.app.service.auth;

import com.spring.app.domain.token.RefreshTokenStore;
import com.spring.app.domain.user.User;
import com.spring.app.domain.user.UserRepository;
import com.spring.app.enums.Role;
import com.spring.app.exception.ApiException;
import com.spring.app.exception.ErrorCode;
import com.spring.app.payload.auth.AuthResponse;
import com.spring.app.payload.auth.LoginRequest;
import com.spring.app.payload.auth.SignupRequest;
import com.spring.app.payload.user.UserResponse;
import com.spring.app.security.CustomUserDetails;
import com.spring.app.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Signup, login, refresh and logout.
 *
 * <p>Nothing in this class logs an email, a password, a hash or a token value. User ids are used
 * instead: they identify an account for support and forensics without turning the log file into a
 * credential store or a list of customer addresses.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final LoginAttemptService loginAttemptService;
    private final UserRepository userRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    // ========================= signup =========================

    @Override
    @Transactional
    public UserResponse signup(SignupRequest request) {
        String email = normaliseEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            // 409 + U002. The email is the account identifier, so a duplicate is a conflict on a
            // resource the caller already knows about; this is not the enumeration-sensitive path.
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS, "Signup rejected: email already registered");
        }

        User user = User.builder()
                .email(email)
                .username(request.getUsername().trim())
                // The raw password exists only as a local variable inside the encoder from here on.
                .password(passwordEncoder.encode(request.getPassword()))
                // Hard-coded, not read from the request. This is the whole defence against
                // privilege escalation at signup: there is no path from the payload to this value.
                .role(Role.USER)
                .enabled(true)
                .locked(false)
                .failedLoginCount(0)
                .build();

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent signups can both pass the existsByEmail check; the unique index is
            // what actually decides. Report it as the same conflict rather than a 500.
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS, "Signup lost the unique-email race");
        }

        log.info("Account created (userId={}, role={})", user.getId(), user.getRole());
        return UserResponse.from(user);
    }

    // ========================= login =========================

    /**
     * Authenticates and issues a token pair.
     *
     * <p>Deliberately not {@code @Transactional}: the failure counter in
     * {@link LoginAttemptService} has to commit even though this method then throws.
     *
     * <p>The account-state checks (locked, disabled) happen inside
     * {@code DaoAuthenticationProvider} <em>before</em> the password is compared, so a locked
     * account reports itself as locked whether or not the submitted password was right.
     */
    @Override
    public AuthResponse login(LoginRequest request) {
        String email = normaliseEmail(request.getEmail());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));

            CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
            User user = loginAttemptService.recordSuccess(principal.getId());

            log.info("Login succeeded (userId={})", user.getId());
            return issueTokens(user);

        } catch (BadCredentialsException e) {
            // One catch for both "no such email" and "wrong password": the provider hides the
            // difference, and so does the response the advice builds from this exception.
            if (loginAttemptService.recordFailure(email)) {
                // The attempt that crossed the threshold reports the lock (403 + A006) rather than
                // another 401, so the user is told why further attempts will not help. This does
                // reveal that the account exists; the README records that trade-off.
                throw new LockedException("Account locked after too many failed attempts");
            }
            throw e;
        }
    }

    // ========================= refresh =========================

    @Override
    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        // Two checks, and both are needed. The signature proves the token was issued by this
        // service and has not been altered or expired; the row proves it has not been revoked
        // since. Neither alone is sufficient: a signature cannot be withdrawn, and a jti with no
        // verified signature is just a string the caller made up.
        Claims claims;
        try {
            claims = tokenProvider.parseRefreshToken(refreshTokenValue);
        } catch (ExpiredJwtException e) {
            throw new ApiException(ErrorCode.REFRESH_TOKEN_EXPIRED, "Refresh token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            // Bad signature, tampered, wrong issuer, or an access token presented here.
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token failed verification");
        }

        RefreshTokenStore.StoredRefreshToken stored = refreshTokenStore.find(claims.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "Unknown refresh token"));

        if (stored.revoked()) {
            // Logged out, or already exchanged. Either way it is dead.
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token has been revoked");
        }
        if (stored.isExpired()) {
            // Belt and braces: the JWT expiry above should already have caught this.
            throw new ApiException(ErrorCode.REFRESH_TOKEN_EXPIRED, "Refresh token has expired");
        }

        User user = userRepository.findById(stored.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token has no account"));

        // Re-check account state on every refresh. This is the point where a lock or a disable
        // applied after the access token was issued actually takes effect.
        if (user.isLocked()) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED, "Refresh refused for a locked account");
        }
        if (!user.isEnabled()) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "Refresh refused for a disabled account");
        }

        // Rotation: one refresh token buys exactly one new pair. A stolen token is then usable only
        // until the legitimate client refreshes, and the theft leaves a trace (the victim's next
        // refresh fails) instead of granting indefinite quiet access.
        refreshTokenStore.revoke(stored.tokenId());

        log.info("Refresh token exchanged (userId={})", user.getId());
        return issueTokens(user);
    }

    // ========================= logout =========================

    @Override
    @Transactional
    public void logout(Long userId) {
        int revoked = refreshTokenStore.revokeAllFor(userId);

        // Only the refresh side is revocable. The access token stays valid until it expires,
        // because verifying it touches no storage - that is what "stateless" costs. Keep the
        // access TTL short (15 minutes here); the README discusses the blacklist alternative.
        log.info("Logout: revoked {} refresh token(s) (userId={})", revoked, userId);
    }

    // ========================= internals =========================

    private AuthResponse issueTokens(User user) {
        String accessToken = tokenProvider.generateAccessToken(user);
        JwtTokenProvider.IssuedRefreshToken refreshToken = tokenProvider.generateRefreshToken(user);

        // Only the jti is recorded. That is enough to revoke the token and not enough to use it.
        refreshTokenStore.issue(user.getId(), refreshToken.tokenId(), refreshToken.expiresAt());

        // No user object in the body: the caller already knows who it just authenticated as, the
        // access token carries the id, email and role for anything that needs them, and GET
        // /api/v1/me returns the profile on demand. Shipping it here as well means every login and
        // every refresh copies account data into logs, proxies and client storage for no use.
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.token())
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessTtlSeconds())
                // Both derived from the role, per the agreed login contract: `sub` is the Spring
                // authority lowercased (ROLE_MANAGER -> role_manager) and `scope` is the bare role
                // name (MANAGER). Neither identifies the account - the access token carries that.
                //
                // Locale.ROOT is not decoration: under a Turkish locale the default toLowerCase()
                // turns the I in ADMIN into a dotless i, so ROLE_ADMIN would become "role_admın"
                // and no client comparison would ever match it.
                .sub(user.getRole().authority().toLowerCase(Locale.ROOT))
                .scope(user.getRole().name())
                .build();
    }

    /**
     * Emails are stored and compared lower-cased and trimmed, so {@code Jane@Example.com} is the
     * same account as {@code jane@example.com} at signup, at login and at the unique index.
     */
    private String normaliseEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
