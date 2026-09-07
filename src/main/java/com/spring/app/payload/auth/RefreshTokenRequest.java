package com.spring.app.payload.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Refresh payload.
 *
 * <p>The token travels in the body, not in a query parameter: query strings are logged by every
 * proxy and web server in the path, land in browser history, and get pasted into bug reports. A
 * refresh token is a long-lived credential and belongs in neither place.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Refresh request")
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    @Schema(description = "The refresh token issued at login or by the previous refresh")
    private String refreshToken;
}
