package com.pokerassistant.error;

import java.util.List;

/** An event that the state machine does not accept in its current state. */
public final class IllegalTransitionException extends PokerAssistantException {

    private static final long serialVersionUID = 1L;

    private final String state;
    private final String event;
    private final transient List<String> acceptedEvents;

    public IllegalTransitionException(String state, String event, List<String> acceptedEvents) {
        super("Event %s is not accepted in state %s (accepted: %s)".formatted(
                event, state, acceptedEvents.isEmpty() ? "none, the machine is finished" : String.join(", ", acceptedEvents)));
        this.state = state;
        this.event = event;
        this.acceptedEvents = List.copyOf(acceptedEvents);
    }

    public String state() {
        return state;
    }

    public String event() {
        return event;
    }

    public List<String> acceptedEvents() {
        return acceptedEvents;
    }
}
