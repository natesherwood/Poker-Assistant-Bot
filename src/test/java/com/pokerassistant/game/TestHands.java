package com.pokerassistant.game;

import com.pokerassistant.cards.CardMask;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** Shorthand for building tables and driving hands in tests. */
public final class TestHands {

    private TestHands() {
    }

    /** Seats 1..n with the given stacks (whole units), blinds 0.5/1, no ante. */
    public static TableConfig table(int button, int hero, long... stacks) {
        List<TableConfig.Seat> seats = new ArrayList<>();
        for (int i = 0; i < stacks.length; i++) {
            seats.add(new TableConfig.Seat(i + 1, Chips.of(stacks[i])));
        }
        return new TableConfig(seats, Chips.ofCents(50), Chips.of(1), Chips.ZERO, button, hero);
    }

    public static HandStateMachine start(TableConfig table) {
        return start(table, null);
    }

    public static HandStateMachine start(TableConfig table, String heroCards) {
        HandStateMachine hand = new HandStateMachine();
        hand.fire(new HandEvent.StartHand(table, heroCards == null ? 0L : cards(heroCards)));
        return hand;
    }

    /** Applies actions for whoever is to act: f, x, c, a, b&lt;amount&gt;, r&lt;amount&gt;. */
    public static void act(HandStateMachine hand, String... actions) {
        for (String action : actions) {
            hand.fire(action(action));
        }
    }

    public static HandEvent.PlayerActed action(String action) {
        return switch (action.charAt(0)) {
            case 'f' -> HandEvent.PlayerActed.of(ActionType.FOLD);
            case 'x' -> HandEvent.PlayerActed.of(ActionType.CHECK);
            case 'c' -> HandEvent.PlayerActed.of(ActionType.CALL);
            case 'a' -> HandEvent.PlayerActed.of(ActionType.ALL_IN);
            case 'b' -> new HandEvent.PlayerActed(ActionType.BET, Chips.parse(action.substring(1)));
            case 'r' -> new HandEvent.PlayerActed(ActionType.RAISE, Chips.parse(action.substring(1)));
            default -> throw new IllegalArgumentException("Unknown test action " + action);
        };
    }

    public static HandEvent.BoardDealt board(String cards) {
        return new HandEvent.BoardDealt(CardMask.parseList(cards));
    }

    public static long cards(String cards) {
        return CardMask.of(CardMask.parseList(cards));
    }

    public static Chips stack(HandStateMachine hand, int seat) {
        return hand.context().player(seat).orElseThrow().stack();
    }

    /** Everything observable about a hand, for "nothing changed" assertions. */
    public static String fingerprint(HandStateMachine hand) {
        HandContext context = hand.context();
        StringJoiner text = new StringJoiner(" | ");
        text.add(hand.state().name()).add("pot " + context.pot()).add("board " + context.boardText())
                .add("toAct " + context.playerToAct().map(PlayerState::seat).orElse(-1))
                .add("actions " + context.actions().size()).add("log " + context.log().size());
        for (PlayerState player : context.players()) {
            text.add("%d:%s/%s/%s/%s/%s".formatted(player.seat(), player.stack(), player.streetBet(),
                    player.totalBet(), player.hasFolded(), player.hasActed()));
        }
        return text.toString();
    }
}
