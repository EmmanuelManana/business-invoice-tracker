package com.invoicetracker.infrastructure.scheduling;

import com.invoicetracker.application.notification.OverdueProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Hosted background job: runs the overdue processor on a fixed delay (default every minute). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.overdue", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
class OverdueScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueScheduler.class);

    private final OverdueProcessor processor;

    OverdueScheduler(OverdueProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(
            initialDelayString = "${app.overdue.initial-delay:PT15S}",
            fixedDelayString = "${app.overdue.interval:PT1M}")
    void run() {
        MDC.put("correlationId", "job-overdue-" + System.currentTimeMillis());
        try {
            processor.run();
        } catch (RuntimeException e) {
            // Never let an exception kill the scheduler thread; the next run will retry.
            log.error("Scheduled overdue run failed", e);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
