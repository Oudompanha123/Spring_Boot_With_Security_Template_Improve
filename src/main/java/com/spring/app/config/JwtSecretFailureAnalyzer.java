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
                Set JWT_SECRET to at least 32 bytes of random data, then start again.

                  Deployed (Render, Fly, Docker, Kubernetes, systemd):
                    Set JWT_SECRET in the platform's environment settings - not in a file inside
                    the image. On Render that is the service's Environment tab, or an envVar in
                    render.yaml with `generateValue: true` if the service is Blueprint-managed.
                    Note that render.yaml is only applied to Blueprint-managed services; a service
                    created as a plain Web Service ignores it, which is the usual reason this
                    message appears on a first deploy.

                  Local shell:
                    export JWT_SECRET="$(openssl rand -base64 48)"                     # bash
                    $env:JWT_SECRET = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Max 256 }))   # PowerShell

                  Local file (development only, git-ignored, never in the image):
                    ./config/application-dev.yml
                      jwt:
                        secret: "<your random value>"

                There is deliberately no built-in default. A signing key committed to this
                repository would let anyone who can read the repository mint valid tokens for
                every deployment that forgot to override it.""";

        return new FailureAnalysis(cause.getMessage(), action, cause);
    }
}
