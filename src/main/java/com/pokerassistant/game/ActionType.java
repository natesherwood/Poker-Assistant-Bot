package com.pokerassistant.game;

/**
 * Betting actions. {@link #ALL_IN} exists only as input: the betting round resolves it to a call,
 * bet or raise of the player's whole stack, so recorded actions never carry it.
 */
public enum ActionType {
    FOLD,
    CHECK,
    CALL,
    BET,
    RAISE,
    ALL_IN;

    public boolean isAggressive() {
        return this == BET || this == RAISE;
    }

    /** True for actions that carry an amount: the player's street total after betting or raising. */
    public boolean isSized() {
        return this == BET || this == RAISE;
    }
}
