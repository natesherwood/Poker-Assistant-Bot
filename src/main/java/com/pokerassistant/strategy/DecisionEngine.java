package com.pokerassistant.strategy;

import com.pokerassistant.cards.Rank;
import com.pokerassistant.equity.EquityCalculator;
import com.pokerassistant.equity.EquityRequest;
import com.pokerassistant.equity.EquityResult;
import com.pokerassistant.equity.OutsCalculator;
import com.pokerassistant.equity.Range;
import com.pokerassistant.equity.StartingHand;
import com.pokerassistant.equity.StartingHandRanking;
import com.pokerassistant.error.IllegalActionException;
import com.pokerassistant.eval.HandCategory;
import com.pokerassistant.eval.HandEvaluator;
import com.pokerassistant.game.ActionRecord;
import com.pokerassistant.game.ActionType;
import com.pokerassistant.game.Chips;
import com.pokerassistant.game.HandContext;
import com.pokerassistant.game.LegalActions;
import com.pokerassistant.game.PlayerState;
import com.pokerassistant.game.Street;
import com.pokerassistant.math.Combinatorics;
import com.pokerassistant.math.PokerMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Recommends hero's action from game-theory building blocks rather than a full solver:
 * <ul>
 *   <li><b>Preflop</b>: solver-derived open-raise frequencies by position; against a raise, equity
 *       versus the raiser's estimated range compared with the pot odds after discounting for how much
 *       equity hero can realize in or out of position; value re-raises above fixed equity thresholds
 *       and blocker-based 3-bet bluffs as a mixed-strategy option.</li>
 *   <li><b>Postflop</b>: equity versus sizing-narrowed ranges against pot odds, with minimum defense
 *       frequency and bluff break-even shown for context; value thresholds scaled by the number of
 *       opponents; texture-based bet sizing; implied odds for strong draws; small range c-bets as the
 *       preflop raiser on dry boards; balanced bluff ratios on the river.</li>
 * </ul>
 * Every threshold is a named constant below so the model is easy to tune or replace with solver data.
 */
public final class DecisionEngine {

    /** Preflop: re-raise for value facing 1, 2 or 3+ raises once equity vs the raiser reaches these. */
    private static final double[] PREFLOP_RERAISE_EQUITY = {0.58, 0.55, 0.50};
    private static final double PREFLOP_REALIZATION_IN_POSITION = 0.95;
    private static final double PREFLOP_REALIZATION_OUT_OF_POSITION = 0.75;
    /** Each player still to act behind a preflop caller may squeeze or wake up with a hand. */
    private static final double SQUEEZE_DISCOUNT_PER_PLAYER_BEHIND = 0.95;
    /** Raising over limpers takes a tighter range than an open. */
    private static final double LIMPED_POT_TIGHTENING = 0.6;
    private static final double BIG_BLIND_RAISE_OVER_LIMPS_PERCENT = 12;

    /** Postflop equity realization discounts. */
    private static final double REALIZATION_OUT_OF_POSITION = 0.85;
    private static final double MULTIWAY_REALIZATION = 0.9;

    /**
     * Postflop thresholds on strength = equity / fair share, where the fair share is 1 / players in the
     * pot. Heads-up, 1.25 means 62.5% equity; three-way, 1.25 means about 42%.
     */
    private static final double VALUE_BET_STRENGTH = 1.25;
    private static final double RIVER_VALUE_BET_STRENGTH = 1.4;
    private static final double VALUE_RAISE_STRENGTH = 1.44;
    private static final double RIVER_VALUE_RAISE_STRENGTH = 1.7;
    private static final double SHOWDOWN_VALUE_STRENGTH = 0.9;

    private static final int STRONG_DRAW_OUTS = 8;
    /** Implied odds count only if the money needed later is at most this share of what is left behind. */
    private static final double MAX_IMPLIED_SHARE_OF_STACK = 0.5;
    /** A bet or raise that would put in this share of the stack becomes an all-in. */
    private static final double COMMIT_FRACTION = 0.85;

    private final EquityCalculator equityCalculator;
    private final RangeEstimator rangeEstimator;

