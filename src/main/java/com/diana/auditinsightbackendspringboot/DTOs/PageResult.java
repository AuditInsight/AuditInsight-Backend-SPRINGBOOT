package com.diana.auditinsightbackendspringboot.DTOs;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Generic paginated list envelope for admin list endpoints. {@code page} is 1-based. */
@Getter
@Setter
@AllArgsConstructor
public class PageResult<T> {
    private List<T> items;
    private long totalItems;
    private int page;
    private int pageSize;
    private int totalPages;

    public static <T> PageResult<T> of(List<T> items, long totalItems, int page, int pageSize) {
        int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) totalItems / pageSize);
        return new PageResult<>(items, totalItems, page, pageSize, totalPages);
    }
}
