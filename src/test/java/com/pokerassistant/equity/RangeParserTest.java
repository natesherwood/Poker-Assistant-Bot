package com.pokerassistant.equity;

import com.pokerassistant.cards.CardMask;
import com.pokerassistant.error.InvalidInputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangeParserTest {

    private static long combo(String cards) {
        return CardMask.of(CardMask.parseList(cards));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "AA          | 6",
            "AKs         | 4",
            "AKo         | 12",
            "AK          | 16",
            "KA          | 16",
            "aks         | 4",
            "QQ+         | 18",
            "22+         | 78",
            "99-66       | 24",
            "66-99       | 24",
            "ATs+        | 16",
            "KTs-K7s     | 16",
            "K7s-KTs     | 16",
            "A2+         | 192",
            "AhKh        | 1",
            "QQ+, AKs    | 22",
            "QQ+ AKs AKs | 22",
            "random      | 1326",
            "any         | 1326"
    })
    void countsCombos(String notation, int combos) {
        assertEquals(combos, Range.parse(notation).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "AAs", "AKx", "ZZ", "KTs-Q7s", "KTs-K7o", "99-AK", "AhAh", "150%", "0%", "AK+-", "15"})
    void rejectsBadNotation(String notation) {
        assertThrows(InvalidInputException.class, () -> Range.parse(notation));
    }

    @Test
    void membershipAndCardRemoval() {
        Range range = Range.parse("AA,KK");
        assertTrue(range.contains(combo("AsAh")));
        assertFalse(range.contains(combo("AsKh")));
        // Holding the ace of spades blocks the three AA combos that contain it.
        assertEquals(9, range.compatibleCount(combo("As")));
        assertEquals(9, range.compatibleCombos(combo("As")).length);
    }

    @Test
    void percentRangesFollowTheStartingHandRanking() {
        Range top10 = Range.parse("10%");
        assertTrue(top10.contains(combo("AsAh")));
        assertFalse(top10.contains(combo("7c2d")));
        assertEquals(top10.size(), Range.parse("top10%").size());
        assertEquals(top10.size(), Range.parse("top10").size());
        assertTrue(top10.fraction() > 0.09 && top10.fraction() < 0.11, "top 10% covered " + top10.fraction());
    }

    @Test
    void rankingPutsPremiumsFirstAndJunkLast() {
        List<StartingHandRanking.Entry> entries = StartingHandRanking.entries();
        assertEquals(169, entries.size());
        assertEquals("AA", entries.get(0).hand().name());
        assertEquals(0.85, entries.get(0).equity(), 0.01);
        assertTrue(StartingHandRanking.percentile(StartingHand.of(combo("KsKh"))) < 1.5);
        assertTrue(StartingHandRanking.percentile(StartingHand.of(combo("3c2d"))) > 95);
        double previous = Double.MAX_VALUE;
        for (StartingHandRanking.Entry entry : entries) {
            assertTrue(entry.equity() <= previous, "sorted by equity");
            previous = entry.equity();
        }
    }

    @Test
    void bandsExcludeTheTopOfTheRange() {
        Range band = StartingHandRanking.between(5, 25);
        assertFalse(band.contains(combo("AsAh")));
        assertEquals(StartingHandRanking.top(25).size() - StartingHandRanking.top(5).size(), band.size());
    }
}
