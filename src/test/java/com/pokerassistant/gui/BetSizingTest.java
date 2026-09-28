package com.pokerassistant.gui;

import com.pokerassistant.game.Chips;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BetSizingTest {

    private static final Chips MIN = Chips.of(2);
    private static final Chips MAX = Chips.of(100);

    @Test
    void potSizedBetIsThePot() {
        assertEquals(Chips.of(10), BetSizing.potFraction(1.0, Chips.of(10), Chips.ZERO, Chips.ZERO, MIN, MAX));
        assertEquals(Chips.of(5), BetSizing.potFraction(0.5, Chips.of(10), Chips.ZERO, Chips.ZERO, MIN, MAX));
    }

    @Test
    void potSizedRaiseCallsThenAddsThePot() {
        // Preflop, blinds 0.5/1, first to act: pot 1.5, call 1, pot-size raise to 1 + (1.5 + 1) = 3.5.
        assertEquals(Chips.parse("3.5"), BetSizing.potFraction(1.0, Chips.parse("1.5"), Chips.of(1), Chips.of(1), MIN, MAX));
        // Facing a 5 bet into 10: pot 15, call 5, pot-size raise to 5 + 20 = 25.
        assertEquals(Chips.of(25), BetSizing.potFraction(1.0, Chips.of(15), Chips.of(5), Chips.of(5), MIN, MAX));
    }

    @Test
    void presetsAreClampedToTheLegalRange() {
        assertEquals(MIN, BetSizing.potFraction(0.33, Chips.of(1), Chips.ZERO, Chips.ZERO, MIN, MAX));
        assertEquals(MAX, BetSizing.potFraction(1.0, Chips.of(500), Chips.ZERO, Chips.ZERO, MIN, MAX));
    }

    @Test
    void sliderNotchesCoverTheRangeAndEndOnTheAllIn() {
        Chips step = BetSizing.step(Chips.of(1));
        assertEquals(Chips.ofCents(10), step);
        Chips min = Chips.parse("2.5");
        Chips max = Chips.parse("97.35");
        int last = BetSizing.notches(min, max, step);
        assertEquals(min, BetSizing.atNotch(0, min, max, step));
        assertEquals(max, BetSizing.atNotch(last, min, max, step));
        assertEquals(last, BetSizing.nearestNotch(max, min, max, step));
        assertEquals(Chips.of(10), BetSizing.atNotch(BetSizing.nearestNotch(Chips.of(10), min, max, step), min, max, step));
    }

    @Test
    void stepNeverDropsBelowOneHundredth() {
        assertEquals(Chips.ofCents(1), BetSizing.step(Chips.ofCents(5)));
    }
}
