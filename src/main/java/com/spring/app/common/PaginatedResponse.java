package com.spring.app.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedResponse<T> {
    private List<T> content;
    private PageInfo pageInfo;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageInfo {
        private int page;           // Current page (0-based)
        private int size;           // Page size
        private long totalElements; // Total number of elements
        private int totalPages;     // Total number of pages
        private boolean first;      // Is first page
        private boolean last;       // Is last page
        private boolean hasNext;    // Has next page
        private boolean hasPrevious; // Has previous page
    }
    
    public static <T> PaginatedResponse<T> from(Page<T> page) {
        PageInfo pageInfo = new PageInfo(
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast(),
            page.hasNext(),
            page.hasPrevious()
        );
        
        return new PaginatedResponse<>(page.getContent(), pageInfo);
    }
}