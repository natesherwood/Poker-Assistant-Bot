package com.pokerassistant.strategy;

import com.pokerassistant.cards.CardMask;
import com.pokerassistant.cards.Rank;
import com.pokerassistant.equity.OutsCalculator;
import com.pokerassistant.equity.Range;
import com.pokerassistant.equity.StartingHandRanking;
import com.pokerassistant.error.InvalidInputException;
import com.pokerassistant.eval.HandCategory;
import com.pokerassistant.eval.HandEvaluator;
import com.pokerassistant.game.ActionRecord;
import com.pokerassistant.game.ActionType;
import com.pokerassistant.game.HandContext;
import com.pokerassistant.game.PlayerState;
import com.pokerassistant.game.Street;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Estimates an opponent's range from the actions seen so far in the hand.
 *
 * <p><b>Preflop</b>, each action maps to a band of the starting-hand ranking: an open from the cutoff
 * is about the top 27%, a 3-bet the top 7%, a flat call a middle band without the 3-betting hands,
 * and so on. The bands of all of a player's actions are intersected.
 *
 * <p><b>Postflop</b>, every bet, raise or call keeps only the strongest share of the remaining combos
 * on that street's board, with strong draws counted as medium-strength made hands. Bigger bets keep
 * less, following bet-sizing theory; checks do not narrow.
 *
 * <p>A range the user sets for a seat overrides the estimate, and cards a player has shown override
 * everything.
 */
public final class RangeEstimator {

    public record Estimate(Range range, String basis) {
    }

    private record Band(double from, double to, String reason) {
    }

    private record Scored(long combo, int score) {
    }

    /** Strong draws (8+ outs to a straight or better) rank like a pair of tens; weaker draws like a pair of fours. */
    private static final int STRONG_DRAW_SCORE = HandEvaluator.floorValue(HandCategory.ONE_PAIR, Rank.TEN);
    private static final int WEAK_DRAW_SCORE = HandEvaluator.floorValue(HandCategory.ONE_PAIR, Rank.FOUR);

    private final Map<Integer, Range> overrides = new HashMap<>();

    public void override(int seat, Range range) {
        overrides.put(seat, range);
    }

    public void clearOverride(int seat) {
        overrides.remove(seat);
    }

    public void clearOverrides() {
        overrides.clear();
    }

    public Optional<Range> overrideFor(int seat) {
        return Optional.ofNullable(overrides.get(seat));
    }

    public Estimate estimate(HandContext hand, int seat) {
        PlayerState player = hand.player(seat)
                .orElseThrow(() -> new InvalidInputException("Seat " + seat + " is not dealt into this hand"));
        if (player.hasKnownCards()) {
            return new Estimate(Range.of(new long[] {player.holeCards()}, CardMask.format(player.holeCards())), "cards shown");
        }
        Range override = overrides.get(seat);
        if (override != null) {
            return new Estimate(override, "set by you");
        }

        Band band = preflopBand(hand, seat);
        Range preflop = band.from() == 0 && band.to() >= 100 ? Range.all() : StartingHandRanking.between(band.from(), band.to());
        long[] combos = preflop.compatibleCombos(hand.knownCards());
        StringBuilder basis = new StringBuilder(band.reason());
        String description = preflop.description();

        for (Street street : List.of(Street.FLOP, Street.TURN, Street.RIVER)) {
            if (street.ordinal() > hand.street().ordinal()) {
                break;
            }
            ActionRecord tightest = null;
            for (ActionRecord action : hand.actions()) {
                if (action.street() == street && action.seat() == seat
                        && (tightest == null || keepFraction(action) < keepFraction(tightest))) {
                    tightest = action;
                }
            }
            if (tightest != null && keepFraction(tightest) < 1) {
                long boardThen = CardMask.of(hand.board().subList(0, street.boardCards()));
                combos = keepStrongest(combos, boardThen, keepFraction(tightest));
                basis.append("; ").append(verb(tightest)).append(" on the ").append(street.label().toLowerCase(Locale.ROOT))
                        .append(" (keeps the top ").append(Math.round(keepFraction(tightest) * 100)).append("%)");
                description = "narrowed " + preflop.description();
            }
        }
        return new Estimate(Range.of(combos, description), basis.toString());
    }

