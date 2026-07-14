package com.spring.app.security;

import lombok.Getter;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

@Getter
public class JwtUserPrincipal {
    private final String userId;
    private final String username;
    private final String email;
    private final String fullName;
    private final List<String> roles;
    private final List<String> permissions;
    
    private JwtUserPrincipal(String userId, String username, String email, 
                           String fullName, List<String> roles, List<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.roles = roles != null ? roles : List.of();
        this.permissions = permissions != null ? permissions : List.of();
    }
    
    public static JwtUserPrincipal fromJwt(Jwt jwt) {
        return new JwtUserPrincipal(
            jwt.getClaim("userId").toString(),
            jwt.getClaimAsString("username"),
            jwt.getClaimAsString("email"),
            jwt.getClaimAsString("fullName"),
            jwt.getClaimAsStringList("roles"),
            jwt.getClaimAsStringList("permissions")
        );
    }

    @Override
    public String toString() {
        return username;
    }
}