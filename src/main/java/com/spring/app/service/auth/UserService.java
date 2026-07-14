package com.spring.app.service.auth;

import com.spring.app.domain.user.UserRepository;
import com.spring.app.enums.ClientType;
import com.spring.app.enums.Status;
import com.spring.app.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        if (!StringUtils.hasText(usernameOrEmail)) {
            log.warn("Attempted to load user with null or empty username/email");
            throw new UsernameNotFoundException("Username or email cannot be null or empty");
        }

        log.debug("Loading user by username/email: {}", usernameOrEmail);

        // Try to find user by username or email with ACTIVE status
        var user = userRepository
                .findByUsernameOrEmailAndStatus(usernameOrEmail, Status.ACTIVE)
                .orElseThrow(() -> {
                    log.warn("User not found or inactive: {}", usernameOrEmail);
                    return new UsernameNotFoundException("User not found: " + usernameOrEmail);
                });

        log.debug("Successfully loaded user: {}", user.getUsername());
        return new SecurityUser(user, ClientType.WEB);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserWithRolesAndPermissions(String usernameOrEmail) throws UsernameNotFoundException {
        if (!StringUtils.hasText(usernameOrEmail)) {
            throw new UsernameNotFoundException("Username or email cannot be null or empty");
        }

        log.debug("Loading user with roles and permissions: {}", usernameOrEmail);

        var user = userRepository
                .findByUsernameOrEmailWithRolesAndPermissions(usernameOrEmail)
                .filter(u -> u.getStatus() == Status.ACTIVE)
                .orElseThrow(() -> {
                    log.warn("User not found or inactive with roles/permissions: {}", usernameOrEmail);
                    return new UsernameNotFoundException("User not found: " + usernameOrEmail);
                });

        log.debug("Successfully loaded user with authorities: {}", user.getUsername());
        return new SecurityUser(user, ClientType.WEB);
    }
}