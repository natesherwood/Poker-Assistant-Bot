package com.pokerassistant.game;

/** Betting streets of Texas Hold'em and how many community cards are visible on each. */
public enum Street {
    PREFLOP("Preflop", 0),
    FLOP("Flop", 3),
    TURN("Turn", 4),
    RIVER("River", 5);

    private final String label;
    private final int boardCards;

    Street(String label, int boardCards) {
        this.label = label;
        this.boardCards = boardCards;
    }

    public String label() {
        return label;
    }

    public int boardCards() {
        return boardCards;
    }
}
