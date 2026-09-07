package com.spring.app.config;

import com.spring.app.domain.book.Book;
import com.spring.app.domain.book.BookRepository;
import com.spring.app.domain.user.User;
import com.spring.app.domain.user.UserRepository;
import com.spring.app.enums.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Creates the accounts needed to exercise the authorization matrix locally.
 *
 * <p>Signup can only ever produce a {@code USER}, which is the point of that rule — so a MANAGER
 * and an ADMIN have to arrive some other way. In a real deployment that is a migration or an admin
 * console; here it is this seeder, switched off unless {@code app.seed.enabled=true} so it can
 * never run in production by accident.
 *
 * <p>Seed passwords come from configuration and are never logged. The defaults are development
 * conveniences and are documented as such in the README: an environment that matters must set
 * {@code app.seed.admin-password} and {@code app.seed.manager-password} explicitly, or leave the
 * seeder off entirely.
 */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final PasswordEncoder passwordEncoder;
    private final SeedProperties seedProperties;

    @Override
    @Transactional
    public void run(String... args) {
        seedUser(seedProperties.getAdminEmail(), "admin", Role.ADMIN, seedProperties.getAdminPassword());
        seedUser(seedProperties.getManagerEmail(), "manager", Role.MANAGER, seedProperties.getManagerPassword());
        seedUser(seedProperties.getUserEmail(), "user", Role.USER, seedProperties.getUserPassword());

        if (bookRepository.count() == 0) {
            bookRepository.saveAll(List.of(
                    Book.builder().title("The Pragmatic Programmer").author("Hunt & Thomas").isbn("978-0135957059").build(),
                    Book.builder().title("Release It!").author("Michael T. Nygard").isbn("978-1680502398").build()
            ));
            log.info("Seeded {} books", bookRepository.count());
        }
    }

    private void seedUser(String email, String username, Role role, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        User user = userRepository.save(User.builder()
                .email(email)
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .enabled(true)
                .locked(false)
                .failedLoginCount(0)
                .build());

        // Role and id only. The password is not logged, not even at debug.
        log.info("Seeded account (userId={}, role={})", user.getId(), role);
    }
}
