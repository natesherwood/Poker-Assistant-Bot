package com.pokerassistant.strategy;

import com.pokerassistant.equity.Range;
import com.pokerassistant.equity.StartingHandRanking;
import com.pokerassistant.game.HandEvent;
import com.pokerassistant.game.HandStateMachine;
import org.junit.jupiter.api.Test;

import static com.pokerassistant.game.TestHands.act;
import static com.pokerassistant.game.TestHands.board;
import static com.pokerassistant.game.TestHands.cards;
import static com.pokerassistant.game.TestHands.start;
import static com.pokerassistant.game.TestHands.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangeEstimatorTest {

    private final RangeEstimator estimator = new RangeEstimator();

    private static HandStateMachine sixMax() {
        return start(table(1, 1, 100, 100, 100, 100, 100, 100));
    }

    @Test
    void anOpenMapsToThatPositionsOpeningRange() {
        HandStateMachine hand = sixMax();
        act(hand, "r2.5"); // UTG
        RangeEstimator.Estimate estimate = estimator.estimate(hand.context(), 4);
        assertEquals(0.17, estimate.range().fraction(), 0.01);
        assertTrue(estimate.basis().contains("opened from UTG"), estimate.basis());
    }

    @Test
    void reraisesAreTighterAndFlatCallsLeaveOutPremiums() {
        HandStateMachine hand = sixMax();
        act(hand, "r2.5", "r8", "f", "f", "f", "c"); // UTG opens, HJ 3-bets, the big blind cold-calls
        Range threeBet = estimator.estimate(hand.context(), 5).range();
        assertEquals(0.07, threeBet.fraction(), 0.01);
        Range coldCall = estimator.estimate(hand.context(), 3).range();
        assertFalse(coldCall.contains(cards("AsAh")), "aces would 4-bet, not call");
        assertTrue(coldCall.contains(cards("JsJh")));
    }

    @Test
    void userRangesOverrideEstimatesAndShownCardsOverrideBoth() {
        HandStateMachine hand = sixMax();
        act(hand, "r2.5");
        estimator.override(4, Range.parse("AA"));
        assertEquals(6, estimator.estimate(hand.context(), 4).range().size());
        hand.fire(new HandEvent.CardsShown(4, cards("KsKd")));
        RangeEstimator.Estimate shown = estimator.estimate(hand.context(), 4);
        assertEquals(1, shown.range().size());
        assertEquals("cards shown", shown.basis());
        estimator.clearOverride(4);
        assertTrue(estimator.overrideFor(4).isEmpty());
    }

    @Test
    void bigPostflopBetsKeepTheStrongerPartOfTheRange() {
        HandStateMachine hand = start(table(1, 2, 100, 100)); // heads-up, hero in the big blind
        act(hand, "r2.5", "c");
        hand.fire(board("As 7d 2c"));
        act(hand, "x", "b5"); // a pot-sized c-bet keeps about 45% of the range
        RangeEstimator.Estimate estimate = estimator.estimate(hand.context(), 1);
        Range preflop = StartingHandRanking.top(80);
        long compatible = preflop.compatibleCount(cards("As 7d 2c"));
        assertTrue(estimate.range().size() < 0.55 * compatible, estimate.range() + " vs " + compatible);
        assertTrue(estimate.range().contains(cards("AhKd")), "top pair stays in the betting range");
        assertTrue(preflop.contains(cards("9c8d")));
        assertFalse(estimate.range().contains(cards("9c8d")), "nine-high with no draw is gone");
        assertTrue(estimate.basis().contains("bet 100% pot on the flop"), estimate.basis());
    }
}
