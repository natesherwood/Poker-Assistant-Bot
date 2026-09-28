package com.pokerassistant.cli;

import com.pokerassistant.equity.EquityCalculator;
import com.pokerassistant.game.TableConfig;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives the whole application through its text interface, the way a user would. */
class PokerCliTest {

    private static String run(String script) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        EquityCalculator calculator = new EquityCalculator(EquityCalculator.DEFAULT_EXACT_BUDGET, 20_000, () -> 42L);
        new PokerCli(new BufferedReader(new StringReader(script)), out, false, true, TableConfig.defaults(), calculator).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void playsAHandFromTheBlindsToTheShowdown() {
        String output = run("""
                setup players=3 stack=100 hero=1 button=1
                new AhKh
                r 3; f; c
                flop Qh 7h 2c
                x
                b 4
                c
                turn 5s
                x; x
                river 9h
                x; b 10; c
                show 3 QsQd
                status
                """);
        assertTrue(output.contains("=== Hand #1 ==="), output);
        assertTrue(output.contains("*** FLOP *** Qh 7h 2c"), output);
        assertTrue(output.contains("advice for hero"), output);
        assertTrue(output.contains("-- preflop betting closed"), output);
        assertTrue(output.contains("Seat 3 shows Qs Qd (Three of a kind, Queens)"), output);
        assertTrue(output.contains("Hand complete. Hero +17.5 (stack 117.5)"), output);
        assertTrue(output.contains("Seat 1 won 34.5"), output);
        assertFalse(output.contains("error:"), output);
        assertFalse(output.contains("internal error"), output);
    }

    @Test
    void badInputIsReportedAndTheSessionCarriesOn() {
        String output = run("""
                blah
                new
                x
                r 0.5
                flop Kh 7d 2c
                r 1000
                undo
                f f f; x
                abort
                status
                quit
                """);
        assertTrue(output.contains("error: Unknown command 'blah'"), output);
        assertTrue(output.contains("can't check while facing a bet"), output);
        assertTrue(output.contains("The minimum raise is to 2"), output);
        assertTrue(output.contains("Can't deal the flop now"), output);
        assertTrue(output.contains("at most 100"), output);
        assertTrue(output.contains("Nothing left to undo"), output);
        assertTrue(output.contains("Hand discarded"), output);
        assertTrue(output.contains("Bye."), output);
        assertFalse(output.contains("internal error"), output);
    }

    @Test
    void undoTakesBackTheLastAction() {
        String output = run("""
                new AsAh
                f
                undo
                status
                """);
        assertTrue(output.contains("Undone: fold"), output);
        assertTrue(output.contains("Action on Seat 4 (UTG)"), output);
    }

    @Test
    void anAllInRunsOutWithTheBoardCommand() {
        String output = run("""
                setup players=2 hero=1 button=1
                new AsAd
                a; c
                show 2 KsKd
                board 2h 7c 9d Jc 3s
                """);
        assertTrue(output.contains("Hand complete. Hero +100 (stack 200)"), output);
    }

    @Test
    void standaloneCalculators() {
        String output = run("""
                equity AsAh vs KcKd
                equity 7h7d vs AKs vs random board Ah 7s 2c
                odds 10 5 30
                help ranges
                fsm
                """);
        assertTrue(output.contains("for As Ah preflop"), output);
        assertTrue(output.contains("exact"), output);
        assertTrue(output.contains("Monte Carlo"), output);
        assertTrue(output.contains("A bet of 5 into 10 (pot now 15)"), output);
        assertTrue(output.contains("calling is profitable"), output);
        assertTrue(output.contains("Range notation"), output);
        assertTrue(output.contains("AWAITING_FLOP"), output);
        assertFalse(output.contains("error:"), output);
    }
}
