package com.spring.app.payload.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login payload.
 *
 * <p>Note what is <em>not</em> validated: the password carries {@code @NotBlank} and nothing else.
 * Applying the signup password rules here would reject a malformed password with a 400 before the
 * credentials are ever checked, which tells an attacker that a given string does not match the
 * password policy and turns login into a policy oracle. A wrong password is a 401, full stop.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Login request")
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Schema(description = "Registered email address", example = "jane@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Schema(description = "Account password", example = "Str0ngPassw0rd")
    private String password;
}
