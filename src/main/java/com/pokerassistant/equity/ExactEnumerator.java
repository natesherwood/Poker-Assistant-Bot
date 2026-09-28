package com.pokerassistant.equity;

import com.pokerassistant.cards.CardMask;
import com.pokerassistant.error.InvalidInputException;
import com.pokerassistant.eval.HandEvaluator;
import com.pokerassistant.math.Combinatorics;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Exact heads-up equity by full enumeration: every villain combo in range times every board runout.
 *
 * <p>Each compatible villain combo leaves the same number of unseen cards, so every (combo, runout)
 * pair is equally likely and a flat count of wins, ties and losses is the exact probability. Work is
 * spread over the common fork-join pool, by combo or, for very narrow ranges, by first runout card.
 */
public final class ExactEnumerator {

    /** Below this many villain combos the runouts of each combo are split across threads instead. */
    private static final int PARALLEL_BY_COMBO_THRESHOLD = 16;

    private record Tally(long wins, long ties, long losses) {
        static final Tally ZERO = new Tally(0, 0, 0);

        Tally plus(Tally other) {
            return new Tally(wins + other.wins, ties + other.ties, losses + other.losses);
        }

        long total() {
            return wins + ties + losses;
        }
    }

    private ExactEnumerator() {
    }

    /** Showdowns {@link #compute} would evaluate, so callers can budget exact against sampled work. */
    public static long showdownCount(EquityRequest request) {
        if (request.villains().size() != 1) {
            return Long.MAX_VALUE;
        }
        long known = request.knownCards();
        long combos = request.villains().get(0).compatibleCount(known);
        int unseen = 52 - Long.bitCount(known) - 2;
        return combos * Combinatorics.choose(unseen, request.missingBoardCards());
    }

    public static EquityResult compute(EquityRequest request) {
        if (request.villains().size() != 1) {
            throw new IllegalArgumentException("Exact enumeration supports exactly one opponent");
        }
        long start = System.nanoTime();
        long hero = request.hero();
        long board = request.board();
        long known = request.knownCards();
        int missing = request.missingBoardCards();
        long[] villains = request.villains().get(0).compatibleCombos(known);
        if (villains.length == 0) {
            throw new InvalidInputException("The opponent's range has no combos left once the known cards are removed");
        }

        Tally total;
        if (villains.length >= PARALLEL_BY_COMBO_THRESHOLD || missing == 0) {
            total = Arrays.stream(villains).parallel()
                    .mapToObj(villain -> runouts(hero, villain, board, known | villain, missing))
                    .reduce(Tally.ZERO, Tally::plus);
        } else {
            total = Arrays.stream(villains)
                    .mapToObj(villain -> runoutsSplitByFirstCard(hero, villain, board, known | villain, missing))
                    .reduce(Tally.ZERO, Tally::plus);
        }
        long showdowns = total.total();
        return new EquityResult(
                (total.wins() + total.ties() / 2.0) / showdowns,
                (double) total.wins() / showdowns,
                (double) total.ties() / showdowns,
                0.0,
                showdowns,
                EquityResult.Method.EXACT,
                (System.nanoTime() - start) / 1_000_000);
    }

    private static Tally runouts(long hero, long villain, long board, long used, int missing) {
        long[] counts = new long[3];
        enumerate(CardMask.remaining(used), 0, missing, board, hero, villain, counts);
        return new Tally(counts[0], counts[1], counts[2]);
    }

    private static Tally runoutsSplitByFirstCard(long hero, long villain, long board, long used, int missing) {
        long[] deck = CardMask.remaining(used);
        return IntStream.rangeClosed(0, deck.length - missing).parallel()
                .mapToObj(first -> {
                    long[] counts = new long[3];
                    enumerate(deck, first + 1, missing - 1, board | deck[first], hero, villain, counts);
                    return new Tally(counts[0], counts[1], counts[2]);
                })
                .reduce(Tally.ZERO, Tally::plus);
    }

    /** Visits every {@code missing}-card subset of {@code deck[from..]}; counts[0..2] = wins, ties, losses. */
    private static void enumerate(long[] deck, int from, int missing, long board, long hero, long villain, long[] counts) {
        if (missing == 0) {
            int heroValue = HandEvaluator.evaluate(hero | board);
            int villainValue = HandEvaluator.evaluate(villain | board);
            counts[heroValue > villainValue ? 0 : heroValue == villainValue ? 1 : 2]++;
            return;
        }
        for (int i = from, last = deck.length - missing; i <= last; i++) {
            enumerate(deck, i + 1, missing - 1, board | deck[i], hero, villain, counts);
        }
    }
}
