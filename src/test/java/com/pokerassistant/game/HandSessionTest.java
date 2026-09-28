package com.pokerassistant.game;

import com.pokerassistant.error.IllegalActionException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.pokerassistant.game.TestHands.action;
import static com.pokerassistant.game.TestHands.fingerprint;
import static com.pokerassistant.game.TestHands.table;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandSessionTest {

    private static HandStateMachine hand(HandSession session) {
        return session.hand().orElseThrow();
    }

    @Test
    void undoRestoresTheExactPreviousState() {
        HandSession session = new HandSession(table(1, 1, 100, 100, 100));
        session.startHand(0L);
        session.apply(action("c"));
        String before = fingerprint(hand(session));
        session.apply(action("r5"));
        HandEvent undone = session.undo();
        assertEquals(action("r5"), undone);
        assertEquals(before, fingerprint(hand(session)));
    }

    @Test
    void aBatchAppliesCompletelyOrNotAtAll() {
        HandSession session = new HandSession(table(1, 1, 100, 100, 100));
        session.startHand(0L);
        String before = fingerprint(hand(session));
        // The button calls, then the small blind tries to check while facing half a blind.
        assertThrows(IllegalActionException.class, () -> session.applyAll(List.of(action("c"), action("x"))));
        assertEquals(before, fingerprint(hand(session)));
        assertEquals(1, session.events().size());
    }

    @Test
    void undoWorksRightAfterTheHandEndsButNotOnceItIsClosed() {
        HandSession session = new HandSession(table(1, 1, 100, 100, 100));
        session.startHand(0L);
        session.applyAll(List.of(action("r3"), action("f"), action("f")));
        assertTrue(hand(session).isComplete());
        session.undo();
        assertFalse(hand(session).isComplete());
        session.apply(action("f"));
        session.updateTable(table -> table.withStack(2, Chips.of(80)));
        assertThrows(IllegalActionException.class, session::undo);
    }

    @Test
    void finishedHandsUpdateStacksAndPassTheButton() {
        HandSession session = new HandSession(table(1, 1, 100, 100, 100));
        session.startHand(0L);
        session.applyAll(List.of(action("r3"), action("f"), action("f")));
        TableConfig next = session.upcomingTable();
        assertEquals(2, next.buttonSeat());
        assertEquals(Chips.ofCents(10150), next.stackOf(1));

        session.startHand(0L);
        assertEquals(2, session.handNumber());
        assertEquals(2, hand(session).context().buttonSeat());
        assertEquals(Chips.ofCents(10150), hand(session).context().player(1).orElseThrow().startingStack());
    }

    @Test
    void theTableIsLockedWhileAHandIsRunning() {
        HandSession session = new HandSession(table(1, 1, 100, 100, 100));
        session.startHand(0L);
        assertThrows(IllegalActionException.class, () -> session.updateTable(table -> table.withButton(2)));
        assertThrows(IllegalActionException.class, () -> session.startHand(0L));
        session.abort();
        assertDoesNotThrow(() -> session.updateTable(table -> table.withButton(2)));
        assertEquals(2, session.upcomingTable().buttonSeat());
    }

    @Test
    void nothingToUndoOrAbortWithoutAHand() {
        HandSession session = new HandSession(table(1, 1, 100, 100));
        assertThrows(IllegalActionException.class, session::undo);
        assertThrows(IllegalActionException.class, session::abort);
        session.startHand(0L);
        assertThrows(IllegalActionException.class, session::undo, "the start of the hand cannot be undone");
    }

    @Test
    void bustedPlayersSitOutOfTheNextHand() {
        HandSession session = new HandSession(table(1, 1, 50, 100, 100));
        session.startHand(TestHands.cards("7c2d"));
        // Seat 1 shoves, both blinds call and check it down, and seat 1 loses at showdown.
        session.applyAll(List.of(action("a"), action("c"), action("c")));
        assertEquals(HandState.AWAITING_FLOP, hand(session).state());
        session.applyAll(List.of(TestHands.board("Ah Kd 9s"), action("x"), action("x"),
                TestHands.board("4h"), action("x"), action("x"), TestHands.board("5c"), action("x"), action("x")));
        session.applyAll(List.of(new HandEvent.CardsShown(2, TestHands.cards("AsAc")), new HandEvent.CardsShown(3, TestHands.cards("QsQc"))));
        assertTrue(hand(session).isComplete());
        assertEquals(Chips.ZERO, session.upcomingTable().stackOf(1));

        session.startHand(0L);
        assertEquals(List.of(2, 3), hand(session).context().players().stream().map(PlayerState::seat).toList());
    }
}