    public DecisionEngine(EquityCalculator equityCalculator, RangeEstimator rangeEstimator) {
        this.equityCalculator = Objects.requireNonNull(equityCalculator, "equityCalculator");
        this.rangeEstimator = Objects.requireNonNull(rangeEstimator, "rangeEstimator");
    }

    public Recommendation recommend(HandContext hand) {
        PlayerState hero = hand.hero()
                .orElseThrow(() -> new IllegalActionException("Hero (seat " + hand.heroSeat() + ") is not dealt into this hand"));
        if (!hero.hasKnownCards()) {
            throw new IllegalActionException("Advice needs hero's hole cards; enter them with 'hole <cards>', e.g. 'hole AhKd'");
        }
        LegalActions legal = hand.legalActions()
                .filter(actions -> actions.seat() == hero.seat())
                .orElseThrow(() -> new IllegalActionException("Advice is available when the action is on hero (seat " + hero.seat() + ")"));
        // Preflop, only players who have entered the pot are opponents; those still to act mostly fold
        // and are accounted for through equity realization instead. Postflop everyone left is in.
        List<Opponent> opponents = hand.livePlayers().stream()
                .filter(player -> player.seat() != hero.seat())
                .filter(player -> hand.street() != Street.PREFLOP || hand.hasActedVoluntarily(player.seat()))
                .map(player -> new Opponent(player, rangeEstimator.estimate(hand, player.seat())))
                .toList();
        List<Range> ranges = opponents.isEmpty()
                ? List.of(Range.all())
                : opponents.stream().map(opponent -> opponent.estimate().range()).toList();
        EquityResult equity = equityCalculator.calculate(new EquityRequest(hero.holeCards(), ranges, hand.boardMask(), 0L));
        Spot spot = new Spot(hand, hero, legal, opponents, equity);

        Draft draft = new Draft();
        String against = opponents.isEmpty()
                ? "1 random hand (reference: nobody has entered the pot)"
                : opponents.size() + " opponent" + (opponents.size() == 1 ? "" : "s");
        draft.stat("Equity", "%s vs %s  [%s]".formatted(percent(equity.equity()), against, equity.describeMethod()));
        for (Opponent opponent : opponents) {
            Range range = opponent.estimate().range();
            draft.stat("Seat " + opponent.player().seat() + " " + opponent.player().position(),
                    "%s, %d combos (%s)".formatted(range.description(), range.size(), opponent.estimate().basis()));
        }
        draft.stat("Pot", "%s | to call %s | SPR %.1f".formatted(spot.pot(), legal.toCall(), spot.spr()));
        return hand.street() == Street.PREFLOP ? preflop(spot, draft) : postflop(spot, draft);
    }

    // ------------------------------------------------------------------ preflop

    private Recommendation preflop(Spot spot, Draft draft) {
        StartingHand holding = StartingHand.of(spot.hero().holeCards());
        double percentile = StartingHandRanking.percentile(holding);
        draft.stat("Hand", "%s, top %.1f%% of starting hands".formatted(holding.name(), percentile));
        int raises = spot.hand().streetAggression();
        return raises == 0 ? unopenedPot(spot, draft, holding, percentile) : facingRaise(spot, draft, holding, raises);
    }

