package com.spring.app.service.book;

import com.spring.app.payload.book.BookRequest;
import com.spring.app.payload.book.BookResponse;

import java.util.List;

/**
 * Book catalogue operations.
 *
 * <p>No authorization logic appears in this contract or its implementation. Who may call what is
 * declared once, in {@code SecurityConfig} and on the controller methods, so the rules can be read
 * as a policy in one place instead of being reconstructed from checks scattered through the service
 * layer.
 */
public interface BookService {

    List<BookResponse> findAll();

    /** @throws com.spring.app.exception.ApiException {@code B001} when no such book exists */
    BookResponse findById(Long id);

    BookResponse create(BookRequest request);

    /** @throws com.spring.app.exception.ApiException {@code B001} when no such book exists */
    BookResponse update(Long id, BookRequest request);

    /** @throws com.spring.app.exception.ApiException {@code B001} when no such book exists */
    void delete(Long id);
}
