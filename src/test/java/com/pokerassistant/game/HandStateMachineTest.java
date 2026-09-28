package com.pokerassistant.game;

import com.pokerassistant.cards.Card;
import com.pokerassistant.error.IllegalActionException;
import com.pokerassistant.error.IllegalTransitionException;
import com.pokerassistant.error.InvalidInputException;
import com.pokerassistant.fsm.StateChange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.pokerassistant.game.HandState.AWAITING_FLOP;
import static com.pokerassistant.game.HandState.AWAITING_RIVER;
import static com.pokerassistant.game.HandState.AWAITING_TURN;
import static com.pokerassistant.game.HandState.COMPLETE;
import static com.pokerassistant.game.HandState.FLOP;
import static com.pokerassistant.game.HandState.PREFLOP;
import static com.pokerassistant.game.HandState.SHOWDOWN;
import static com.pokerassistant.game.TestHands.act;
import static com.pokerassistant.game.TestHands.board;
import static com.pokerassistant.game.TestHands.cards;
import static com.pokerassistant.game.TestHands.fingerprint;
import static com.pokerassistant.game.TestHands.stack;
import static com.pokerassistant.game.TestHands.start;
import static com.pokerassistant.game.TestHands.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandStateMachineTest {

    @Test
    void blindsArePostedAndActionStartsLeftOfTheBigBlind() {
        HandStateMachine hand = start(table(1, 1, 100, 100, 100, 100, 100, 100));
        HandContext context = hand.context();
        assertEquals(PREFLOP, hand.state());
        assertEquals(2, context.smallBlindSeat());
        assertEquals(3, context.bigBlindSeat());
        assertEquals(Chips.ofCents(150), context.pot());
        assertEquals(4, context.playerToAct().orElseThrow().seat());
        assertEquals(List.of("BTN", "SB", "BB", "UTG", "HJ", "CO"),
                context.players().stream().map(PlayerState::position).toList());
    }

    @Test
    void headsUpTheButtonPostsTheSmallBlindAndActsFirstOnlyPreflop() {
        HandStateMachine hand = start(table(1, 1, 100, 100));
        assertEquals(1, hand.context().smallBlindSeat());
        assertEquals(1, hand.context().playerToAct().orElseThrow().seat());
        act(hand, "c", "x");
        hand.fire(board("Qh 7h 2c"));
        assertEquals(2, hand.context().playerToAct().orElseThrow().seat(), "the big blind acts first after the flop");
        assertTrue(hand.context().actsLastPostflop(1));
    }

    @Test
    void everyoneFoldingEndsTheHandAndReturnsTheUncalledBet() {
        HandStateMachine hand = start(table(1, 1, 100, 100, 100));
        List<StateChange<HandState>> changes = hand.fire(TestHands.action("r3"));
        assertTrue(changes.isEmpty());
        act(hand, "f", "f");
        assertEquals(COMPLETE, hand.state());
        // Raised to 3, got 2 back uncalled, won the blinds: 100 - 3 + 2 + 2.5
        assertEquals(Chips.ofCents(10150), stack(hand, 1));
        assertEquals(Chips.ofCents(9950), stack(hand, 2));
        assertEquals(Chips.of(99), stack(hand, 3));
        assertEquals(Chips.ofCents(250), hand.context().finalPot());
        assertTrue(hand.context().log().contains("Uncalled bet of 2 returned to seat 1"));
    }

    @Test
    void aFullHandIsDrivenToShowdownAndSettlesItself() {
        HandStateMachine hand = start(table(1, 1, 100, 100), "AhKh");
        act(hand, "r3", "c");
        assertEquals(AWAITING_FLOP, hand.state());
        assertEquals(Chips.of(6), hand.context().pot());

        hand.fire(board("Qh 7h 2c"));
        assertEquals(FLOP, hand.state());
        act(hand, "x", "b4", "c");
        assertEquals(AWAITING_TURN, hand.state());
        assertEquals(Chips.of(14), hand.context().pot());

        hand.fire(board("5s"));
        act(hand, "x", "x");
        assertEquals(AWAITING_RIVER, hand.state());
        hand.fire(board("9h"));
        act(hand, "b10", "c");
        assertEquals(SHOWDOWN, hand.state());

        hand.fire(new HandEvent.CardsShown(2, cards("QsQd")));
        assertEquals(COMPLETE, hand.state(), "all hands known: the showdown settles automatically");
        assertEquals(Chips.of(117), stack(hand, 1));
        assertEquals(Chips.of(83), stack(hand, 2));
        assertEquals(Chips.of(34), hand.context().winnings().get(1));
    }

    @Test
    void illegalInputIsRejectedWithoutSideEffects() {
        HandStateMachine hand = start(table(1, 1, 100, 100, 100));
        String before = fingerprint(hand);
        IllegalTransitionException wrongState = assertThrows(IllegalTransitionException.class, () -> hand.fire(board("Qh 7h 2c")));
        assertEquals("PREFLOP", wrongState.state());
        assertTrue(wrongState.acceptedEvents().contains("PlayerActed"));
        assertThrows(IllegalActionException.class, () -> act(hand, "x"));
        assertThrows(IllegalActionException.class, () -> act(hand, "b5"));
        assertThrows(IllegalActionException.class, () -> act(hand, "r1.5"));
        assertThrows(IllegalActionException.class, () -> act(hand, "r101"));
        assertEquals(before, fingerprint(hand));

        act(hand, "c", "c", "x");
        assertEquals(AWAITING_FLOP, hand.state());
        assertThrows(IllegalTransitionException.class, () -> act(hand, "x"));
        assertThrows(InvalidInputException.class, () -> hand.fire(board("Qh 7h")));
        assertThrows(InvalidInputException.class, () -> hand.fire(new HandEvent.BoardDealt(
                List.of(Card.parse("Qh"), Card.parse("Qh"), Card.parse("2c")))));
        assertEquals(AWAITING_FLOP, hand.state());
    }

    @Test
    void boardCardsCannotRepeatKnownCards() {
        HandStateMachine hand = start(table(1, 1, 100, 100), "AhKh");
        act(hand, "c", "x");
        assertThrows(InvalidInputException.class, () -> hand.fire(board("Ah 7h 2c")));
        hand.fire(board("Qh 7h 2c"));
        act(hand, "x", "x");
        assertThrows(InvalidInputException.class, () -> hand.fire(board("7h")));
    }

    @Test
    void theBigBlindGetsItsOptionInALimpedPot() {
        HandStateMachine hand = start(table(1, 3, 100, 100, 100));
        act(hand, "c", "c");
        assertEquals(PREFLOP, hand.state());
        assertEquals(3, hand.context().playerToAct().orElseThrow().seat());
        LegalActions legal = hand.context().legalActions().orElseThrow();
        assertTrue(legal.canCheck());
        assertTrue(legal.canRaise());
        assertFalse(legal.canFold());
        assertEquals(Chips.of(2), legal.minTotal());
    }

    @Test
    void minimumRaiseTracksTheLastFullRaise() {
        HandStateMachine hand = start(table(1, 1, 100, 100, 100));
        act(hand, "r3");                       // raise of 2 over the big blind
        assertEquals(Chips.of(5), hand.context().legalActions().orElseThrow().minTotal());
        assertThrows(IllegalActionException.class, () -> act(hand, "r4.99"));
        act(hand, "r9");                       // raise of 6
        assertEquals(Chips.of(15), hand.context().legalActions().orElseThrow().minTotal());
    }

    @Test
    void anIncompleteAllInRaiseDoesNotReopenTheBetting() {
        HandStateMachine hand = start(table(1, 1, 100, 15, 100));
        act(hand, "c", "c", "x");
        hand.fire(board("Qh 7h 2c"));
        act(hand, "x", "b10", "c"); // postflop order 2, 3, 1: seat 2 checks, seat 3 bets 10, seat 1 calls
        act(hand, "a");             // seat 2 is all-in for 14: only 4 more, less than a full raise
        LegalActions seat3 = hand.context().legalActions().orElseThrow();
        assertEquals(3, seat3.seat());
        assertFalse(seat3.canRaise(), "seat 3 bet and only faces an incomplete raise");
        assertEquals(Chips.of(4), seat3.toCall());
        assertThrows(IllegalActionException.class, () -> act(hand, "r30"));
        act(hand, "c");
        assertFalse(hand.context().legalActions().orElseThrow().canRaise(), "seat 1 already acted too");
        act(hand, "c");
        assertEquals(AWAITING_TURN, hand.state());
    }

    @Test
    void foldingIsOnlyAllowedWhenFacingABet() {
        HandStateMachine hand = start(table(1, 1, 100, 100));
        act(hand, "c");
        assertThrows(IllegalActionException.class, () -> act(hand, "f"));
    }

    @Test
    void allInPlayersRunTheBoardOutWithoutBetting() {
        HandStateMachine hand = start(table(1, 1, 50, 100), "AsAd");
        act(hand, "a", "c");
        assertEquals(AWAITING_FLOP, hand.state());
        hand.fire(board("Kh 7h 2c"));
        assertEquals(AWAITING_TURN, hand.state(), "nobody can bet, so the flop closes immediately");
        hand.fire(board("5s"));
        hand.fire(board("9d"));
        assertEquals(SHOWDOWN, hand.state());
        hand.fire(new HandEvent.CardsShown(2, cards("KsKd")));
        assertEquals(COMPLETE, hand.state());
        assertEquals(Chips.ZERO, stack(hand, 1));
        assertEquals(Chips.of(150), stack(hand, 2));
    }

    @Test
    void sidePotsAreLayeredAndAwardedSeparately() {
        HandStateMachine hand = start(table(1, 1, 20, 50, 100));
        act(hand, "a", "a", "c");
        assertEquals(List.of(new Pot(Chips.of(60), List.of(1, 2, 3)), new Pot(Chips.of(60), List.of(2, 3))), hand.context().pots());
        hand.fire(board("2c 7d 9h"));
        hand.fire(board("Js"));
        hand.fire(board("4d"));
        hand.fire(new HandEvent.CardsShown(1, cards("AsAh")));
        hand.fire(new HandEvent.CardsShown(2, cards("KsKh")));
        assertEquals(SHOWDOWN, hand.state());
        hand.fire(new HandEvent.CardsShown(3, cards("QsQh")));
        assertEquals(COMPLETE, hand.state());
        assertEquals(Chips.of(60), stack(hand, 1));  // best hand, main pot only
        assertEquals(Chips.of(60), stack(hand, 2));  // second best takes the side pot
        assertEquals(Chips.of(50), stack(hand, 3));
    }

    @Test
    void splitPotsGiveTheOddChipToTheFirstSeatLeftOfTheButton() {
        List<TableConfig.Seat> seats = List.of(new TableConfig.Seat(1, Chips.of(100)), new TableConfig.Seat(2, Chips.of(100)),
                new TableConfig.Seat(3, Chips.of(100)));
        HandStateMachine hand = start(new TableConfig(seats, Chips.ofCents(50), Chips.of(1), Chips.ofCents(1), 1, 1));
        act(hand, "c", "f", "x");            // pot: 3 antes + 1 + 1 + a dead 0.5 = 2.53
        hand.fire(board("As Ks Qs"));
        act(hand, "x", "x");
        hand.fire(board("Js"));
        act(hand, "x", "x");
        hand.fire(board("Ts"));             // a royal flush on board: everyone plays it
        act(hand, "x", "x");
        hand.fire(new HandEvent.CardsShown(3, cards("2c3d")));
        hand.fire(new HandEvent.CardsShown(1, cards("4c5d")));
        assertEquals(COMPLETE, hand.state());
        assertEquals(Chips.ofCents(10026), stack(hand, 3), "seat 3 is first left of the button and gets the odd cent");
        assertEquals(Chips.ofCents(10025), stack(hand, 1));
        assertEquals(Chips.ofCents(9949), stack(hand, 2));
    }

    @Test
    void winnersCanBeDeclaredWithoutShowingCards() {
        HandStateMachine hand = start(table(1, 1, 20, 50, 100));
        act(hand, "a", "a", "c");
        hand.fire(board("2c 7d 9h"));
        hand.fire(board("Js"));
        hand.fire(board("4d"));
        assertThrows(IllegalActionException.class, () -> hand.fire(new HandEvent.WinnersDeclared(List.of(1))),
                "seat 1 is not eligible for the side pot, so someone else must be named");
        hand.fire(new HandEvent.WinnersDeclared(List.of(1, 3)));
        assertEquals(COMPLETE, hand.state());
        assertEquals(Chips.of(30), stack(hand, 1));              // main pot 60 split with seat 3
        assertEquals(Chips.of(50 + 30 + 60), stack(hand, 3));    // half the main pot and all of the side pot
    }

    @Test
    void muckingConcedesThePot() {
        HandStateMachine hand = start(table(1, 1, 100, 100), "AhKh");
        act(hand, "c", "x");
        hand.fire(board("Qh 7h 2c"));
        act(hand, "x", "x");
        hand.fire(board("5s"));
        act(hand, "x", "x");
        hand.fire(board("9d"));
        act(hand, "x", "x");
        assertEquals(SHOWDOWN, hand.state());
        hand.fire(new HandEvent.Mucked(1));
        assertEquals(COMPLETE, hand.state());
        assertEquals(Chips.of(101), stack(hand, 2));
    }

    @Test
    void shownCardsDuringAnAllInAreKeptForTheShowdown() {
        HandStateMachine hand = start(table(1, 1, 50, 100), "AsAd");
        act(hand, "a");
        hand.fire(new HandEvent.CardsShown(2, cards("KsKd")));
        assertEquals(PREFLOP, hand.state());
        act(hand, "c");
        hand.fire(board("2h 7c 9d"));
        hand.fire(board("Jc"));
        hand.fire(board("3s"));
        assertEquals(COMPLETE, hand.state(), "both hands were known, so the showdown settled on the river card");
        assertEquals(Chips.of(100), stack(hand, 1));
    }

    @Test
    void theTransitionTableCoversEveryState() {
        String table = HandStateMachine.definition().describe();
        for (HandState state : HandState.values()) {
            assertTrue(table.contains(state.name()), state + " missing from\n" + table);
        }
    }
}