    private Recommendation unopenedPot(Spot spot, Draft draft, StartingHand holding, double percentile) {
        HandContext hand = spot.hand();
        LegalActions legal = spot.legal();
        Chips bigBlind = hand.bigBlind();
        int limpers = (int) hand.actions().stream()
                .filter(action -> action.street() == Street.PREFLOP && action.type() == ActionType.CALL)
                .count();
        boolean headsUp = hand.players().size() == 2;

        if (legal.canCheck()) { // the big blind's option after limps
            if (percentile <= BIG_BLIND_RAISE_OVER_LIMPS_PERCENT && legal.canRaise()) {
                draft.reason("Raise the limp%s for value: %s is a top %.0f%% hand".formatted(
                        limpers == 1 ? "" : "s", holding.name(), BIG_BLIND_RAISE_OVER_LIMPS_PERCENT));
                return aggress(spot, draft, bigBlind.times(3 + limpers), (3 + limpers) + " bb");
            }
            draft.reason("Check the option: see a free flop with a wide range");
            return draft.finish(ActionType.CHECK, null, "CHECK");
        }

        int behind = hand.opponentsYetToAct(spot.hero().seat());
        double open = PreflopCharts.openRaisePercent(behind, headsUp);
        double threshold = limpers > 0 ? open * LIMPED_POT_TIGHTENING : open;
        String where = "%s with %d player%s left to act".formatted(spot.hero().position(), behind, behind == 1 ? "" : "s");
        if (percentile <= threshold && legal.canRaise()) {
            boolean smallBlind = spot.hero().seat() == hand.smallBlindSeat() && !headsUp;
            double sizeInBigBlinds = (smallBlind ? 3.0 : 2.5) + limpers;
            draft.reason(limpers > 0
                    ? "Isolate the limper%s: %s is inside the tightened top %.0f%% from %s".formatted(limpers == 1 ? "" : "s", holding.name(), threshold, where)
                    : "Open-raise: %s is inside the ~%.0f%% solver opening range from %s".formatted(holding.name(), threshold, where));
            return aggress(spot, draft, bigBlind.times(sizeInBigBlinds), bigBlinds(sizeInBigBlinds));
        }
        draft.reason("Fold: %s (top %.0f%%) is outside the ~%.0f%% %s range from %s".formatted(
                holding.name(), percentile, threshold, limpers > 0 ? "isolation" : "opening", where));
        return draft.finish(ActionType.FOLD, null, "FOLD");
    }

    private Recommendation facingRaise(Spot spot, Draft draft, StartingHand holding, int raises) {
        HandContext hand = spot.hand();
        LegalActions legal = spot.legal();
        double equity = spot.equity().equity();
        double pot = spot.pot().toDouble();
        double toCall = legal.toCall().toDouble();
        double required = PokerMath.requiredEquity(pot, toCall);
        boolean inPosition = spot.inPosition();
        int behind = hand.opponentsYetToAct(spot.hero().seat());
        double realization = (inPosition ? PREFLOP_REALIZATION_IN_POSITION : PREFLOP_REALIZATION_OUT_OF_POSITION)
                * Math.pow(SQUEEZE_DISCOUNT_PER_PLAYER_BEHIND, behind);
        draft.stat("Pot odds", "need %s to call %s (%.1f : 1)".formatted(percent(required), legal.toCall(), PokerMath.potOddsRatio(pot, toCall)));

        double reraiseEquity = PREFLOP_RERAISE_EQUITY[Math.min(raises, 3) - 1];
        if (equity >= reraiseEquity && legal.canRaise()) {
            Chips currentBet = hand.currentBet();
            int callers = callersSinceLastRaise(hand);
            Chips target = switch (raises) {
                case 1 -> currentBet.times(inPosition ? 3 : 4).plus(currentBet.times(callers));
                case 2 -> currentBet.times(inPosition ? 2.2 : 2.5);
                default -> legal.maxTotal();
            };
            String name = raises == 1 ? "3-bet" : raises == 2 ? "4-bet" : "Jam";
            draft.reason("%s for value: %s equity vs the raiser's range (threshold %s)".formatted(name, percent(equity), percent(reraiseEquity)));
            if (raises == 1) {
                String sizing = inPosition ? "In position: 3x the open" : "Out of position: 4x the open";
                draft.reason(callers > 0 ? sizing + ", plus 1x per caller" : sizing);
            }
            return aggress(spot, draft, target, null);
        }
        if (raises == 1 && legal.canRaise() && isThreeBetBluffCandidate(holding)) {
            draft.alternative(holding.high() == Rank.ACE
                    ? "Mix in a 3-bet bluff about a third of the time: the ace blocks their strongest hands and the wheel draw plays well"
                    : "Mix in a 3-bet bluff about a third of the time: suited and connected, it plays well when called");
        }
        double realized = equity * realization;
        String facing = raises == 1 ? "the open" : raises == 2 ? "the 3-bet" : "the " + (raises + 1) + "-bet";
        String realizationNote = (inPosition ? "in position" : "out of position")
                + (behind > 0 ? ", %d player%s still to act".formatted(behind, behind == 1 ? "" : "s") : "");
        if (realized >= required) {
            draft.reason("Call: %s equity x %.2f realization (%s) = %s, above the %s the price requires".formatted(
                    percent(equity), realization, realizationNote, percent(realized), percent(required)));
            return draft.finish(ActionType.CALL, null, "CALL " + legal.toCall());
        }
        draft.reason("Fold to %s: %s equity x %.2f realization (%s) = %s, below the %s the price requires".formatted(
                facing, percent(equity), realization, realizationNote, percent(realized), percent(required)));
        return draft.finish(ActionType.FOLD, null, "FOLD");
    }

