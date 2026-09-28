package com.pokerassistant.strategy;

import com.pokerassistant.equity.EquityCalculator;
import com.pokerassistant.equity.Range;
import com.pokerassistant.equity.StartingHand;
import com.pokerassistant.equity.StartingHandRanking;
import com.pokerassistant.error.IllegalActionException;
import com.pokerassistant.game.ActionType;
import com.pokerassistant.game.Chips;
import com.pokerassistant.game.HandStateMachine;
import com.pokerassistant.game.TableConfig;
import com.pokerassistant.game.TestHands;
import org.junit.jupiter.api.Test;

import static com.pokerassistant.game.TestHands.act;
import static com.pokerassistant.game.TestHands.board;
import static com.pokerassistant.game.TestHands.start;
import static com.pokerassistant.game.TestHands.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionEngineTest {

    private final RangeEstimator ranges = new RangeEstimator();
    private final DecisionEngine engine = new DecisionEngine(new EquityCalculator(3_000_000, 40_000, () -> 7L), ranges);

    private static TableConfig sixMax(int button, int hero) {
        return table(button, hero, 100, 100, 100, 100, 100, 100);
    }

    @Test
    void opensPremiumsUnderTheGunAndFoldsJunk() {
        Recommendation open = engine.recommend(start(sixMax(1, 4), "AsAh").context()); // seat 4 is UTG
        assertEquals(ActionType.RAISE, open.action());
        assertEquals(Chips.ofCents(250), open.amount());
        assertEquals("r 2.5", open.command());
        assertFalse(open.reasons().isEmpty());
        assertTrue(open.stats().stream().anyMatch(stat -> stat.label().equals("Equity")));

        assertEquals(ActionType.FOLD, engine.recommend(start(sixMax(1, 4), "7c2d").context()).action());
    }

    @Test
    void theButtonOpensWiderThanUnderTheGun() {
        double percentile = StartingHandRanking.percentile(StartingHand.of(TestHands.cards("Kc9d")));
        assertTrue(percentile > 17 && percentile < 43, "K9o should sit between the UTG and button ranges, was " + percentile);
        assertEquals(ActionType.FOLD, engine.recommend(start(sixMax(1, 4), "Kc9d").context()).action());

        HandStateMachine button = start(sixMax(1, 1), "Kc9d");
        act(button, "f", "f", "f"); // UTG, HJ and CO fold to hero on the button
        assertEquals(ActionType.RAISE, engine.recommend(button.context()).action());
    }

    @Test
    void theBigBlindDefendsWideButNotWithEverything() {
        HandStateMachine suited = start(sixMax(1, 3), "Kh7h");
        act(suited, "f", "f", "f", "r2.5", "f"); // button opens, small blind folds
        assertEquals(ActionType.CALL, engine.recommend(suited.context()).action());

        HandStateMachine junk = start(sixMax(1, 3), "7c2d");
        act(junk, "f", "f", "f", "r2.5", "f");
        assertEquals(ActionType.FOLD, engine.recommend(junk.context()).action());
    }

    @Test
    void premiumsThreeBetForValue() {
        HandStateMachine hand = start(sixMax(1, 3), "KsKd");
        act(hand, "f", "f", "f", "r2.5", "f");
        Recommendation threeBet = engine.recommend(hand.context());
        assertEquals(ActionType.RAISE, threeBet.action());
        assertEquals(Chips.of(10), threeBet.amount(), "4x the open out of position");
    }

    @Test
    void valueBetsTheNutsWhenCheckedTo() {
        HandStateMachine hand = start(table(1, 1, 100, 100), "AhKh");
        act(hand, "r3", "c");
        hand.fire(board("Qh 7h 2c"));
        act(hand, "x", "x");
        hand.fire(board("5s"));
        act(hand, "x", "x");
        hand.fire(board("9h"));
        act(hand, "x");
        Recommendation bet = engine.recommend(hand.context());
        assertEquals(ActionType.BET, bet.action(), bet.headline());
        assertEquals(Chips.ofCents(450), bet.amount(), "75% of a 6 pot with the nuts on the river");
    }

    @Test
    void foldsAirToABigRiverBet() {
        HandStateMachine hand = start(table(1, 1, 100, 100), "4c3d");
        act(hand, "c", "x");
        hand.fire(board("Qh 7h 2c"));
        act(hand, "x", "x");
        hand.fire(board("Ks"));
        act(hand, "x", "x");
        hand.fire(board("9h"));
        act(hand, "b2");
        ranges.override(2, Range.parse("QQ+,AK"));
        Recommendation fold = engine.recommend(hand.context());
        assertEquals(ActionType.FOLD, fold.action(), fold.headline());
    }

    @Test
    void onlyAdvisesHeroOnHerosTurnWithKnownCards() {
        HandStateMachine notHerosTurn = start(sixMax(1, 1), "AsAh"); // UTG acts first, hero is on the button
        assertThrows(IllegalActionException.class, () -> engine.recommend(notHerosTurn.context()));
        HandStateMachine noCards = start(sixMax(1, 4));
        assertThrows(IllegalActionException.class, () -> engine.recommend(noCards.context()));
    }
}
