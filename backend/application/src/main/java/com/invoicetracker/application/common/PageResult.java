package com.invoicetracker.application.common;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResult<T> of(List<T> items, int page, int size, long totalItems) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
        return new PageResult<>(items, page, size, totalItems, totalPages);
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), page, size, totalItems, totalPages);
    }
}
