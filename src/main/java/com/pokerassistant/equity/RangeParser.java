package com.pokerassistant.equity;

import com.pokerassistant.cards.CardMask;
import com.pokerassistant.cards.Rank;
import com.pokerassistant.error.InvalidInputException;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the range notation used by most poker tools. Tokens are separated by commas and/or spaces:
 * <pre>
 *   AA   22+   99-66        a pair, a pair "and better", a span of pairs
 *   AKs  AKo   AK           suited, offsuit, both
 *   ATs+  KTo+              kicker raised up to one below the top card: ATs, AJs, AQs, AKs
 *   KTs-K7s                 span of kickers under the same top card
 *   AhKh                    one specific combo
 *   15%   top15%            the strongest 15% of starting hands (see StartingHandRanking)
 *   random | any | all      every combo
 * </pre>
 */
public final class RangeParser {

    private static final int MAX_LENGTH = 500;
    private static final Pattern PERCENT = Pattern.compile("(?i)(top)?(\\d{1,3}(?:\\.\\d{1,2})?)(%)?");
    private static final Pattern SPECIFIC_COMBO = Pattern.compile("(?i)[2-9TJQKA][CDHS][2-9TJQKA][CDHS]");
    private static final Pattern HAND = Pattern.compile("(?i)([2-9TJQKA])([2-9TJQKA])([SO]?)");

    private enum Suitedness { ANY, SUITED, OFFSUIT }

    private record HandPattern(Rank high, Rank low, Suitedness suitedness) {
        boolean isPair() {
            return high == low;
        }
    }

    private RangeParser() {
    }

    public static Range parse(String notation) {
        String text = notation == null ? "" : notation.trim();
        if (text.isEmpty()) {
            throw new InvalidInputException("The range is empty");
        }
        if (text.length() > MAX_LENGTH) {
            throw new InvalidInputException("Range notation is too long (max " + MAX_LENGTH + " characters)");
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.equals("random") || lower.equals("any") || lower.equals("all")) {
            return Range.all();
        }
        Set<Long> combos = new HashSet<>();
        StringBuilder canonical = new StringBuilder();
        for (String token : text.split("[,\\s]+")) {
            if (token.isEmpty()) {
                continue;
            }
            addToken(token, combos);
            canonical.append(canonical.isEmpty() ? "" : ",").append(token);
        }
        if (combos.isEmpty()) {
            throw new InvalidInputException("The range '" + text + "' contains no hands");
        }
        return Range.of(combos, canonical.toString());
    }

    private static void addToken(String token, Set<Long> out) {
        Matcher percent = PERCENT.matcher(token);
        if (percent.matches() && (percent.group(1) != null || percent.group(3) != null)) {
            double value = Double.parseDouble(percent.group(2));
            if (value <= 0 || value > 100) {
                throw new InvalidInputException("A percentage range must be above 0 and at most 100: '" + token + "'");
            }
            addAll(out, StartingHandRanking.top(value).combos());
            return;
        }
        if (SPECIFIC_COMBO.matcher(token).matches()) {
            out.add(CardMask.parseExactly(token, 2, "Combo '" + token + "'"));
            return;
        }
        int dash = token.indexOf('-');
        if (dash >= 0) {
            addSpan(token, token.substring(0, dash), token.substring(dash + 1), out);
        } else if (token.endsWith("+")) {
            addPlus(parseHand(token.substring(0, token.length() - 1), token), out);
        } else {
            addHand(parseHand(token, token), out);
        }
    }

    /** {@code QQ+} = QQ, KK, AA; {@code ATs+} = ATs, AJs, AQs, AKs. */
    private static void addPlus(HandPattern hand, Set<Long> out) {
        if (hand.isPair()) {
            for (int rank = hand.low().ordinal(); rank <= Rank.ACE.ordinal(); rank++) {
                addHand(pair(Rank.ofOrdinal(rank)), out);
            }
        } else {
            for (int kicker = hand.low().ordinal(); kicker < hand.high().ordinal(); kicker++) {
                addHand(new HandPattern(hand.high(), Rank.ofOrdinal(kicker), hand.suitedness()), out);
            }
        }
    }

    /** {@code 99-66} or {@code KTs-K7s}, in either order. */
    private static void addSpan(String token, String left, String right, Set<Long> out) {
        HandPattern from = parseHand(left, token);
        HandPattern to = parseHand(right, token);
        if (from.isPair() && to.isPair()) {
            int low = Math.min(from.high().ordinal(), to.high().ordinal());
            int high = Math.max(from.high().ordinal(), to.high().ordinal());
            for (int rank = low; rank <= high; rank++) {
                addHand(pair(Rank.ofOrdinal(rank)), out);
            }
            return;
        }
        if (from.isPair() || to.isPair() || from.high() != to.high() || from.suitedness() != to.suitedness()) {
            throw new InvalidInputException("Can't read span '" + token
                    + "': both ends need the same top card and suitedness, e.g. KTs-K7s or 99-66");
        }
        int low = Math.min(from.low().ordinal(), to.low().ordinal());
        int high = Math.max(from.low().ordinal(), to.low().ordinal());
        for (int kicker = low; kicker <= high; kicker++) {
            addHand(new HandPattern(from.high(), Rank.ofOrdinal(kicker), from.suitedness()), out);
        }
    }

    private static HandPattern parseHand(String text, String token) {
        Matcher matcher = HAND.matcher(text);
        if (!matcher.matches()) {
            throw new InvalidInputException("Can't read range token '" + token
                    + "' (examples: QQ+, AKs, ATo+, KTs-K7s, AhKh, 15%)");
        }
        Rank first = Rank.fromSymbol(matcher.group(1).charAt(0));
        Rank second = Rank.fromSymbol(matcher.group(2).charAt(0));
        Rank high = first.compareTo(second) >= 0 ? first : second;
        Rank low = first.compareTo(second) >= 0 ? second : first;
        String suffix = matcher.group(3).toLowerCase(Locale.ROOT);
        if (high == low && !suffix.isEmpty()) {
            throw new InvalidInputException("A pair can't be suited or offsuit: '" + token + "'");
        }
        Suitedness suitedness = switch (suffix) {
            case "s" -> Suitedness.SUITED;
            case "o" -> Suitedness.OFFSUIT;
            default -> Suitedness.ANY;
        };
        return new HandPattern(high, low, suitedness);
    }

    private static HandPattern pair(Rank rank) {
        return new HandPattern(rank, rank, Suitedness.ANY);
    }

    private static void addHand(HandPattern hand, Set<Long> out) {
        if (hand.isPair()) {
            addAll(out, new StartingHand(hand.high(), hand.low(), false).combos());
            return;
        }
        if (hand.suitedness() != Suitedness.OFFSUIT) {
            addAll(out, new StartingHand(hand.high(), hand.low(), true).combos());
        }
        if (hand.suitedness() != Suitedness.SUITED) {
            addAll(out, new StartingHand(hand.high(), hand.low(), false).combos());
        }
    }

    private static void addAll(Set<Long> out, long[] combos) {
        for (long combo : combos) {
            out.add(combo);
        }
    }
}
