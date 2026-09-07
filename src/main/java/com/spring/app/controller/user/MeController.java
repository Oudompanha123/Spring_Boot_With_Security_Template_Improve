package com.spring.app.controller.user;

import com.spring.app.common.AbstractRestController;
import com.spring.app.enums.Role;
import com.spring.app.exception.ErrorResponse;
import com.spring.app.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Echoes back what the access token proved, which makes it the quickest way to test a token. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Me", description = "The authenticated caller")
public class MeController extends AbstractRestController {

    @Operation(summary = "Who am I", description = "Requires any authenticated role.")
    @ApiResponse(responseCode = "401", description = "A001 no token / A004 expired / A005 invalid",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal CustomUserDetails principal) {
        // Answered entirely from the verified token: no database round trip on the hot path.
        return ok(new MeResponse(
                principal.getId(),
                principal.getEmail(),
                principal.getDisplayName(),
                principal.getRole()
        ));
    }

    public record MeResponse(Long id, String email, String username, Role role) {
    }
}
