package com.pokerassistant.fsm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Immutable transition table of a finite-state machine, built once and shared by every running
 * {@link StateMachine} instance.
 *
 * <p>Two kinds of transitions, in UML terms:
 * <ul>
 *   <li><b>Event transitions</b> {@code source --Event / action--> target}: looked up by the event's
 *       exact runtime class, so at most one can match and dispatch is deterministic.</li>
 *   <li><b>Completion transitions</b> {@code source --[guard] / action--> target}: fired automatically,
 *       with no event, whenever their guard holds after a step. They are tried in declaration order.</li>
 * </ul>
 * The builder rejects duplicate event transitions, completion self-loops (they would never settle),
 * outgoing transitions from final states, and states unreachable from the initial state.
 *
 * @param <S> state enum
 * @param <E> event base type (typically a sealed interface of records)
 * @param <C> mutable context the actions operate on
 */
public final class StateMachineDefinition<S extends Enum<S>, E, C> {

    record Transition<S, E, C>(S source, Class<?> eventType, S target, BiConsumer<C, E> action) {
    }

    record Completion<S, C>(S source, S target, String reason, Predicate<C> guard, Consumer<C> action) {
    }

    private final Class<S> stateType;
    private final S initialState;
    private final Set<S> finalStates;
    private final Map<S, Map<Class<?>, Transition<S, E, C>>> transitions;
    private final Map<S, List<Completion<S, C>>> completions;

    private StateMachineDefinition(Builder<S, E, C> builder) {
        this.stateType = builder.stateType;
        this.initialState = builder.initialState;
        this.finalStates = Collections.unmodifiableSet(EnumSet.copyOf(builder.finalStates));
        Map<S, Map<Class<?>, Transition<S, E, C>>> byState = new EnumMap<>(stateType);
        builder.transitions.forEach((state, byType) -> byState.put(state, Collections.unmodifiableMap(new LinkedHashMap<>(byType))));
        this.transitions = Collections.unmodifiableMap(byState);
        Map<S, List<Completion<S, C>>> completionsByState = new EnumMap<>(stateType);
        builder.completions.forEach((state, list) -> completionsByState.put(state, List.copyOf(list)));
        this.completions = Collections.unmodifiableMap(completionsByState);
    }

    public static <S extends Enum<S>, E, C> Builder<S, E, C> builder(Class<S> stateType, S initialState) {
        return new Builder<>(stateType, initialState);
    }

    /** A new machine in the initial state, operating on {@code context}. */
    public StateMachine<S, E, C> start(C context) {
        return new StateMachine<>(this, context);
    }

    public S initialState() {
        return initialState;
    }

    public boolean isFinal(S state) {
        return finalStates.contains(state);
    }

    /** Simple names of the event types {@code state} accepts, e.g. {@code [BoardDealt, CardsShown]}. */
    public List<String> acceptedEvents(S state) {
        return transitions.getOrDefault(state, Map.of()).keySet().stream().map(Class::getSimpleName).sorted().toList();
    }

    Optional<Transition<S, E, C>> transition(S state, E event) {
        return Optional.ofNullable(transitions.getOrDefault(state, Map.of()).get(event.getClass()));
    }

    List<Completion<S, C>> completions(S state) {
        return completions.getOrDefault(state, List.of());
    }

    /** The transition table as text, one transition per line. */
    public String describe() {
        StringBuilder text = new StringBuilder();
        for (S state : stateType.getEnumConstants()) {
            for (Transition<S, E, C> t : transitions.getOrDefault(state, Map.of()).values()) {
                String arrow = t.target() == state ? "(internal)" : "--> " + t.target();
                text.append("%-15s on %-16s %s%n".formatted(state, t.eventType().getSimpleName(), arrow));
            }
            for (Completion<S, C> c : completions(state)) {
                text.append("%-15s when %-30s --> %s%n".formatted(state, "[" + c.reason() + "]", c.target()));
            }
            if (finalStates.contains(state)) {
                text.append("%-15s (final)%n".formatted(state));
            }
        }
        return text.toString();
    }

    /** Fluent builder; see the class comment for the validation performed by {@link #build()}. */
    public static final class Builder<S extends Enum<S>, E, C> {

        private final Class<S> stateType;
        private final S initialState;
        private final Set<S> finalStates;
        private final Map<S, Map<Class<?>, Transition<S, E, C>>> transitions;
        private final Map<S, List<Completion<S, C>>> completions;

        private Builder(Class<S> stateType, S initialState) {
            this.stateType = Objects.requireNonNull(stateType, "stateType");
            this.initialState = Objects.requireNonNull(initialState, "initialState");
            this.finalStates = EnumSet.noneOf(stateType);
            this.transitions = new EnumMap<>(stateType);
            this.completions = new EnumMap<>(stateType);
        }

        /** {@code source --eventType / action--> target}. */
        public <T extends E> Builder<S, E, C> on(S source, Class<T> eventType, S target, BiConsumer<? super C, ? super T> action) {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(action, "action");
            BiConsumer<C, E> typed = (context, event) -> action.accept(context, eventType.cast(event));
            Transition<S, E, C> previous = transitions.computeIfAbsent(source, s -> new LinkedHashMap<>())
                    .putIfAbsent(eventType, new Transition<>(source, eventType, target, typed));
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate transition for " + eventType.getSimpleName() + " in state " + source);
            }
            return this;
        }

        /** An internal transition (runs the action, keeps the state) in each of {@code sources}. */
        public <T extends E> Builder<S, E, C> internal(Set<S> sources, Class<T> eventType, BiConsumer<? super C, ? super T> action) {
            for (S source : sources) {
                on(source, eventType, source, action);
            }
            return this;
        }

        /** {@code source --[guard] / action--> target}, taken automatically once the guard holds. */
        public Builder<S, E, C> complete(S source, S target, String reason, Predicate<? super C> guard, Consumer<? super C> action) {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(guard, "guard");
            Objects.requireNonNull(action, "action");
            if (source == target) {
                throw new IllegalArgumentException("A completion transition must change state, otherwise it never settles: " + source);
            }
            completions.computeIfAbsent(source, s -> new ArrayList<>())
                    .add(new Completion<>(source, target, reason, guard::test, action::accept));
            return this;
        }

        public Builder<S, E, C> finalStates(Set<S> states) {
            finalStates.addAll(states);
            return this;
        }

        public StateMachineDefinition<S, E, C> build() {
            for (S state : finalStates) {
                if (transitions.containsKey(state) || completions.containsKey(state)) {
                    throw new IllegalStateException("Final state " + state + " must not have outgoing transitions");
                }
            }
            Set<S> unreachable = EnumSet.allOf(stateType);
            unreachable.removeAll(reachableStates());
            if (!unreachable.isEmpty()) {
                throw new IllegalStateException("States unreachable from " + initialState + ": " + unreachable);
            }
            return new StateMachineDefinition<>(this);
        }

        private Set<S> reachableStates() {
            Set<S> seen = EnumSet.of(initialState);
            Deque<S> pending = new ArrayDeque<>(seen);
            while (!pending.isEmpty()) {
                S state = pending.pop();
                List<S> targets = new ArrayList<>();
                transitions.getOrDefault(state, Map.of()).values().forEach(t -> targets.add(t.target()));
                completions.getOrDefault(state, List.of()).forEach(c -> targets.add(c.target()));
                for (S target : targets) {
                    if (seen.add(target)) {
                        pending.push(target);
                    }
                }
            }
            return seen;
        }
    }
}
