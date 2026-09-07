package com.spring.app.enums;

/**
 * Application roles, ordered from least to most privileged.
 *
 * <p>The enum name is what lands in the database and in the JWT {@code role} claim, so the
 * constants here are also the contract with clients. Spring Security expects role authorities to
 * carry the {@code ROLE_} prefix, which {@link #authority()} adds — the prefix is deliberately not
 * part of the enum name so the stored value stays a clean {@code USER}/{@code MANAGER}/{@code ADMIN}.
 */
public enum Role {

    USER,
    MANAGER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
