package com.pokerassistant.cli;

import com.pokerassistant.cards.Card;
import com.pokerassistant.cards.CardMask;
import com.pokerassistant.equity.EquityCalculator;
import com.pokerassistant.equity.EquityRequest;
import com.pokerassistant.equity.Range;
import com.pokerassistant.equity.StartingHandRanking;
import com.pokerassistant.error.IllegalActionException;
import com.pokerassistant.error.IllegalTransitionException;
import com.pokerassistant.error.InvalidInputException;
import com.pokerassistant.error.PokerAssistantException;
import com.pokerassistant.fsm.StateChange;
import com.pokerassistant.game.Chips;
import com.pokerassistant.game.HandContext;
import com.pokerassistant.game.HandEvent;
import com.pokerassistant.game.HandSession;
import com.pokerassistant.game.HandState;
import com.pokerassistant.game.HandStateMachine;
import com.pokerassistant.game.PlayerState;
import com.pokerassistant.game.TableConfig;
import com.pokerassistant.strategy.DecisionEngine;
import com.pokerassistant.strategy.RangeEstimator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The read-eval-print loop. Reads a line, parses it into commands, runs them against the
 * {@link HandSession}, and renders what changed. It never dies on bad input: expected failures
 * ({@link PokerAssistantException}) are shown as errors with a hint, anything else as an internal
 * error, and the loop continues. Input comes from a plain {@link BufferedReader}, so it also works in
 * IntelliJ's Run console (where {@code System.console()} is unavailable) and with piped scripts.
 */
public final class PokerCli {

    private final BufferedReader in;
    private final PrintStream out;
    private final boolean echo;
    private final ConsoleRenderer view;
    private final CommandParser parser = new CommandParser();
    private final HandSession session;
    private final RangeEstimator ranges = new RangeEstimator();
    private final EquityCalculator equityCalculator;
    private final DecisionEngine engine;
    private boolean autoAdvice = true;
    private int logCursor;

    public PokerCli(BufferedReader in, PrintStream out, boolean color, boolean echo) {
        this(in, out, color, echo, TableConfig.defaults(), new EquityCalculator());
    }

    PokerCli(BufferedReader in, PrintStream out, boolean color, boolean echo, TableConfig table, EquityCalculator equityCalculator) {
        this.in = in;
        this.out = out;
        this.echo = echo;
        this.view = new ConsoleRenderer(out, new Ansi(color));
        this.session = new HandSession(table);
        this.equityCalculator = equityCalculator;
        this.engine = new DecisionEngine(equityCalculator, ranges);
    }

    /** Runs until 'quit' or end of input. */
    public void run() {
        StartingHandRanking.warmUpInBackground();
        view.banner(session.upcomingTable());
        while (true) {
            out.print(view.prompt(promptState()));
            out.flush();
            String line;
            try {
                line = in.readLine();
            } catch (IOException e) {
                view.error("Could not read input: " + e.getMessage());
                return;
            }
            if (line == null) {
                out.println();
                view.info("End of input. Bye.");
                return;
            }
            if (echo) {
                out.println(line.replace("﻿", ""));
            }
            if (!handle(line)) {
                view.info("Bye.");
                return;
            }
        }
    }

    /** Runs one input line. Returns false when the user asked to quit; never throws. */
    boolean handle(String line) {
        try {
            for (Command command : parser.parse(line)) {
                if (!execute(command)) {
                    return false;
                }
            }
        } catch (IllegalTransitionException e) {
            view.error(explain(e));
        } catch (PokerAssistantException e) {
            view.error(e.getMessage());
        } catch (RuntimeException e) {
            view.internalError(e);
        }
        return true;
    }

    private boolean execute(Command command) {
        switch (command) {
            case Command.Quit quit -> {
                return false;
            }
            case Command.Help help -> view.help(help.topic());
            case Command.Status status -> view.status(session);
            case Command.Setup setup -> setup(setup);
            case Command.SetStack stack -> {
                session.updateTable(table -> table.withStack(stack.seat(), stack.stack()));
                view.success("Seat %d stack set to %s".formatted(stack.seat(), stack.stack()));
            }
            case Command.MoveButton button -> {
                session.updateTable(table -> table.withButton(button.seat()));
                view.success("Button moved to seat " + button.seat());
            }
            case Command.NewHand newHand -> startHand(newHand.heroCards());
            case Command.SetHoleCards hole -> apply(List.of(new HandEvent.HoleCardsDealt(hole.cards())));
            case Command.Act act -> apply(act.actions().stream()
                    .<HandEvent>map(action -> new HandEvent.PlayerActed(action.type(), action.amount()))
                    .toList());
            case Command.DealBoard deal -> apply(boardEvents(deal));
            case Command.Show show -> apply(List.of(new HandEvent.CardsShown(show.seat(), show.cards())));
            case Command.Muck muck -> apply(List.of(new HandEvent.Mucked(muck.seat())));
            case Command.Winners winners -> apply(List.of(new HandEvent.WinnersDeclared(winners.seats())));
            case Command.Advise advise -> view.recommendation(engine.recommend(currentHand().context()));
            case Command.EquityQuery query -> equity(query);
            case Command.Odds odds -> view.odds(odds);
            case Command.SetRange range -> setRange(range);
            case Command.ShowRanges showRanges -> showRanges();
            case Command.Undo undo -> undo();
            case Command.History history -> view.history(currentHand().context().log());
            case Command.Abort abort -> {
                session.abort();
                ranges.clearOverrides();
                view.success("Hand discarded; stacks and button are as they were before it");
            }
            case Command.ShowFsm fsm -> view.fsm(HandStateMachine.definition().describe());
            case Command.AutoAdvice auto -> {
                autoAdvice = auto.enabled();
                view.success("Automatic advice " + (autoAdvice ? "on" : "off"));
            }
        }
        return true;
    }

