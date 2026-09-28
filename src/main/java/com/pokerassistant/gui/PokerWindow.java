package com.pokerassistant.gui;

import com.pokerassistant.cards.Card;
import com.pokerassistant.cards.CardMask;
import com.pokerassistant.equity.EquityCalculator;
import com.pokerassistant.equity.StartingHandRanking;
import com.pokerassistant.error.PokerAssistantException;
import com.pokerassistant.game.ActionType;
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
import com.pokerassistant.strategy.Recommendation;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * The graphical front end: a poker-client style table driven by the same {@link HandSession} and
 * {@link DecisionEngine} as the command line. Cards are entered by clicking card slots, bets with the
 * slider, and actions with the Fold / Check-Call / Bet-Raise buttons.
 */
@SuppressWarnings("serial")
public final class PokerWindow extends JFrame implements TableView.Listener, ActionBar.Actions {

    private final HandSession session = new HandSession(TableConfig.defaults());
    private final RangeEstimator ranges = new RangeEstimator();
    private final DecisionEngine engine = new DecisionEngine(new EquityCalculator(), ranges);
    private final TableView table;
    private final ActionBar bar;
    private final AdvicePanel advice = new AdvicePanel();
    private final JTextArea history = new JTextArea();
    private int adviceRequest;

    private PokerWindow() {
        super("Poker Assistant");
        table = new TableView(this);
        bar = new ActionBar(this);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(Theme.BACKGROUND);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(table, BorderLayout.CENTER);
        getContentPane().add(sidePanel(), BorderLayout.EAST);
        getContentPane().add(bar, BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(event -> undo(),
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setMinimumSize(new Dimension(1180, 760));
        setSize(1400, 900);
        setLocationRelativeTo(null);
        refresh();
    }

    /** Opens the window on the Swing event thread. */
    public static void open() {
        StartingHandRanking.warmUpInBackground();
        SwingUtilities.invokeLater(() -> new PokerWindow().setVisible(true));
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.Category.FORMAT, Locale.ROOT);
        open();
    }

    // ------------------------------------------------------------------ table clicks

    @Override
    public void heroCardsClicked() {
        if (!session.isHandInProgress()) {
            deal();
            return;
        }
        HandContext context = currentContext();
        long heroCards = context.hero().map(PlayerState::holeCards).orElse(0L);
        CardPicker.pick(this, "Your hole cards", 2, context.knownCards() & ~heroCards)
                .ifPresent(cards -> attempt(() -> session.apply(new HandEvent.HoleCardsDealt(CardMask.of(cards)))));
    }

    @Override
    public void boardClicked() {
        pickBoard();
    }

    @Override
    public void seatCardsClicked(int seat) {
        showCards(seat);
    }

    // ------------------------------------------------------------------ action bar

    @Override
    public void deal() {
        CardPicker.pick(this, "Your hole cards", 2, 0L).ifPresent(cards -> attempt(() -> {
            session.startHand(CardMask.of(cards));
            ranges.clearOverrides();
        }));
    }

    @Override
    public void act(ActionType type, Chips amount) {
        attempt(() -> session.apply(new HandEvent.PlayerActed(type, amount)));
    }

    @Override
    public void pickBoard() {
        HandState state = session.hand().map(HandStateMachine::state).orElse(HandState.IDLE);
        if (!state.isAwaitingBoard()) {
            return;
        }
        int count = state == HandState.AWAITING_FLOP ? 3 : 1;
        String street = switch (state) {
            case AWAITING_FLOP -> "Deal the flop";
            case AWAITING_TURN -> "Deal the turn";
            default -> "Deal the river";
        };
        CardPicker.pick(this, street, count, currentContext().knownCards())
                .ifPresent(cards -> attempt(() -> session.apply(new HandEvent.BoardDealt(sorted(cards, count)))));
    }

    @Override
    public void showCards(int seat) {
        CardPicker.pick(this, "Seat " + seat + " shows", 2, currentContext().knownCards())
                .ifPresent(cards -> attempt(() -> session.apply(new HandEvent.CardsShown(seat, CardMask.of(cards)))));
    }

    @Override
    public void muck(int seat) {
        attempt(() -> session.apply(new HandEvent.Mucked(seat)));
    }

    @Override
    public void declareWinner(int seat) {
        attempt(() -> session.apply(new HandEvent.WinnersDeclared(List.of(seat))));
    }

    @Override
    public void undo() {
        if (session.hand().isEmpty()) {
            return;
        }
        attempt(session::undo);
    }

    @Override
    public void abort() {
        boolean done = attempt(() -> {
            session.abort();
            ranges.clearOverrides();
        });
        if (done) {
            bar.setStatus("Hand discarded; stacks and button are as they were before it", false);
        }
    }

    @Override
    public void setup() {
        SetupDialog.edit(this, session.upcomingTable()).ifPresent(updated -> {
            if (attempt(() -> session.updateTable(current -> updated))) {
                bar.setStatus("Table updated", false);
            }
        });
    }

    // ------------------------------------------------------------------ plumbing

    /** Runs a change to the hand; expected failures show in the status line and change nothing. Returns whether it worked. */
    private boolean attempt(Runnable change) {
        boolean done = false;
        try {
            change.run();
            bar.setStatus(session.hand().map(hand -> lastLine(hand.context().log())).orElse(" "), false);
            done = true;
        } catch (PokerAssistantException e) {
            bar.setStatus(e.getMessage(), true);
        } catch (RuntimeException e) {
            bar.setStatus("Internal error: " + e, true);
            e.printStackTrace();
        }
        refresh();
        return done;
    }

    private void refresh() {
        HandStateMachine hand = session.hand().orElse(null);
        table.display(hand != null ? hand.context().table() : session.upcomingTable(), hand);
        bar.update(hand, session.isHandInProgress(), session.events().size());
        if (hand == null) {
            history.setText("");
        } else {
            history.setText("Hand #" + session.handNumber() + "\n" + String.join("\n", hand.context().log()));
            history.setCaretPosition(history.getDocument().getLength());
        }
        requestAdvice(hand);
    }

    /**
     * Computes advice in the background when the action is on hero. The engine works on a replayed
     * copy of the hand, so the live hand can change meanwhile; stale answers are dropped.
     */
    private void requestAdvice(HandStateMachine hand) {
        int request = ++adviceRequest;
        if (hand == null) {
            advice.waiting("Deal a hand to get advice: click your card slots or press Deal hand.");
            return;
        }
        HandContext context = hand.context();
        if (hand.isComplete()) {
            advice.waiting("Hand over. Press Next hand to deal again.");
            return;
        }
        if (!context.isHeroToAct()) {
            advice.waiting(context.playerToAct()
                    .map(player -> "Seat %d (%s) is to act. Enter their action with the buttons below; advice appears when it's your turn."
                            .formatted(player.seat(), player.position()))
                    .orElse(hand.state() == HandState.SHOWDOWN
                            ? "Showdown: reveal the remaining hands, muck, or pick the winner."
                            : hand.state().description() + ". Click the board to enter the cards."));
            return;
        }
        if (context.hero().filter(PlayerState::hasKnownCards).isEmpty()) {
            advice.waiting("It's your turn. Click your card slots to enter your hole cards and get advice.");
            return;
        }
        advice.thinking();
        List<HandEvent> events = session.events();
        new SwingWorker<Recommendation, Void>() {
            @Override
            protected Recommendation doInBackground() {
                HandStateMachine copy = new HandStateMachine();
                events.forEach(copy::fire);
                return engine.recommend(copy.context());
            }

            @Override
            protected void done() {
                if (request != adviceRequest) {
                    return;
                }
                try {
                    Recommendation recommendation = get();
                    advice.show(recommendation);
                    bar.suggest(recommendation);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof PokerAssistantException expected) {
                        advice.unavailable(expected.getMessage());
                    } else {
                        advice.unavailable("internal error (" + e.getCause() + ")");
                        e.getCause().printStackTrace();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private HandContext currentContext() {
        return session.hand().orElseThrow().context();
    }

    private JPanel sidePanel() {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Theme.PANEL);
        side.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.BORDER));
        side.setPreferredSize(new Dimension(360, 0));
        side.add(advice, BorderLayout.CENTER);

        JPanel log = new JPanel(new BorderLayout(0, 6));
        log.setBackground(Theme.PANEL);
        log.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(10, 16, 10, 10)));
        JLabel title = new JLabel("HAND HISTORY");
        title.setFont(Theme.font(Font.BOLD, 12f));
        title.setForeground(Theme.MUTED);
        log.add(title, BorderLayout.NORTH);
        history.setEditable(false);
        history.setLineWrap(true);
        history.setWrapStyleWord(true);
        history.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        history.setBackground(Theme.PANEL);
        history.setForeground(Theme.TEXT);
        JScrollPane scroll = new JScrollPane(history);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(340, 230));
        Theme.darkScrollBars(scroll);
        log.add(scroll, BorderLayout.CENTER);
        side.add(log, BorderLayout.SOUTH);
        return side;
    }

    private static List<Card> sorted(List<Card> cards, int count) {
        return count == 1 ? cards : cards.stream().sorted((a, b) -> b.compareTo(a)).toList();
    }

    private static String lastLine(List<String> log) {
        return log.isEmpty() ? " " : log.get(log.size() - 1);
    }
}
