package com.invoicetracker.api.web;

/** Converts between entity versions and HTTP entity tags ({@code "3"}). */
public final class Etags {

    private Etags() {}

    public static String of(long version) {
        return "\"" + version + "\"";
    }

    /** Parses an If-Match value such as {@code "3"} or {@code W/"3"}. */
    public static long parse(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException("The If-Match header is required for updates");
        }
        String value = ifMatch.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        value = value.replace("\"", "");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("If-Match must contain the ETag returned by the server");
        }
    }
}
