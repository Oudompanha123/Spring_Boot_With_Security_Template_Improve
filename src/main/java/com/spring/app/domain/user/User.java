package com.spring.app.domain.user;

import com.spring.app.domain.BaseEntity;
import com.spring.app.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true),
        @Index(name = "idx_users_username", columnList = "username")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "password") // never let a stray toString() put the hash in a log line
public class User extends BaseEntity {

    /** How many consecutive failures lock the account. */
    public static final int MAX_FAILED_LOGINS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Login identifier. Unique because it is what {@code CustomUserDetailsService} loads by. */
    @Column(name = "email", unique = true, nullable = false, length = 180)
    private String email;

    /** BCrypt hash — never the raw password. */
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    /** Display name. Not the login identifier; see {@code CustomUserDetails#getUsername()}. */
    @Column(name = "username", nullable = false, length = 80)
    private String username;

    /*
     * Stored as STRING rather than ORDINAL on purpose.
     *
     * EnumType.ORDINAL persists the declaration index (0, 1, 2...). Inserting or reordering a
     * constant then silently re-points every existing row at a different role: add a value above
     * ADMIN and yesterday's ADMIN rows become MANAGER. For a column that decides authorization,
     * that is a privilege-escalation bug written into the schema, and nothing in the code fails
     * loudly when it happens.
     *
     * EnumType.STRING persists the name, so rows stay meaningful independently of declaration
     * order, the column is readable in ad-hoc SQL and audits, and a constant that is renamed or
     * deleted blows up at read time instead of quietly resolving to the wrong role.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "locked", nullable = false)
    @Builder.Default
    private boolean locked = false;

    @Column(name = "failed_login_count", nullable = false)
    @Builder.Default
    private int failedLoginCount = 0;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /**
     * Records one failed attempt and locks the account once the threshold is reached.
     *
     * @return {@code true} when this failure is the one that locked the account
     */
    public boolean registerFailedLogin() {
        this.failedLoginCount++;
        if (this.failedLoginCount >= MAX_FAILED_LOGINS) {
            this.locked = true;
        }
        return this.locked;
    }

    /** Clears the failure streak and stamps the successful login. */
    public void registerSuccessfulLogin() {
        this.failedLoginCount = 0;
        this.lastLoginAt = Instant.now();
    }
}