    private static Band preflopBand(HandContext hand, int seat) {
        boolean headsUp = hand.players().size() == 2;
        double from = 0;
        double to = 100;
        List<String> reasons = new ArrayList<>();
        Band last = null;
        for (ActionRecord action : hand.actions()) {
            if (action.street() != Street.PREFLOP || action.seat() != seat) {
                continue;
            }
            Band band = bandFor(action, hand, headsUp);
            if (band != null) {
                from = Math.max(from, band.from());
                to = Math.min(to, band.to());
                reasons.add(band.reason());
                last = band;
            }
        }
        if (last == null) {
            return new Band(0, 100, "no voluntary preflop action, so any two cards");
        }
        return from < to ? new Band(from, to, String.join(", then ", reasons)) : last;
    }

    private static Band bandFor(ActionRecord action, HandContext hand, boolean headsUp) {
        int level = action.aggressionBefore();
        if (action.type().isAggressive()) {
            return switch (level) {
                case 0 -> {
                    double open = PreflopCharts.openRaisePercent(action.playersYetToAct(), headsUp);
                    yield new Band(0, open, "opened from " + action.position() + " (top " + Math.round(open) + "%)");
                }
                case 1 -> new Band(0, 7, "3-bet (top 7%)");
                case 2 -> new Band(0, 3, "4-bet (top 3%)");
                default -> new Band(0, 1.5, "5-bet or more (top 1.5%)");
            };
        }
        if (action.type() == ActionType.CALL) {
            boolean bigBlind = action.seat() == hand.bigBlindSeat();
            boolean smallBlind = action.seat() == hand.smallBlindSeat() && !headsUp;
            return switch (level) {
                case 0 -> new Band(5, 50, "limped");
                case 1 -> bigBlind ? new Band(6, headsUp ? 80 : 60, "defended the big blind")
                        : smallBlind ? new Band(6, 25, "called from the small blind")
                        : new Band(6, 22, "flat-called a raise");
                case 2 -> new Band(1.5, 14, "called a 3-bet");  // QQ+ would usually 4-bet; JJ-TT often flat
                default -> new Band(0.7, 5, "called a 4-bet");
            };
        }
        if (action.type() == ActionType.CHECK) {
            return new Band(8, 100, "checked the big blind (no raise)");
        }
        return null;
    }

    /** Share of the range still consistent with an action, from bet-sizing theory (bigger = stronger). */
    private static double keepFraction(ActionRecord action) {
        return switch (action.type()) {
            case RAISE -> 0.35;
            case BET -> action.sizeFractionOfPot() <= 0.4 ? 0.70 : action.sizeFractionOfPot() <= 0.8 ? 0.55 : 0.45;
            case CALL -> 0.75;
            default -> 1.0;
        };
    }

    private static String verb(ActionRecord action) {
        return switch (action.type()) {
            case RAISE -> "raised";
            case BET -> "bet " + Math.round(action.sizeFractionOfPot() * 100) + "% pot";
            case CALL -> "called";
            default -> action.type().name().toLowerCase(Locale.ROOT);
        };
    }

    /** The strongest {@code keep} share of {@code combos} on {@code board}; ties at the cut are all kept. */
    static long[] keepStrongest(long[] combos, long board, double keep) {
        if (combos.length == 0) {
            return combos;
        }
        Scored[] scored = Arrays.stream(combos)
                .mapToObj(combo -> new Scored(combo, strength(combo, board)))
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .toArray(Scored[]::new);
        int kept = Math.max(1, (int) Math.ceil(scored.length * keep));
        int cutoff = scored[kept - 1].score();
        while (kept < scored.length && scored[kept].score() == cutoff) {
            kept++;
        }
        return Arrays.stream(scored, 0, kept).mapToLong(Scored::combo).toArray();
    }

    private static int strength(long combo, long board) {
        int made = HandEvaluator.evaluate(combo | board);
        if (Long.bitCount(board) < 5 && HandEvaluator.category(made).compareTo(HandCategory.ONE_PAIR) <= 0) {
            int strongOuts = OutsCalculator.count(combo, board, 0L).strong();
            if (strongOuts >= 8) {
                return Math.max(made, STRONG_DRAW_SCORE);
            }
            if (strongOuts >= 4) {
                return Math.max(made, WEAK_DRAW_SCORE);
            }
        }
        return made;
    }
}
