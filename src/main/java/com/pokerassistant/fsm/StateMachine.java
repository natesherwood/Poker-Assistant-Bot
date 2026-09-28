package com.pokerassistant.fsm;

import com.pokerassistant.error.IllegalTransitionException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A running instance of a {@link StateMachineDefinition}: the current state plus the context the
 * transition actions work on. Not thread-safe; drive it from one thread.
 *
 * <p>Actions run <em>before</em> the state changes, so an action that throws leaves the machine in
 * its previous state. Actions should validate before they mutate the context; callers that need
 * full atomicity (see {@code HandSession}) rebuild the context by replaying their event log.
 */
public final class StateMachine<S extends Enum<S>, E, C> {

    /** A well-formed machine settles in a handful of completion steps; more means a guard cycle. */
    private static final int MAX_COMPLETION_CHAIN = 64;

    private final StateMachineDefinition<S, E, C> definition;
    private final C context;
    private S state;

    StateMachine(StateMachineDefinition<S, E, C> definition, C context) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.context = Objects.requireNonNull(context, "context");
        this.state = definition.initialState();
    }

    public S state() {
        return state;
    }

    public C context() {
        return context;
    }

    public boolean isFinished() {
        return definition.isFinal(state);
    }

    /**
     * Dispatches one event: runs the matching transition's action, moves to its target, then follows
     * completion transitions until none applies.
     *
     * @return every state change taken, in order (empty for an internal transition that settled)
     * @throws IllegalTransitionException if the current state does not accept this event
     */
    public List<StateChange<S>> fire(E event) {
        Objects.requireNonNull(event, "event");
        StateMachineDefinition.Transition<S, E, C> transition = definition.transition(state, event)
                .orElseThrow(() -> new IllegalTransitionException(
                        state.name(), event.getClass().getSimpleName(), definition.acceptedEvents(state)));
        transition.action().accept(context, event);
        List<StateChange<S>> changes = new ArrayList<>();
        moveTo(transition.target(), event.getClass().getSimpleName(), changes);
        settle(changes);
        return List.copyOf(changes);
    }

    private void settle(List<StateChange<S>> changes) {
        for (int step = 0; step < MAX_COMPLETION_CHAIN; step++) {
            StateMachineDefinition.Completion<S, C> next = null;
            for (StateMachineDefinition.Completion<S, C> completion : definition.completions(state)) {
                if (completion.guard().test(context)) {
                    next = completion;
                    break;
                }
            }
            if (next == null) {
                return;
            }
            next.action().accept(context);
            moveTo(next.target(), next.reason(), changes);
        }
        throw new IllegalStateException("Completion transitions did not settle within " + MAX_COMPLETION_CHAIN + " steps (state " + state + ")");
    }

    private void moveTo(S target, String cause, List<StateChange<S>> changes) {
        if (target != state) {
            changes.add(new StateChange<>(state, target, cause));
            state = target;
        }
    }
}
