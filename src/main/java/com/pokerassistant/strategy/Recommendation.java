package com.pokerassistant.strategy;

import com.pokerassistant.game.ActionType;
import com.pokerassistant.game.Chips;

import java.util.List;

/**
 * A recommended action with the reasoning and numbers behind it.
 *
 * @param action      FOLD, CHECK, CALL, BET, RAISE or ALL_IN
 * @param amount      street total for BET, RAISE and ALL_IN; null otherwise
 * @param headline    e.g. "RAISE to 7.5" or "BET 3.3 (50% pot)"
 * @param reasons     why, most important first
 * @param stats       the numbers behind the decision: equity, ranges, pot odds, MDF, SPR...
 * @param alternative a reasonable mixed-strategy alternative, or null
 */
public record Recommendation(
        ActionType action,
        Chips amount,
        String headline,
        List<String> reasons,
        List<Recommendation.Stat> stats,
        String alternative) {

    public record Stat(String label, String value) {
    }

    public Recommendation {
        reasons = List.copyOf(reasons);
        stats = List.copyOf(stats);
    }

    /** What to type in the CLI to take this action. */
    public String command() {
        return switch (action) {
            case FOLD -> "f";
            case CHECK -> "x";
            case CALL -> "c";
            case BET -> "b " + amount;
            case RAISE -> "r " + amount;
            case ALL_IN -> "a";
        };
    }
}
