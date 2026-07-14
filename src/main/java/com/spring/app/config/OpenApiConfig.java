package com.spring.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8088}")
    private String serverPort;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Spring JWT Authentication API")
//                        .description("""
//                            ## Spring Boot JWT Authentication System
//
//                            This API provides JWT-based authentication and authorization.
//
//                            ### How to use:
//                            1. **Register** a new user via `/api/v1/auth/register`
//                            2. **Login** with credentials via `/api/v1/auth/login` to get JWT tokens
//                            3. **Use the access token** in the Authorization header: `Bearer <your-token>`
//                            4. **Refresh tokens** when needed via `/api/v1/auth/refresh`
//
//                            ### Public endpoints (no authentication required):
//                            - Registration and login endpoints
//                            - File serving endpoints (`/api/v1/image/**`)
//                            - Health check endpoints
//
//                            ### Protected endpoints (require JWT token):
//                            - All other API endpoints
//                            """)
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Soeuk Sophanit")
                                .email("dev@example.com")
                                .url("https://github.com/soeuksophanit"))
//                        .license(new License()
//                                .name("MIT License")
//                                .url("https://opensource.org/licenses/MIT"))
                )
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Development Server"),
                        new Server()
                                .url("https://api.yourdomain.com")
                                .description("Production Server")
                ))
                // ❌ DON'T add global security requirement
                // .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", createBearerAuthScheme()));
    }

    private SecurityScheme createBearerAuthScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Enter JWT Bearer token in the format: Bearer <your-token>");
    }
}