    // ------------------------------------------------------------------ postflop

    private Recommendation postflop(Spot spot, Draft draft) {
        HandContext hand = spot.hand();
        long hole = spot.hero().holeCards();
        int made = HandEvaluator.evaluate(hole | hand.boardMask());
        OutsCalculator.Outs outs = OutsCalculator.count(hole, hand.boardMask(), 0L);
        BoardTexture texture = BoardTexture.of(hand.boardMask());
        double strength = spot.equity().equity() * (spot.opponents().size() + 1);

        draft.stat("Made hand", HandEvaluator.describe(made));
        draft.stat("Board", texture.describe());
        if (outs.total() > 0) {
            int unseen = 52 - 2 - hand.board().size();
            int toCome = 5 - hand.board().size();
            draft.stat("Outs", outs.strong() > 0
                    ? "%d to a straight or better (%s by the river), %d improving cards in all".formatted(
                            outs.strong(), percent(Combinatorics.hitProbability(outs.strong(), unseen, toCome)), outs.total())
                    : "%d improving cards (%s to improve by the river)".formatted(
                            outs.total(), percent(Combinatorics.hitProbability(outs.total(), unseen, toCome))));
        }
        boolean strongDraw = hand.street() != Street.RIVER
                && outs.strong() >= STRONG_DRAW_OUTS
                && HandEvaluator.category(made).compareTo(HandCategory.ONE_PAIR) <= 0;
        return spot.legal().canCall()
                ? facingBet(spot, draft, strength, strongDraw, outs)
                : checkedTo(spot, draft, strength, strongDraw, texture, outs);
    }

    private Recommendation facingBet(Spot spot, Draft draft, double strength, boolean strongDraw, OutsCalculator.Outs outs) {
        HandContext hand = spot.hand();
        LegalActions legal = spot.legal();
        boolean river = hand.street() == Street.RIVER;
        double equity = spot.equity().equity();
        double pot = spot.pot().toDouble();
        double toCall = legal.toCall().toDouble();
        double required = PokerMath.requiredEquity(pot, toCall);
        draft.stat("Pot odds", "need %s (%.1f : 1); EV of calling now %+.2f".formatted(
                percent(required), PokerMath.potOddsRatio(pot, toCall), PokerMath.callEv(equity, pot, toCall)));
        lastAggression(hand).ifPresent(bet -> {
            double before = bet.potBefore().toDouble();
            double size = bet.added().toDouble();
            draft.stat("Their bet", "%.0f%% pot: MDF %s; a pure bluff needs %s folds".formatted(
                    100 * bet.sizeFractionOfPot(), percent(PokerMath.minimumDefenseFrequency(before, size)),
                    percent(PokerMath.bluffBreakEven(before, size))));
        });

        double raiseStrength = river ? RIVER_VALUE_RAISE_STRENGTH : VALUE_RAISE_STRENGTH;
        if (strength >= raiseStrength && legal.canRaise()) {
            draft.reason("Raise for value: %s equity is far ahead of their betting range".formatted(percent(equity)));
            return aggress(spot, draft, hand.currentBet().times(3), "3x");
        }
        double realization = river ? 1.0
                : (spot.inPosition() ? 1.0 : REALIZATION_OUT_OF_POSITION) * (spot.opponents().size() > 1 ? MULTIWAY_REALIZATION : 1.0);
        double realized = equity * realization;
        if (realized >= required) {
            draft.reason(realization < 1
                    ? "Call: %s equity (%s after realization) beats the %s the price requires".formatted(percent(equity), percent(realized), percent(required))
                    : "Call: %s equity beats the %s the price requires".formatted(percent(equity), percent(required)));
            if (strongDraw && legal.canRaise() && spot.opponents().size() == 1) {
                draft.alternative("Semi-bluff raise: fold equity plus %d outs".formatted(outs.strong()));
            }
            return draft.finish(ActionType.CALL, null, "CALL " + legal.toCall());
        }
        if (strongDraw) {
            double implied = PokerMath.impliedOddsNeeded(equity, pot, toCall);
            double behind = spot.effectiveStack().toDouble() - toCall;
            if (implied <= MAX_IMPLIED_SHARE_OF_STACK * behind) {
                draft.reason("Call on implied odds: %d outs; you must win about %.1f more later and %.1f is behind".formatted(
                        outs.strong(), implied, behind));
                return draft.finish(ActionType.CALL, null, "CALL " + legal.toCall());
            }
        }
        draft.reason("Fold: %s equity is below the %s the price requires".formatted(percent(equity), percent(required)));
        draft.reason("MDF is a whole-range target: defend with the hands that have the most equity, and this one falls short");
        return draft.finish(ActionType.FOLD, null, "FOLD");
    }

