package com.spring.app.service.auth;

import com.spring.app.domain.user.User;
import com.spring.app.domain.user.UserRepository;
import com.spring.app.exception.ApiException;
import com.spring.app.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the consecutive-failure counter that locks an account.
 *
 * <h2>Why this is a separate bean</h2>
 *
 * The counter has to survive the failure that incremented it. If the increment happened inside the
 * same transaction that then throws {@code BadCredentialsException}, the rollback would undo it and
 * the account would never lock no matter how many attempts were made — a lockout that quietly does
 * nothing is worse than none, because everyone believes it is there.
 *
 * <p>So each method commits on its own ({@code REQUIRES_NEW}) and <em>returns</em> the outcome
 * instead of throwing. The caller decides what exception to raise, after the write is durable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    private final UserRepository userRepository;

    /**
     * Records one failed attempt against the account with this email, if it exists.
     *
     * @return {@code true} when the account is now locked
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(String email) {
        return userRepository.findByEmail(email)
                .map(user -> {
                    boolean locked = user.registerFailedLogin();
                    userRepository.save(user);
                    if (locked) {
                        log.warn("Account locked after {} consecutive failed logins (userId={})",
                                User.MAX_FAILED_LOGINS, user.getId());
                    } else {
                        log.debug("Failed login {} of {} (userId={})",
                                user.getFailedLoginCount(), User.MAX_FAILED_LOGINS, user.getId());
                    }
                    return locked;
                })
                // Unknown email: nothing to count, and nothing observable either way. Creating a
                // counter for a non-existent account would be a way to probe which emails exist.
                .orElse(false);
    }

    /** Clears the failure streak and stamps {@code lastLoginAt}. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User recordSuccess(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND,
                        "Authenticated principal has no account row: id=" + userId));
        user.registerSuccessfulLogin();
        return userRepository.save(user);
    }
}
