package com.spring.app.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;
import java.util.TimeZone;

@Configuration
@EnableJpaAuditing // Enable JPA auditing
public class JpaAuditingConfig {

    // This bean provides the current Instant for @CreatedDate and @LastModifiedDate
    @Bean
    public AuditorAware<String> auditorProvider() {
        // You can implement user-based auditing here
        return () -> Optional.of("system"); // or get from SecurityContext
    }

    // Optional: Configure timezone for the entire application
    @PostConstruct
    public void init() {
        // Set JVM timezone (affects LocalDateTime operations)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        System.setProperty("user.timezone", "UTC");
    }
}