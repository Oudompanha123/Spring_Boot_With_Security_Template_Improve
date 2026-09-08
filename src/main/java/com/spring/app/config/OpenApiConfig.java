package com.spring.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
 *
 * <h2>No servers list, on purpose</h2>
 *
 * This used to pin {@code servers} to {@code http://localhost:${server.port}}. That is correct
 * exactly once - on the machine that built it - and wrong everywhere else. Deployed behind HTTPS,
 * Swagger UI read that block and sent every "Try it out" to the browser's own localhost over plain
 * http from an https page: wrong host, mixed content and a cross-origin request in one go,
 * surfacing as "Failed to fetch" and "URL scheme must be http or https for CORS request" - which
 * sends you looking at CORS, where the problem is not.
 *
 * <p>With no servers list, springdoc derives the URL from the incoming request. Combined with
 * {@code forward-headers-strategy: framework}, which makes Spring trust the proxy's
 * {@code X-Forwarded-Proto} and {@code X-Forwarded-Host}, the document describes whatever host it
 * was actually fetched from - localhost in development, the public HTTPS URL in a deployment - and
 * requests stay same-origin, so CORS never enters into it.
 */
@Configuration
public class OpenApiConfig {

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
