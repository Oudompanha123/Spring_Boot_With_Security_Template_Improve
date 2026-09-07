package com.spring.app.payload.user;

import com.spring.app.domain.user.User;
import com.spring.app.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Public projection of an account.
 *
 * <p>Built field by field from the entity on purpose. Returning the entity itself is how password
 * hashes, lock counters and audit columns end up in a JSON response the day someone adds a field.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "User profile")
public class UserResponse {

    private Long id;
    private String email;
    private String username;
    private Role role;
    private boolean enabled;
    private Instant lastLoginAt;
    private Instant createdAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
