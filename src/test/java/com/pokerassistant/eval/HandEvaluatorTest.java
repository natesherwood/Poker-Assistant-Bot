package com.pokerassistant.eval;

import com.pokerassistant.cards.CardMask;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.BitSet;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandEvaluatorTest {

    private static int eval(String cards) {
        return HandEvaluator.evaluate(CardMask.of(CardMask.parseList(cards)));
    }

    @ParameterizedTest
    @CsvSource({
            "AsKsQsJsTs, STRAIGHT_FLUSH",
            "5d4d3d2dAd, STRAIGHT_FLUSH",
            "9c9d9h9s2c, FOUR_OF_A_KIND",
            "KcKdKh2s2c, FULL_HOUSE",
            "Ah9h7h4h2h, FLUSH",
            "5c4d3h2sAc, STRAIGHT",
            "TcJdQhKsAc, STRAIGHT",
            "7c7d7hKs2c, THREE_OF_A_KIND",
            "7c7dKhKs2c, TWO_PAIR",
            "7c7dKhQs2c, ONE_PAIR",
            "AcJd9h5s3c, HIGH_CARD",
            "AsKsQsJsTs9s8s, STRAIGHT_FLUSH",
            "KcKdKhQsQcQd2s, FULL_HOUSE",
            "AcAdKhKsQcQd2s, TWO_PAIR",
            "2h3h4h5h9hAcKd, FLUSH",
            "9c9d9h9sAcAdAh, FOUR_OF_A_KIND",
            "6c5d4h3s2cAdKd, STRAIGHT"
    })
    void detectsCategories(String cards, HandCategory expected) {
        assertEquals(expected, HandEvaluator.category(eval(cards)));
    }

    @Test
    void ordersEveryCategoryBoundary() {
        String[] ascending = {
                "7c5d4h3s2c", "AcKdQhJs9c",   // high card
                "2c2d3h4s5c", "AcAdKhQsJc",   // one pair
                "2c2d3h3s4c", "AcAdKhKsQc",   // two pair
                "2c2d2h3s4c", "AcAdAhKsQc",   // trips
                "5c4d3h2sAc", "6c5d4h3s2c", "TcJdQhKsAc", // straights: the wheel is the lowest
                "7h5h4h3h2h", "AhKhQhJh9h",   // flush
                "2c2d2h3s3c", "AcAdAhKsKc",   // full house
                "2c2d2h2s3c", "AcAdAhAsKc",   // quads
                "5c4c3c2cAc", "TcJcQcKcAc"    // straight flush: steel wheel to royal
        };
        for (int i = 1; i < ascending.length; i++) {
            assertTrue(eval(ascending[i - 1]) < eval(ascending[i]), ascending[i - 1] + " should lose to " + ascending[i]);
        }
    }

    @Test
    void kickersBreakTiesAndUnusedCardsDoNot() {
        assertTrue(eval("AcAdKh9s2c") > eval("AcAdQh9s2c"));
        assertTrue(eval("KcKd8h8sAc") > eval("KcKd8h8s3c"));
        assertTrue(eval("AcAdAhKsQc") > eval("AcAdAhKsJc"));
        assertEquals(eval("AhKd7c5s2h"), eval("AcKh7d5c2s"));
        assertEquals(eval("AcAdKhQsJc"), eval("AcAdKhQsJc3d2h"));
        assertEquals(eval("AhKhQhJh9h"), eval("AhKhQhJh9h3h2h"));
    }

    @Test
    void describesHandsInPlainEnglish() {
        assertEquals("Royal flush", HandEvaluator.describe(eval("AsKsQsJsTs")));
        assertEquals("Full house, Kings full of Twos", HandEvaluator.describe(eval("KcKdKh2s2c")));
        assertEquals("Straight, Five high", HandEvaluator.describe(eval("5c4d3h2sAc")));
        assertEquals("Two pair, Aces and Nines", HandEvaluator.describe(eval("AcAd9h9s2c")));
        assertEquals("Pair of Queens", HandEvaluator.describe(eval("QcQd9h7s2c")));
        assertEquals("Ace high", HandEvaluator.describe(eval("AcJd9h5s3c")));
    }

    @Test
    void rejectsWrongCardCounts() {
        assertThrows(IllegalArgumentException.class, () -> eval("AcKd"));
        assertThrows(IllegalArgumentException.class, () -> eval("AcKdQh2s3s4s5s6s"));
    }

    /** Every one of the C(52,5) = 2,598,960 hands: category counts and distinct values match the known totals. */
    @Test
    void allFiveCardHandsMatchKnownFrequencies() {
        long[] deck = CardMask.remaining(0L);
        long[] counts = new long[HandCategory.values().length];
        BitSet distinct = new BitSet(1 << 24);
        for (int a = 0; a < 48; a++) {
            for (int b = a + 1; b < 49; b++) {
                for (int c = b + 1; c < 50; c++) {
                    for (int d = c + 1; d < 51; d++) {
                        for (int e = d + 1; e < 52; e++) {
                            int value = HandEvaluator.evaluate(deck[a] | deck[b] | deck[c] | deck[d] | deck[e]);
                            counts[HandEvaluator.category(value).ordinal()]++;
                            distinct.set(value);
                        }
                    }
                }
            }
        }
        assertArrayEquals(new long[] {1_302_540, 1_098_240, 123_552, 54_912, 10_200, 5_108, 3_744, 624, 40}, counts);
        assertEquals(7_462, distinct.cardinality());
    }

    /** Every one of the C(52,7) = 133,784,560 seven-card hands, in parallel. Excluded from the default Maven run. */
    @Test
    @Tag("slow")
    void allSevenCardHandsMatchKnownFrequencies() {
        long[] deck = CardMask.remaining(0L);
        long[] counts = IntStream.range(0, 46).parallel().mapToObj(a -> {
            long[] local = new long[HandCategory.values().length];
            for (int b = a + 1; b < 47; b++) {
                for (int c = b + 1; c < 48; c++) {
                    for (int d = c + 1; d < 49; d++) {
                        for (int e = d + 1; e < 50; e++) {
                            long five = deck[a] | deck[b] | deck[c] | deck[d] | deck[e];
                            for (int f = e + 1; f < 51; f++) {
                                for (int g = f + 1; g < 52; g++) {
                                    local[HandEvaluator.category(HandEvaluator.evaluate(five | deck[f] | deck[g])).ordinal()]++;
                                }
                            }
                        }
                    }
                }
            }
            return local;
        }).reduce(new long[HandCategory.values().length], (x, y) -> {
            long[] sum = new long[x.length];
            for (int i = 0; i < x.length; i++) {
                sum[i] = x[i] + y[i];
            }
            return sum;
        });
        assertArrayEquals(new long[] {23_294_460, 58_627_800, 31_433_400, 6_461_620, 6_180_020, 4_047_644, 3_473_184, 224_848, 41_584}, counts);
    }
}
