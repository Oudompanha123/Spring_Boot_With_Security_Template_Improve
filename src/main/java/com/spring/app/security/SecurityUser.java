package com.spring.app.security;

import com.spring.app.domain.permission.Permission;
import com.spring.app.domain.role.Role;
import com.spring.app.domain.user.User;
import com.spring.app.enums.ClientType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public record SecurityUser(User user, ClientType clientType) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new HashSet<>();

        // Add role-based authorities (prefixed with ROLE_)
        if (user.getRoles() != null) {
            Set<GrantedAuthority> roleAuthorities = user.getRoles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getRoleName()))
                    .collect(Collectors.toSet());
            authorities.addAll(roleAuthorities);

            // Add permission-based authorities
            Set<GrantedAuthority> permissionAuthorities = user.getRoles().stream()
                    .flatMap(role -> role.getPermissions().stream())
                    .map(permission -> new SimpleGrantedAuthority(permission.getPermissionName()))
                    .collect(Collectors.toSet());
            authorities.addAll(permissionAuthorities);
        }

        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return user.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return user.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }

    // Convenience methods
    public Long getUserId() {
        return user.getUserId();
    }

    public String getEmail() {
        return user.getEmail();
    }

    public String getFullName() {
        return user.getFullName();
    }

    public Set<String> getRoleNames() {
        if (user.getRoles() == null) {
            return new HashSet<>();
        }
        return user.getRoles().stream()
                .map(Role::getRoleName)
                .collect(Collectors.toSet());
    }

    public Set<String> getPermissionNames() {
        if (user.getRoles() == null) {
            return new HashSet<>();
        }
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getPermissionName)
                .collect(Collectors.toSet());
    }

    public boolean hasRole(String roleName) {
        return getRoleNames().contains(roleName);
    }

    public boolean hasPermission(String permissionName) {
        return getPermissionNames().contains(permissionName);
    }

    public boolean hasAnyRole(String... roleNames) {
        Set<String> userRoles = getRoleNames();
        for (String roleName : roleNames) {
            if (userRoles.contains(roleName)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAnyPermission(String... permissionNames) {
        Set<String> userPermissions = getPermissionNames();
        for (String permissionName : permissionNames) {
            if (userPermissions.contains(permissionName)) {
                return true;
            }
        }
        return false;
    }
}