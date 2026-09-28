package com.pokerassistant.gui;

import com.pokerassistant.game.Chips;

/**
 * Bet-slider arithmetic. Amounts are street totals ("raise to"), like everywhere else in the engine.
 * A pot-sized raise is the call plus the pot after calling: {@code currentBet + (pot + toCall)}.
 */
final class BetSizing {

    private BetSizing() {
    }

    /**
     * The street total for a bet or raise of {@code fraction} of the pot, clamped to the legal range.
     *
     * @param pot        the whole pot, including every bet on the current street
     * @param currentBet the highest street total so far (0 when nobody has bet)
     * @param toCall     what the player needs to call
     */
    static Chips potFraction(double fraction, Chips pot, Chips currentBet, Chips toCall, Chips min, Chips max) {
        Chips target = currentBet.plus(pot.plus(toCall).times(fraction));
        return clamp(target, min, max);
    }

    static Chips clamp(Chips amount, Chips min, Chips max) {
        return amount.max(min).min(max);
    }

    /** Slider notches between {@code min} and {@code max}, one per {@code step}; the last notch is always {@code max}. */
    static int notches(Chips min, Chips max, Chips step) {
        long span = max.cents() - min.cents();
        return (int) Math.min(Integer.MAX_VALUE, (span + step.cents() - 1) / step.cents());
    }

    static Chips atNotch(int notch, Chips min, Chips max, Chips step) {
        return clamp(Chips.ofCents(min.cents() + notch * step.cents()), min, max);
    }

    static int nearestNotch(Chips amount, Chips min, Chips max, Chips step) {
        if (!amount.isLessThan(max)) {
            return notches(min, max, step);
        }
        long offset = Math.max(0, amount.cents() - min.cents());
        return (int) ((offset + step.cents() / 2) / step.cents());
    }

    /** Slider granularity: a tenth of a big blind, but never less than one hundredth. */
    static Chips step(Chips bigBlind) {
        return Chips.ofCents(Math.max(1, bigBlind.cents() / 10));
    }
}
