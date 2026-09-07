package com.spring.app;

import com.spring.app.domain.book.Book;
import com.spring.app.domain.book.BookRepository;
import com.spring.app.domain.token.RefreshTokenRepository;
import com.spring.app.domain.user.User;
import com.spring.app.domain.user.UserRepository;
import com.spring.app.enums.Role;
import com.spring.app.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The authorization matrix, endpoint by endpoint and role by role.
 *
 * <p>Tokens are minted directly rather than obtained by logging in: this suite is about what a role
 * may do, and going through login would make every case depend on the login flow as well (which
 * {@link AuthFlowIntegrationTest} covers).
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookAuthorizationIntegrationTest {

    private static final String BOOK_JSON = """
            {"title":"Domain-Driven Design","author":"Eric Evans","isbn":"978-0321125217"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private String userToken;
    private String managerToken;
    private String adminToken;
    private Long bookId;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        bookRepository.deleteAll();

        userToken = tokenFor("user@example.com", Role.USER);
        managerToken = tokenFor("manager@example.com", Role.MANAGER);
        adminToken = tokenFor("admin@example.com", Role.ADMIN);

        bookId = bookRepository.save(
                Book.builder().title("Refactoring").author("Martin Fowler").isbn("978-0134757599").build()
        ).getId();
    }

    // ========================= GET: public =========================

    @Test
    @DisplayName("GET /api/v1/books works with no token")
    void listIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("Refactoring"));
    }

    @Test
    @DisplayName("GET /api/v1/books/{id} works with no token")
    void getOneIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/books/" + bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author").value("Martin Fowler"));
    }

    // ========================= POST: MANAGER or ADMIN =========================

    @Test
    @DisplayName("POST /api/v1/books without a token is 401 JSON, not HTML")
    void createWithoutTokenIsJsonUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON).content(BOOK_JSON))
                .andExpect(status().isUnauthorized())
                // The point of the custom entry point: a JSON body, not a Whitelabel error page.
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("A001"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/v1/books"));
    }

    @Test
    @DisplayName("POST /api/v1/books as USER is 403 with code A002")
    void createAsUserIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/books")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOK_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    @DisplayName("POST /api/v1/books as MANAGER creates the book")
    void createAsManagerSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/books")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOK_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Domain-Driven Design"));
    }

    @Test
    @DisplayName("POST /api/v1/books as ADMIN creates the book")
    void createAsAdminSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/books")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOK_JSON))
                .andExpect(status().isCreated());
    }

    // ========================= PUT: MANAGER or ADMIN =========================

    @Test
    @DisplayName("PUT /api/v1/books/{id} as USER is 403; as MANAGER it succeeds")
    void updateRequiresCuratorRole() throws Exception {
        mockMvc.perform(put("/api/v1/books/" + bookId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOK_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));

        mockMvc.perform(put("/api/v1/books/" + bookId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOK_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Domain-Driven Design"));
    }

    // ========================= DELETE: ADMIN only =========================

    @Test
    @DisplayName("DELETE as USER is 403 with code A002")
    void deleteAsUserIsForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/books/" + bookId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    @DisplayName("DELETE as MANAGER is 403 with code A002, answered by the advice")
    void deleteAsManagerIsForbidden() throws Exception {
        // This denial comes from @PreAuthorize inside the dispatch, i.e. the
        // @RestControllerAdvice path - and must still be JSON with the same code.
        mockMvc.perform(delete("/api/v1/books/" + bookId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("A002"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("DELETE as ADMIN returns 204 with no body")
    void deleteAsAdminSucceeds() throws Exception {
        mockMvc.perform(delete("/api/v1/books/" + bookId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("DELETE without a token is 401, not 403")
    void deleteWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/books/" + bookId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
    }

    // ========================= admin area =========================

    @Test
    @DisplayName("/api/v1/admin/** is ADMIN-only")
    void adminAreaIsAdminOnly() throws Exception {
        // No controller is mapped there yet; what matters is that authorization runs first and
        // refuses a MANAGER before any handler lookup happens.
        mockMvc.perform(get("/api/v1/admin/anything")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    private String tokenFor(String email, Role role) {
        User user = userRepository.save(User.builder()
                .email(email)
                .username(email.substring(0, email.indexOf('@')))
                .password(passwordEncoder.encode("Str0ngPassw0rd"))
                .role(role)
                .enabled(true)
                .locked(false)
                .failedLoginCount(0)
                .build());
        return tokenProvider.generateAccessToken(user);
    }
}
