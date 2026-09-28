package com.pokerassistant.game;

/**
 * One applied betting action, as kept in the hand history.
 *
 * @param type             resolved action, never {@link ActionType#ALL_IN}
 * @param added            chips this action moved from the stack into the pot
 * @param streetTotal      the player's total bet on this street afterwards
 * @param aggressionBefore bets and raises already made on this street (preflop: 0 = unopened pot)
 * @param playersYetToAct  opponents who had not acted yet on this street when the action was taken
 * @param potBefore        pot, all current bets included, just before the action
 * @param toCallBefore     amount the player was facing
 */
public record ActionRecord(
        Street street,
        int seat,
        String position,
        ActionType type,
        Chips added,
        Chips streetTotal,
        boolean allIn,
        int aggressionBefore,
        int playersYetToAct,
        Chips potBefore,
        Chips toCallBefore) {

    /** Size of a bet or raise relative to the pot it went into; 0 for passive actions. */
    public double sizeFractionOfPot() {
        if (!type.isAggressive() || potBefore.isZero()) {
            return 0;
        }
        return added.toDouble() / potBefore.toDouble();
    }

    public String describe() {
        String who = "Seat " + seat + " (" + position + ")";
        String allInNote = allIn ? " (all-in)" : "";
        return switch (type) {
            case FOLD -> who + " folds";
            case CHECK -> who + " checks";
            case CALL -> who + " calls " + added + allInNote;
            case BET -> who + " bets " + streetTotal + allInNote;
            case RAISE -> who + " raises to " + streetTotal + allInNote;
            case ALL_IN -> who + " is all-in for " + streetTotal;
        };
    }
}
