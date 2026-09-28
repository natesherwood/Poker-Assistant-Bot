package com.pokerassistant.game;

import com.pokerassistant.error.IllegalActionException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * No-limit betting for one street: whose turn it is, what they may do, and applying their action.
 *
 * <p>Rules enforced:
 * <ul>
 *   <li>the minimum bet is one big blind; the minimum raise equals the last full bet or raise;</li>
 *   <li>going all-in for less than a minimum is always allowed;</li>
 *   <li>an all-in raise smaller than a full raise does not re-open betting for players who already
 *       acted, unless the raises they now face add up to a full raise (TDA rules);</li>
 *   <li>nobody may bet or raise when no opponent could respond, and folding requires facing a bet.</li>
 * </ul>
 * Each action is validated completely before any chips move, so a rejected action changes nothing.
 */
final class BettingRound {

    private final Street street;
    private final List<PlayerState> players;
    private final Chips bigBlind;
    private Chips currentBet;
    private Chips minRaise;
    private int aggression;
    private int toAct;

    private BettingRound(Street street, List<PlayerState> players, Chips bigBlind, Chips currentBet) {
        this.street = street;
        this.players = players;
        this.bigBlind = bigBlind;
        this.currentBet = currentBet;
        this.minRaise = bigBlind;
    }

    /** Preflop: the big blind is the bet to match and action starts left of the big blind. */
    static BettingRound preflop(List<PlayerState> players, int bigBlindIndex, Chips bigBlind) {
        BettingRound round = new BettingRound(Street.PREFLOP, players, bigBlind, bigBlind);
        round.toAct = round.nextToAct(bigBlindIndex);
        return round;
    }

    /** Postflop: no bet yet and action starts left of the button. */
    static BettingRound postflop(Street street, List<PlayerState> players, int buttonIndex, Chips bigBlind) {
        BettingRound round = new BettingRound(street, players, bigBlind, Chips.ZERO);
        round.toAct = round.nextToAct(buttonIndex);
        return round;
    }

    Street street() {
        return street;
    }

    boolean isClosed() {
        return toAct < 0;
    }

    Chips currentBet() {
        return currentBet;
    }

    /** Bets and raises made so far on this street. */
    int aggression() {
        return aggression;
    }

    PlayerState playerToAct() {
        if (toAct < 0) {
            throw new IllegalStateException("The " + street.label().toLowerCase(Locale.ROOT) + " betting round is closed");
        }
        return players.get(toAct);
    }

    LegalActions legalActions() {
        PlayerState player = playerToAct();
        Chips owed = currentBet.minus(player.streetBet());
        Chips toCall = owed.min(player.stack());
        Chips maxTotal = player.streetBet().plus(player.stack());
        boolean facingBet = owed.isPositive();
        boolean opponentCanRespond = countCanAct() > 1;
        boolean reopened = !player.hasActed() || !currentBet.minus(player.levelAtLastAction()).isLessThan(minRaise);
        boolean canBet = currentBet.isZero() && opponentCanRespond;
        boolean canRaise = currentBet.isPositive() && opponentCanRespond && reopened && maxTotal.isGreaterThan(currentBet);
        Chips minTotal = canBet ? bigBlind.min(maxTotal)
                : canRaise ? currentBet.plus(minRaise).min(maxTotal)
                : Chips.ZERO;
        return new LegalActions(
                player.seat(),
                toCall,
                facingBet && toCall.equals(player.stack()),
                !facingBet,
                facingBet,
                canBet,
                canRaise,
                minTotal,
                canBet || canRaise ? maxTotal : Chips.ZERO);
    }

