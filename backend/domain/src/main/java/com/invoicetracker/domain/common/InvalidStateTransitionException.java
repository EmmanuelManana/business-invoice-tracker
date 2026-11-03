package com.invoicetracker.domain.common;

/** Thrown when an entity is asked to move to a state that its lifecycle does not allow. */
public class InvalidStateTransitionException extends DomainException {

    private final String from;
    private final String to;

    public InvalidStateTransitionException(String entity, Enum<?> from, Enum<?> to) {
        super(
                "invalid_state_transition",
                "%s cannot transition from %s to %s".formatted(entity, from.name(), to.name()));
        this.from = from.name();
        this.to = to.name();
    }

    public String from() {
        return from;
    }

    public String to() {
        return to;
    }
}
