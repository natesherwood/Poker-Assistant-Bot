package com.pokerassistant.equity;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/**
 * Entry point for equity questions. Chooses the engine per request: exact enumeration when the
 * heads-up showdown count fits the budget (typical on the flop, turn and river), otherwise Monte Carlo
 * (multi-way pots, wide ranges preflop).
 */
public final class EquityCalculator {

    /** Showdowns an exact calculation may evaluate; about 50 ms of work on a laptop. */
    public static final long DEFAULT_EXACT_BUDGET = 3_000_000L;
    public static final int DEFAULT_TRIALS = 150_000;

    private final long exactBudget;
    private final int trials;
    private final LongSupplier seeds;

    public EquityCalculator() {
        this(DEFAULT_EXACT_BUDGET, DEFAULT_TRIALS, () -> ThreadLocalRandom.current().nextLong());
    }

    public EquityCalculator(long exactBudget, int trials, LongSupplier seeds) {
        if (exactBudget < 0 || trials <= 0) {
            throw new IllegalArgumentException("exactBudget must be >= 0 and trials > 0");
        }
        this.exactBudget = exactBudget;
        this.trials = trials;
        this.seeds = seeds;
    }

    public EquityResult calculate(EquityRequest request) {
        if (ExactEnumerator.showdownCount(request) <= exactBudget) {
            return ExactEnumerator.compute(request);
        }
        return MonteCarloSimulator.run(request, trials, seeds.getAsLong());
    }
}
