package com.invoicetracker.application.common;

/** Page number (zero-based) and size, clamped to safe bounds. */
public record Paging(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static Paging of(Integer page, Integer size) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new Paging(p, s);
    }
}
