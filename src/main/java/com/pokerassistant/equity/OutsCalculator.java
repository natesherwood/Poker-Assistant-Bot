package com.pokerassistant.equity;

import com.pokerassistant.cards.Card;
import com.pokerassistant.cards.CardMask;
import com.pokerassistant.eval.HandCategory;
import com.pokerassistant.eval.HandEvaluator;

/**
 * Counts outs: unseen cards that lift hero's hand category above both hero's current category and
 * whatever the board makes on its own (so a card that merely pairs the board is not an out).
 */
public final class OutsCalculator {

    /**
     * @param total  every improving card
     * @param strong improving cards that make a straight or better (the classic draw count: 9 for a
     *               flush draw, 8 for an open-ender)
     * @param cards  mask of the improving cards
     */
    public record Outs(int total, int strong, long cards) {
        public static final Outs NONE = new Outs(0, 0, 0);
    }

    private OutsCalculator() {
    }

    /** Outs for the next card; only meaningful on the flop or turn, otherwise {@link Outs#NONE}. */
    public static Outs count(long hole, long board, long dead) {
        int boardCards = Long.bitCount(board);
        if (Long.bitCount(hole) != 2 || (boardCards != 3 && boardCards != 4)) {
            return Outs.NONE;
        }
        HandCategory current = HandEvaluator.category(HandEvaluator.evaluate(hole | board));
        int total = 0;
        int strong = 0;
        long cards = 0;
        for (long card : CardMask.remaining(hole | board | dead)) {
            long nextBoard = board | card;
            HandCategory improved = HandEvaluator.category(HandEvaluator.evaluate(hole | nextBoard));
            if (improved.compareTo(current) > 0 && improved.compareTo(boardCategory(nextBoard)) > 0) {
                total++;
                cards |= card;
                if (improved.compareTo(HandCategory.STRAIGHT) >= 0) {
                    strong++;
                }
            }
        }
        return new Outs(total, strong, cards);
    }

    /** The category the board makes by itself (4 or 5 cards). */
    static HandCategory boardCategory(long board) {
        if (Long.bitCount(board) >= 5) {
            return HandEvaluator.category(HandEvaluator.evaluate(board));
        }
        int[] perRank = new int[13];
        for (Card card : CardMask.toList(board)) {
            perRank[card.rank().ordinal()]++;
        }
        int pairs = 0;
        int most = 0;
        for (int count : perRank) {
            most = Math.max(most, count);
            if (count == 2) {
                pairs++;
            }
        }
        if (most == 4) {
            return HandCategory.FOUR_OF_A_KIND;
        }
        if (most == 3) {
            return HandCategory.THREE_OF_A_KIND;
        }
        return pairs == 2 ? HandCategory.TWO_PAIR : pairs == 1 ? HandCategory.ONE_PAIR : HandCategory.HIGH_CARD;
    }
}
