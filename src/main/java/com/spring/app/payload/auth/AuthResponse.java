package com.spring.app.payload.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authentication response")
public class AuthResponse {
    
    @Schema(description = "JWT access token")
    private String accessToken;
    
    @Schema(description = "JWT refresh token")
    private String refreshToken;
    
    @Schema(description = "Token expiration time in seconds", example = "900")
    private Long expiresIn;
    
    @Schema(description = "Token type", example = "Bearer")
    private String tokenType = "Bearer";
    
    @Schema(description = "User information")
    private UserInfo userInfo;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long userId;
        private String username;
        private String email;
        private String fullName;
    }
}
