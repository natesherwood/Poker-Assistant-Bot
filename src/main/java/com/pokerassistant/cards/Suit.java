package com.pokerassistant.cards;

import com.pokerassistant.error.InvalidInputException;

/** The four suits. The ordinal selects the card's 16-bit lane inside card masks. */
public enum Suit {
    CLUBS('c'),
    DIAMONDS('d'),
    HEARTS('h'),
    SPADES('s');

    private static final Suit[] VALUES = values();

    private final char symbol;

    Suit(char symbol) {
        this.symbol = symbol;
    }

    public char symbol() {
        return symbol;
    }

    /** Parses {@code c, d, h, s}, case-insensitively. */
    public static Suit fromSymbol(char symbol) {
        char lower = Character.toLowerCase(symbol);
        for (Suit suit : VALUES) {
            if (suit.symbol == lower) {
                return suit;
            }
        }
        throw new InvalidInputException("Unknown suit '" + symbol + "' (use c, d, h or s)");
    }
}
