package com.pokerassistant.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds main and side pots from each player's total contribution, and splits pots between winners. */
final class PotCalculator {

    private PotCalculator() {
    }

    /**
     * Layers the pot at every distinct contribution level of the players still in the hand. Each layer
     * collects everyone's chips between the previous level and its own (folded players' chips are dead
     * money) and can be won by the live players who contributed at least that level.
     */
    static List<Pot> buildPots(List<PlayerState> players) {
        List<Chips> levels = players.stream()
                .filter(PlayerState::isInHand)
                .map(PlayerState::totalBet)
                .filter(Chips::isPositive)
                .distinct()
                .sorted()
                .toList();
        List<Pot> pots = new ArrayList<>();
        Chips previous = Chips.ZERO;
        for (Chips level : levels) {
            Chips amount = Chips.ZERO;
            for (PlayerState player : players) {
                amount = amount.plus(player.totalBet().min(level).minus(player.totalBet().min(previous)));
            }
            List<Integer> eligible = players.stream()
                    .filter(player -> player.isInHand() && !player.totalBet().isLessThan(level))
                    .map(PlayerState::seat)
                    .toList();
            pots.add(new Pot(amount, eligible));
            previous = level;
        }

        Chips leftover = Chips.ZERO;
        for (PlayerState player : players) {
            leftover = leftover.plus(player.totalBet().minus(player.totalBet().min(previous)));
        }
        if (leftover.isPositive()) {
            // Dead money above every live player's level: it goes to the top pot.
            if (pots.isEmpty()) {
                List<Integer> live = players.stream().filter(PlayerState::isInHand).map(PlayerState::seat).toList();
                pots.add(new Pot(leftover, live));
            } else {
                Pot top = pots.remove(pots.size() - 1);
                pots.add(new Pot(top.amount().plus(leftover), top.eligibleSeats()));
            }
        }
        return pots;
    }

    /** Splits {@code amount} evenly; {@code winners} come in odd-chip order (first seat left of the button first). */
    static Map<Integer, Chips> split(Chips amount, List<Integer> winners) {
        List<Chips> shares = amount.split(winners.size());
        Map<Integer, Chips> result = new LinkedHashMap<>();
        for (int i = 0; i < winners.size(); i++) {
            result.merge(winners.get(i), shares.get(i), Chips::plus);
        }
        return result;
    }
}
