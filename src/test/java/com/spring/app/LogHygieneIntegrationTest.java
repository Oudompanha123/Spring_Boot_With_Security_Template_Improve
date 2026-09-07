package com.spring.app;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import com.spring.app.domain.token.RefreshTokenRepository;
import com.spring.app.domain.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Proves the "nothing secret in the logs" rule instead of asserting it in a README.
 *
 * <p>Captures everything this application logs at TRACE while a full auth flow runs, then fails if
 * any line contains the password, either token or a BCrypt hash. Scope is deliberately
 * {@code com.spring.app}: it covers the code in this repository, which is what this repository can
 * promise. The one framework logger that would break the rule is Hibernate's
 * {@code org.hibernate.orm.jdbc.bind}, which prints every bound parameter (including the hash on
 * insert) at TRACE and is therefore pinned no lower than INFO in every profile.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LogHygieneIntegrationTest {

    private static final String EMAIL = "loghygiene@example.com";
    private static final String PASSWORD = "Str0ngPassw0rd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private Logger applicationLogger;
    private ListAppender<ILoggingEvent> capturedLogs;
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        applicationLogger = (Logger) LoggerFactory.getLogger("com.spring.app");
        originalLevel = applicationLogger.getLevel();
        applicationLogger.setLevel(Level.TRACE);

        capturedLogs = new ListAppender<>();
        capturedLogs.start();
        applicationLogger.addAppender(capturedLogs);
    }

    @AfterEach
    void tearDown() {
        applicationLogger.detachAppender(capturedLogs);
        capturedLogs.stop();
        applicationLogger.setLevel(originalLevel);
    }

    @Test
    @DisplayName("no password, hash or token is written to the log during a full auth flow")
    void authFlowLeaksNothing() throws Exception {
        // signup
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"username\":\"loghygiene\",\"password\":\"" + PASSWORD + "\"}"));

        // login
        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();

        String accessToken = JsonPath.read(loginBody, "$.data.accessToken");
        String refreshToken = JsonPath.read(loginBody, "$.data.refreshToken");
        String storedHash = userRepository.findByEmail(EMAIL).orElseThrow().getPassword();

        // authenticated call, refresh, logout
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken));
        String refreshBody = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + accessToken));

        // failure paths log the most, so exercise them too
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"WrongPassw0rd\"}"));
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken + "tampered"));
        mockMvc.perform(post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"author\":\"y\"}"));

        String rotatedRefresh = JsonPath.read(refreshBody, "$.data.refreshToken");
        List<String> lines = capturedLogs.list.stream().map(this::renderLine).toList();

        assertThat(lines).isNotEmpty();
        assertThat(lines).noneMatch(line -> line.contains(PASSWORD));
        assertThat(lines).noneMatch(line -> line.contains("WrongPassw0rd"));
        assertThat(lines).noneMatch(line -> line.contains(storedHash));
        assertThat(lines).noneMatch(line -> line.contains("$2a$") || line.contains("$2b$"));
        assertThat(lines).noneMatch(line -> line.contains(accessToken));
        assertThat(lines).noneMatch(line -> line.contains(refreshToken));
        assertThat(lines).noneMatch(line -> line.contains(rotatedRefresh));
    }

    /** Message plus any exception text: a leak in a stack trace is still a leak. */
    private String renderLine(ILoggingEvent event) {
        StringBuilder line = new StringBuilder(event.getFormattedMessage());
        var throwable = event.getThrowableProxy();
        while (throwable != null) {
            line.append(' ').append(throwable.getClassName()).append(' ').append(throwable.getMessage());
            throwable = throwable.getCause();
        }
        return line.toString();
    }
}
