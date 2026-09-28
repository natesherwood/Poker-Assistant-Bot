package com.pokerassistant.game;

/**
 * States of one hand. Betting states alternate with "awaiting" states in which the machine needs the
 * next community cards from the user:
 *
 * <pre>
 * IDLE -> PREFLOP -> AWAITING_FLOP -> FLOP -> AWAITING_TURN -> TURN -> AWAITING_RIVER -> RIVER -> SHOWDOWN -> COMPLETE
 *            \___________________________________________________________________________/
 *                         everyone else folds: straight to COMPLETE from any betting state
 * </pre>
 * When nobody can bet any more (all-in), each betting state closes the moment its cards are dealt, so
 * the hand runs out to showdown as fast as the user types the board.
 */
public enum HandState {
    IDLE("No hand in progress"),
    PREFLOP("Preflop betting"),
    AWAITING_FLOP("Waiting for the flop"),
    FLOP("Flop betting"),
    AWAITING_TURN("Waiting for the turn"),
    TURN("Turn betting"),
    AWAITING_RIVER("Waiting for the river"),
    RIVER("River betting"),
    SHOWDOWN("Showdown"),
    COMPLETE("Hand complete");

    private final String description;

    HandState(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public boolean isBetting() {
        return this == PREFLOP || this == FLOP || this == TURN || this == RIVER;
    }

    public boolean isAwaitingBoard() {
        return this == AWAITING_FLOP || this == AWAITING_TURN || this == AWAITING_RIVER;
    }
}
