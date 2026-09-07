package com.spring.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI document and the Swagger UI Authorize button.
 *
 * <p>The {@code bearerAuth} scheme declared here is what makes Authorize work: with
 * {@code Type.HTTP} + {@code scheme("bearer")}, the UI adds {@code Authorization: Bearer <token>}
 * to requests on operations that reference the scheme, so paste the raw JWT with no {@code Bearer}
 * prefix.
 *
 * <p>The requirement is applied per operation with {@code @SecurityRequirement} rather than
 * globally, so the document tells the truth about which endpoints are public. A global requirement
 * would make {@code GET /api/v1/books} and login look as if they need a token.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8088}")
    private String serverPort;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Spring Boot JWT Security Template")
                        .version("v1.0.0")
                        .description("""
                                JWT authentication and role-based authorization.

                                ## Getting a token
                                1. `POST /api/v1/auth/signup` — creates a USER account.
                                2. `POST /api/v1/auth/login` — returns `accessToken` and `refreshToken`.
                                3. Click **Authorize** and paste the `accessToken` (no `Bearer` prefix).
                                4. `POST /api/v1/auth/refresh` when the access token expires (15 min).
                                5. `POST /api/v1/auth/logout` — revokes the refresh tokens.

                                ## Roles
                                | Endpoint | USER | MANAGER | ADMIN | anonymous |
                                |---|---|---|---|---|
                                | `GET /api/v1/books` | yes | yes | yes | yes |
                                | `POST /api/v1/books` | no | yes | yes | no |
                                | `PUT /api/v1/books/{id}` | no | yes | yes | no |
                                | `DELETE /api/v1/books/{id}` | no | no | yes | no |

                                ## Error codes
                                Failures return a JSON `ErrorResponse` with a stable `code`:
                                `A001` unauthenticated, `A002` forbidden, `A003` invalid credentials,
                                `A004` token expired, `A005` token invalid, `A006` account locked,
                                `A007` account disabled, `A008`/`A009` refresh token revoked/expired,
                                `U002` email already registered, `V001` validation failed.
                                """)
                        .contact(new Contact()
                                .name("Soeuk Sophanit")
                                .url("https://github.com/soeuksophanit"))
                )
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Development server")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", bearerAuthScheme()));
    }

    private SecurityScheme bearerAuthScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Paste the access token returned by POST /api/v1/auth/login.");
    }
}
