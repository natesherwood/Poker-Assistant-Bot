package com.pokerassistant.fsm;

import com.pokerassistant.error.IllegalTransitionException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateMachineTest {

    enum S { A, B, C, D }

    sealed interface Ev permits Go, Poke {
    }

    record Go() implements Ev {
    }

    record Poke() implements Ev {
    }

    private static StateMachineDefinition.Builder<S, Ev, List<String>> builder() {
        return StateMachineDefinition.builder(S.class, S.A);
    }

    @Test
    void firesEventThenFollowsCompletionTransitions() {
        StateMachineDefinition<S, Ev, List<String>> definition = builder()
                .on(S.A, Go.class, S.B, (trace, event) -> trace.add("go"))
                .internal(EnumSet.of(S.B, S.C), Poke.class, (trace, event) -> trace.add("poke"))
                .complete(S.B, S.C, "b is done", trace -> trace.contains("poke"), trace -> trace.add("auto"))
                .on(S.C, Go.class, S.D, (trace, event) -> trace.add("go again"))
                .finalStates(EnumSet.of(S.D))
                .build();
        List<String> trace = new ArrayList<>();
        StateMachine<S, Ev, List<String>> machine = definition.start(trace);

        assertEquals(List.of(new StateChange<>(S.A, S.B, "Go")), machine.fire(new Go()));
        assertEquals(List.of(new StateChange<>(S.B, S.C, "b is done")), machine.fire(new Poke()));
        assertEquals(List.of(), machine.fire(new Poke()), "internal transition: no state change");
        machine.fire(new Go());
        assertEquals(S.D, machine.state());
        assertTrue(machine.isFinished());
        assertEquals(List.of("go", "poke", "auto", "poke", "go again"), trace);
    }

    @Test
    void rejectsEventsTheStateDoesNotAccept() {
        StateMachine<S, Ev, List<String>> machine = linear().start(new ArrayList<>());
        IllegalTransitionException error = assertThrows(IllegalTransitionException.class, () -> machine.fire(new Poke()));
        assertEquals("A", error.state());
        assertEquals("Poke", error.event());
        assertEquals(List.of("Go"), error.acceptedEvents());
        assertEquals(S.A, machine.state());
    }

    @Test
    void failingActionLeavesTheStateUnchanged() {
        StateMachine<S, Ev, List<String>> machine = builder()
                .on(S.A, Go.class, S.B, (trace, event) -> {
                    throw new IllegalArgumentException("nope");
                })
                .on(S.B, Go.class, S.C, (trace, event) -> { })
                .on(S.C, Go.class, S.D, (trace, event) -> { })
                .build()
                .start(new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> machine.fire(new Go()));
        assertEquals(S.A, machine.state());
    }

    @Test
    void builderRejectsBrokenDefinitions() {
        assertThrows(IllegalStateException.class, () -> builder().on(S.A, Go.class, S.B, (t, e) -> { }).build(),
                "C and D are unreachable");
        assertThrows(IllegalArgumentException.class, () -> builder().complete(S.A, S.A, "loop", t -> true, t -> { }));
        assertThrows(IllegalArgumentException.class, () -> builder()
                .on(S.A, Go.class, S.B, (t, e) -> { })
                .on(S.A, Go.class, S.C, (t, e) -> { }));
        assertThrows(IllegalStateException.class, () -> builder()
                .on(S.A, Go.class, S.B, (t, e) -> { })
                .on(S.B, Go.class, S.C, (t, e) -> { })
                .on(S.C, Go.class, S.D, (t, e) -> { })
                .on(S.D, Go.class, S.A, (t, e) -> { })
                .finalStates(EnumSet.of(S.D))
                .build(), "a final state cannot have outgoing transitions");
    }

    @Test
    void completionCyclesAreDetectedInsteadOfLoopingForever() {
        StateMachine<S, Ev, List<String>> machine = builder()
                .on(S.A, Go.class, S.B, (t, e) -> { })
                .complete(S.B, S.C, "to c", t -> true, t -> { })
                .complete(S.C, S.B, "to b", t -> true, t -> { })
                .on(S.C, Poke.class, S.D, (t, e) -> { })
                .build()
                .start(new ArrayList<>());
        assertThrows(IllegalStateException.class, () -> machine.fire(new Go()));
    }

    @Test
    void describesTheTransitionTable() {
        String table = linear().describe();
        assertTrue(table.contains("on Go"), table);
        assertTrue(table.contains("(final)"), table);
    }

    private static StateMachineDefinition<S, Ev, List<String>> linear() {
        return builder()
                .on(S.A, Go.class, S.B, (t, e) -> { })
                .on(S.B, Go.class, S.C, (t, e) -> { })
                .on(S.C, Go.class, S.D, (t, e) -> { })
                .finalStates(EnumSet.of(S.D))
                .build();
    }
}
