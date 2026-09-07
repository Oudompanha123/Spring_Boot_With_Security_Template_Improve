package com.spring.app.config;

import com.spring.app.exception.JwtSecretConfigurationException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Turns a missing or too-short JWT secret into an instruction the reader can act on.
 *
 * <p>Refusing to start without a signing key is the right behaviour, but by default the reason
 * arrives as four nested {@code BeanCreationException}s under "Unable to start embedded Tomcat",
 * with the actual sentence at the bottom of a 40-line stack. A failure that nobody reads is only
 * half a safety mechanism, so this analyzer replaces the stack with the
 * {@code APPLICATION FAILED TO START} block Spring Boot uses for its own diagnostics.
 *
 * <p>Registered in {@code META-INF/spring.factories}, which is still the mechanism for
 * {@code FailureAnalyzer} in Boot 3 — analyzers run before the context exists, so they cannot be
 * beans.
 */
public class JwtSecretFailureAnalyzer extends AbstractFailureAnalyzer<JwtSecretConfigurationException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, JwtSecretConfigurationException cause) {
        String action = """
                Set a signing secret of at least 32 bytes, then start again. Either:

                  1. Export it (Git Bash / Linux / macOS):
                       export JWT_SECRET="$(openssl rand -base64 48)"

                     PowerShell:
                       $env:JWT_SECRET = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Max 256 }))

                  2. Or put it in ./config/application.yml, which Spring Boot reads automatically
                     and which is git-ignored:

                       jwt:
                         secret: "<your random value>"

                There is deliberately no built-in default. A signing key committed to this
                repository would let anyone who can read the repository mint valid tokens for
                every deployment that forgot to override it.""";

        return new FailureAnalysis(cause.getMessage(), action, cause);
    }
}
