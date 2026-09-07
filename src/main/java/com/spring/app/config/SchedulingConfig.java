package com.spring.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled}, for {@link com.spring.app.service.auth.RefreshTokenCleanupJob}.
 *
 * <p>Its own class rather than an annotation on the application class, so that what scheduling
 * exists for is discoverable from the config package, and so a deployment that runs many instances
 * has one obvious place to reach for when it needs the job to run on only one of them (a lock, or
 * a profile).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
