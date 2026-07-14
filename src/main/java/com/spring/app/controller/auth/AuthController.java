package com.spring.app.controller.auth;


import com.spring.app.common.AbstractRestController;
import com.spring.app.payload.auth.AuthResponse;
import com.spring.app.payload.auth.LoginRequest;
import com.spring.app.payload.auth.RegisterRequest;
import com.spring.app.payload.user.UserResponse;
import com.spring.app.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication management APIs")
public class AuthController extends AbstractRestController {

    private final AuthService authService;

    @Operation(
        summary = "User Registration",
        description = "Register a new user account"
    )
    @ApiResponse(responseCode = "201", description = "User registered successfully")
    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return created(response);
    }

    @Operation(
        summary = "User Login",
        description = "Authenticate user and return JWT tokens"
    )
    @ApiResponse(responseCode = "200", description = "Login successful")
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request) {
        return ok(authService.login(request));
    }

    @Operation(
        summary = "Refresh Token",
        description = "Get new access token using refresh token"
    )
    @ApiResponse(responseCode = "200", description = "Token refreshed successfully")
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
            @RequestParam String refreshToken) {
        AuthResponse response = authService.refreshToken(refreshToken);
        return ok(response);
    }

    @Operation(
        summary = "User Logout",
        description = "Logout user (client should remove tokens)"
    )
    @ApiResponse(responseCode = "200", description = "Logout successful")
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        authService.logout();
        return ok();
    }
}
