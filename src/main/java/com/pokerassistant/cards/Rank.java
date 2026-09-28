package com.pokerassistant.cards;

import com.pokerassistant.error.InvalidInputException;

/** Card ranks from deuce to ace. The ordinal doubles as the rank's bit index inside card masks. */
public enum Rank {
    TWO('2', "Two", "Twos"),
    THREE('3', "Three", "Threes"),
    FOUR('4', "Four", "Fours"),
    FIVE('5', "Five", "Fives"),
    SIX('6', "Six", "Sixes"),
    SEVEN('7', "Seven", "Sevens"),
    EIGHT('8', "Eight", "Eights"),
    NINE('9', "Nine", "Nines"),
    TEN('T', "Ten", "Tens"),
    JACK('J', "Jack", "Jacks"),
    QUEEN('Q', "Queen", "Queens"),
    KING('K', "King", "Kings"),
    ACE('A', "Ace", "Aces");

    private static final Rank[] VALUES = values();

    private final char symbol;
    private final String singular;
    private final String plural;

    Rank(char symbol, String singular, String plural) {
        this.symbol = symbol;
        this.singular = singular;
        this.plural = plural;
    }

    public char symbol() {
        return symbol;
    }

    public String singular() {
        return singular;
    }

    public String plural() {
        return plural;
    }

    public static Rank ofOrdinal(int ordinal) {
        return VALUES[ordinal];
    }

    /** Parses {@code 2-9, T, J, Q, K, A}, case-insensitively. */
    public static Rank fromSymbol(char symbol) {
        char upper = Character.toUpperCase(symbol);
        for (Rank rank : VALUES) {
            if (rank.symbol == upper) {
                return rank;
            }
        }
        throw new InvalidInputException("Unknown rank '" + symbol + "' (use 2-9, T, J, Q, K or A)");
    }
}