    private Recommendation checkedTo(Spot spot, Draft draft, double strength, boolean strongDraw, BoardTexture texture, OutsCalculator.Outs outs) {
        HandContext hand = spot.hand();
        LegalActions legal = spot.legal();
        Street street = hand.street();
        boolean river = street == Street.RIVER;
        Chips pot = spot.pot();
        if (!legal.canBet() && !legal.canRaise()) {
            draft.reason("Check: nobody is left who could call a bet");
            return draft.finish(ActionType.CHECK, null, "CHECK");
        }
        double valueStrength = river ? RIVER_VALUE_BET_STRENGTH : VALUE_BET_STRENGTH;
        if (strength >= valueStrength) {
            double fraction = river
                    ? (strength >= RIVER_VALUE_RAISE_STRENGTH ? 0.75 : 0.5)
                    : texture.isWet() ? 0.75 : texture.isDry() ? 0.33 : 0.5;
            draft.reason("Bet for value: %s equity vs their range".formatted(percent(spot.equity().equity())));
            draft.reason(river
                    ? "River sizing: bigger with the strongest hands"
                    : "Sizing follows the texture: %s board, %.0f%% pot".formatted(texture.label(), fraction * 100));
            return aggress(spot, draft, pot.times(fraction), "%.0f%% pot".formatted(fraction * 100));
        }
        if (strongDraw) {
            draft.reason("Semi-bluff: %d outs to a straight or better, plus fold equity".formatted(outs.strong()));
            draft.alternative("Checking is fine too; solvers mix strong draws between betting and checking");
            return aggress(spot, draft, pot.times(0.5), "50% pot");
        }
        boolean preflopRaiser = lastPreflopRaiser(hand) == spot.hero().seat();
        if (street == Street.FLOP && spot.opponents().size() == 1 && preflopRaiser && texture.isDry()) {
            draft.reason("Range c-bet: as the preflop raiser you have the range advantage on a dry board, where solvers bet small very often");
            draft.alternative("Check back now and then to protect your checking range");
            return aggress(spot, draft, pot.times(0.33), "33% pot");
        }
        if (strength >= SHOWDOWN_VALUE_STRENGTH) {
            draft.reason("Check: medium strength with showdown value; keep the pot small");
            return draft.finish(ActionType.CHECK, null, "CHECK");
        }
        if (river) {
            double share = PokerMath.balancedBluffShare(pot.toDouble(), pot.toDouble() * 0.75);
            draft.reason("Check: no showdown value; bluff only with hands that block their calls");
            draft.alternative("A balanced 75%% pot bet holds about %s bluffs; pick the ones with the best blockers".formatted(percent(share)));
            return draft.finish(ActionType.CHECK, null, "CHECK");
        }
        draft.reason("Check: too weak to bet for value and no strong draw");
        return draft.finish(ActionType.CHECK, null, "CHECK");
    }

    // ------------------------------------------------------------------ helpers

