package com.pokerassistant.game;

import com.pokerassistant.error.InvalidInputException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * An exact, non-negative chip amount stored in hundredths, so pot arithmetic never drifts the way
 * {@code double} sums do. The unit is whatever the table plays in: big blinds, dollars or chips.
 */
public record Chips(long cents) implements Comparable<Chips> {

    public static final Chips ZERO = new Chips(0);

    /** Largest accepted amount, one trillion units: beyond any real stack and far from overflow. */
    public static final Chips MAX = new Chips(100_000_000_000_000L);

    private static final Pattern DECIMAL = Pattern.compile("\\d{1,13}(\\.\\d{1,2})?|\\.\\d{1,2}");

    public Chips {
        if (cents < 0) {
            throw new IllegalArgumentException("Chip amounts cannot be negative: " + cents);
        }
    }

    public static Chips of(long units) {
        return new Chips(Math.multiplyExact(units, 100L));
    }

    public static Chips ofCents(long cents) {
        return new Chips(cents);
    }

    /** Converts a computed amount (a pot fraction, say), rounding to the nearest hundredth. */
    public static Chips fromDouble(double units) {
        if (!Double.isFinite(units) || units < 0) {
            throw new IllegalArgumentException("Not a valid chip amount: " + units);
        }
        return new Chips(Math.round(units * 100));
    }

    /**
     * Parses user input such as {@code 100}, {@code 2.5} or {@code .25}. Rejects signs, exponents,
     * separators, more than two decimals and absurdly large values.
     */
    public static Chips parse(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (!DECIMAL.matcher(trimmed).matches()) {
            throw new InvalidInputException("'" + trimmed + "' is not a valid amount (use a number with at most 2 decimals, e.g. 2.5)");
        }
        long cents = new BigDecimal(trimmed).movePointRight(2).longValueExact();
        if (cents > MAX.cents) {
            throw new InvalidInputException("Amount " + trimmed + " is too large");
        }
        return new Chips(cents);
    }

    public Chips plus(Chips other) {
        return new Chips(Math.addExact(cents, other.cents));
    }

    public Chips minus(Chips other) {
        if (other.cents > cents) {
            throw new IllegalArgumentException(this + " - " + other + " would be negative");
        }
        return new Chips(cents - other.cents);
    }

    /** {@code this * factor}, rounded to the nearest hundredth. */
    public Chips times(double factor) {
        return fromDouble(cents * factor / 100.0);
    }

    public Chips min(Chips other) {
        return compareTo(other) <= 0 ? this : other;
    }

    public Chips max(Chips other) {
        return compareTo(other) >= 0 ? this : other;
    }

    public boolean isZero() {
        return cents == 0;
    }

    public boolean isPositive() {
        return cents > 0;
    }

    public boolean isGreaterThan(Chips other) {
        return cents > other.cents;
    }

    public boolean isLessThan(Chips other) {
        return cents < other.cents;
    }

    public double toDouble() {
        return cents / 100.0;
    }

    /** Nearest multiple of {@code step}, halves rounding up; unchanged when {@code step} is zero. */
    public Chips roundTo(Chips step) {
        if (step.isZero()) {
            return this;
        }
        return new Chips((cents + step.cents / 2) / step.cents * step.cents);
    }

    /**
     * Splits into {@code parts} shares differing by at most one hundredth. The leftover hundredths go
     * to the first shares, so callers order winners by poker's odd-chip rule.
     */
    public List<Chips> split(int parts) {
        if (parts <= 0) {
            throw new IllegalArgumentException("parts must be positive");
        }
        long base = cents / parts;
        long extra = cents % parts;
        List<Chips> shares = new ArrayList<>(parts);
        for (int i = 0; i < parts; i++) {
            shares.add(new Chips(base + (i < extra ? 1 : 0)));
        }
        return shares;
    }

    @Override
    public int compareTo(Chips other) {
        return Long.compare(cents, other.cents);
    }

    /** {@code 100}, {@code 2.5}, {@code 0.25}: no trailing zeros. */
    @Override
    public String toString() {
        long whole = cents / 100;
        long fraction = cents % 100;
        if (fraction == 0) {
            return Long.toString(whole);
        }
        if (fraction % 10 == 0) {
            return whole + "." + fraction / 10;
        }
        return whole + "." + (fraction < 10 ? "0" : "") + fraction;
    }
}
