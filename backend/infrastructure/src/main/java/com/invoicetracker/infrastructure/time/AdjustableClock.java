package com.invoicetracker.infrastructure.time;

import com.invoicetracker.application.port.ClockControl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The business clock: system time plus an offset. The offset is always zero unless the dev tools
 * move it forward to demonstrate overdue behaviour. Thread-safe.
 */
public class AdjustableClock extends Clock implements ClockControl {

    private final Clock base;
    private final AtomicReference<Duration> offset;

    public AdjustableClock(Clock base) {
        this(base, new AtomicReference<>(Duration.ZERO));
    }

    private AdjustableClock(Clock base, AtomicReference<Duration> offset) {
        this.base = base;
        this.offset = offset;
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset.get());
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        // Shares the offset so a zone-adjusted view still moves with the original.
        return new AdjustableClock(base.withZone(zone), offset);
    }

    @Override
    public Duration offset() {
        return offset.get();
    }

    @Override
    public void advance(Duration amount) {
        if (amount.isNegative()) {
            throw new IllegalArgumentException("The clock can only move forward");
        }
        offset.updateAndGet(current -> current.plus(amount));
    }

    @Override
    public void reset() {
        offset.set(Duration.ZERO);
    }
}
