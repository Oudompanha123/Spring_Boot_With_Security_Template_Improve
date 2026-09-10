package com.spring.app.payload.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.spring.app.common.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The wire shape of {@code POST /api/v1/auth/login}.
 *
 * <pre>
 * {
 *   "status": { "code": 200, "message": "Success" },
 *   "res": {
 *     "data": { "access_token": "...", "refresh_token": "...",
 *               "token_type": "Bearer", "expires_in": 300 },
 *     "sub": "role_manager",
 *     "scope": "MANAGER"
 *   }
 * }
 * </pre>
 *
 * <p>Its own type rather than the {@code ApiResponse} envelope used everywhere else, because the
 * payload key here is {@code res} and not {@code data}. That is a deliberate choice by the client:
 * it means a caller cannot parse login with the same code it uses for {@code /books}, {@code /me},
 * signup or any error, all of which return {@code data}.
 *
 * <p>Token field names are snake_case to match the OAuth2 token-response convention
 * ({@code access_token}, {@code token_type}, {@code expires_in}), spelled out with
 * {@link JsonProperty} rather than a naming strategy so no other DTO in the project is affected.
 */
@Schema(description = "Login response")
public record LoginResponse(ApiResponse.Status status, Res res) {

    public static LoginResponse of(AuthResponse tokens) {
        return new LoginResponse(
                new ApiResponse.Status(200, "Success"),
                new Res(
                        new TokenData(
                                tokens.getAccessToken(),
                                tokens.getRefreshToken(),
                                tokens.getTokenType(),
                                tokens.getExpiresIn()
                        ),
                        tokens.getSub(),
                        tokens.getScope()
                )
        );
    }

    /**
     * @param data  the tokens
     * @param sub   the granted authority, lowercased: {@code role_user}, {@code role_manager},
     *              {@code role_admin}
     * @param scope the granted role: {@code USER}, {@code MANAGER}, {@code ADMIN}
     *
     *              <p>Both describe the role, not the account. Nothing here identifies who logged
     *              in — that is in the access token, and in {@code GET /api/v1/me}.
     */
    @Schema(description = "Login payload")
    public record Res(TokenData data, String sub, String scope) {
    }

    @Schema(description = "Issued tokens")
    public record TokenData(
            @JsonProperty("access_token")
            @Schema(description = "JWT to send as: Authorization: Bearer <token>")
            String accessToken,

            @JsonProperty("refresh_token")
            @Schema(description = "Single-use token for POST /api/v1/auth/refresh")
            String refreshToken,

            @JsonProperty("token_type")
            @Schema(example = "Bearer")
            String tokenType,

            @JsonProperty("expires_in")
            @Schema(description = "Access token lifetime in seconds", example = "300")
            Long expiresIn
    ) {
    }
}
