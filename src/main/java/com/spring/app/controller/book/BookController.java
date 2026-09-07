package com.spring.app.controller.book;

import com.spring.app.common.AbstractRestController;
import com.spring.app.exception.ErrorResponse;
import com.spring.app.payload.book.BookRequest;
import com.spring.app.payload.book.BookResponse;
import com.spring.app.service.book.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The book catalogue, and the worked example of the authorization matrix.
 *
 * <pre>
 *   GET    /api/v1/books        public
 *   POST   /api/v1/books        MANAGER, ADMIN   (denied by the filter chain    -> AccessDeniedHandler)
 *   PUT    /api/v1/books/{id}   MANAGER, ADMIN   (denied by the filter chain    -> AccessDeniedHandler)
 *   DELETE /api/v1/books/{id}   ADMIN only       (denied by method security     -> @RestControllerAdvice)
 * </pre>
 *
 * The two write paths are guarded by two different mechanisms on purpose, so both JSON error paths
 * are exercised by a real endpoint. Both return 403 with code {@code A002}.
 */
@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
@Tag(name = "Books", description = "Public reads; role-restricted writes")
public class BookController extends AbstractRestController {

    private final BookService bookService;

    @Operation(summary = "List books", description = "Public: no token required.")
    @GetMapping
    public ResponseEntity<?> list() {
        List<BookResponse> books = bookService.findAll();
        return ok(books);
    }

    @Operation(summary = "Get one book", description = "Public: no token required.")
    @ApiResponse(responseCode = "404", description = "B001 book not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return ok(bookService.findById(id));
    }

    @Operation(summary = "Create a book", description = "Requires MANAGER or ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Book created"),
            @ApiResponse(responseCode = "401", description = "A001/A004/A005 missing or bad token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "A002 role not permitted",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody BookRequest request) {
        return created(bookService.create(request));
    }

    @Operation(summary = "Update a book", description = "Requires MANAGER or ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book updated"),
            @ApiResponse(responseCode = "403", description = "A002 role not permitted",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "B001 book not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return ok(bookService.update(id, request));
    }

    /**
     * ADMIN only, enforced here rather than in {@code SecurityConfig}.
     *
     * <p>The filter chain only requires a token for this route; this annotation makes the role
     * decision. A MANAGER therefore fails inside the dispatch with {@code AuthorizationDeniedException}
     * and is answered by {@code GlobalApiExceptionHandler} — the path that produces an HTML error
     * page in APIs that register only an {@code AccessDeniedHandler}.
     */
    @Operation(summary = "Delete a book", description = "Requires ADMIN. Returns 204 with no body.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Book deleted"),
            @ApiResponse(responseCode = "403", description = "A002 not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "B001 book not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bookService.delete(id);
        return noContent();
    }
}
