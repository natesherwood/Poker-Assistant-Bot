package com.pokerassistant.eval;

/** Hand categories from weakest to strongest; the ordinal is stored in the top bits of a hand value. */
public enum HandCategory {
    HIGH_CARD("High card"),
    ONE_PAIR("Pair"),
    TWO_PAIR("Two pair"),
    THREE_OF_A_KIND("Three of a kind"),
    STRAIGHT("Straight"),
    FLUSH("Flush"),
    FULL_HOUSE("Full house"),
    FOUR_OF_A_KIND("Four of a kind"),
    STRAIGHT_FLUSH("Straight flush");

    private final String label;

    HandCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
