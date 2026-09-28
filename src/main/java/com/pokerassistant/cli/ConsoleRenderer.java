package com.pokerassistant.cli;

import com.pokerassistant.cards.CardMask;
import com.pokerassistant.equity.EquityRequest;
import com.pokerassistant.equity.EquityResult;
import com.pokerassistant.error.InvalidInputException;
import com.pokerassistant.fsm.StateChange;
import com.pokerassistant.game.Chips;
import com.pokerassistant.game.HandContext;
import com.pokerassistant.game.HandSession;
import com.pokerassistant.game.HandState;
import com.pokerassistant.game.HandStateMachine;
import com.pokerassistant.game.PlayerState;
import com.pokerassistant.game.Pot;
import com.pokerassistant.game.TableConfig;
import com.pokerassistant.math.PokerMath;
import com.pokerassistant.strategy.Recommendation;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** All console output. Plain ASCII, so it renders the same in IntelliJ, Windows Terminal and Unix shells. */
final class ConsoleRenderer {

    private static final String HELP = """
            Hand flow  (betting commands act for whoever is to act; chain them: f f r 2.5 c)
              new [cards]            start the next hand, e.g. 'new AhKd' (posts blinds, moves the button)
              hole <cards>           set or correct hero's hole cards
              f | x | c              fold | check | call
              b <amt> | r <amt>      bet | raise TO <amt> (the street total); compact forms: b3 r7.5
              a                      all-in (becomes a call, bet or raise)
              flop <3 cards>         e.g. 'flop Kh 7d 2c'  (then: turn <card>, river <card>)
              board <cards>          deal every pending street at once, e.g. after an all-in
              show <seat> <cards>    reveal a hand (at showdown, or tabled all-in for exact equity)
              muck <seat>            a player gives up at showdown
              winner <seat...>       award the pot(s) without entering cards
              undo | abort           take back the last input | discard the hand
            Analysis
              advise | ?             recommendation for hero (automatic when hero is to act)
              equity [...]           equity right now, or 'equity AsKs vs QQ+,AKs vs random board Kh7d2c'
              odds <pot> <bet> [eq%] pot odds, MDF and bluff maths for a bet into a pot
              range <seat> <range>   set a villain's range, e.g. 'range 4 22+,AJs+,KQs,AQo+' ('auto' resets)
              ranges                 each opponent's estimated range
            Table and session
              setup key=value ...    players, stack, sb, bb, ante, button, hero (between hands)
              stack <seat> <amt>     set a stack between hands (0 sits the seat out)
              button <seat>          move the button between hands
              status | history | fsm | auto on|off | help <topic> | quit
            Topics: actions, ranges, showdown, setup, equity.  Separate commands with ';'.  '#' starts a comment.
            """;

    private static final Map<String, String> TOPICS = Map.of(
            "actions", """
                    Betting commands always apply to the player the machine says is to act.
                      Amounts are street totals: 'r 7.5' means raise TO 7.5, not by 7.5.
                      The minimum bet is one big blind; the minimum raise adds at least the last full bet or raise.
                      An all-in for less than a full raise does not re-open betting for players who already acted.
                      Folding is only allowed when facing a bet; raising only while an opponent can still respond.
                      Several actions per line run as one unit: if any is illegal, none is applied.""",
            "ranges", """
                    Range notation (commas or spaces between tokens):
                      AA  22+  99-66      a pair, a pair and better, a span of pairs
                      AKs AKo AK          suited, offsuit, both
                      ATs+  KTo+          kicker up to one below the top card
                      KTs-K7s             a span of kickers
                      AhKh                one specific combo
                      15%  top15%         the strongest 15% of starting hands (by equity vs a random hand)
                      random              any two cards
                    Without 'range', opponents' ranges are estimated from their actions (see 'ranges').""",
            "showdown", """
                    After the river betting closes the hand waits at SHOWDOWN:
                      show <seat> <cards>  reveal each remaining player's hand; once all are known the
                                           machine settles every pot, side pots included, by itself
                      muck <seat>          a player gives up (not allowed if nobody else could win their pot)
                      winner <seat...>     settle without cards: each pot is split among the listed seats eligible for it""",
            "setup", """
                    setup players=6 stack=100 sb=0.5 bb=1 ante=0 button=1 hero=3
                      Any subset of keys; the rest keep their values. Changing players or stack resets all stacks.
                      Use 'stack <seat> <amount>' for individual stacks and 'button <seat>' to move the button.""",
            "equity", """
                    equity                                  hero vs the estimated ranges in the current hand
                    equity AsKs                             vs one random hand, preflop
                    equity AsKs vs QQ+,AKs board Kh7d2c     vs a range on a board
                    equity 7h7d vs AKs vs random            multiway: one 'vs' per opponent
                    Heads-up spots small enough are enumerated exactly; the rest use parallel Monte Carlo,
                    shown with a 95% margin of error.""");

    private final PrintStream out;
    private final Ansi ansi;

    ConsoleRenderer(PrintStream out, Ansi ansi) {
        this.out = out;
        this.ansi = ansi;
    }

