package com.pokerassistant.game;

import com.pokerassistant.cards.Card;
import com.pokerassistant.cards.CardMask;
import com.pokerassistant.error.IllegalActionException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plays thousands of random hands (2-10 players, short and deep stacks, antes, random legal actions,
 * random illegal probes) and checks the invariants that must hold for every hand:
 * chips are conserved at every step, rejected actions change nothing, and every hand terminates
 * with the whole pot back in the players' stacks.
 */
class HandFuzzTest {

    private static final int HANDS = 3_000;

    @Test
    void randomHandsConserveChipsAndAlwaysFinish() {
        SplittableRandom random = new SplittableRandom(20_260_928L);
        for (int i = 0; i < HANDS; i++) {
            playRandomHand(random, i);
        }
    }

    private static void playRandomHand(SplittableRandom random, int handIndex) {
        int players = 2 + random.nextInt(9);
        List<TableConfig.Seat> seats = new ArrayList<>();
        for (int seat = 1; seat <= players; seat++) {
            long cents = random.nextInt(4) == 0 ? 20 + random.nextInt(600) : 1_000 + random.nextInt(40_000);
            seats.add(new TableConfig.Seat(seat, Chips.ofCents(cents)));
        }
        Chips ante = random.nextInt(3) == 0 ? Chips.ofCents(10) : Chips.ZERO;
        TableConfig table = new TableConfig(seats, Chips.ofCents(50), Chips.of(1), ante,
                1 + random.nextInt(players), 1 + random.nextInt(players));

        HandStateMachine hand = new HandStateMachine();
        hand.fire(new HandEvent.StartHand(table, 0L));
        HandContext context = hand.context();
        Chips total = context.initialChips();
        List<Card> deck = shuffledDeck(random);
        int nextCard = 0;

        for (int step = 0; !hand.isComplete(); step++) {
            String where = "hand " + handIndex + ", step " + step + ", " + hand.state();
            assertTrue(step < 1_000, "hand did not finish: " + where);
            assertEquals(total, context.chipsInPlay(), "chips must be conserved: " + where);

            HandState state = hand.state();
            if (state.isBetting()) {
                LegalActions legal = context.legalActions().orElseThrow();
                probeIllegalAction(hand, legal, random);
                hand.fire(randomLegalAction(legal, random));
            } else if (state.isAwaitingBoard()) {
                int count = state == HandState.AWAITING_FLOP ? 3 : 1;
                hand.fire(new HandEvent.BoardDealt(deck.subList(nextCard, nextCard + count)));
                nextCard += count;
            } else if (state == HandState.SHOWDOWN) {
                List<PlayerState> live = context.livePlayers();
                if (random.nextInt(8) == 0) {
                    hand.fire(new HandEvent.WinnersDeclared(live.stream().map(PlayerState::seat).toList()));
                } else {
                    for (PlayerState player : live) {
                        if (!hand.isComplete() && !player.hasKnownCards()) {
                            hand.fire(new HandEvent.CardsShown(player.seat(), CardMask.of(deck.get(nextCard), deck.get(nextCard + 1))));
                            nextCard += 2;
                        }
                    }
                }
            } else {
                throw new AssertionError("Unexpected state " + where);
            }
        }

        Chips stacks = Chips.ZERO;
        for (PlayerState player : context.players()) {
            stacks = stacks.plus(player.stack());
        }
        assertEquals(total, stacks, "every chip is back in a stack after hand " + handIndex);
        assertEquals(Chips.ZERO, context.pot());
        Chips won = context.winnings().values().stream().reduce(Chips.ZERO, Chips::plus);
        assertEquals(context.finalPot(), won, "the whole pot was paid out in hand " + handIndex);
    }

    private static HandEvent randomLegalAction(LegalActions legal, SplittableRandom random) {
        List<HandEvent> options = new ArrayList<>();
        if (legal.canCheck()) {
            options.add(HandEvent.PlayerActed.of(ActionType.CHECK));
        }
        if (legal.canCall()) {
            options.add(HandEvent.PlayerActed.of(ActionType.CALL));
            options.add(HandEvent.PlayerActed.of(ActionType.FOLD));
        }
        if (legal.canBet() || legal.canRaise()) {
            ActionType type = legal.canBet() ? ActionType.BET : ActionType.RAISE;
            long min = legal.minTotal().cents();
            long max = legal.maxTotal().cents();
            options.add(new HandEvent.PlayerActed(type, Chips.ofCents(min + random.nextLong(max - min + 1))));
            options.add(HandEvent.PlayerActed.of(ActionType.ALL_IN));
        }
        return options.get(random.nextInt(options.size()));
    }

    /** Tries something the rules forbid and checks that it is rejected without touching the hand. */
    private static void probeIllegalAction(HandStateMachine hand, LegalActions legal, SplittableRandom random) {
        List<HandEvent> illegal = new ArrayList<>();
        if (!legal.canCheck()) {
            illegal.add(HandEvent.PlayerActed.of(ActionType.CHECK));
        }
        if (!legal.canFold()) {
            illegal.add(HandEvent.PlayerActed.of(ActionType.FOLD));
        }
        if (!legal.canCall()) {
            illegal.add(HandEvent.PlayerActed.of(ActionType.CALL));
        }
        if (legal.canRaise() && legal.minTotal().cents() > 1 && !legal.minTotal().equals(legal.maxTotal())) {
            illegal.add(new HandEvent.PlayerActed(ActionType.RAISE, Chips.ofCents(legal.minTotal().cents() - 1)));
        }
        if (legal.canBet() || legal.canRaise()) {
            ActionType type = legal.canBet() ? ActionType.BET : ActionType.RAISE;
            illegal.add(new HandEvent.PlayerActed(type, Chips.ofCents(legal.maxTotal().cents() + 1)));
        }
        if (legal.canRaise()) {
            illegal.add(new HandEvent.PlayerActed(ActionType.BET, legal.minTotal()));
        }
        if (illegal.isEmpty() || random.nextInt(3) != 0) {
            return;
        }
        HandEvent probe = illegal.get(random.nextInt(illegal.size()));
        String before = TestHands.fingerprint(hand);
        assertThrows(IllegalActionException.class, () -> hand.fire(probe), () -> "should reject " + probe);
        assertEquals(before, TestHands.fingerprint(hand), () -> "rejected " + probe + " must not change the hand");
    }

    private static List<Card> shuffledDeck(SplittableRandom random) {
        List<Card> deck = new ArrayList<>(Card.all());
        for (int i = deck.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Card swap = deck.get(i);
            deck.set(i, deck.get(j));
            deck.set(j, swap);
        }
        return deck;
    }
}
