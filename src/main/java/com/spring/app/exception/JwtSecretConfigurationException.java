package com.spring.app.exception;

/**
 * The JWT signing secret is missing or too weak to use.
 *
 * <p>A dedicated type, rather than a bare {@code IllegalStateException}, so that
 * {@code JwtSecretFailureAnalyzer} can recognise it in the cause chain and print an instruction
 * instead of a bean-creation stack trace. Extends {@link IllegalStateException} because that is
 * what it is: the application has been asked to start in a configuration it cannot honour.
 *
 * <p>Never carries the secret, or any part of it, in its message.
 */
public class JwtSecretConfigurationException extends IllegalStateException {

    public JwtSecretConfigurationException(String message) {
        super(message);
    }
}
