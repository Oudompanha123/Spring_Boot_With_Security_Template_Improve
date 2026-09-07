package com.spring.app.security;

import com.spring.app.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads accounts by email, which is this application's login identifier.
 *
 * <p>The {@link UsernameNotFoundException} thrown here never reaches the client as itself:
 * {@code DaoAuthenticationProvider} converts it to {@code BadCredentialsException}
 * ({@code hideUserNotFoundExceptions} is on by default), so an unknown email and a wrong password
 * come back as the same 401. Keep it that way — a distinguishable "no such user" is a free account
 * enumeration API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(normalise(email))
                .map(CustomUserDetails::from)
                .orElseThrow(() -> {
                    // Logged at debug: this fires on every wrong-email login attempt and the value
                    // is attacker-controlled, so it does not belong in INFO-level output.
                    log.debug("Authentication attempted for an unknown account");
                    return new UsernameNotFoundException("Account not found");
                });
    }

    private String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
