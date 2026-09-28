package com.pokerassistant.eval;

import com.pokerassistant.cards.CardMask;
import com.pokerassistant.cards.Rank;

/**
 * Table-free evaluator for 5, 6 or 7 cards given as a card mask.
 *
 * <p>The result is a totally ordered strength: a higher int wins, equal ints split. Layout:
 * {@code category << 20 | r1 << 16 | r2 << 12 | r3 << 8 | r4 << 4 | r5}, where r1..r5 are the rank
 * indexes (0 = deuce .. 12 = ace) that break ties, most significant first.
 *
 * <p>Each suit's 13-bit rank set is read straight out of the mask. XOR-ing the four sets gives rank
 * parity (so {@code ranks ^ parity} is the ranks held exactly twice once quads are ruled out), and
 * pairwise ANDs find ranks held three or four times. With at most seven cards a flush or a straight
 * leaves too few cards for quads or a full house, so those are checked first.
 */
public final class HandEvaluator {

    private static final int RANK_BITS = 0x1FFF;
    private static final int CATEGORY_SHIFT = 20;
    private static final HandCategory[] CATEGORIES = HandCategory.values();

    private HandEvaluator() {
    }

    public static int evaluate(long cards) {
        int count = Long.bitCount(cards);
        if (count < 5 || count > 7 || !CardMask.isValid(cards)) {
            throw new IllegalArgumentException("Expected 5 to 7 cards, got a mask with " + count);
        }
        int clubs = (int) cards & RANK_BITS;
        int diamonds = (int) (cards >>> 16) & RANK_BITS;
        int hearts = (int) (cards >>> 32) & RANK_BITS;
        int spades = (int) (cards >>> 48) & RANK_BITS;
        int ranks = clubs | diamonds | hearts | spades;

        int flush = flushRanks(clubs, diamonds, hearts, spades);
        if (flush != 0) {
            int straightFlushHigh = straightHigh(flush);
            return straightFlushHigh >= 0
                    ? value(HandCategory.STRAIGHT_FLUSH, straightFlushHigh << 16)
                    : value(HandCategory.FLUSH, kickers(flush, 5, 16));
        }
        int straight = straightHigh(ranks);
        if (straight >= 0) {
            return value(HandCategory.STRAIGHT, straight << 16);
        }

        int quads = clubs & diamonds & hearts & spades;
        if (quads != 0) {
            int quad = highestBit(quads);
            return value(HandCategory.FOUR_OF_A_KIND, quad << 16 | highestBit(ranks & ~quads) << 12);
        }
        int trips = ((clubs & diamonds) | (hearts & spades)) & ((clubs & hearts) | (diamonds & spades));
        int pairs = ranks ^ (clubs ^ diamonds ^ hearts ^ spades);
        if (trips != 0) {
            int trip = highestBit(trips);
            int fillers = pairs | (trips & ~(1 << trip));
            if (fillers != 0) {
                return value(HandCategory.FULL_HOUSE, trip << 16 | highestBit(fillers) << 12);
            }
            return value(HandCategory.THREE_OF_A_KIND, trip << 16 | kickers(ranks & ~trips, 2, 12));
        }
        if (pairs != 0) {
            int high = highestBit(pairs);
            int lowerPairs = pairs & ~(1 << high);
            if (lowerPairs != 0) {
                int low = highestBit(lowerPairs);
                int kicker = highestBit(ranks & ~(1 << high) & ~(1 << low));
                return value(HandCategory.TWO_PAIR, high << 16 | low << 12 | kicker << 8);
            }
            return value(HandCategory.ONE_PAIR, high << 16 | kickers(ranks & ~pairs, 3, 12));
        }
        return value(HandCategory.HIGH_CARD, kickers(ranks, 5, 16));
    }

    public static HandCategory category(int value) {
        return CATEGORIES[value >>> CATEGORY_SHIFT];
    }

    /**
     * The weakest value in {@code category} whose leading rank is {@code top}, e.g. "pair of tens with
     * the lowest kickers". Useful as a threshold when comparing hand values.
     */
    public static int floorValue(HandCategory category, Rank top) {
        return value(category, top.ordinal() << 16);
    }

    /** Plain-English name of a hand value, e.g. "Full house, Kings full of Sevens". */
    public static String describe(int value) {
        Rank first = Rank.ofOrdinal(value >>> 16 & 15);
        Rank second = Rank.ofOrdinal(value >>> 12 & 15);
        return switch (category(value)) {
            case STRAIGHT_FLUSH -> first == Rank.ACE ? "Royal flush" : "Straight flush, " + first.singular() + " high";
            case FOUR_OF_A_KIND -> "Four of a kind, " + first.plural();
            case FULL_HOUSE -> "Full house, " + first.plural() + " full of " + second.plural();
            case FLUSH -> "Flush, " + first.singular() + " high";
            case STRAIGHT -> "Straight, " + first.singular() + " high";
            case THREE_OF_A_KIND -> "Three of a kind, " + first.plural();
            case TWO_PAIR -> "Two pair, " + first.plural() + " and " + second.plural();
            case ONE_PAIR -> "Pair of " + first.plural();
            case HIGH_CARD -> first.singular() + " high";
        };
    }

    private static int value(HandCategory category, int tieBreak) {
        return category.ordinal() << CATEGORY_SHIFT | tieBreak;
    }

    private static int flushRanks(int clubs, int diamonds, int hearts, int spades) {
        if (Integer.bitCount(clubs) >= 5) {
            return clubs;
        }
        if (Integer.bitCount(diamonds) >= 5) {
            return diamonds;
        }
        if (Integer.bitCount(hearts) >= 5) {
            return hearts;
        }
        return Integer.bitCount(spades) >= 5 ? spades : 0;
    }

    /** Rank index of the top card of the best straight in {@code ranks}, or -1. Covers the wheel. */
    private static int straightHigh(int ranks) {
        int extended = ranks << 1 | ranks >>> 12; // bit 0 = ace playing low, rank r sits at bit r + 1
        int runs = extended & extended >>> 1 & extended >>> 2 & extended >>> 3 & extended >>> 4;
        return runs == 0 ? -1 : highestBit(runs) + 3;
    }

    /** Packs the {@code count} highest ranks of {@code mask} into nibbles, starting at bit {@code shift}. */
    private static int kickers(int mask, int count, int shift) {
        int packed = 0;
        for (int i = 0; i < count; i++, shift -= 4) {
            int rank = highestBit(mask);
            packed |= rank << shift;
            mask &= ~(1 << rank);
        }
        return packed;
    }

    private static int highestBit(int mask) {
        return 31 - Integer.numberOfLeadingZeros(mask);
    }
}
