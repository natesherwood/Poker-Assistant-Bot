package com.pokerassistant.game;

import java.util.List;
import java.util.Objects;

/** A main or side pot and the seats that can win it: players still in the hand who covered it. */
public record Pot(Chips amount, List<Integer> eligibleSeats) {

    public Pot {
        Objects.requireNonNull(amount, "amount");
        eligibleSeats = List.copyOf(eligibleSeats);
    }
}
