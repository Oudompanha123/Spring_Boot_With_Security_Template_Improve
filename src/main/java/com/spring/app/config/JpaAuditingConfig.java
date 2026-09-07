package com.spring.app.config;

import com.spring.app.security.CustomUserDetails;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.TimeZone;

/**
 * Turns on JPA auditing and tells it who the current user is.
 *
 * <p>The auditor is a {@code Long} — the user id from {@link CustomUserDetails#getId()}, not the
 * email. An id is a stable foreign key: it still points at the right account after a rename, it
 * does not put a customer address into every row of every table, and it can be joined.
 *
 * <p>Reading the principal from {@code SecurityContextHolder} means {@code createdBy}/
 * {@code modifiedBy} populate themselves on every write made in a request thread, with no
 * service-layer plumbing to forget. Writes with no principal (the seeder, a migration, an
 * anonymous signup) return {@link Optional#empty()} and leave the column null, which is the honest
 * answer rather than a fabricated "system" user id.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<Long> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.empty();
            }
            // Anonymous requests and test principals are not CustomUserDetails; both fall through.
            return authentication.getPrincipal() instanceof CustomUserDetails principal
                    ? Optional.ofNullable(principal.getId())
                    : Optional.empty();
        };
    }

    /** Everything is stored in UTC; local time is a presentation concern. */
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        System.setProperty("user.timezone", "UTC");
    }
}
