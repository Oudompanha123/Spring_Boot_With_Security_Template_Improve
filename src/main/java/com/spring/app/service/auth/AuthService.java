package com.spring.app.service.auth;

import com.spring.app.payload.auth.AuthResponse;
import com.spring.app.payload.auth.LoginRequest;
import com.spring.app.payload.auth.SignupRequest;
import com.spring.app.payload.user.UserResponse;

public interface AuthService {

    /** Creates a {@code USER} account. Never anything more privileged. */
    UserResponse signup(SignupRequest request);

    AuthResponse login(LoginRequest request);

    /** Exchanges a live refresh token for a new pair, rotating the refresh token. */
    AuthResponse refresh(String refreshToken);

    /** Revokes every refresh token the user holds, so no further refresh can succeed. */
    void logout(Long userId);
}
