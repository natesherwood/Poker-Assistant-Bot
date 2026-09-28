package com.pokerassistant.game;

import com.pokerassistant.fsm.StateChange;
import com.pokerassistant.fsm.StateMachine;
import com.pokerassistant.fsm.StateMachineDefinition;
import com.pokerassistant.game.HandEvent.BoardDealt;
import com.pokerassistant.game.HandEvent.CardsShown;
import com.pokerassistant.game.HandEvent.HoleCardsDealt;
import com.pokerassistant.game.HandEvent.Mucked;
import com.pokerassistant.game.HandEvent.PlayerActed;
import com.pokerassistant.game.HandEvent.StartHand;
import com.pokerassistant.game.HandEvent.WinnersDeclared;

import java.util.EnumSet;
import java.util.List;

import static com.pokerassistant.game.HandState.AWAITING_FLOP;
import static com.pokerassistant.game.HandState.AWAITING_RIVER;
import static com.pokerassistant.game.HandState.AWAITING_TURN;
import static com.pokerassistant.game.HandState.COMPLETE;
import static com.pokerassistant.game.HandState.FLOP;
import static com.pokerassistant.game.HandState.IDLE;
import static com.pokerassistant.game.HandState.PREFLOP;
import static com.pokerassistant.game.HandState.RIVER;
import static com.pokerassistant.game.HandState.SHOWDOWN;
import static com.pokerassistant.game.HandState.TURN;

/**
 * The finite-state machine that drives one hand from the blinds to the payout.
 *
 * <p>User input arrives as {@link HandEvent}s. Everything that follows from the rules happens through
 * <em>completion transitions</em>, without further input: a betting round that closes moves the hand
 * to "awaiting" the next cards (collecting bets and returning an uncalled excess), a fold that leaves
 * one player ends the hand, all-in players make each street close as soon as it is dealt, and a
 * showdown settles itself, side pots included, once every remaining hand is known.
 */
public final class HandStateMachine {

    private static final StateMachineDefinition<HandState, HandEvent, HandContext> DEFINITION = define();

    private final StateMachine<HandState, HandEvent, HandContext> machine;

    public HandStateMachine() {
        this.machine = DEFINITION.start(new HandContext());
    }

    /** Applies one event and any automatic transitions it triggers; returns the state changes taken. */
    public List<StateChange<HandState>> fire(HandEvent event) {
        return machine.fire(event);
    }

    public HandState state() {
        return machine.state();
    }

    public HandContext context() {
        return machine.context();
    }

    public boolean isComplete() {
        return machine.isFinished();
    }

    /** The shared transition table (for documentation, the CLI's {@code fsm} command and tests). */
    public static StateMachineDefinition<HandState, HandEvent, HandContext> definition() {
        return DEFINITION;
    }

    private static StateMachineDefinition<HandState, HandEvent, HandContext> define() {
        EnumSet<HandState> liveHand = EnumSet.range(PREFLOP, SHOWDOWN);
        return StateMachineDefinition.<HandState, HandEvent, HandContext>builder(HandState.class, IDLE)
                .on(IDLE, StartHand.class, PREFLOP, HandContext::startHand)

                // Betting: each action stays on its street; completion transitions decide what follows.
                .on(PREFLOP, PlayerActed.class, PREFLOP, HandContext::applyAction)
                .on(FLOP, PlayerActed.class, FLOP, HandContext::applyAction)
                .on(TURN, PlayerActed.class, TURN, HandContext::applyAction)
                .on(RIVER, PlayerActed.class, RIVER, HandContext::applyAction)

                // Dealing the board opens the next betting round.
                .on(AWAITING_FLOP, BoardDealt.class, FLOP, (hand, event) -> hand.dealBoard(event, Street.FLOP))
                .on(AWAITING_TURN, BoardDealt.class, TURN, (hand, event) -> hand.dealBoard(event, Street.TURN))
                .on(AWAITING_RIVER, BoardDealt.class, RIVER, (hand, event) -> hand.dealBoard(event, Street.RIVER))

                // Information that can arrive at any point of a live hand.
                .internal(liveHand, HoleCardsDealt.class, HandContext::dealHeroCards)
                .internal(liveHand, CardsShown.class, HandContext::showCards)

                // Showdown input.
                .internal(EnumSet.of(SHOWDOWN), Mucked.class, HandContext::muck)
                .on(SHOWDOWN, WinnersDeclared.class, COMPLETE, HandContext::declareWinners)

                // Completion transitions, tried in order after every event.
                .complete(PREFLOP, COMPLETE, "everyone else folded", HandContext::onlyOnePlayerLeft, HandContext::awardUncontested)
                .complete(PREFLOP, AWAITING_FLOP, "preflop betting closed", HandContext::bettingRoundClosed, HandContext::closeBettingRound)
                .complete(FLOP, COMPLETE, "everyone else folded", HandContext::onlyOnePlayerLeft, HandContext::awardUncontested)
                .complete(FLOP, AWAITING_TURN, "flop betting closed", HandContext::bettingRoundClosed, HandContext::closeBettingRound)
                .complete(TURN, COMPLETE, "everyone else folded", HandContext::onlyOnePlayerLeft, HandContext::awardUncontested)
                .complete(TURN, AWAITING_RIVER, "turn betting closed", HandContext::bettingRoundClosed, HandContext::closeBettingRound)
                .complete(RIVER, COMPLETE, "everyone else folded", HandContext::onlyOnePlayerLeft, HandContext::awardUncontested)
                .complete(RIVER, SHOWDOWN, "river betting closed", HandContext::bettingRoundClosed, HandContext::closeBettingRound)
                .complete(SHOWDOWN, COMPLETE, "all remaining hands are known", HandContext::showdownDecided, HandContext::settleShowdown)

                .finalStates(EnumSet.of(COMPLETE))
                .build();
    }
}
