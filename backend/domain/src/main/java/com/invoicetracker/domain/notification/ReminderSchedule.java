package com.invoicetracker.domain.notification;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Reminder escalation policy, e.g. offsets {@code [-3, 0, 1, 7, 14]}.
 *
 * <p>For a given day only the <em>latest</em> stage that has been reached is due. If the job was
 * down for a week, the customer gets one "7 days overdue" reminder rather than a burst of every
 * missed stage. Combined with a uniqueness check per (invoice, stage, channel) this makes reminder
 * generation idempotent: running the job any number of times on the same day yields the same
 * notifications.
 */
public final class ReminderSchedule {

    private final List<ReminderStage> stages;

    public ReminderSchedule(Collection<Integer> offsets) {
        if (offsets == null || offsets.isEmpty()) {
            throw new IllegalArgumentException("Reminder schedule needs at least one offset");
        }
        this.stages =
                offsets.stream().distinct().sorted().map(ReminderStage::new).toList();
    }

    public List<ReminderStage> stages() {
        return stages;
    }

    /** The most recent stage reached on {@code today}, if any. */
    public Optional<ReminderStage> latestDueStage(LocalDate dueDate, LocalDate today) {
        ReminderStage latest = null;
        for (ReminderStage stage : stages) {
            if (!dueDate.plusDays(stage.offsetDays()).isAfter(today)) {
                latest = stage;
            }
        }
        return Optional.ofNullable(latest);
    }

    /** How many days before the due date the first reminder goes out (0 if none are early). */
    public int leadDays() {
        return Math.max(0, -stages.getFirst().offsetDays());
    }
}
