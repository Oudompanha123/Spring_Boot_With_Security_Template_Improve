package com.spring.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SpringAppApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
    }

    @Test
    @DisplayName("the suite runs against in-memory H2, never a real database")
    void testsUseAnInMemoryDatabase() throws SQLException {
        // A guard against config leaking in from outside the classpath. Spring Boot reads
        // ./config/application.yml at higher precedence than src/test/resources/application.yml,
        // so an unscoped local override file silently repoints the whole suite at Postgres - which
        // then fails as "Unable to determine Dialect" in a dozen unrelated tests. Local overrides
        // therefore live in ./config/application-dev.yml, and this test says so out loud if that
        // ever stops being true.
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:");
        }
    }
}
