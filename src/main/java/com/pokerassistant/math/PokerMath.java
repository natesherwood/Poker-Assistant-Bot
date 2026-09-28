package com.pokerassistant.math;

/**
 * The closed-form quantities behind pot-odds and game-theory reasoning. All amounts are in the same
 * unit (chips or big blinds); results are fractions in [0, 1] unless stated otherwise.
 *
 * <p>Naming convention: {@code pot} is the pot <em>including</em> the bet being faced;
 * {@code potBeforeBet} excludes it.
 */
public final class PokerMath {

    private PokerMath() {
    }

    /** Equity a call needs to break even: {@code toCall / (pot + toCall)}. */
    public static double requiredEquity(double pot, double toCall) {
        requireNonNegative(pot, toCall);
        return toCall == 0 ? 0 : toCall / (pot + toCall);
    }

    /** Pot odds as the X in "X : 1": what a call wins per chip risked. */
    public static double potOddsRatio(double pot, double toCall) {
        requireNonNegative(pot, toCall);
        return toCall == 0 ? Double.POSITIVE_INFINITY : pot / toCall;
    }

    /**
     * Minimum defense frequency: the share of a range that must continue against a bet so that a bluff
     * with any two cards does not profit, {@code potBeforeBet / (potBeforeBet + bet)}.
     */
    public static double minimumDefenseFrequency(double potBeforeBet, double bet) {
        requireNonNegative(potBeforeBet, bet);
        return potBeforeBet + bet == 0 ? 1 : potBeforeBet / (potBeforeBet + bet);
    }

    /** Alpha: how often a pure bluff must succeed to break even, {@code bet / (potBeforeBet + bet)}. */
    public static double bluffBreakEven(double potBeforeBet, double bet) {
        requireNonNegative(potBeforeBet, bet);
        return potBeforeBet + bet == 0 ? 0 : bet / (potBeforeBet + bet);
    }

    /**
     * Share of bluffs in a balanced, polarized river betting range: {@code bet / (potBeforeBet + 2 bet)}.
     * At that ratio a bluff-catcher is indifferent between calling and folding.
     */
    public static double balancedBluffShare(double potBeforeBet, double bet) {
        requireNonNegative(potBeforeBet, bet);
        return potBeforeBet + bet == 0 ? 0 : bet / (potBeforeBet + 2 * bet);
    }

    /** Chip EV of calling now, ignoring later streets: {@code equity * (pot + toCall) - toCall}. */
    public static double callEv(double equity, double pot, double toCall) {
        requireProbability(equity);
        requireNonNegative(pot, toCall);
        return equity * (pot + toCall) - toCall;
    }

    /**
     * Implied odds: chips that must be won on later streets for a call to break even, or 0 when the
     * direct pot odds already suffice. From {@code equity * (pot + toCall + x) - toCall = 0}.
     */
    public static double impliedOddsNeeded(double equity, double pot, double toCall) {
        requireProbability(equity);
        requireNonNegative(pot, toCall);
        if (equity == 0) {
            return toCall == 0 ? 0 : Double.POSITIVE_INFINITY;
        }
        return Math.max(0, toCall / equity - pot - toCall);
    }

    /** Stack-to-pot ratio: how many pots are left to bet. */
    public static double stackToPotRatio(double effectiveStack, double pot) {
        requireNonNegative(effectiveStack, pot);
        return pot == 0 ? Double.POSITIVE_INFINITY : effectiveStack / pot;
    }

    /** The "rule of 2 and 4" shortcut for hitting one of {@code outs} with 1 or 2 cards to come. */
    public static double ruleOfTwoAndFour(int outs, int cardsToCome) {
        if (outs < 0 || cardsToCome < 1 || cardsToCome > 2) {
            throw new IllegalArgumentException("outs must be >= 0 and cardsToCome 1 or 2");
        }
        return Math.min(1.0, outs * (cardsToCome == 2 ? 0.04 : 0.02));
    }

    private static void requireNonNegative(double... amounts) {
        for (double amount : amounts) {
            if (!(amount >= 0) || Double.isInfinite(amount)) {
                throw new IllegalArgumentException("Amounts must be finite and non-negative: " + amount);
            }
        }
    }

    private static void requireProbability(double p) {
        if (!(p >= 0 && p <= 1)) {
            throw new IllegalArgumentException("Probability must be in [0, 1]: " + p);
        }
    }
}
