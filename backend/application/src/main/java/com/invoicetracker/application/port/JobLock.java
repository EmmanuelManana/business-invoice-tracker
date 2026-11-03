package com.invoicetracker.application.port;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cluster-wide mutual exclusion for background jobs, so that with several API instances (or the
 * scheduler and a manual trigger at the same moment) only one run of a job executes at a time.
 */
public interface JobLock {

    /** Runs {@code work} if the lock is free; returns empty without running it otherwise. */
    <T> Optional<T> tryRun(String jobName, Supplier<T> work);
}
