package com.spring.app.config;

import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Opens the H2 console — and only under the {@code h2} profile.
 *
 * <h2>Why this is a separate chain rather than a rule in {@code SecurityConfig}</h2>
 *
 * The console needs three things the API deliberately refuses: unauthenticated access, framing, and
 * form POSTs without a CSRF token. Granting those inside the main chain would mean carrying them in
 * every environment and relying on a path matcher to keep them contained. As its own
 * {@code @Profile("h2")} bean, the whole thing simply does not exist under {@code dev} or
 * {@code prod} — there is no rule to misread and no matcher to get wrong.
 *
 * <p>That containment is the point. A database console reachable without a login exposes the
 * schema, every row and arbitrary SQL; on a public URL it is the most damaging thing this
 * repository could ship. H2 is also a {@code developmentOnly} dependency, so the deployed jar has
 * no console to serve even if this profile were somehow activated.
 *
 * <p>{@code @Order(1)} puts this chain ahead of the API chain, which has no {@code securityMatcher}
 * and therefore has to be evaluated last.
 */
@Configuration
@Profile("h2")
public class H2ConsoleSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
        http
                // Spring Boot's own matcher for whatever spring.h2.console.path is set to, so the
                // two cannot drift apart.
                .securityMatcher(PathRequest.toH2Console())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // The console posts its login form without a token.
                .csrf(AbstractHttpConfigurer::disable)
                // Its UI is frame-based; the default DENY renders it blank. sameOrigin rather than
                // disabling the header, so another site still cannot frame it.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}
