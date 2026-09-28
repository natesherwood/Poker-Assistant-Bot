package com.pokerassistant.cards;

import com.pokerassistant.error.InvalidInputException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helpers for sets of cards encoded as {@code long} bit masks (see {@link Card#mask()}). Masks are
 * what the evaluator and the equity engines work on: union is {@code |}, overlap is {@code &}.
 */
public final class CardMask {

    /** Every valid card bit: the 13 low bits of each of the four 16-bit suit lanes. */
    public static final long FULL_DECK = 0x1FFF_1FFF_1FFF_1FFFL;

    private static final long[] SINGLE_CARDS = new long[52];

    static {
        for (Card card : Card.all()) {
            SINGLE_CARDS[card.index()] = card.mask();
        }
    }

    private CardMask() {
    }

    public static long of(Card... cards) {
        long mask = 0;
        for (Card card : cards) {
            mask |= card.mask();
        }
        return mask;
    }

    public static long of(Collection<Card> cards) {
        long mask = 0;
        for (Card card : cards) {
            mask |= card.mask();
        }
        return mask;
    }

    /** Mask of the single card with dense index {@code 0..51}. */
    public static long singleCard(int index) {
        return SINGLE_CARDS[index];
    }

    public static int size(long mask) {
        return Long.bitCount(mask);
    }

    /** True when every set bit is a real card. */
    public static boolean isValid(long mask) {
        return (mask & ~FULL_DECK) == 0;
    }

    /** The cards in {@code mask}, highest rank first. */
    public static List<Card> toList(long mask) {
        List<Card> cards = new ArrayList<>(Long.bitCount(mask));
        for (long rest = mask; rest != 0; rest &= rest - 1) {
            cards.add(Card.ofBit(Long.numberOfTrailingZeros(rest)));
        }
        cards.sort(Comparator.reverseOrder());
        return cards;
    }

    /** Space-separated cards, highest first, e.g. {@code "As Kd"}; empty string for an empty mask. */
    public static String format(long mask) {
        return toList(mask).stream().map(Card::toString).collect(Collectors.joining(" "));
    }

    /** Single-card masks of every card not in {@code used}, in dense index order. */
    public static long[] remaining(long used) {
        long[] cards = new long[52 - Long.bitCount(used & FULL_DECK)];
        int next = 0;
        for (long card : SINGLE_CARDS) {
            if ((used & card) == 0) {
                cards[next++] = card;
            }
        }
        return cards;
    }

    /**
     * Parses cards written with or without separators ({@code "AhKd"}, {@code "Ah Kd"},
     * {@code "ah,kd"}, {@code "10h 9s"}), keeping input order and rejecting duplicates.
     */
    public static List<Card> parseList(String text) {
        String compact = text == null ? "" : text.replaceAll("[\\s,]+", "");
        if (compact.isEmpty()) {
            throw new InvalidInputException("No cards given");
        }
        List<Card> cards = new ArrayList<>();
        long seen = 0;
        int position = 0;
        while (position < compact.length()) {
            int length = compact.startsWith("10", position) ? 3 : 2;
            if (position + length > compact.length()) {
                throw new InvalidInputException("Incomplete card '" + compact.substring(position) + "' in '" + text.trim() + "'");
            }
            Card card = Card.parse(compact.substring(position, position + length));
            if ((seen & card.mask()) != 0) {
                throw new InvalidInputException("Duplicate card " + card);
            }
            seen |= card.mask();
            cards.add(card);
            position += length;
        }
        return cards;
    }

    /** Parses exactly {@code count} cards; {@code what} names them in error messages ("Hero's hand"). */
    public static long parseExactly(String text, int count, String what) {
        List<Card> cards = parseList(text);
        if (cards.size() != count) {
            throw new InvalidInputException("%s needs exactly %d card%s, got %d".formatted(
                    what, count, count == 1 ? "" : "s", cards.size()));
        }
        return of(cards);
    }
}
