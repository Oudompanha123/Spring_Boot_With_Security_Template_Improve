package com.spring.app.payload.book;

import com.spring.app.domain.book.Book;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "A book in the catalogue")
public class BookResponse {

    private Long id;
    private String title;
    private String author;
    private String isbn;
    private Instant createdAt;

    public static BookResponse from(Book book) {
        return BookResponse.builder()
                .id(book.getId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .isbn(book.getIsbn())
                .createdAt(book.getCreatedAt())
                .build();
    }
}