    String prompt(String state) {
        return ansi.dim("[" + state + "]") + " > ";
    }

    void banner(TableConfig table) {
        out.println(ansi.bold("Poker Assistant") + " - real-time No-Limit Hold'em helper. Type 'help' for commands.");
        table(table);
        out.println(ansi.dim("Start a hand with 'new <your cards>', e.g. 'new AhKd'."));
    }

    void table(TableConfig table) {
        out.printf("Table: %d seats | blinds %s/%s%s | button seat %d | hero seat %d%n",
                table.seats().size(), table.smallBlind(), table.bigBlind(),
                table.ante().isPositive() ? " ante " + table.ante() : "", table.buttonSeat(), table.heroSeat());
        StringBuilder stacks = new StringBuilder("Stacks:");
        for (TableConfig.Seat seat : table.seats()) {
            stacks.append("  ").append(seat.number()).append(seat.number() == table.heroSeat() ? "*" : "")
                    .append('=').append(seat.stack().isZero() ? "out" : seat.stack().toString());
        }
        out.println(stacks);
    }

    void handHeader(int handNumber, HandContext hand) {
        String cards = hand.hero().filter(PlayerState::hasKnownCards)
                .map(hero -> ", holding " + CardMask.format(hero.holeCards())).orElse("");
        out.println(ansi.bold("=== Hand #" + handNumber + " ===") + " button seat " + hand.buttonSeat() + ", hero seat " + hand.heroSeat() + cards);
    }

    void logLines(List<String> lines) {
        for (String line : lines) {
            if (line.startsWith("***")) {
                out.println(ansi.bold(ansi.cyan(line)));
            } else if (line.contains(" wins ") || line.contains(" split ")) {
                out.println("  " + ansi.green(line));
            } else {
                out.println("  " + line);
            }
        }
    }

    void transitions(List<StateChange<HandState>> changes, HandStateMachine hand) {
        for (StateChange<HandState> change : changes) {
            if (change.to().isAwaitingBoard() || change.to() == HandState.SHOWDOWN) {
                out.println(ansi.dim("  -- " + change.cause() + ", pot " + hand.context().pot()));
            } else if (change.to() == HandState.COMPLETE) {
                handResult(hand.context());
            }
        }
    }

    private void handResult(HandContext hand) {
        Optional<PlayerState> hero = hand.hero();
        if (hero.isEmpty()) {
            out.println(ansi.bold("Hand complete."));
            return;
        }
        Chips start = hero.get().startingStack();
        Chips end = hero.get().stack();
        String net = end.compareTo(start) >= 0 ? "+" + end.minus(start) : "-" + start.minus(end);
        out.println(ansi.bold("Hand complete.") + " Hero " + net + " (stack " + end + ")");
    }

    String nextStepHint(HandStateMachine hand) {
        HandContext context = hand.context();
        return switch (hand.state()) {
            case IDLE -> "Start a hand: new [hero cards]";
            case PREFLOP, FLOP, TURN, RIVER -> context.playerToAct()
                    .map(player -> "Action on " + seatLabel(context, player) + ": " + context.legalActions().orElseThrow().describe())
                    .orElse("");
            case AWAITING_FLOP -> "Deal the flop: flop <c1> <c2> <c3>";
            case AWAITING_TURN -> "Deal the turn: turn <card>";
            case AWAITING_RIVER -> "Deal the river: river <card>";
            case SHOWDOWN -> "Showdown: 'show <seat> <cards>' for each remaining player (or 'muck <seat>', 'winner <seat...>')";
            case COMPLETE -> "Hand complete. Next hand: new [hero cards]";
        };
    }

    void nextStep(HandStateMachine hand) {
        String hint = nextStepHint(hand);
        out.println(hand.state().isBetting() ? ansi.yellow(hint) : ansi.dim(hint));
    }

    void status(HandSession session) {
        Optional<HandStateMachine> current = session.hand();
        if (current.isEmpty()) {
            table(session.upcomingTable());
            out.println(ansi.dim("No hand yet. Start one with 'new <your cards>'."));
            return;
        }
        HandStateMachine hand = current.get();
        HandContext context = hand.context();
        Chips pot = hand.isComplete() ? context.finalPot() : context.pot();
        out.println(ansi.bold("Hand #%d | %s | pot %s".formatted(session.handNumber(), hand.state().description(), pot)));
        out.println("Board: " + context.boardText());
        int toAct = context.playerToAct().map(PlayerState::seat).orElse(-1);
        for (PlayerState player : context.players()) {
            boolean hero = player.seat() == context.heroSeat();
            String line = "%s %-7s %-6s stack %9s  bet %7s  %-6s %s%s".formatted(
                    player.seat() == toAct ? ">" : " ",
                    "Seat " + player.seat(),
                    player.position(),
                    player.stack(),
                    player.streetBet().isPositive() ? player.streetBet() : "-",
                    player.hasFolded() ? "folded" : player.isAllIn() ? "all-in" : "",
                    hero ? "Hero " : "",
                    player.hasKnownCards() ? "[" + CardMask.format(player.holeCards()) + "]" : "");
            out.println(hero ? ansi.bold(line) : player.hasFolded() ? ansi.dim(line) : line);
        }
        // Mid-street an unmatched bet would look like a side pot, so only show settled pots.
        List<Pot> pots = context.pots();
        if (pots.size() > 1 && !context.isBettingOpen() && !hand.isComplete()) {
            StringBuilder text = new StringBuilder("Pots:");
            for (int i = 0; i < pots.size(); i++) {
                String seats = String.join(", ", pots.get(i).eligibleSeats().stream().map(String::valueOf).toList());
                text.append(i == 0 ? " main " : " | side pot " + i + " ").append(pots.get(i).amount())
                        .append(" (seats ").append(seats).append(')');
            }
            out.println(text);
        }
        if (hand.isComplete()) {
            context.winnings().forEach((seat, amount) -> out.println(ansi.green("  Seat " + seat + " won " + amount)));
        }
        nextStep(hand);
    }

