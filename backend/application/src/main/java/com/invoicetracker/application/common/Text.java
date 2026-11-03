package com.invoicetracker.application.common;

import java.util.regex.Pattern;

/** Input sanitisation helpers applied to free-text fields before they are stored. */
public final class Text {

    // Control characters other than tab, newline and carriage return.
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\t\\n\\r]]");

    private Text() {}

    /** Trims, strips control characters and converts blank strings to null. */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = CONTROL_CHARS.matcher(value).replaceAll("").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
