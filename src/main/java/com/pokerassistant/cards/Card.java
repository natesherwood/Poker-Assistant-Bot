package com.pokerassistant.cards;

import com.pokerassistant.error.InvalidInputException;

import java.util.List;

/**
 * One of the 52 cards. Instances are interned, so identity comparison is safe.
 *
 * <p>Every card owns one bit of a {@code long} card mask: bit {@code 16 * suit + rank}. The four
 * 16-bit lanes (one per suit, 13 bits used) let the hand evaluator pull out each suit's rank set with
 * a single shift and mask.
 */
public final class Card implements Comparable<Card> {

    private static final Card[] BY_INDEX = new Card[52];
    private static final List<Card> ALL;

    static {
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                Card card = new Card(rank, suit);
                BY_INDEX[card.index()] = card;
            }
        }
        ALL = List.of(BY_INDEX);
    }

    private final Rank rank;
    private final Suit suit;

    private Card(Rank rank, Suit suit) {
        this.rank = rank;
        this.suit = suit;
    }

    public static Card of(Rank rank, Suit suit) {
        return BY_INDEX[suit.ordinal() * 13 + rank.ordinal()];
    }

    /** The card with dense index {@code 0..51} (suit-major). */
    public static Card ofIndex(int index) {
        if (index < 0 || index >= 52) {
            throw new IllegalArgumentException("Card index out of range: " + index);
        }
        return BY_INDEX[index];
    }

    /** The card that owns bit {@code bit} of a card mask. */
    public static Card ofBit(int bit) {
        int suit = bit >>> 4;
        int rank = bit & 15;
        if (bit < 0 || suit > 3 || rank > 12) {
            throw new IllegalArgumentException("Not a card bit: " + bit);
        }
        return BY_INDEX[suit * 13 + rank];
    }

    /** Parses one card such as {@code Ah}, {@code td} or {@code 10s}, case-insensitively. */
    public static Card parse(String text) {
        String card = text.trim();
        if (card.length() == 3 && card.startsWith("10")) {
            card = "T" + card.charAt(2);
        }
        if (card.length() != 2) {
            throw new InvalidInputException("'" + text.trim() + "' is not a card (expected rank + suit, e.g. Ah, Td, 9c)");
        }
        return of(Rank.fromSymbol(card.charAt(0)), Suit.fromSymbol(card.charAt(1)));
    }

    public static List<Card> all() {
        return ALL;
    }

    public Rank rank() {
        return rank;
    }

    public Suit suit() {
        return suit;
    }

    /** Dense index {@code 0..51}, suit-major. */
    public int index() {
        return suit.ordinal() * 13 + rank.ordinal();
    }

    /** Bit position inside a card mask. */
    public int bit() {
        return suit.ordinal() * 16 + rank.ordinal();
    }

    public long mask() {
        return 1L << bit();
    }

    /** Orders by rank, then suit. */
    @Override
    public int compareTo(Card other) {
        int byRank = rank.compareTo(other.rank);
        return byRank != 0 ? byRank : suit.compareTo(other.suit);
    }

    @Override
    public String toString() {
        return String.valueOf(rank.symbol()) + suit.symbol();
    }
}
