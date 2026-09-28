package com.pokerassistant.strategy;

/**
 * Preflop reference numbers approximating 100bb cash-game solver output. They are deliberately few
 * and in one place: swap them for your own charts or solver exports.
 */
public final class PreflopCharts {

    /**
     * Share of starting hands (percent) opened by a solver when {@code index} opponents are still to
     * act behind: 1 = small blind vs big blind, 2 = button, 3 = cutoff, 4 = hijack, 5 = UTG at six-max...
     */
    private static final double[] OPEN_RAISE_PERCENT_BY_PLAYERS_BEHIND = {100, 40, 43, 27, 21, 17, 15, 13, 11, 10};

    /** Heads-up, the button (who is also the small blind) opens most hands. */
    private static final double HEADS_UP_BUTTON_OPEN_PERCENT = 80;

    private PreflopCharts() {
    }

    public static double openRaisePercent(int playersBehind, boolean headsUp) {
        if (headsUp) {
            return HEADS_UP_BUTTON_OPEN_PERCENT;
        }
        int index = Math.max(0, Math.min(playersBehind, OPEN_RAISE_PERCENT_BY_PLAYERS_BEHIND.length - 1));
        return OPEN_RAISE_PERCENT_BY_PLAYERS_BEHIND[index];
    }
}
