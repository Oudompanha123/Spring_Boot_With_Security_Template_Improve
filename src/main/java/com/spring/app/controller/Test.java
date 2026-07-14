package com.spring.app.controller;

import com.spring.app.common.AbstractRestController;
import com.spring.app.helper.AuthHelper;
import com.spring.app.security.JwtUserPrincipal;
import com.spring.app.security.SecurityUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class Test extends AbstractRestController {

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Object principal) {
        // Log what we actually got injected (works for both JWT and FORM)
        if (principal instanceof JwtUserPrincipal jwt) {
            log.info("🎯 JWT principal: {}", jwt.getUsername());
            log.info("🎯 JWT roles: {}", jwt.getRoles());
            log.info("🎯 JWT permissions: {}", jwt.getPermissions());
        } else if (principal instanceof SecurityUser su) {
            log.info("🎯 Form principal: {}", su.getUsername());
        } else {
            log.info("🎯 Principal type: {}", principal == null ? "null" : principal.getClass().getName());
        }

        // Build response using AuthHelper (abstracts over auth type)
        String username = AuthHelper.getUsername();
        String email = AuthHelper.getEmail();
        Set<String> roles = AuthHelper.getRoles();
        Set<String> permissions = AuthHelper.getPermissions();
        String authType = AuthHelper.getAuthType().name();

        return new MeResponse(username, email, roles, permissions, authType);
    }

    // Simple DTO (Java 17+ record)
    public record MeResponse(
            String username,
            String email,
            Set<String> roles,
            Set<String> permissions,
            String authType
    ) {}
}