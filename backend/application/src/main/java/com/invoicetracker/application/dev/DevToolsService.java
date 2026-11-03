package com.invoicetracker.application.dev;

import com.invoicetracker.application.notification.OverdueProcessor;
import com.invoicetracker.application.notification.OverdueRunResult;
import com.invoicetracker.application.port.ClockControl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Demo helpers: shift the application clock and trigger the overdue job on demand. Not a Spring
 * bean by default; the API layer registers it only when dev tools are enabled.
 */
public class DevToolsService {

    public static final int MAX_ADVANCE_DAYS = 365;

    private final ClockControl clockControl;
    private final Clock clock;
    private final OverdueProcessor overdueProcessor;

    public DevToolsService(ClockControl clockControl, Clock clock, OverdueProcessor overdueProcessor) {
        this.clockControl = clockControl;
        this.clock = clock;
        this.overdueProcessor = overdueProcessor;
    }

    public ClockStatus status() {
        Duration offset = clockControl.offset();
        return new ClockStatus(clock.instant(), LocalDate.now(clock), offset.toDays(), offset.toString());
    }

    public ClockStatus advanceDays(int days) {
        if (days < 1 || days > MAX_ADVANCE_DAYS) {
            throw new IllegalArgumentException("days must be between 1 and " + MAX_ADVANCE_DAYS);
        }
        clockControl.advance(Duration.ofDays(days));
        return status();
    }

    public ClockStatus reset() {
        clockControl.reset();
        return status();
    }

    public OverdueRunResult runOverdueCheck() {
        return overdueProcessor.run();
    }

    public record ClockStatus(Instant now, LocalDate today, long offsetDays, String offset) {}
}
