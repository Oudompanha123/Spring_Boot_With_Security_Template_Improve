package com.spring.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenAPI openApi = new OpenApiConfig().openAPI();

    @Test
    @DisplayName("no server URL is pinned, so springdoc derives it from the request")
    void doesNotPinAServerUrl() {
        // The regression this guards: the document used to pin
        // servers = [http://localhost:${server.port}]. Deployed behind HTTPS, Swagger UI read that
        // and sent every "Try it out" to the browser's own localhost over plain http - reported as
        // "Failed to fetch" plus a CORS complaint, which points at the wrong thing entirely.
        //
        // Left null, springdoc fills it in from the incoming request (honouring X-Forwarded-* via
        // forward-headers-strategy), so the document matches whatever host served it.
        assertThat(openApi.getServers()).isNull();
    }

    @Test
    @DisplayName("the bearerAuth scheme exists, which is what the Authorize button needs")
    void declaresBearerAuth() {
        SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get("bearerAuth");

        assertThat(scheme).isNotNull();
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
    }

    @Test
    @DisplayName("no security requirement is applied globally")
    void doesNotRequireAuthGlobally() {
        // A global requirement would make login and the public book reads look as though they
        // need a token, which is a lie the document should not tell.
        assertThat(openApi.getSecurity()).isNull();
    }
}
