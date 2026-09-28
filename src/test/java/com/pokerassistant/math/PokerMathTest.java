package com.pokerassistant.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PokerMathTest {

    private static final double EPS = 1e-12;

    @Test
    void potOddsForAHalfPotBet() {
        // Pot 10, villain bets 5: the pot is now 15 and a call of 5 wins 20.
        assertEquals(0.25, PokerMath.requiredEquity(15, 5), EPS);
        assertEquals(3.0, PokerMath.potOddsRatio(15, 5), EPS);
        assertEquals(0.0, PokerMath.requiredEquity(15, 0), EPS);
    }

    @Test
    void minimumDefenseFrequencyAndAlpha() {
        assertEquals(2.0 / 3, PokerMath.minimumDefenseFrequency(10, 5), EPS);
        assertEquals(1.0 / 3, PokerMath.bluffBreakEven(10, 5), EPS);
        assertEquals(0.5, PokerMath.minimumDefenseFrequency(10, 10), EPS);
        assertEquals(1.0 / 3, PokerMath.balancedBluffShare(10, 10), EPS); // pot-sized bet: 1 bluff per 2 value bets
    }

    @Test
    void callEvIsZeroExactlyAtThePotOddsPrice() {
        assertEquals(0.0, PokerMath.callEv(0.25, 15, 5), EPS);
        assertEquals(5.0, PokerMath.callEv(0.5, 15, 5), EPS);
        assertEquals(-5.0, PokerMath.callEv(0.0, 15, 5), EPS);
    }

    @Test
    void impliedOddsNeededToJustifyACall() {
        // 20% equity, pot 15, call 5: 5 / 0.2 - 15 - 5 = 5 more must be won later.
        assertEquals(5.0, PokerMath.impliedOddsNeeded(0.2, 15, 5), 1e-9);
        assertEquals(0.0, PokerMath.impliedOddsNeeded(0.5, 15, 5), EPS);
        assertEquals(Double.POSITIVE_INFINITY, PokerMath.impliedOddsNeeded(0.0, 15, 5));
    }

    @Test
    void stackToPotRatio() {
        assertEquals(4.0, PokerMath.stackToPotRatio(40, 10), EPS);
    }

    @Test
    void drawOddsExactVersusRuleOfThumb() {
        // Nine outs with two cards to come from 47 unseen: 1 - C(38,2)/C(47,2) = 34.97%.
        assertEquals(0.3497, Combinatorics.hitProbability(9, 47, 2), 1e-4);
        assertEquals(9.0 / 46, Combinatorics.hitProbability(9, 46, 1), EPS);
        assertEquals(0.36, PokerMath.ruleOfTwoAndFour(9, 2), EPS);
        assertEquals(0.18, PokerMath.ruleOfTwoAndFour(9, 1), EPS);
    }

    @Test
    void binomialCoefficients() {
        assertEquals(2_598_960L, Combinatorics.choose(52, 5));
        assertEquals(133_784_560L, Combinatorics.choose(52, 7));
        assertEquals(1_712_304L, Combinatorics.choose(48, 5));
        assertEquals(1L, Combinatorics.choose(5, 0));
        assertEquals(0L, Combinatorics.choose(3, 5));
        assertEquals(Combinatorics.choose(52, 47), Combinatorics.choose(52, 5));
    }

    @Test
    void rejectsNonsense() {
        assertThrows(IllegalArgumentException.class, () -> PokerMath.requiredEquity(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> PokerMath.callEv(1.5, 10, 5));
        assertThrows(IllegalArgumentException.class, () -> PokerMath.potOddsRatio(Double.NaN, 5));
        assertThrows(IllegalArgumentException.class, () -> Combinatorics.hitProbability(10, 5, 1));
        assertThrows(IllegalArgumentException.class, () -> Combinatorics.choose(-1, 2));
    }
}