    private void setup(Command.Setup setup) {
        session.updateTable(current -> {
            int players = setup.players() != null ? setup.players() : current.seats().size();
            boolean resize = players != current.seats().size();
            Chips smallBlind = setup.smallBlind() != null ? setup.smallBlind() : current.smallBlind();
            Chips bigBlind = setup.bigBlind() != null ? setup.bigBlind() : current.bigBlind();
            Chips ante = setup.ante() != null ? setup.ante() : current.ante();
            int button = setup.button() != null ? setup.button() : Math.min(current.buttonSeat(), players);
            int hero = setup.hero() != null ? setup.hero() : Math.min(current.heroSeat(), players);
            TableConfig updated;
            if (setup.stack() != null || resize) {
                Chips stack = setup.stack() != null ? setup.stack()
                        : current.seats().stream().map(TableConfig.Seat::stack).max(Chips::compareTo).orElseThrow();
                updated = TableConfig.uniform(players, stack, smallBlind, bigBlind, ante, button, hero);
            } else {
                updated = new TableConfig(current.seats(), smallBlind, bigBlind, ante, button, hero);
            }
            return setup.button() != null ? updated.withButton(button) : updated;
        });
        view.success("Table updated");
        view.table(session.upcomingTable());
    }

    private void startHand(long heroCards) {
        List<StateChange<HandState>> changes = session.startHand(heroCards);
        ranges.clearOverrides();
        logCursor = 0;
        view.handHeader(session.handNumber(), currentHand().context());
        report(changes);
    }

    private void apply(List<? extends HandEvent> events) {
        report(session.applyAll(events));
    }

    /** Prints what the last input changed, the next step, and advice if the action is on hero. */
    private void report(List<StateChange<HandState>> changes) {
        HandStateMachine hand = currentHand();
        List<String> log = hand.context().log();
        view.logLines(log.subList(Math.min(logCursor, log.size()), log.size()));
        logCursor = log.size();
        view.transitions(changes, hand);
        view.nextStep(hand);
        adviseIfHeroToAct(hand);
    }

    private void adviseIfHeroToAct(HandStateMachine hand) {
        HandContext context = hand.context();
        if (!autoAdvice || !context.isHeroToAct()) {
            return;
        }
        if (context.hero().filter(PlayerState::hasKnownCards).isEmpty()) {
            view.warn("The action is on hero: enter hero's cards with 'hole <cards>' to get advice");
            return;
        }
        try {
            view.recommendation(engine.recommend(context));
        } catch (PokerAssistantException e) {
            view.warn("No advice: " + e.getMessage());
        }
    }

    /** 'flop', 'turn' and 'river' must match the street being dealt; 'board' fills whatever is pending. */
    private List<HandEvent> boardEvents(Command.DealBoard deal) {
        HandStateMachine hand = currentHand();
        HandState state = hand.state();
        if (deal.street() != null) {
            HandState expected = switch (deal.street()) {
                case FLOP -> HandState.AWAITING_FLOP;
                case TURN -> HandState.AWAITING_TURN;
                case RIVER -> HandState.AWAITING_RIVER;
                case PREFLOP -> throw new IllegalStateException("No preflop board");
            };
            if (state != expected) {
                throw new IllegalActionException("Can't deal the %s now (%s). %s".formatted(
                        deal.street().label().toLowerCase(Locale.ROOT), state.description().toLowerCase(Locale.ROOT), view.nextStepHint(hand)));
            }
            return List.of(new HandEvent.BoardDealt(deal.cards()));
        }
        if (!state.isAwaitingBoard()) {
            throw new IllegalActionException("No board cards are due now (%s). %s".formatted(
                    state.description().toLowerCase(Locale.ROOT), view.nextStepHint(hand)));
        }
        List<HandEvent> events = new ArrayList<>();
        List<Card> cards = deal.cards();
        int onBoard = hand.context().board().size();
        int next = 0;
        while (next < cards.size()) {
            if (onBoard >= 5) {
                throw new InvalidInputException("Too many cards: the board only holds five");
            }
            int size = onBoard == 0 ? 3 : 1;
            if (next + size > cards.size()) {
                throw new InvalidInputException("The flop needs 3 cards, got " + (cards.size() - next));
            }
            events.add(new HandEvent.BoardDealt(cards.subList(next, next + size)));
            next += size;
            onBoard += size;
        }
        return events;
    }

