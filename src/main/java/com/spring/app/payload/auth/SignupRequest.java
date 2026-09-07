package com.spring.app.payload.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Signup payload.
 *
 * <h2>Why there is no role field</h2>
 *
 * Privilege is not something a caller gets to ask for. This DTO has no {@code role} property at
 * all, so {@code {"email":"...","role":"ADMIN"}} cannot bind one: Jackson has nowhere to put the
 * value and {@code AuthServiceImpl} hard-codes {@code Role.USER} on the entity it builds. Unknown
 * properties are ignored explicitly below rather than by relying on the framework default, because
 * that default is a configuration flag someone could flip.
 *
 * <p>The stronger reason is the shape of the code: the service never copies a role off a request
 * object, so no future edit to this class can accidentally re-open the hole. Elevating an account
 * is an out-of-band operation (an admin endpoint or a migration), never a self-service one.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Signup request. Any role supplied in the body is ignored; signup always creates a USER.")
public class SignupRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    @Size(max = 180, message = "Email must not exceed 180 characters")
    @Schema(description = "Login identifier", example = "jane@example.com")
    private String email;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 80, message = "Username must be between 3 and 80 characters")
    @Schema(description = "Display name", example = "jane_doe")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "Password must contain at least one letter and one digit"
    )
    @Schema(description = "Plaintext password; stored only as a BCrypt hash", example = "Str0ngPassw0rd")
    private String password;
}