    /**
     * Applies the action of the player to act.
     *
     * @param amount    street total for BET and RAISE ("raise to"), null otherwise
     * @param potBefore pot including all current bets, recorded for later analysis
     */
    ActionRecord apply(ActionType requested, Chips amount, Chips potBefore) {
        Objects.requireNonNull(requested, "requested");
        PlayerState player = playerToAct();
        LegalActions legal = legalActions();
        int aggressionBefore = aggression;
        int yetToAct = opponentsYetToAct(player);
        Chips committedBefore = player.totalBet();

        ActionType type = requested;
        Chips target = amount;
        if (requested == ActionType.ALL_IN) {
            Chips allIn = player.streetBet().plus(player.stack());
            if (!allIn.isGreaterThan(currentBet)) {
                type = ActionType.CALL; // the whole stack does not even cover the call
            } else if (legal.canBet() || legal.canRaise()) {
                type = legal.canBet() ? ActionType.BET : ActionType.RAISE;
                target = allIn;
            } else {
                throw new IllegalActionException(raiseBlockedReason(player));
            }
        }

        switch (type) {
            case FOLD -> {
                if (!legal.canFold()) {
                    throw new IllegalActionException("Seat %d can check for free, so folding is not allowed".formatted(player.seat()));
                }
                player.fold();
            }
            case CHECK -> {
                if (!legal.canCheck()) {
                    throw new IllegalActionException("Seat %d can't check while facing a bet (%s)".formatted(player.seat(), legal.describe()));
                }
                player.markActed(currentBet);
            }
            case CALL -> {
                if (!legal.canCall()) {
                    throw new IllegalActionException("Seat %d has nothing to call; check instead".formatted(player.seat()));
                }
                player.commit(legal.toCall());
                player.markActed(currentBet);
            }
            case BET, RAISE -> betOrRaise(player, legal, type, target);
            case ALL_IN -> throw new IllegalStateException("ALL_IN is resolved before dispatch");
        }

        ActionRecord record = new ActionRecord(street, player.seat(), player.position(), type,
                player.totalBet().minus(committedBefore), player.streetBet(), player.isAllIn(),
                aggressionBefore, yetToAct, potBefore, legal.toCall());
        toAct = nextToAct(toAct);
        return record;
    }

    private void betOrRaise(PlayerState player, LegalActions legal, ActionType type, Chips target) {
        if (target == null) {
            throw new IllegalActionException("'" + type.name().toLowerCase(Locale.ROOT) + "' needs an amount");
        }
        if (type == ActionType.BET && !legal.canBet()) {
            throw new IllegalActionException(currentBet.isPositive()
                    ? "There is already a bet of %s; use 'raise <total>' instead".formatted(currentBet)
                    : "Seat %d can't bet: no opponent is left to respond".formatted(player.seat()));
        }
        if (type == ActionType.RAISE && !legal.canRaise()) {
            throw new IllegalActionException(raiseBlockedReason(player));
        }
        String verb = type == ActionType.BET ? "bet" : "raise to";
        if (target.isGreaterThan(legal.maxTotal())) {
            throw new IllegalActionException("Seat %d can %s at most %s, which is all-in (use 'allin')".formatted(
                    player.seat(), verb, legal.maxTotal()));
        }
        boolean allIn = target.equals(legal.maxTotal());
        if (!allIn && target.isLessThan(legal.minTotal())) {
            throw new IllegalActionException(type == ActionType.BET
                    ? "The minimum bet is %s".formatted(legal.minTotal())
                    : "The minimum raise is to %s (amounts are street totals: 'raise to', not 'raise by')".formatted(legal.minTotal()));
        }

        Chips increment = target.minus(currentBet);
        player.commit(target.minus(player.streetBet()));
        if (!increment.isLessThan(minRaise)) {
            minRaise = increment; // only a full bet or raise sets the next minimum
        }
        currentBet = target;
        aggression++;
        player.markActed(currentBet);
    }

    private String raiseBlockedReason(PlayerState player) {
        if (currentBet.isZero()) {
            return "Nothing to raise; use 'bet <amount>'";
        }
        if (countCanAct() <= 1) {
            return "Seat %d can't raise: every opponent is all-in (call or fold)".formatted(player.seat());
        }
        if (!player.streetBet().plus(player.stack()).isGreaterThan(currentBet)) {
            return "Seat %d doesn't have enough chips to raise (call all-in or fold)".formatted(player.seat());
        }
        return "Seat %d can't re-raise: the last all-in was less than a full raise, so betting is not re-opened (call or fold)"
                .formatted(player.seat());
    }

    private int nextToAct(int from) {
        int count = players.size();
        for (int step = 1; step <= count; step++) {
            int index = Math.floorMod(from + step, count);
            if (needsToAct(players.get(index))) {
                return index;
            }
        }
        return -1;
    }

    /** Owes chips, or has not acted yet while someone else could still respond to a bet. */
    private boolean needsToAct(PlayerState player) {
        if (!player.canAct()) {
            return false;
        }
        if (player.streetBet().isLessThan(currentBet)) {
            return true;
        }
        return !player.hasActed() && countCanAct() > 1;
    }

    private int countCanAct() {
        int count = 0;
        for (PlayerState player : players) {
            if (player.canAct()) {
                count++;
            }
        }
        return count;
    }

    private int opponentsYetToAct(PlayerState actor) {
        int count = 0;
        for (PlayerState player : players) {
            if (player != actor && player.canAct() && !player.hasActed()) {
                count++;
            }
        }
        return count;
    }
}
