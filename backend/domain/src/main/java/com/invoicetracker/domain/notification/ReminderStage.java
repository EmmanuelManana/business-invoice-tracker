package com.invoicetracker.domain.notification;

/**
 * A point in the reminder escalation schedule, expressed as days relative to the due date:
 * negative = before due, 0 = on the due date, positive = days overdue.
 */
public record ReminderStage(int offsetDays) implements Comparable<ReminderStage> {

    /** Stage code stored on notifications, e.g. {@code D-3}, {@code D0}, {@code D+7}. */
    public String code() {
        if (offsetDays == 0) {
            return "D0";
        }
        return offsetDays < 0 ? "D" + offsetDays : "D+" + offsetDays;
    }

    /** Human-readable label, e.g. "3 days before due", "Due today", "7 days overdue". */
    public String label() {
        if (offsetDays == 0) {
            return "Due today";
        }
        int days = Math.abs(offsetDays);
        String unit = days == 1 ? "day" : "days";
        return offsetDays < 0 ? "%d %s before due".formatted(days, unit) : "%d %s overdue".formatted(days, unit);
    }

    public boolean isBeforeDue() {
        return offsetDays < 0;
    }

    public boolean isDueDay() {
        return offsetDays == 0;
    }

    public boolean isOverdue() {
        return offsetDays > 0;
    }

    @Override
    public int compareTo(ReminderStage other) {
        return Integer.compare(offsetDays, other.offsetDays);
    }
}
