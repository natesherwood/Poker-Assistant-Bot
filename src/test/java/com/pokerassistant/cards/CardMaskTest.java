package com.pokerassistant.cards;

import com.pokerassistant.error.InvalidInputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardMaskTest {

    @Test
    void parsesCardsWithOrWithoutSeparators() {
        List<Card> expected = List.of(Card.of(Rank.ACE, Suit.HEARTS), Card.of(Rank.KING, Suit.DIAMONDS));
        assertEquals(expected, CardMask.parseList("AhKd"));
        assertEquals(expected, CardMask.parseList("ah, kd"));
        assertEquals(expected, CardMask.parseList(" Ah   Kd "));
    }

    @Test
    void acceptsTenWrittenAsTen() {
        assertSame(Card.of(Rank.TEN, Suit.SPADES), Card.parse("10s"));
        assertEquals(List.of(Card.parse("Ts"), Card.parse("9h")), CardMask.parseList("10s9h"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "A", "Ahk", "1h", "Ax", "AhAh", "Zz", "Ah K", "AhKd,,Q"})
    void rejectsMalformedCards(String text) {
        assertThrows(InvalidInputException.class, () -> CardMask.parseList(text));
    }

    @Test
    void cardsOwnOneBitInTheirSuitLane() {
        Card aceOfSpades = Card.parse("As");
        assertEquals(3 * 16 + 12, aceOfSpades.bit());
        assertSame(aceOfSpades, Card.ofBit(aceOfSpades.bit()));
        assertEquals(52, Long.bitCount(CardMask.FULL_DECK));
        for (Card card : Card.all()) {
            assertTrue((CardMask.FULL_DECK & card.mask()) != 0, card.toString());
            assertSame(card, Card.ofIndex(card.index()));
        }
    }

    @Test
    void formatsHighestCardFirst() {
        assertEquals("As Kd 2c", CardMask.format(CardMask.of(CardMask.parseList("2c As Kd"))));
        assertEquals("", CardMask.format(0L));
    }

    @Test
    void remainingDeckExcludesUsedCards() {
        long used = CardMask.of(CardMask.parseList("AsKs"));
        long[] rest = CardMask.remaining(used);
        assertEquals(50, rest.length);
        for (long card : rest) {
            assertEquals(0L, card & used);
            assertEquals(1, Long.bitCount(card));
        }
    }

    @Test
    void exactCountIsEnforcedWithAClearMessage() {
        InvalidInputException error = assertThrows(InvalidInputException.class,
                () -> CardMask.parseExactly("AhKdQc", 2, "Hero's hand"));
        assertEquals("Hero's hand needs exactly 2 cards, got 3", error.getMessage());
    }
}