    /** A bet or raise to {@code target}, rounded to a tenth of a big blind and clamped to the legal range. */
    private static Recommendation aggress(Spot spot, Draft draft, Chips target, String sizeNote) {
        LegalActions legal = spot.legal();
        Chips step = Chips.ofCents(Math.max(1, spot.hand().bigBlind().cents() / 10));
        Chips amount = target.roundTo(step).max(legal.minTotal()).min(legal.maxTotal());
        if (amount.toDouble() >= COMMIT_FRACTION * legal.maxTotal().toDouble()) {
            draft.reason("That is most of the stack: just move all-in");
            return draft.finish(ActionType.ALL_IN, legal.maxTotal(), "ALL-IN " + legal.maxTotal());
        }
        ActionType type = legal.canBet() ? ActionType.BET : ActionType.RAISE;
        String headline = (type == ActionType.BET ? "BET " : "RAISE to ") + amount + (sizeNote == null ? "" : " (" + sizeNote + ")");
        return draft.finish(type, amount, headline);
    }

    /** Suited wheel aces (blockers) and suited connectors or one- and two-gappers from 65s up. */
    private static boolean isThreeBetBluffCandidate(StartingHand holding) {
        if (!holding.suited()) {
            return false;
        }
        if (holding.high() == Rank.ACE) {
            return holding.low().compareTo(Rank.FIVE) <= 0;
        }
        return holding.high().ordinal() - holding.low().ordinal() <= 2 && holding.low().compareTo(Rank.FIVE) >= 0;
    }

    private static int callersSinceLastRaise(HandContext hand) {
        int callers = 0;
        for (ActionRecord action : hand.actions()) {
            if (action.street() != Street.PREFLOP) {
                continue;
            }
            if (action.type().isAggressive()) {
                callers = 0;
            } else if (action.type() == ActionType.CALL) {
                callers++;
            }
        }
        return callers;
    }

    private static Optional<ActionRecord> lastAggression(HandContext hand) {
        ActionRecord last = null;
        for (ActionRecord action : hand.actions()) {
            if (action.street() == hand.street() && action.type().isAggressive()) {
                last = action;
            }
        }
        return Optional.ofNullable(last);
    }

    private static int lastPreflopRaiser(HandContext hand) {
        int seat = -1;
        for (ActionRecord action : hand.actions()) {
            if (action.street() == Street.PREFLOP && action.type().isAggressive()) {
                seat = action.seat();
            }
        }
        return seat;
    }

    private static String percent(double fraction) {
        return "%.1f%%".formatted(fraction * 100);
    }

    private static String bigBlinds(double amount) {
        return (amount == Math.rint(amount) ? String.valueOf((long) amount) : String.valueOf(amount)) + " bb";
    }

    private record Opponent(PlayerState player, RangeEstimator.Estimate estimate) {
    }

    private record Spot(HandContext hand, PlayerState hero, LegalActions legal, List<Opponent> opponents, EquityResult equity) {

        Chips pot() {
            return hand.pot();
        }

        /** Hero acts after every opponent contesting the pot. */
        boolean inPosition() {
            int heroOrder = hand.postflopActingOrder(hero.seat());
            return opponents.stream().allMatch(opponent -> hand.postflopActingOrder(opponent.player().seat()) < heroOrder);
        }

        /** Chips that can still change hands between hero and the deepest player still in the hand. */
        Chips effectiveStack() {
            Chips deepest = Chips.ZERO;
            for (PlayerState player : hand.livePlayers()) {
                if (player != hero) {
                    deepest = deepest.max(player.stack());
                }
            }
            return hero.stack().min(deepest);
        }

        double spr() {
            return PokerMath.stackToPotRatio(effectiveStack().toDouble(), pot().toDouble());
        }
    }

    /** Collects stats, reasons and an optional alternative while a recommendation is being built. */
    private static final class Draft {

        private final List<Recommendation.Stat> stats = new ArrayList<>();
        private final List<String> reasons = new ArrayList<>();
        private String alternative;

        void stat(String label, String value) {
            stats.add(new Recommendation.Stat(label, value));
        }

        void reason(String reason) {
            reasons.add(reason);
        }

        void alternative(String text) {
            alternative = text;
        }

        Recommendation finish(ActionType action, Chips amount, String headline) {
            return new Recommendation(action, amount, headline, reasons, stats, alternative);
        }
    }
}
