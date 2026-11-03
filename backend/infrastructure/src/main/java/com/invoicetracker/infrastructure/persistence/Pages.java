package com.invoicetracker.infrastructure.persistence;

import com.invoicetracker.application.common.PageResult;
import org.springframework.data.domain.Page;

final class Pages {

    private Pages() {}

    static <T> PageResult<T> toResult(Page<T> page) {
        return new PageResult<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Escapes LIKE wildcards in user input and wraps it for a case-insensitive contains match. */
    static String containsPattern(String search) {
        String escaped =
                search.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
