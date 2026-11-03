package com.invoicetracker.application.support;

import com.invoicetracker.application.port.JobLock;
import java.util.Optional;
import java.util.function.Supplier;

/** Runs work immediately, or refuses when {@link #held} is set to simulate another instance. */
public final class FakeJobLock implements JobLock {

    public boolean held;

    @Override
    public <T> Optional<T> tryRun(String jobName, Supplier<T> work) {
        return held ? Optional.empty() : Optional.of(work.get());
    }
}
