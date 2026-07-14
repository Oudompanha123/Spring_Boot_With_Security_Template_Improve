package com.spring.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.app.common.ApiResponse;
import com.spring.app.converter.JwtAuthenticationConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origins:*}")
    private List<String> allowedOrigins;

    @Value("${app.cors.max-age:3600}")
    private Long corsMaxAge;

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/image/**",
            "/favicon.ico"
    };

    private static final String[] SWAGGER_ENDPOINTS = {
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/webjars/**"
    };


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF for stateless API
                .csrf(AbstractHttpConfigurer::disable)

                // Configure CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Configure session management (stateless)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Configure security headers
//                .headers(headers -> headers
//                        .frameOptions().deny()
//                        .contentTypeOptions().and()
//                        .httpStrictTransportSecurity(hstsConfig -> hstsConfig
//                                .maxAgeInSeconds(31536000)
//                                .includeSubDomains(true)
//                        )
//                        .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
//                )

                // Configure authorization rules
                .authorizeHttpRequests(auth -> auth
                                // Public authentication endpoints
                                .requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll()

                                // Image
                                .requestMatchers(HttpMethod.GET, "/api/v1/image/**","/favicon.ico").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/v1/me").hasAnyRole("USER") // ROLE_USER
//                        .requestMatchers(HttpMethod.GET, "/api/v1/me").hasAnyAuthority("ROLE_USER") // ROLE_USER
                                .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN") // ROLE_USER


                                // Documentation and monitoring endpoints
                                .requestMatchers(SWAGGER_ENDPOINTS).permitAll()

                                // Error endpoint
                                .requestMatchers("/error").permitAll()

                                // All other endpoints require authentication
                                .anyRequest().authenticated()
                )

                // Configure OAuth2 Resource Server for JWT
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)
                        )
                        .accessDeniedHandler(accessDeniedHandler())
                        .authenticationEntryPoint(authenticationEntryPoint())
                )

                // Global exception handling
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                );

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            log.warn("Unauthorized access attempt: {} - {}", request.getRequestURI(), authException.getMessage());

            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            // Add basic security headers to error responses
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

            ApiResponse<Object> apiResponse = ApiResponse.error(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Authentication required"
            );

            objectMapper.writeValue(response.getOutputStream(), apiResponse);
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            log.warn("Access denied for user attempting to access: {} - {}",
                    request.getRequestURI(), accessDeniedException.getMessage());

            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            // Add basic security headers to error responses
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

            ApiResponse<Object> apiResponse = ApiResponse.error(
                    HttpStatus.FORBIDDEN.value(),
                    "Access denied"
            );

            objectMapper.writeValue(response.getOutputStream(), apiResponse);
        };
    }


    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Use stronger BCrypt configuration for production
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Configure allowed origins (avoid using * in production)
        if (allowedOrigins.contains("*")) {
            log.warn("CORS configured to allow all origins. This should not be used in production!");
            configuration.setAllowedOriginPatterns(List.of("*"));
        } else {
            configuration.setAllowedOrigins(allowedOrigins);
        }

        // Configure allowed methods
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // Configure allowed headers
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));

        // Configure exposed headers
        configuration.setExposedHeaders(Arrays.asList(
                "Access-Control-Allow-Origin",
                "Access-Control-Allow-Credentials"
        ));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(Duration.ofSeconds(corsMaxAge));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}