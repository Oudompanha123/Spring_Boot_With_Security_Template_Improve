package com.spring.app.service.auth;

import com.spring.app.domain.role.Role;
import com.spring.app.domain.role.RoleRepository;
import com.spring.app.domain.user.User;
import com.spring.app.domain.user.UserRepository;
import com.spring.app.enums.Status;
import com.spring.app.exception.BusinessException;
import com.spring.app.payload.auth.AuthResponse;
import com.spring.app.payload.auth.LoginRequest;
import com.spring.app.payload.auth.RegisterRequest;
import com.spring.app.payload.user.UserResponse;
import com.spring.app.security.SecurityUser;
import com.spring.app.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final String DEFAULT_USER_ROLE = "USER";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        validateRegisterRequest(request);

        log.info("Registering user: {}", request.getUsername());

        checkUserExistence(request);
        User user = createUser(request);
        User savedUser = userRepository.save(user);

        log.info("User registered successfully: {}", savedUser.getUsername());
        return mapToUserResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        validateLoginRequest(request);

        log.info("Login attempt for user: {}", request.getUsername());

        User user = authenticateUser(request);
        TokenPair tokens = generateTokens(user);

        log.info("User logged in successfully: {}", user.getUsername());
        return buildAuthResponse(user, tokens);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BadCredentialsException("Refresh token cannot be null or empty");
        }

        log.info("Processing refresh token request");

        try {
            Jwt jwt = jwtUtil.decodeToken(refreshToken);
            jwtUtil.validateTokenType(jwt, TOKEN_TYPE_REFRESH);

            String username = jwt.getClaims().get("sub").toString();
            if (!StringUtils.hasText(username)) {
                throw new BadCredentialsException("Invalid token: missing subject");
            }

            User user = loadUserWithDetails(username);
            String newAccessToken = jwtUtil.generateAccessToken(user);
            TokenPair tokens = new TokenPair(newAccessToken, refreshToken);

            log.info("Token refreshed successfully for user: {}", username);
            return buildAuthResponse(user, tokens);

        } catch (JwtException e) {
            log.warn("Invalid refresh token: {}", e.getMessage());
            throw new BadCredentialsException("Invalid refresh token", e);
        }
    }

    @Override
    public void logout() {
        // In JWT stateless architecture, logout is handled client-side by removing the token
        // For enhanced security, you could maintain a token blacklist in Redis or database
        log.info("User logout requested");
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Register request cannot be null");
        }

        if (!StringUtils.hasText(request.getUsername())) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        if (!StringUtils.hasText(request.getEmail())) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        if (!StringUtils.hasText(request.getPassword())) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }

        // Add more validation as needed (email format, password strength, etc.)
    }

    private void validateLoginRequest(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Login request cannot be null");
        }

        if (!StringUtils.hasText(request.getUsername())) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        if (!StringUtils.hasText(request.getPassword())) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
    }

    private void checkUserExistence(RegisterRequest request) {
        if (userRepository.existsByUsernameAndStatus(request.getUsername(), Status.ACTIVE)) {
            log.warn("Registration attempt with existing username: {}", request.getUsername());
            throw new BusinessException("Username already exists");
        }

        if (userRepository.existsByEmailAndStatus(request.getEmail(), Status.ACTIVE)) {
            log.warn("Registration attempt with existing email: {}", request.getEmail());
            throw new BusinessException("Email already exists");
        }
    }

    private User createUser(RegisterRequest request) {
        Role userRole = roleRepository.findByRoleName(DEFAULT_USER_ROLE)
                .orElseThrow(() -> new BusinessException("Default USER role not found"));

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setStatus(Status.ACTIVE);
        user.setEmailVerified(false);
        user.setRoles(Set.of(userRole));

        return user;
    }

    private User authenticateUser(LoginRequest request) {
        try {
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword());

            Authentication authentication = authenticationManager.authenticate(authToken);
            SecurityUser principal = (SecurityUser) authentication.getPrincipal();

            return principal.user();

        } catch (BadCredentialsException e) {
            log.warn("Invalid credentials for user: {}", request.getUsername());
            throw new BadCredentialsException("Invalid username or password");
        } catch (DisabledException e) {
            log.warn("Account disabled for user: {}", request.getUsername());
            throw new BadCredentialsException("Account is disabled");
        } catch (LockedException e) {
            log.warn("Account locked for user: {}", request.getUsername());
            throw new BadCredentialsException("Account is locked");
        } catch (AccountExpiredException e) {
            log.warn("Account expired for user: {}", request.getUsername());
            throw new BadCredentialsException("Account is expired");
        } catch (CredentialsExpiredException e) {
            log.warn("Credentials expired for user: {}", request.getUsername());
            throw new BadCredentialsException("Credentials are expired");
        } catch (Exception e) {
            log.error("Authentication error for user: {}", request.getUsername(), e);
            throw new BadCredentialsException("Authentication failed");
        }
    }

    private TokenPair generateTokens(User user) {
        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId().toString());
        return new TokenPair(accessToken, refreshToken);
    }

    private User loadUserWithDetails(String username) {
        return userRepository.findByUsernameWithRolesAndPermissionsByUserId(Long.valueOf(username))
                .orElseThrow(() -> {
                    log.error("User not found during token refresh: {}", username);
                    return new BadCredentialsException("User not found");
                });
    }

    private AuthResponse buildAuthResponse(User user, TokenPair tokens) {
        AuthResponse.UserInfo userInfo = new AuthResponse.UserInfo(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName()
        );

        return new AuthResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                jwtUtil.getAccessTtlSeconds(),
                "Bearer",
                userInfo
        );
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getStatus().name(),
                user.getEmailVerified()
        );
    }

    // Helper record for a token pair
    private record TokenPair(String accessToken, String refreshToken) {}
}