    private void equity(Command.EquityQuery query) {
        EquityRequest request;
        List<String> opponents = new ArrayList<>();
        if (query.hero() == 0) {
            HandContext hand = currentHand().context();
            PlayerState hero = hand.hero().filter(PlayerState::hasKnownCards).orElseThrow(() -> new IllegalActionException(
                    "Hero's cards are unknown: use 'hole <cards>', or ask directly, e.g. 'equity AhKd vs QQ+'"));
            if (!hero.isInHand()) {
                throw new IllegalActionException("Hero has folded this hand");
            }
            List<Range> villains = new ArrayList<>();
            for (PlayerState player : hand.livePlayers()) {
                if (player.seat() != hero.seat()) {
                    RangeEstimator.Estimate estimate = ranges.estimate(hand, player.seat());
                    villains.add(estimate.range());
                    opponents.add("Seat %d %s: %s (%s)".formatted(player.seat(), player.position(), estimate.range(), estimate.basis()));
                }
            }
            request = new EquityRequest(hero.holeCards(), villains, hand.boardMask(), 0L);
        } else {
            request = new EquityRequest(query.hero(), query.villains(), query.board(), 0L);
            query.villains().forEach(range -> opponents.add(range.toString()));
        }
        view.equity(request, equityCalculator.calculate(request), opponents);
    }

    private void setRange(Command.SetRange command) {
        HandContext hand = currentHand().context();
        if (hand.player(command.seat()).isEmpty()) {
            throw new InvalidInputException("Seat " + command.seat() + " is not dealt into this hand");
        }
        if (command.seat() == hand.heroSeat()) {
            throw new InvalidInputException("Seat " + command.seat() + " is hero; ranges are for opponents");
        }
        if (command.range() == null) {
            ranges.clearOverride(command.seat());
            view.success("Seat " + command.seat() + ": back to the automatic estimate");
        } else {
            ranges.override(command.seat(), command.range());
            view.success("Seat %d range set to %s".formatted(command.seat(), command.range()));
        }
        adviseIfHeroToAct(currentHand());
    }

    private void showRanges() {
        HandContext hand = currentHand().context();
        for (PlayerState player : hand.livePlayers()) {
            if (player.seat() != hand.heroSeat()) {
                RangeEstimator.Estimate estimate = ranges.estimate(hand, player.seat());
                view.info("Seat %d %-6s %s  [%s]".formatted(player.seat(), player.position(), estimate.range(), estimate.basis()));
            }
        }
    }

    private void undo() {
        HandEvent removed = session.undo();
        HandStateMachine hand = currentHand();
        logCursor = hand.context().log().size();
        view.success("Undone: " + describe(removed));
        view.nextStep(hand);
        adviseIfHeroToAct(hand);
    }

    private HandStateMachine currentHand() {
        return session.hand().orElseThrow(() -> new IllegalActionException("No hand yet; start one with 'new [hero cards]'"));
    }

    private String promptState() {
        Optional<HandStateMachine> current = session.hand();
        if (current.isEmpty()) {
            return "no hand";
        }
        HandStateMachine hand = current.get();
        Chips pot = hand.isComplete() ? hand.context().finalPot() : hand.context().pot();
        String state = hand.state().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return "#%d %s | pot %s".formatted(session.handNumber(), state, pot);
    }

    private String explain(IllegalTransitionException e) {
        String attempted = switch (e.event()) {
            case "PlayerActed" -> "Betting";
            case "BoardDealt" -> "Dealing board cards";
            case "StartHand" -> "Starting a hand";
            case "HoleCardsDealt" -> "Setting hole cards";
            case "CardsShown" -> "Showing cards";
            case "Mucked" -> "Mucking";
            case "WinnersDeclared" -> "Declaring winners";
            default -> e.event();
        };
        String state = HandState.valueOf(e.state()).description().toLowerCase(Locale.ROOT);
        String hint = session.hand().map(view::nextStepHint).orElse("");
        return "%s isn't possible now (%s). %s".formatted(attempted, state, hint);
    }

    private static String describe(HandEvent event) {
        return switch (event) {
            case HandEvent.StartHand start -> "start of hand";
            case HandEvent.HoleCardsDealt hole -> "hole cards " + CardMask.format(hole.cards());
            case HandEvent.PlayerActed acted -> acted.type().name().toLowerCase(Locale.ROOT).replace('_', '-')
                    + (acted.amount() == null ? "" : " " + acted.amount());
            case HandEvent.BoardDealt board -> "board " + board.cards();
            case HandEvent.CardsShown shown -> "seat " + shown.seat() + " shows " + CardMask.format(shown.cards());
            case HandEvent.Mucked mucked -> "seat " + mucked.seat() + " mucks";
            case HandEvent.WinnersDeclared winners -> "winners " + winners.seats();
        };
    }
}
