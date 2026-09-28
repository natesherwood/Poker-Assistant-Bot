package com.pokerassistant.strategy;

import com.pokerassistant.cards.Card;
import com.pokerassistant.cards.CardMask;

import java.util.ArrayList;
import java.util.List;

/**
 * How coordinated a board is. Wet boards (flush and straight possibilities) favour larger bets that
 * charge draws; dry boards favour small, frequent bets.
 *
 * @param maxSuitCount     most cards of one suit on the board
 * @param straightPossible three board ranks fit in a five-rank window (a straight needs two hole cards)
 * @param connected        two board ranks fit in a five-rank window (straight draws exist)
 * @param wetness          0 (bone dry) and up
 */
public record BoardTexture(boolean paired, int maxSuitCount, boolean straightPossible, boolean connected, int cards, int wetness) {

    public static BoardTexture of(long board) {
        List<Card> cards = CardMask.toList(board);
        int[] perSuit = new int[4];
        int ranks = 0;
        boolean paired = false;
        for (Card card : cards) {
            perSuit[card.suit().ordinal()]++;
            int bit = 1 << card.rank().ordinal();
            paired |= (ranks & bit) != 0;
            ranks |= bit;
        }
        int maxSuit = 0;
        for (int count : perSuit) {
            maxSuit = Math.max(maxSuit, count);
        }
        int extended = ranks << 1 | ranks >>> 12; // ace also plays low
        int bestWindow = 0;
        for (int low = 0; low <= 9; low++) {
            bestWindow = Math.max(bestWindow, Integer.bitCount(extended >>> low & 0x1F));
        }
        boolean straightPossible = bestWindow >= 3;
        boolean connected = bestWindow >= 2;
        int flushScore = maxSuit >= 3 ? 3 : maxSuit == 2 && cards.size() < 5 ? 1 : 0;
        int straightScore = straightPossible ? 2 : connected ? 1 : 0;
        int wetness = Math.max(0, flushScore + straightScore - (paired ? 1 : 0));
        return new BoardTexture(paired, maxSuit, straightPossible, connected, cards.size(), wetness);
    }

    public boolean isDry() {
        return wetness <= 1;
    }

    public boolean isWet() {
        return wetness >= 3;
    }

    public String label() {
        return isDry() ? "dry" : isWet() ? "wet" : "semi-wet";
    }

    public String describe() {
        List<String> traits = new ArrayList<>();
        if (maxSuitCount >= 3) {
            traits.add("flush possible");
        } else if (maxSuitCount == 2 && cards < 5) {
            traits.add("flush draw possible");
        } else {
            traits.add("rainbow");
        }
        if (straightPossible) {
            traits.add("straight possible");
        } else if (connected) {
            traits.add("straight draws");
        }
        if (paired) {
            traits.add("paired");
        }
        return label() + " (" + String.join(", ", traits) + ")";
    }
}
