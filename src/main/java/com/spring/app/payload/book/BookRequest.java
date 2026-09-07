package com.spring.app.payload.book;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create or update a book")
public class BookRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    @Schema(example = "The Pragmatic Programmer")
    private String title;

    @NotBlank(message = "Author is required")
    @Size(max = 120, message = "Author must not exceed 120 characters")
    @Schema(example = "Hunt & Thomas")
    private String author;

    @Size(max = 20, message = "ISBN must not exceed 20 characters")
    @Schema(example = "978-0135957059")
    private String isbn;
}
