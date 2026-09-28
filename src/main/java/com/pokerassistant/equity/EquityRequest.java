package com.pokerassistant.equity;

import com.pokerassistant.cards.CardMask;
import com.pokerassistant.error.InvalidInputException;

import java.util.List;

/**
 * Hero's two cards against one or more opponent ranges on a partial board. {@code dead} holds cards
 * known to be out of play (e.g. folded hands that were shown). All card sets are card masks.
 */
public record EquityRequest(long hero, List<Range> villains, long board, long dead) {

    public static final int MAX_OPPONENTS = 9;

    public EquityRequest {
        villains = List.copyOf(villains);
        if (!CardMask.isValid(hero) || Long.bitCount(hero) != 2) {
            throw new InvalidInputException("Hero needs exactly two hole cards");
        }
        int boardCards = Long.bitCount(board);
        if (!CardMask.isValid(board) || boardCards == 1 || boardCards == 2 || boardCards > 5) {
            throw new InvalidInputException("The board must have 0, 3, 4 or 5 cards, got " + boardCards);
        }
        if ((hero & board) != 0) {
            throw new InvalidInputException("Hero's cards overlap the board: " + CardMask.format(hero & board));
        }
        if (!CardMask.isValid(dead) || (dead & (hero | board)) != 0) {
            throw new InvalidInputException("Dead cards must be real cards that are not in hero's hand or on the board");
        }
        if (villains.isEmpty() || villains.size() > MAX_OPPONENTS) {
            throw new InvalidInputException("Equity needs 1 to %d opponents, got %d".formatted(MAX_OPPONENTS, villains.size()));
        }
    }

    /** Every card whose location is known. */
    public long knownCards() {
        return hero | board | dead;
    }

    public int missingBoardCards() {
        return 5 - Long.bitCount(board);
    }
}
