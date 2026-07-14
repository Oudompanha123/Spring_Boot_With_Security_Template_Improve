package com.spring.app.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;

public class JwtAuthenticationTokenWithPrincipal extends JwtAuthenticationToken {
    
    private final JwtUserPrincipal userPrincipal;
    
    public JwtAuthenticationTokenWithPrincipal(Jwt jwt,
                                               Collection<GrantedAuthority> authorities,
                                               JwtUserPrincipal userPrincipal) {
        super(jwt, authorities, userPrincipal.getUsername());
        this.userPrincipal = userPrincipal;
    }
    
    @Override
    public Object getPrincipal() {
        return userPrincipal;
    }
}