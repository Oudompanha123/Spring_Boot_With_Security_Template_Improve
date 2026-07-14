package com.spring.app.converter;


import com.spring.app.enums.ClientType;
import com.spring.app.security.JwtAuthenticationTokenWithPrincipal;
import com.spring.app.security.JwtUserPrincipal;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {

        log.debug("=== creating UserPrincipal from JWT ===");

        // Create rich user principal from JWT claims
        JwtUserPrincipal userPrincipal = JwtUserPrincipal.fromJwt(jwt);

        // Extract client type
        String clientTypeStr = jwt.getClaimAsString("client_type");
        ClientType clientType = clientTypeStr != null ?
                ClientType.valueOf(clientTypeStr.toUpperCase()) : ClientType.WEB;

        // Build authorities
        Collection<GrantedAuthority> authorities = buildAuthoritiesFromPrincipal(userPrincipal);

        // Create an authentication token with UserPrincipal as principal
        return new JwtAuthenticationTokenWithPrincipal(jwt, authorities, userPrincipal);
    }

    private Collection<GrantedAuthority> buildAuthoritiesFromPrincipal(JwtUserPrincipal principal) {
        Stream<GrantedAuthority> roleAuthorities = principal.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role));

        Stream<GrantedAuthority> permissionAuthorities = principal.getPermissions().stream()
                .map(SimpleGrantedAuthority::new);

        return Stream.concat(roleAuthorities, permissionAuthorities)
                .collect(Collectors.toList());
    }
}