package com.pokerassistant.equity;

import com.pokerassistant.cards.Card;
import com.pokerassistant.cards.CardMask;
import com.pokerassistant.cards.Rank;
import com.pokerassistant.cards.Suit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One of the 169 strategically distinct starting hands: a pair ({@code QQ}), a suited ({@code AKs})
 * or an offsuit ({@code AKo}) combination of two ranks.
 */
public record StartingHand(Rank high, Rank low, boolean suited) {

    private static final List<StartingHand> ALL = buildAll();

    public StartingHand {
        Objects.requireNonNull(high, "high");
        Objects.requireNonNull(low, "low");
        if (high.compareTo(low) < 0) {
            throw new IllegalArgumentException("high rank must not be below low rank");
        }
        if (high == low && suited) {
            throw new IllegalArgumentException("a pair cannot be suited");
        }
    }

    /** All 169 starting hands. */
    public static List<StartingHand> all() {
        return ALL;
    }

    /** The starting hand of a two-card mask. */
    public static StartingHand of(long twoCards) {
        if (Long.bitCount(twoCards) != 2) {
            throw new IllegalArgumentException("A starting hand needs exactly two cards");
        }
        List<Card> cards = CardMask.toList(twoCards);
        Card first = cards.get(0);
        Card second = cards.get(1);
        boolean suited = first.rank() != second.rank() && first.suit() == second.suit();
        return new StartingHand(first.rank(), second.rank(), suited);
    }

    public boolean isPair() {
        return high == low;
    }

    /** 6 for a pair, 4 suited, 12 offsuit. */
    public int comboCount() {
        return isPair() ? 6 : suited ? 4 : 12;
    }

    /** Every concrete two-card combo of this hand as card masks. */
    public long[] combos() {
        long[] combos = new long[comboCount()];
        int next = 0;
        for (Suit first : Suit.values()) {
            for (Suit second : Suit.values()) {
                boolean include = isPair() ? first.ordinal() < second.ordinal() : suited == (first == second);
                if (include) {
                    combos[next++] = Card.of(high, first).mask() | Card.of(low, second).mask();
                }
            }
        }
        return combos;
    }

    /** Standard notation: {@code AA}, {@code AKs}, {@code AKo}. */
    public String name() {
        String ranks = String.valueOf(high.symbol()) + low.symbol();
        return isPair() ? ranks : ranks + (suited ? 's' : 'o');
    }

    @Override
    public String toString() {
        return name();
    }

    private static List<StartingHand> buildAll() {
        List<StartingHand> hands = new ArrayList<>(169);
        for (Rank high : Rank.values()) {
            for (Rank low : Rank.values()) {
                if (high == low) {
                    hands.add(new StartingHand(high, low, false));
                } else if (high.compareTo(low) > 0) {
                    hands.add(new StartingHand(high, low, true));
                    hands.add(new StartingHand(high, low, false));
                }
            }
        }
        return List.copyOf(hands);
    }
}