    void recommendation(Recommendation recommendation) {
        out.println(ansi.cyan("  ---- advice for hero --------------------------------------------"));
        for (Recommendation.Stat stat : recommendation.stats()) {
            out.println("  %-13s %s".formatted(stat.label(), stat.value()));
        }
        out.println("  " + ansi.bold(ansi.green(">> " + recommendation.headline())) + ansi.dim("    type: " + recommendation.command()));
        for (String reason : recommendation.reasons()) {
            out.println("     - " + reason);
        }
        if (recommendation.alternative() != null) {
            out.println(ansi.dim("     alt: " + recommendation.alternative()));
        }
    }

    void equity(EquityRequest request, EquityResult result, List<String> opponents) {
        String board = request.board() == 0 ? "preflop" : "on " + CardMask.format(request.board());
        out.println(ansi.bold("Equity " + percent(result.equity())) + " for " + CardMask.format(request.hero()) + " " + board);
        out.println("  win %s | tie %s | %s".formatted(percent(result.win()), percent(result.tie()), result.describeMethod()));
        for (String opponent : opponents) {
            out.println("  vs " + opponent);
        }
    }

    void odds(Command.Odds odds) {
        double pot = odds.pot().toDouble();
        double bet = odds.bet().toDouble();
        double potWithBet = pot + bet;
        double required = PokerMath.requiredEquity(potWithBet, bet);
        out.println("A bet of %s into %s (pot now %s):".formatted(odds.bet(), odds.pot(), odds.pot().plus(odds.bet())));
        out.println("  Pot odds       %.1f : 1, so a call needs %s equity".formatted(PokerMath.potOddsRatio(potWithBet, bet), percent(required)));
        out.println("  MDF            %s of the defending range should continue".formatted(percent(PokerMath.minimumDefenseFrequency(pot, bet))));
        out.println("  Alpha          a pure bluff of this size needs %s folds".formatted(percent(PokerMath.bluffBreakEven(pot, bet))));
        out.println("  River balance  %s of a polarized betting range can be bluffs".formatted(percent(PokerMath.balancedBluffShare(pot, bet))));
        if (odds.equityPercent() != null) {
            double equity = odds.equityPercent() / 100;
            double ev = PokerMath.callEv(equity, potWithBet, bet);
            String verdict = ev >= 0 ? "calling is profitable"
                    : "fold, unless you expect to win %.2f more later (implied odds)".formatted(PokerMath.impliedOddsNeeded(equity, potWithBet, bet));
            out.println("  With %s equity: EV of calling %+.2f, %s".formatted(percent(equity), ev, verdict));
        }
    }

    void history(List<String> log) {
        if (log.isEmpty()) {
            out.println(ansi.dim("Nothing has happened yet."));
        }
        logLines(log);
    }

    void fsm(String table) {
        out.println(ansi.bold("Hand state machine (event transitions, then [automatic] completion transitions):"));
        out.print(table);
    }

    void help(String topic) {
        if (topic == null) {
            out.print(HELP);
            return;
        }
        String text = TOPICS.get(topic);
        if (text == null) {
            throw new InvalidInputException("No help topic '" + topic + "' (topics: actions, ranges, showdown, setup, equity)");
        }
        out.println(text);
    }

    void info(String text) {
        out.println(text);
    }

    void success(String text) {
        out.println(ansi.green(text));
    }

    void warn(String text) {
        out.println(ansi.yellow("! " + text));
    }

    void error(String text) {
        out.println(ansi.red("error: " + text));
    }

    void internalError(RuntimeException error) {
        StackTraceElement[] trace = error.getStackTrace();
        String where = trace.length > 0 ? " at " + trace[0] : "";
        out.println(ansi.red("internal error: " + error + where + " (this is a bug; the session is still running)"));
    }

    static String seatLabel(HandContext hand, PlayerState player) {
        return player.seat() == hand.heroSeat()
                ? "HERO (seat " + player.seat() + ", " + player.position() + ")"
                : "Seat " + player.seat() + " (" + player.position() + ")";
    }

    private static String percent(double fraction) {
        return "%.1f%%".formatted(fraction * 100);
    }
}
