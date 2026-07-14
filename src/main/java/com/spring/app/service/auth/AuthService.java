package com.spring.app.service.auth;


import com.spring.app.payload.auth.AuthResponse;
import com.spring.app.payload.auth.LoginRequest;
import com.spring.app.payload.auth.RegisterRequest;
import com.spring.app.payload.user.UserResponse;

public interface AuthService {
    UserResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refreshToken(String refreshToken);
    void logout();
}
