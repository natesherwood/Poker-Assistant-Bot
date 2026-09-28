package com.pokerassistant.equity;

import com.pokerassistant.cards.CardMask;
import com.pokerassistant.error.InvalidInputException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.pokerassistant.equity.EquityResult.Method.EXACT;
import static com.pokerassistant.equity.EquityResult.Method.MONTE_CARLO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EquityCalculatorTest {

    private final EquityCalculator calculator = new EquityCalculator(3_000_000, 100_000, () -> 42L);

    private static long cards(String text) {
        return CardMask.of(CardMask.parseList(text));
    }

    private static EquityRequest request(String hero, String board, String... villains) {
        return new EquityRequest(cards(hero), List.of(villains).stream().map(Range::parse).toList(),
                board.isEmpty() ? 0L : cards(board), 0L);
    }

    @Test
    void acesAgainstKingsPreflopIsEnumeratedExactly() {
        EquityResult result = calculator.calculate(request("AsAh", "", "KcKd"));
        assertEquals(EXACT, result.method());
        assertEquals(1_712_304L, result.samples()); // C(48, 5) boards
        assertEquals(0.82, result.equity(), 0.02);
        assertEquals(0.0, result.standardError());
    }

    @Test
    void riverShowdownIsDecided() {
        // Nut flush against a set.
        EquityResult result = calculator.calculate(request("AhKh", "Qh7h2hJs3c", "7c7d"));
        assertEquals(1.0, result.equity(), 1e-12);
        assertEquals(1L, result.samples());
    }

    @Test
    void boardThatPlaysForBothIsASplit() {
        EquityResult result = calculator.calculate(request("2c3d", "AsKsQsJsTs", "2h3s"));
        assertEquals(0.5, result.equity(), 1e-12);
        assertEquals(1.0, result.tie(), 1e-12);
    }

    @Test
    void monteCarloAgreesWithExactEnumeration() {
        EquityRequest spot = request("AhKh", "Qh7h2c", "22+,AJs+,KQs");
        EquityResult exact = ExactEnumerator.compute(spot);
        EquityResult sampled = MonteCarloSimulator.run(spot, 400_000, 7L);
        assertEquals(exact.equity(), sampled.equity(), 4 * sampled.standardError());
    }

    @Test
    void monteCarloIsExactlyReproducibleForASeed() {
        EquityRequest spot = request("JhTh", "", "random", "random");
        EquityResult first = MonteCarloSimulator.run(spot, 50_000, 99L);
        EquityResult second = MonteCarloSimulator.run(spot, 50_000, 99L);
        assertEquals(first.equity(), second.equity());
        assertEquals(first.win(), second.win());
        assertEquals(first.tie(), second.tie());
        assertEquals(first.standardError(), second.standardError());
    }

    @Test
    void multiwayAcesAgainstTwoRandomHands() {
        EquityResult result = calculator.calculate(request("AsAh", "", "random", "random"));
        assertEquals(MONTE_CARLO, result.method());
        assertEquals(0.735, result.equity(), 0.015);
    }

    @Test
    void picksExactWhenAffordableAndSamplingOtherwise() {
        assertEquals(EXACT, calculator.calculate(request("AhKh", "Qh7h2c", "random")).method());   // 1081 x 990 showdowns
        assertEquals(MONTE_CARLO, calculator.calculate(request("AhKh", "", "random")).method());   // 1225 x 1.7M
        assertEquals(MONTE_CARLO, calculator.calculate(request("AhKh", "Qh7h2c", "random", "random")).method());
    }

    @Test
    void blockersRemoveVillainCombos() {
        // Hero holds two aces, so only AcAd remains of villain's AA.
        EquityResult result = ExactEnumerator.compute(request("AsAh", "", "AA"));
        assertEquals(1_712_304L, result.samples());
        assertEquals(0.5, result.equity(), 0.02);
    }

    @Test
    void impossibleRangesAreReported() {
        assertThrows(InvalidInputException.class, () -> calculator.calculate(request("AsAh", "", "AsKs")));
        assertThrows(InvalidInputException.class, () -> calculator.calculate(request("AsAh", "", "KsKh", "KsKh")));
    }

    @Test
    void requestsAreValidated() {
        assertThrows(InvalidInputException.class, () -> request("AsAhKd", "", "random"));
        assertThrows(InvalidInputException.class, () -> request("AsAh", "As7d2c", "random"));
        assertThrows(InvalidInputException.class, () -> request("AsAh", "Kd7d", "random"));
        assertThrows(InvalidInputException.class, () -> new EquityRequest(cards("AsAh"), List.of(), 0L, 0L));
    }

    @Test
    void outsOfAFlushDrawWithTwoOvercards() {
        OutsCalculator.Outs outs = OutsCalculator.count(cards("AhKh"), cards("Qh7h2c"), 0L);
        assertEquals(9, outs.strong()); // the nine remaining hearts
        assertEquals(15, outs.total()); // plus three aces and three kings
    }

    @Test
    void cardsThatOnlyPairTheBoardAreNotOuts() {
        OutsCalculator.Outs outs = OutsCalculator.count(cards("9c8d"), cards("Th7s2h"), 0L);
        assertEquals(8, outs.strong()); // any jack or six makes the straight
        assertEquals(14, outs.total()); // plus three nines and three eights; a ten, seven or deuce only pairs the board
        assertEquals(OutsCalculator.Outs.NONE, OutsCalculator.count(cards("9c8d"), cards("Th7s2h5d3c"), 0L));
    }
}
