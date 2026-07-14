package com.spring.app.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pagination {
    private boolean last;
    private boolean first;
    private int size;
    private int currentPage;
    private int totalPages;
    private long totalElements;

    public Pagination(Page<?> page) {
        this.last = page.isLast();
        this.first = page.isFirst();
        this.size = page.getSize();
        this.currentPage = page.getNumber();
        this.totalPages = page.getTotalPages();
        this.totalElements = page.getTotalElements();

    }
}