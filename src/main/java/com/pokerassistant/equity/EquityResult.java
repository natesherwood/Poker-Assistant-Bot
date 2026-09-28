package com.pokerassistant.equity;

/**
 * Hero's share of the pot. {@code equity} counts split pots fractionally (a two-way tie is worth 0.5),
 * {@code win} and {@code tie} are the probabilities of winning outright and of any split.
 */
public record EquityResult(
        double equity,
        double win,
        double tie,
        double standardError,
        long samples,
        Method method,
        long elapsedMillis) {

    public enum Method { EXACT, MONTE_CARLO }

    /** Half-width of the 95% confidence interval; 0 for exact results. */
    public double marginOfError() {
        return 1.96 * standardError;
    }

    public String describeMethod() {
        return method == Method.EXACT
                ? "exact, %,d showdowns, %d ms".formatted(samples, elapsedMillis)
                : "Monte Carlo, %,d trials, +/-%.1f%%, %d ms".formatted(samples, marginOfError() * 100, elapsedMillis);
    }
}
