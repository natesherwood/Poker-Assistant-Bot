package com.pokerassistant.equity;

import com.pokerassistant.cards.CardMask;
import com.pokerassistant.error.InvalidInputException;
import com.pokerassistant.eval.HandEvaluator;

import java.util.SplittableRandom;
import java.util.stream.IntStream;

/**
 * Multi-way equity by random sampling, run in parallel chunks with independent split random streams.
 *
 * <p>Villain hands are drawn uniformly from their ranges with <em>whole-deal rejection</em>: if any
 * two sampled combos collide, all of them are redrawn. Resampling only the colliding player would
 * bias the joint distribution toward combos that rarely collide. The standard error of the pot share
 * is reported so callers can show a confidence interval.
 *
 * <p>Results are exactly reproducible for a given seed and trial count on any machine: the chunk
 * layout does not depend on the core count, and pot shares are accumulated as integers (a k-way
 * split pays {@code SHARE_UNITS / k}, exact for up to ten players), so the order in which parallel
 * chunks are combined cannot change the result.
 */
public final class MonteCarloSimulator {

    private static final int MAX_DEAL_ATTEMPTS = 10_000;
    private static final int MIN_TRIALS_PER_CHUNK = 4_000;
    private static final int MAX_CHUNKS = 64;
    /** lcm(1..10): every split among up to ten players is a whole number of units. */
    private static final long SHARE_UNITS = 2520;

    private record Stats(long wins, long ties, long shareUnits, long shareUnitsSquared) {
        static final Stats ZERO = new Stats(0, 0, 0, 0);

        Stats plus(Stats other) {
            return new Stats(wins + other.wins, ties + other.ties,
                    shareUnits + other.shareUnits, shareUnitsSquared + other.shareUnitsSquared);
        }
    }

    private MonteCarloSimulator() {
    }

    public static EquityResult run(EquityRequest request, int trials, long seed) {
        if (trials <= 0) {
            throw new IllegalArgumentException("trials must be positive");
        }
        long start = System.nanoTime();
        long known = request.knownCards();
        long[][] ranges = new long[request.villains().size()][];
        for (int i = 0; i < ranges.length; i++) {
            ranges[i] = request.villains().get(i).compatibleCombos(known);
            if (ranges[i].length == 0) {
                throw new InvalidInputException("Opponent #" + (i + 1) + "'s range has no combos left once the known cards are removed");
            }
        }

        int chunks = Math.max(1, Math.min(trials / MIN_TRIALS_PER_CHUNK, MAX_CHUNKS));
        SplittableRandom root = new SplittableRandom(seed);
        SplittableRandom[] streams = new SplittableRandom[chunks];
        for (int i = 0; i < chunks; i++) {
            streams[i] = root.split();
        }
        int base = trials / chunks;
        int extra = trials % chunks;
        Stats total = IntStream.range(0, chunks).parallel()
                .mapToObj(i -> simulate(request, ranges, i < extra ? base + 1 : base, streams[i]))
                .reduce(Stats.ZERO, Stats::plus);

        double mean = (double) total.shareUnits() / SHARE_UNITS / trials;
        double meanOfSquares = (double) total.shareUnitsSquared() / (SHARE_UNITS * SHARE_UNITS) / trials;
        double variance = Math.max(0, meanOfSquares - mean * mean);
        return new EquityResult(
                mean,
                (double) total.wins() / trials,
                (double) total.ties() / trials,
                Math.sqrt(variance / trials),
                trials,
                EquityResult.Method.MONTE_CARLO,
                (System.nanoTime() - start) / 1_000_000);
    }

    private static Stats simulate(EquityRequest request, long[][] ranges, int trials, SplittableRandom random) {
        long hero = request.hero();
        long known = request.knownCards();
        int missing = request.missingBoardCards();
        long[] dealt = new long[ranges.length];
        long wins = 0;
        long ties = 0;
        long shareUnits = 0;
        long shareUnitsSquared = 0;

        for (int trial = 0; trial < trials; trial++) {
            long used = dealVillains(ranges, known, random, dealt);
            long board = request.board();
            for (int card = 0; card < missing; card++) {
                long next;
                do {
                    next = CardMask.singleCard(random.nextInt(52));
                } while ((used & next) != 0);
                used |= next;
                board |= next;
            }

            int best = HandEvaluator.evaluate(hero | board);
            int playersAtBest = 1;
            boolean heroBest = true;
            for (long villain : dealt) {
                int value = HandEvaluator.evaluate(villain | board);
                if (value > best) {
                    best = value;
                    playersAtBest = 1;
                    heroBest = false;
                } else if (value == best) {
                    playersAtBest++;
                }
            }
            if (heroBest) {
                long units = SHARE_UNITS / playersAtBest;
                shareUnits += units;
                shareUnitsSquared += units * units;
                if (playersAtBest == 1) {
                    wins++;
                } else {
                    ties++;
                }
            }
        }
        return new Stats(wins, ties, shareUnits, shareUnitsSquared);
    }

    /** Deals one combo per range with no shared cards; returns the updated used-card mask. */
    private static long dealVillains(long[][] ranges, long known, SplittableRandom random, long[] dealt) {
        for (int attempt = 0; attempt < MAX_DEAL_ATTEMPTS; attempt++) {
            long used = known;
            int villain = 0;
            while (villain < ranges.length) {
                long combo = ranges[villain][random.nextInt(ranges[villain].length)];
                if ((used & combo) != 0) {
                    break;
                }
                used |= combo;
                dealt[villain++] = combo;
            }
            if (villain == ranges.length) {
                return used;
            }
        }
        throw new InvalidInputException("The opponents' ranges are too narrow to be dealt together (their cards keep colliding)");
    }
}
