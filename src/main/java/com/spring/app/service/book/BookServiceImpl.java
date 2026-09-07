package com.spring.app.service.book;

import com.spring.app.domain.book.Book;
import com.spring.app.domain.book.BookRepository;
import com.spring.app.exception.ErrorCode;
import com.spring.app.payload.book.BookRequest;
import com.spring.app.payload.book.BookResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;

    @Override
    @Transactional(readOnly = true)
    public List<BookResponse> findAll() {
        return bookRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(BookResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BookResponse findById(Long id) {
        return BookResponse.from(require(id));
    }

    @Override
    @Transactional
    public BookResponse create(BookRequest request) {
        Book book = bookRepository.save(Book.builder()
                .title(request.getTitle().trim())
                .author(request.getAuthor().trim())
                .isbn(request.getIsbn())
                .build());

        log.info("Book created (bookId={})", book.getId());
        return BookResponse.from(book);
    }

    @Override
    @Transactional
    public BookResponse update(Long id, BookRequest request) {
        Book book = require(id);
        book.setTitle(request.getTitle().trim());
        book.setAuthor(request.getAuthor().trim());
        book.setIsbn(request.getIsbn());

        log.info("Book updated (bookId={})", book.getId());
        return BookResponse.from(bookRepository.save(book));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        bookRepository.delete(require(id));
        log.info("Book deleted (bookId={})", id);
    }

    /**
     * Loads a book or fails with {@code B001}.
     *
     * <p>Every mutating path goes through this rather than {@code deleteById}/{@code existsById}:
     * a missing row must be a 404 with a code the client can branch on, not a silent no-op that
     * reports success for work that never happened.
     */
    private Book require(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> ErrorCode.BOOK_NOT_FOUND.exception(id));
    }
}
