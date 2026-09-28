package com.pokerassistant.equity;

import com.pokerassistant.cards.CardMask;

import java.util.Arrays;
import java.util.Collection;

/**
 * An immutable, unweighted set of two-card combos such as {@code "QQ+, AKs"} (22 combos). Combos are
 * two-bit card masks, kept sorted and unique so membership is a binary search.
 */
public final class Range {

    /** C(52, 2): every possible two-card combo. */
    public static final int TOTAL_COMBOS = 1326;

    private static final Range ALL = new Range(everyCombo(), "random");

    private final long[] combos;
    private final String description;

    private Range(long[] sortedUniqueCombos, String description) {
        this.combos = sortedUniqueCombos;
        this.description = description;
    }

    /** Any two cards. */
    public static Range all() {
        return ALL;
    }

    /** Parses range notation; see {@link RangeParser}. */
    public static Range parse(String notation) {
        return RangeParser.parse(notation);
    }

    public static Range of(long[] combos, String description) {
        long[] unique = Arrays.stream(combos).distinct().sorted().toArray();
        for (long combo : unique) {
            if (Long.bitCount(combo) != 2) {
                throw new IllegalArgumentException("A combo must have exactly two cards");
            }
        }
        return new Range(unique, description);
    }

    public static Range of(Collection<Long> combos, String description) {
        return of(combos.stream().mapToLong(Long::longValue).toArray(), description);
    }

    public int size() {
        return combos.length;
    }

    public boolean isEmpty() {
        return combos.length == 0;
    }

    /** Share of all 1326 combos covered by this range. */
    public double fraction() {
        return (double) combos.length / TOTAL_COMBOS;
    }

    public String description() {
        return description;
    }

    public boolean contains(long combo) {
        return Arrays.binarySearch(combos, combo) >= 0;
    }

    public long[] combos() {
        return combos.clone();
    }

    /** Combos that share no card with {@code dead} (card removal / blockers). */
    public long[] compatibleCombos(long dead) {
        return Arrays.stream(combos).filter(combo -> (combo & dead) == 0).toArray();
    }

    public int compatibleCount(long dead) {
        int count = 0;
        for (long combo : combos) {
            if ((combo & dead) == 0) {
                count++;
            }
        }
        return count;
    }

    public Range withDescription(String newDescription) {
        return new Range(combos, newDescription);
    }

    @Override
    public String toString() {
        return description + " (" + combos.length + " combos)";
    }

    private static long[] everyCombo() {
        long[] all = new long[TOTAL_COMBOS];
        int next = 0;
        for (int first = 0; first < 52; first++) {
            for (int second = first + 1; second < 52; second++) {
                all[next++] = CardMask.singleCard(first) | CardMask.singleCard(second);
            }
        }
        Arrays.sort(all);
        return all;
    }
}
