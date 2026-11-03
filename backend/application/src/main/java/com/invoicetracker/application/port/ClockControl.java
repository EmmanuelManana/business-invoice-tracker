package com.invoicetracker.application.port;

import java.time.Duration;

/**
 * Lets the dev tools shift the application clock so overdue behaviour can be demonstrated without
 * waiting. Only wired up when dev tools are enabled.
 */
public interface ClockControl {

    Duration offset();

    void advance(Duration amount);

    void reset();
}
