package com.pokerassistant.game;

import java.util.ArrayList;
import java.util.List;

/**
 * What the player to act may do. Bet and raise amounts are the player's street total ("raise to").
 *
 * @param toCall      chips needed to call, already capped at the player's stack
 * @param minTotal    smallest legal bet / raise-to (the all-in amount if the stack is short)
 * @param maxTotal    largest legal bet / raise-to: the all-in amount
 */
public record LegalActions(
        int seat,
        Chips toCall,
        boolean callIsAllIn,
        boolean canCheck,
        boolean canCall,
        boolean canBet,
        boolean canRaise,
        Chips minTotal,
        Chips maxTotal) {

    /** Folding is only allowed when facing a bet. */
    public boolean canFold() {
        return canCall;
    }

    public String describe() {
        List<String> options = new ArrayList<>();
        if (canCheck) {
            options.add("check");
        }
        if (canCall) {
            options.add("call " + toCall + (callIsAllIn ? " (all-in)" : ""));
        }
        if (canBet) {
            options.add(minTotal.equals(maxTotal) ? "bet " + maxTotal + " (all-in)" : "bet " + minTotal + ".." + maxTotal);
        }
        if (canRaise) {
            options.add(minTotal.equals(maxTotal) ? "raise to " + maxTotal + " (all-in)" : "raise to " + minTotal + ".." + maxTotal);
        }
        if (canFold()) {
            options.add("fold");
        }
        return String.join(" | ", options);
    }
}
