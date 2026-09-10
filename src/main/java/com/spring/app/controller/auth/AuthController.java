package com.spring.app.controller.auth;

import com.spring.app.common.AbstractRestController;
import com.spring.app.exception.ErrorResponse;
import com.spring.app.payload.auth.AuthResponse;
import com.spring.app.payload.auth.LoginRequest;
import com.spring.app.payload.auth.LoginResponse;
import com.spring.app.payload.auth.RefreshTokenRequest;
import com.spring.app.payload.auth.SignupRequest;
import com.spring.app.payload.user.UserResponse;
import com.spring.app.security.CustomUserDetails;
import com.spring.app.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Signup, login, token refresh and logout")
public class AuthController extends AbstractRestController {

    private final AuthService authService;

    @Operation(
            summary = "Sign up",
            description = "Creates an account. The role is always USER: a role supplied in the "
                    + "request body is ignored, and there is no field to bind it to."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "V001 validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "U002 email already registered",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        UserResponse response = authService.signup(request);
        return created(response);
    }

    @Operation(
            summary = "Log in",
            description = "Returns an access token and a refresh token. An unknown email and a "
                    + "wrong password produce the same 401/A003 body."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "401", description = "A003 invalid credentials",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "A006 account locked / A007 disabled",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse tokens = authService.login(request);
        // Returned directly rather than through ok(): login uses a `status`/`res` envelope of its
        // own, not the `data` envelope the rest of the API shares. See LoginResponse.
        return ResponseEntity.ok(LoginResponse.of(tokens));
    }

    @Operation(
            summary = "Refresh tokens",
            description = "Exchanges a live refresh token for a new pair. The submitted token is "
                    + "revoked in the process, so each refresh token works exactly once."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair issued"),
            @ApiResponse(responseCode = "401", description = "A008 revoked or unknown / A009 expired",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refresh(request.getRefreshToken());
        return ok(response);
    }

    @Operation(
            summary = "Log out",
            description = "Revokes every refresh token held by the caller. The current access token "
                    + "remains valid until it expires - see the README on stateless logout."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refresh tokens revoked"),
            @ApiResponse(responseCode = "401", description = "A001 no token / A004 expired / A005 invalid",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@AuthenticationPrincipal CustomUserDetails principal) {
        // getId() is why CustomUserDetails exists: the caller is identified by primary key, not by
        // re-parsing a username string.
        authService.logout(principal.getId());
        return ok("Logged out");
    }
}
