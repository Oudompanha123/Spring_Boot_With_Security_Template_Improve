package com.spring.app.payload.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "User registration request")
public class RegisterRequest {
    
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 80, message = "Username must be between 3 and 80 characters")
    @Schema(description = "Unique username", example = "john_doe")
    private String username;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    @Schema(description = "User email address", example = "john.doe@example.com")
    private String email;
    
    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    @Schema(description = "User password", example = "securePassword123")
    private String password;
    
    @Size(max = 150, message = "Full name must not exceed 150 characters")
    @Schema(description = "User's full name", example = "John Doe")
    private String fullName;
    
    @Size(max = 15, message = "Phone must not exceed 15 characters")
    @Schema(description = "User's phone number", example = "+1234567890")
    private String phone;
}
