package com.pokerassistant.game;

import com.pokerassistant.error.InvalidInputException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Immutable table setup carried from hand to hand: seats and stacks, blinds, ante, the button and
 * hero's seat. Seats are numbered clockwise; a seat with no chips sits out.
 */
public record TableConfig(List<TableConfig.Seat> seats, Chips smallBlind, Chips bigBlind, Chips ante, int buttonSeat, int heroSeat) {

    public static final int MIN_SEATS = 2;
    public static final int MAX_SEATS = 10;

    /** A numbered seat and the chips in front of it. */
    public record Seat(int number, Chips stack) {
        public Seat {
            if (number < 1 || number > MAX_SEATS) {
                throw new InvalidInputException("Seat numbers run from 1 to " + MAX_SEATS + ", got " + number);
            }
            Objects.requireNonNull(stack, "stack");
        }

        public Seat withStack(Chips newStack) {
            return new Seat(number, newStack);
        }
    }

    public TableConfig {
        seats = List.copyOf(seats);
        Objects.requireNonNull(smallBlind, "smallBlind");
        Objects.requireNonNull(bigBlind, "bigBlind");
        Objects.requireNonNull(ante, "ante");
        if (seats.size() < MIN_SEATS || seats.size() > MAX_SEATS) {
            throw new InvalidInputException("A table needs %d to %d seats, got %d".formatted(MIN_SEATS, MAX_SEATS, seats.size()));
        }
        for (int i = 1; i < seats.size(); i++) {
            if (seats.get(i).number() <= seats.get(i - 1).number()) {
                throw new InvalidInputException("Seat numbers must be unique and listed clockwise");
            }
        }
        if (!bigBlind.isPositive()) {
            throw new InvalidInputException("The big blind must be greater than zero");
        }
        if (!smallBlind.isPositive() || smallBlind.isGreaterThan(bigBlind)) {
            throw new InvalidInputException("The small blind must be greater than zero and no larger than the big blind");
        }
        if (ante.isGreaterThan(bigBlind)) {
            throw new InvalidInputException("The ante cannot be larger than the big blind");
        }
        requireSeat(seats, buttonSeat, "Button");
        requireSeat(seats, heroSeat, "Hero");
    }

    /** Seats {@code 1..players}, all with the same stack. */
    public static TableConfig uniform(int players, Chips stack, Chips smallBlind, Chips bigBlind, Chips ante, int buttonSeat, int heroSeat) {
        if (players < MIN_SEATS || players > MAX_SEATS) {
            throw new InvalidInputException("Players must be between %d and %d, got %d".formatted(MIN_SEATS, MAX_SEATS, players));
        }
        if (!stack.isPositive()) {
            throw new InvalidInputException("Starting stacks must be greater than zero");
        }
        List<Seat> seats = IntStream.rangeClosed(1, players).mapToObj(number -> new Seat(number, stack)).toList();
        return new TableConfig(seats, smallBlind, bigBlind, ante, buttonSeat, heroSeat);
    }

    /** Six-handed, 100 big blinds deep, blinds 0.5/1, hero in seat 1 holding the button. */
    public static TableConfig defaults() {
        return uniform(6, Chips.of(100), Chips.ofCents(50), Chips.of(1), Chips.ZERO, 1, 1);
    }

    public Seat seat(int number) {
        return seats.stream().filter(seat -> seat.number() == number).findFirst()
                .orElseThrow(() -> new InvalidInputException("There is no seat %d (seats are %d-%d)".formatted(
                        number, seats.get(0).number(), seats.get(seats.size() - 1).number())));
    }

    public Chips stackOf(int seatNumber) {
        return seat(seatNumber).stack();
    }

    public int playersWithChips() {
        return (int) seats.stream().filter(seat -> seat.stack().isPositive()).count();
    }

    public TableConfig withStack(int seatNumber, Chips stack) {
        seat(seatNumber);
        List<Seat> updated = seats.stream().map(seat -> seat.number() == seatNumber ? seat.withStack(stack) : seat).toList();
        return new TableConfig(updated, smallBlind, bigBlind, ante, buttonSeat, heroSeat);
    }

    public TableConfig withStacks(Map<Integer, Chips> stacks) {
        List<Seat> updated = seats.stream().map(seat -> seat.withStack(stacks.getOrDefault(seat.number(), seat.stack()))).toList();
        return new TableConfig(updated, smallBlind, bigBlind, ante, buttonSeat, heroSeat);
    }

    /** Moves the button; the new button seat must have chips. */
    public TableConfig withButton(int seatNumber) {
        if (stackOf(seatNumber).isZero()) {
            throw new InvalidInputException("Seat " + seatNumber + " has no chips and can't take the button");
        }
        return new TableConfig(seats, smallBlind, bigBlind, ante, seatNumber, heroSeat);
    }

    /** The table after a hand: new stacks, button passed clockwise to the next seat with chips. */
    public TableConfig afterHand(Map<Integer, Chips> finalStacks) {
        TableConfig updated = withStacks(finalStacks);
        return new TableConfig(updated.seats, smallBlind, bigBlind, ante, updated.nextButton(), heroSeat);
    }

    /** First seat clockwise from the button that has chips (the button itself if no other seat does). */
    public int nextButton() {
        int index = indexOf(buttonSeat);
        for (int step = 1; step <= seats.size(); step++) {
            Seat seat = seats.get((index + step) % seats.size());
            if (seat.stack().isPositive()) {
                return seat.number();
            }
        }
        return buttonSeat;
    }

    private int indexOf(int seatNumber) {
        for (int i = 0; i < seats.size(); i++) {
            if (seats.get(i).number() == seatNumber) {
                return i;
            }
        }
        throw new InvalidInputException("There is no seat " + seatNumber);
    }

    private static void requireSeat(List<Seat> seats, int number, String role) {
        if (seats.stream().noneMatch(seat -> seat.number() == number)) {
            throw new InvalidInputException("%s seat %d does not exist (seats are %d-%d)".formatted(
                    role, number, seats.get(0).number(), seats.get(seats.size() - 1).number()));
        }
    }
}
