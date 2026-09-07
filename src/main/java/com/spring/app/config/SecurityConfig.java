package com.spring.app.config;

import com.spring.app.security.CustomAccessDeniedHandler;
import com.spring.app.security.CustomAuthenticationEntryPoint;
import com.spring.app.security.CustomUserDetailsService;
import com.spring.app.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * The single place where this API decides who may call what.
 *
 * <p>Component-based configuration: a {@link SecurityFilterChain} bean, not the removed
 * {@code WebSecurityConfigurerAdapter}. The chain is built once at startup and the rules below are
 * evaluated in declaration order, so the specific matchers must come before {@code anyRequest()}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // enables @PreAuthorize; see the DELETE rule below
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Value("${app.cors.allowed-origins:*}")
    private List<String> allowedOrigins;

    @Value("${app.cors.max-age:3600}")
    private Long corsMaxAge;

    /** Endpoints that must work with no token at all. */
    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh"
    };

    private static final String[] SWAGGER_ENDPOINTS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // No cookies, no session, so no CSRF token to protect: a bearer token is not sent
                // automatically by the browser, which is the attack CSRF defends against. The day a
                // credential moves into a cookie (see the refresh-cookie note in the README), CSRF
                // has to come back on for that endpoint.
                .csrf(AbstractHttpConfigurer::disable)

                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Every request carries its own proof. Nothing is remembered between requests, so
                // there is no session to fixate, hijack or replicate across instances.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // This is a JSON API: no login page, no browser redirects, no basic-auth popup.
                // Leaving these on is how a 401 turns into a 302 to /login that no client expects.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // ----- open -----
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .requestMatchers(SWAGGER_ENDPOINTS).permitAll()
                        .requestMatchers("/error").permitAll()
                        // Liveness probe for the hosting platform, which cannot authenticate.
                        // Only this one actuator path is open, and it reports "UP" with no
                        // component detail (see the management block in application-prod.yml) -
                        // a probe should not double as a public inventory of the internals.
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/image/**", "/favicon.ico").permitAll()

                        // ----- books: the authorization matrix -----
                        // Reading the catalogue is public.
                        .requestMatchers(HttpMethod.GET, "/api/v1/books", "/api/v1/books/**").permitAll()
                        // Writing requires a curator role. A USER reaching here is refused by the
                        // filter chain, so the 403 comes from CustomAccessDeniedHandler.
                        .requestMatchers(HttpMethod.POST, "/api/v1/books").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/books/**").hasAnyRole("MANAGER", "ADMIN")
                        // Deleting is ADMIN-only, but the rule is enforced by @PreAuthorize on the
                        // controller method rather than here. That is deliberate: it exercises the
                        // second failure path (method security -> @RestControllerAdvice) so both
                        // paths are proven to return the same JSON. The chain only demands a token.
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/books/**").authenticated()

                        // ----- everything else -----
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )

                // Reads the Authorization header before the (disabled) form-login filter, so an
                // authenticated context exists by the time the authorization rules are evaluated.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // Both filter-chain failure paths answer in JSON instead of an HTML error page.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );

        return http.build();
    }

    /**
     * BCrypt at strength 12: roughly an order of magnitude slower than the default 10, which is the
     * point. The cost is paid once per login and multiplies the cost of an offline attack on a
     * stolen hash dump.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Wires the database-backed authentication used by {@code POST /api/v1/auth/login}.
     *
     * <p>{@code hideUserNotFoundExceptions} is left on (the default) and stated explicitly here
     * because the identical-response rule depends on it: it converts
     * {@code UsernameNotFoundException} into {@code BadCredentialsException} so an unknown email
     * and a wrong password are indistinguishable from outside.
     */
    @Bean
    public AuthenticationProvider authenticationProvider(CustomUserDetailsService userDetailsService,
                                                         PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        if (allowedOrigins.contains("*")) {
            log.warn("CORS is configured to allow all origins. Do not ship this to production.");
            configuration.setAllowedOriginPatterns(List.of("*"));
        } else {
            configuration.setAllowedOrigins(allowedOrigins);
        }

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin",
                "Access-Control-Request-Method", "Access-Control-Request-Headers"));
        configuration.setExposedHeaders(Arrays.asList(
                "Access-Control-Allow-Origin", "Access-Control-Allow-Credentials"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(Duration.ofSeconds(corsMaxAge));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
