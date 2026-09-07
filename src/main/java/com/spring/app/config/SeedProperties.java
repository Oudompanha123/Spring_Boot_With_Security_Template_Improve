package com.spring.app.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Bindings for the development seed accounts. See {@link DataSeeder}. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.seed")
public class SeedProperties {

    private boolean enabled = false;

    private String adminEmail = "admin@example.com";
    private String managerEmail = "manager@example.com";
    private String userEmail = "user@example.com";

    /*
     * Development defaults. They exist so a fresh clone can be exercised in one command; they are
     * not secrets and must be overridden (or the seeder disabled) anywhere that matters.
     */
    private String adminPassword = "Admin12345";
    private String managerPassword = "Manager12345";
    private String userPassword = "User12345";
}
