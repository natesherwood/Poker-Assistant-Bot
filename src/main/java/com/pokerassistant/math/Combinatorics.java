package com.pokerassistant.math;

/** Exact counting helpers for card problems. */
public final class Combinatorics {

    private Combinatorics() {
    }

    /** Binomial coefficient C(n, k), exact for every value a 52-card deck can produce. */
    public static long choose(int n, int k) {
        if (n < 0 || k < 0) {
            throw new IllegalArgumentException("choose(%d, %d) is undefined".formatted(n, k));
        }
        if (k > n) {
            return 0;
        }
        int smaller = Math.min(k, n - k);
        long result = 1;
        for (int i = 1; i <= smaller; i++) {
            result = result * (n - smaller + i) / i; // stays integral: result is C(n - smaller + i, i)
        }
        return result;
    }

    /**
     * Probability that at least one of {@code outs} specific cards appears when {@code draws} cards are
     * dealt from {@code unseen} unknown cards: {@code 1 - C(unseen - outs, draws) / C(unseen, draws)}.
     */
    public static double hitProbability(int outs, int unseen, int draws) {
        if (outs < 0 || outs > unseen || draws < 0 || draws > unseen) {
            throw new IllegalArgumentException("Invalid draw: %d outs, %d unseen, %d draws".formatted(outs, unseen, draws));
        }
        return 1.0 - (double) choose(unseen - outs, draws) / choose(unseen, draws);
    }
}
