package com.spring.app.payload.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What login and refresh hand back: a short-lived access token and the means to renew it.
 *
 * <p>Tokens only. No profile, no email, no role — the access token already carries what a client
 * needs to render itself, and {@code GET /api/v1/me} returns the profile for anything more.
 * Repeating account data in every login and refresh response only widens where it gets copied:
 * proxy logs, browser storage, crash reports.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Authentication response")
public class AuthResponse {

    @Schema(description = "JWT to send as: Authorization: Bearer <token>")
    private String accessToken;

    @Schema(description = "Opaque token for POST /api/v1/auth/refresh. Single use: refreshing rotates it.")
    private String refreshToken;

    @Schema(description = "Token type", example = "Bearer")
    @Builder.Default
    private String tokenType = "Bearer";

    @Schema(description = "Access token lifetime in seconds", example = "900")
    private Long expiresIn;
}
