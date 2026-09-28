package com.pokerassistant.equity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orders the 169 starting hands by all-in equity against one random hand: the ordering behind the
 * "top X%" ranges of most equity tools. The table is computed once per JVM with this project's own
 * Monte Carlo engine (fixed seed, so the order is reproducible) and then cached.
 */
public final class StartingHandRanking {

    /** A ranked hand; {@code percentile} is the midpoint of its slice of the 1326 combos, in percent. */
    public record Entry(StartingHand hand, double equity, double percentile) {
    }

    private record Ranking(List<Entry> entries, Map<StartingHand, Entry> byHand) {
    }

    private static final int TRIALS_PER_HAND = 30_000;
    private static final long SEED = 20_260_928L;

    private static volatile Ranking ranking;

    private StartingHandRanking() {
    }

    /** All 169 hands, strongest first. */
    public static List<Entry> entries() {
        return ranking().entries();
    }

    /** Where {@code hand} sits in the ranking, as "top X%". */
    public static double percentile(StartingHand hand) {
        return ranking().byHand().get(hand).percentile();
    }

    /** The strongest {@code percent}% of starting hands. */
    public static Range top(double percent) {
        return between(0, percent);
    }

    /** Hands ranked in the band {@code (fromPercent, toPercent]}: "top 25% without the top 5%". */
    public static Range between(double fromPercent, double toPercent) {
        if (!(fromPercent >= 0 && fromPercent < toPercent && toPercent <= 100)) {
            throw new IllegalArgumentException("Invalid percentile band %s-%s".formatted(fromPercent, toPercent));
        }
        List<Long> combos = new ArrayList<>();
        for (Entry entry : entries()) {
            if (entry.percentile() > fromPercent && entry.percentile() <= toPercent) {
                for (long combo : entry.hand().combos()) {
                    combos.add(combo);
                }
            }
        }
        String description = fromPercent == 0
                ? "top " + percent(toPercent)
                : "top " + percent(toPercent) + " minus top " + percent(fromPercent);
        return Range.of(combos, description);
    }

    /** Starts building the table on a daemon thread so the first lookup does not wait. */
    public static void warmUpInBackground() {
        Thread thread = new Thread(StartingHandRanking::ranking, "starting-hand-ranking");
        thread.setDaemon(true);
        thread.start();
    }

    private static Ranking ranking() {
        Ranking current = ranking;
        if (current == null) {
            synchronized (StartingHandRanking.class) {
                current = ranking;
                if (current == null) {
                    current = compute();
                    ranking = current;
                }
            }
        }
        return current;
    }

    private static Ranking compute() {
        List<StartingHand> hands = StartingHand.all();
        Map<StartingHand, Double> equity = new HashMap<>();
        for (int i = 0; i < hands.size(); i++) {
            StartingHand hand = hands.get(i);
            EquityRequest request = new EquityRequest(hand.combos()[0], List.of(Range.all()), 0L, 0L);
            equity.put(hand, MonteCarloSimulator.run(request, TRIALS_PER_HAND, SEED + i).equity());
        }
        List<StartingHand> ordered = new ArrayList<>(hands);
        ordered.sort(Comparator.comparingDouble((StartingHand hand) -> equity.get(hand)).reversed()
                .thenComparing(StartingHand::name));

        List<Entry> entries = new ArrayList<>(ordered.size());
        Map<StartingHand, Entry> byHand = new HashMap<>();
        int combosBefore = 0;
        for (StartingHand hand : ordered) {
            double midpoint = (combosBefore + hand.comboCount() / 2.0) * 100.0 / Range.TOTAL_COMBOS;
            Entry entry = new Entry(hand, equity.get(hand), midpoint);
            entries.add(entry);
            byHand.put(hand, entry);
            combosBefore += hand.comboCount();
        }
        return new Ranking(List.copyOf(entries), Map.copyOf(byHand));
    }

    private static String percent(double value) {
        return (value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value)) + "%";
    }
}
