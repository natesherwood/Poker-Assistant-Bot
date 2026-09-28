package com.pokerassistant.game;

import com.pokerassistant.cards.Card;
import com.pokerassistant.cards.CardMask;
import com.pokerassistant.error.InvalidInputException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Inputs to the hand state machine. Events are immutable facts; a hand is fully described by its
 * event log, which is what makes undo (replay all but the last event) exact.
 */
public sealed interface HandEvent {

    /** Seats the players, posts antes and blinds. {@code heroCards} is 0 when not known yet. */
    record StartHand(TableConfig table, long heroCards) implements HandEvent {
        public StartHand {
            Objects.requireNonNull(table, "table");
            if (heroCards != 0) {
                requireTwoCards(heroCards, "Hero's hand");
            }
        }
    }

    /** Hero's hole cards, when they were not given at the start of the hand (or need correcting). */
    record HoleCardsDealt(long cards) implements HandEvent {
        public HoleCardsDealt {
            requireTwoCards(cards, "Hero's hand");
        }
    }

    /** The player to act acts. {@code amount} is the street total for BET/RAISE and null otherwise. */
    record PlayerActed(ActionType type, Chips amount) implements HandEvent {
        public PlayerActed {
            Objects.requireNonNull(type, "type");
            String name = type.name().toLowerCase(Locale.ROOT);
            if (type.isSized() && amount == null) {
                throw new InvalidInputException("'" + name + "' needs an amount");
            }
            if (!type.isSized() && amount != null) {
                throw new InvalidInputException("'" + name + "' does not take an amount");
            }
        }

        public static PlayerActed of(ActionType type) {
            return new PlayerActed(type, null);
        }
    }

    /** Community cards for the next street: three for the flop, one for the turn or river. */
    record BoardDealt(List<Card> cards) implements HandEvent {
        public BoardDealt {
            cards = List.copyOf(cards);
            if (cards.isEmpty()) {
                throw new InvalidInputException("No board cards given");
            }
        }
    }

    /** A player's hole cards become known (at showdown, or tabled during an all-in). */
    record CardsShown(int seat, long cards) implements HandEvent {
        public CardsShown {
            requireTwoCards(cards, "Seat " + seat + "'s hand");
        }
    }

    /** A player gives up their claim to the pot at showdown. */
    record Mucked(int seat) implements HandEvent {
    }

    /** Manual result: each pot is split between the listed seats that are eligible for it. */
    record WinnersDeclared(List<Integer> seats) implements HandEvent {
        public WinnersDeclared {
            seats = List.copyOf(seats);
            if (seats.isEmpty()) {
                throw new InvalidInputException("Name at least one winning seat");
            }
            if (new HashSet<>(seats).size() != seats.size()) {
                throw new InvalidInputException("A winning seat is listed twice");
            }
        }
    }

    private static void requireTwoCards(long cards, String what) {
        if (!CardMask.isValid(cards) || Long.bitCount(cards) != 2) {
            throw new InvalidInputException(what + " needs exactly two cards");
        }
    }
}
