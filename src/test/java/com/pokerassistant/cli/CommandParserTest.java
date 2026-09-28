package com.pokerassistant.cli;

import com.pokerassistant.cards.CardMask;
import com.pokerassistant.error.InvalidInputException;
import com.pokerassistant.game.ActionType;
import com.pokerassistant.game.Chips;
import com.pokerassistant.game.Street;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandParserTest {

    private final CommandParser parser = new CommandParser();

    private Command one(String line) {
        List<Command> commands = parser.parse(line);
        assertEquals(1, commands.size(), () -> "expected one command from '" + line + "' but got " + commands);
        return commands.get(0);
    }

    private static long cards(String text) {
        return CardMask.of(CardMask.parseList(text));
    }

    @Test
    void parsesActionSequencesInLongAndCompactForms() {
        Command.Act act = assertInstanceOf(Command.Act.class, one("f fold r 2.5 c b3 x A Shove"));
        assertEquals(List.of(
                new Command.ActionInput(ActionType.FOLD, null),
                new Command.ActionInput(ActionType.FOLD, null),
                new Command.ActionInput(ActionType.RAISE, Chips.ofCents(250)),
                new Command.ActionInput(ActionType.CALL, null),
                new Command.ActionInput(ActionType.BET, Chips.of(3)),
                new Command.ActionInput(ActionType.CHECK, null),
                new Command.ActionInput(ActionType.ALL_IN, null),
                new Command.ActionInput(ActionType.ALL_IN, null)), act.actions());
    }

    @Test
    void parsesHandAndBoardCommands() {
        assertEquals(new Command.NewHand(cards("AhKd")), one("new Ah Kd"));
        assertEquals(new Command.NewHand(0L), one("new"));
        assertEquals(new Command.SetHoleCards(cards("AhKd")), one("hole AhKd"));
        assertEquals(new Command.DealBoard(Street.FLOP, CardMask.parseList("Kh7d2c")), one("flop Kh7d2c"));
        assertEquals(new Command.DealBoard(Street.FLOP, CardMask.parseList("Kh7d2c")), one("FLOP kh 7d 2c"));
        assertEquals(new Command.DealBoard(Street.RIVER, CardMask.parseList("5s")), one("river 5s"));
        assertEquals(new Command.DealBoard(null, CardMask.parseList("Kh7d2c5s9h")), one("board Kh 7d 2c 5s 9h"));
        assertEquals(new Command.Show(4, cards("QsQd")), one("show 4 Qs Qd"));
        assertEquals(new Command.Winners(List.of(3, 5)), one("winner 3 5"));
        assertEquals(new Command.Muck(2), one("muck 2"));
    }

    @Test
    void parsesTableCommands() {
        assertEquals(new Command.Setup(6, Chips.of(100), Chips.ofCents(50), Chips.of(1), null, 1, 3),
                one("setup players=6 stack=100 sb=0.5 bb=1 button=1 hero=3"));
        assertEquals(new Command.Setup(null, null, null, null, Chips.ofCents(10), null, null), one("setup ante=0.1"));
        assertEquals(new Command.SetStack(3, Chips.of(150)), one("stack 3 150"));
        assertEquals(new Command.MoveButton(4), one("btn 4"));
    }

    @Test
    void parsesAnalysisCommands() {
        assertEquals(new Command.EquityQuery(0L, List.of(), 0L), one("equity"));
        Command.EquityQuery query = assertInstanceOf(Command.EquityQuery.class, one("equity AsKs vs QQ+ AKs vs random board Kh 7d 2c"));
        assertEquals(cards("AsKs"), query.hero());
        assertEquals(2, query.villains().size());
        assertEquals(22, query.villains().get(0).size());
        assertEquals(1326, query.villains().get(1).size());
        assertEquals(cards("Kh7d2c"), query.board());
        assertEquals(1326, assertInstanceOf(Command.EquityQuery.class, one("eq 7h7d")).villains().get(0).size());
        assertEquals(new Command.Odds(Chips.of(10), Chips.of(5), 30.0), one("odds 10 5 30%"));
        assertNull(assertInstanceOf(Command.Odds.class, one("odds 10 5")).equityPercent());
        assertEquals(18, assertInstanceOf(Command.SetRange.class, one("range 4 QQ+")).range().size());
        assertNull(assertInstanceOf(Command.SetRange.class, one("range 4 auto")).range());
        assertEquals(new Command.AutoAdvice(false), one("auto off"));
    }

    @Test
    void separatorsCommentsAndBlankLines() {
        List<Command> commands = parser.parse("status; help   # everything after the hash is ignored ; quit");
        assertEquals(List.of(new Command.Status(), new Command.Help(null)), commands);
        assertTrue(parser.parse("   ").isEmpty());
        assertTrue(parser.parse("# just a comment").isEmpty());
        assertTrue(parser.parse(null).isEmpty());
    }

    @Test
    void ignoresByteOrderMarks() {
        assertEquals(new Command.Status(), one("﻿status"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "raise", "r", "b", "bet x", "r -5", "r 2.555", "r 1e3", "c 5", "f zz",
            "flop Kh 7d", "turn 5s 6s", "river", "board 2c 3c 4c 5c 6c 7c",
            "stack 3", "stack 11 100", "stack 3 -1", "button x", "btn 0",
            "setup", "setup players=11", "setup players=1", "setup foo=1", "setup players=6 players=7", "setup sb=0", "setup stack",
            "odds 10", "odds 0 5", "odds 10 5 150", "odds 10 5 abc",
            "range 3", "range 3 AKx", "range x QQ+", "auto maybe", "show 4 Qs", "show 4", "winner", "winner 3 3",
            "new AhAh", "new Ah", "hole", "help a b", "status now", "quit now",
            "equity vs QQ", "equity AhKd vs", "equity AhKd board Kh 7d 2c board 5s", "equity AhKd board Kh7d",
            "raize 3", "blah"
    })
    void rejectsInvalidInputWithAMessage(String line) {
        InvalidInputException error = assertThrows(InvalidInputException.class, () -> parser.parse(line));
        assertTrue(error.getMessage() != null && !error.getMessage().isBlank());
    }

    @Test
    void suggestsTheClosestCommand() {
        InvalidInputException error = assertThrows(InvalidInputException.class, () -> parser.parse("staus"));
        assertTrue(error.getMessage().contains("Did you mean 'status'?"), error.getMessage());
    }

    @Test
    void guardsAgainstHugeAndBinaryInput() {
        assertThrows(InvalidInputException.class, () -> parser.parse("f ".repeat(300)));
        assertThrows(InvalidInputException.class, () -> parser.parse("f ".repeat(40)));
        assertThrows(InvalidInputException.class, () -> parser.parse("status\u0007"));
    }
}
