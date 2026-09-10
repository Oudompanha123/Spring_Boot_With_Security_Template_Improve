package com.spring.app.payload.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What login and refresh hand back: a short-lived access token and the means to renew it.
 *
 * <p>Internal carrier, not the wire shape: {@code LoginResponse} maps this into the
 * {@code status}/{@code res} envelope the login endpoint returns.
 *
 * <p>It carries {@link #sub} and {@link #scope} — the username and the role — because the login
 * contract asks for them, mirroring the OAuth2 token response. Deliberately nothing beyond those
 * two: no email, no id, no profile block. The access token already carries what a client needs to
 * render itself, and {@code GET /api/v1/me} returns the rest on demand, so anything extra here
 * only widens where account data gets copied — proxy logs, browser storage, crash reports.
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

    @Schema(description = "Access token lifetime in seconds", example = "300")
    private Long expiresIn;

    @Schema(description = "The granted authority, lowercased", example = "role_manager")
    private String sub;

    @Schema(description = "The granted role", example = "MANAGER")
    private String scope;
}
