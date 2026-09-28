package com.pokerassistant.gui;

import com.pokerassistant.error.InvalidInputException;
import com.pokerassistant.game.ActionType;
import com.pokerassistant.game.Chips;
import com.pokerassistant.game.HandContext;
import com.pokerassistant.game.HandState;
import com.pokerassistant.game.HandStateMachine;
import com.pokerassistant.game.LegalActions;
import com.pokerassistant.game.PlayerState;
import com.pokerassistant.strategy.Recommendation;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The controls under the table. What it shows follows the hand: a Deal button between hands, the
 * betting controls (fold / check-call / bet-raise with a sizing slider) while betting is open, a prompt
 * for the next board cards, showdown choices, and the result. Betting always acts for whoever the
 * hand says is to act, so opponents' actions are entered with the same buttons.
 */
@SuppressWarnings("serial")
final class ActionBar extends JPanel {

    interface Actions {
        void deal();

        void act(ActionType type, Chips amount);

        void pickBoard();

        void showCards(int seat);

        void muck(int seat);

        void declareWinner(int seat);

        void undo();

        void abort();

        void setup();
    }

    private static final String IDLE = "idle";
    private static final String BET = "bet";
    private static final String BOARD = "board";
    private static final String SHOWDOWN = "showdown";
    private static final String COMPLETE = "complete";

    private final Actions actions;
    private final CardLayout modes = new CardLayout();
    private final JPanel modePanel = new JPanel(modes);
    private final JLabel status = new JLabel(" ");

    private final PokerButton undo = new PokerButton("Undo", Theme.NEUTRAL, 13f);
    private final PokerButton abort = new PokerButton("Abort hand", Theme.NEUTRAL, 13f);
    private final PokerButton setup = new PokerButton("Table setup", Theme.NEUTRAL, 13f);

    private final JLabel toAct = label("", 15f, Font.PLAIN, Theme.TEXT);
    private final PokerButton fold = new PokerButton("FOLD", Theme.FOLD, 17f);
    private final PokerButton checkCall = new PokerButton("CHECK", Theme.CALL, 17f);
    private final PokerButton betRaise = new PokerButton("BET", Theme.RAISE, 17f);
    private final JSlider slider = new JSlider(0, 1, 0);
    private final JTextField amountField = new JTextField(7);
    private final List<PokerButton> presets = new ArrayList<>();

    private final JLabel boardPrompt = label("", 16f, Font.PLAIN, Theme.TEXT);
    private final PokerButton boardButton = new PokerButton("Pick cards", Theme.RAISE, 15f);
    private final JPanel showdownSeats = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
    private final JLabel result = label("", 16f, Font.BOLD, Theme.WIN);

    private LegalActions legal;
    private HandContext context;
    private Chips amount = Chips.ZERO;
    private Chips step = Chips.ofCents(10);
    private int decision = -1;
    private boolean syncing;

    ActionBar(Actions actions) {
        super(new BorderLayout(0, 6));
        this.actions = actions;
        setBackground(Theme.PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(8, 14, 12, 14)));

        status.setFont(Theme.font(Font.PLAIN, 13f));
        status.setForeground(Theme.MUTED);
        add(status, BorderLayout.NORTH);

        JPanel utility = new JPanel();
        utility.setOpaque(false);
        utility.setLayout(new BoxLayout(utility, BoxLayout.Y_AXIS));
        for (PokerButton button : List.of(undo, abort, setup)) {
            button.setAlignmentX(LEFT_ALIGNMENT);
            button.setMaximumSize(new Dimension(130, 32));
            button.setPreferredSize(new Dimension(130, 32));
            utility.add(button);
            utility.add(Box.createVerticalStrut(5));
        }
        undo.addActionListener(event -> actions.undo());
        abort.addActionListener(event -> actions.abort());
        setup.addActionListener(event -> actions.setup());
        add(utility, BorderLayout.WEST);

        modePanel.setOpaque(false);
        modePanel.add(idlePanel(), IDLE);
        modePanel.add(betPanel(), BET);
        modePanel.add(boardPanel(), BOARD);
        modePanel.add(showdownPanel(), SHOWDOWN);
        modePanel.add(completePanel(), COMPLETE);
        add(modePanel, BorderLayout.CENTER);
    }

    void setStatus(String text, boolean error) {
        status.setText(text == null || text.isBlank() ? " " : text);
        status.setForeground(error ? Theme.ERROR : Theme.MUTED);
    }

    /**
     * Shows the controls for the hand's current state. {@code decisionId} changes whenever a new
     * decision starts, which resets the bet amount and the suggestion highlight.
     */
    void update(HandStateMachine hand, boolean handInProgress, int decisionId) {
        undo.setEnabled(hand != null);
        abort.setEnabled(handInProgress);
        setup.setEnabled(!handInProgress);
        fold.setSuggested(false);
        checkCall.setSuggested(false);
        betRaise.setSuggested(false);

        if (hand == null) {
            modes.show(modePanel, IDLE);
            return;
        }
        HandState state = hand.state();
        context = hand.context();
        if (context.legalActions().isPresent()) {
            showBetting(context.legalActions().get(), decisionId);
            modes.show(modePanel, BET);
        } else if (state.isAwaitingBoard()) {
            String street = switch (state) {
                case AWAITING_FLOP -> "flop";
                case AWAITING_TURN -> "turn";
                default -> "river";
            };
            boardPrompt.setText("Betting is closed. Deal the " + street + ": click the board or");
            boardButton.setText("Pick the " + street);
            modes.show(modePanel, BOARD);
        } else if (state == HandState.SHOWDOWN) {
            rebuildShowdown();
            modes.show(modePanel, SHOWDOWN);
        } else {
            result.setText(describeResult());
            modes.show(modePanel, COMPLETE);
        }
    }

    /** Rings the recommended button in gold and moves the slider to the recommended size. */
    void suggest(Recommendation recommendation) {
        if (legal == null) {
            return;
        }
        switch (recommendation.action()) {
            case FOLD -> fold.setSuggested(true);
            case CHECK, CALL -> checkCall.setSuggested(true);
            case BET, RAISE -> {
                betRaise.setSuggested(true);
                if (recommendation.amount() != null) {
                    setAmount(recommendation.amount());
                }
            }
            case ALL_IN -> {
                if (betRaise.isEnabled()) {
                    betRaise.setSuggested(true);
                    setAmount(legal.maxTotal());
                } else {
                    checkCall.setSuggested(true);
                }
            }
        }
    }

    // ------------------------------------------------------------------ betting

    private void showBetting(LegalActions actionsNow, int decisionId) {
        legal = actionsNow;
        PlayerState player = context.playerToAct().orElseThrow();
        boolean hero = player.seat() == context.heroSeat();
        String who = hero ? "<b style='color:#f5c451'>Your turn</b>" : "<b>Seat %d (%s)</b> to act".formatted(player.seat(), player.position());
        String price = legal.canCall() ? "To call " + legal.toCall() : "No bet to call";
        toAct.setText("<html>%s<br><span style='color:#8e9aab'>%s &nbsp;·&nbsp; stack %s</span></html>".formatted(who, price, player.stack()));

        fold.setEnabled(legal.canFold());
        checkCall.setEnabled(legal.canCheck() || legal.canCall());
        checkCall.setText(legal.canCheck() ? "CHECK" : "CALL " + legal.toCall() + (legal.callIsAllIn() ? " (ALL-IN)" : ""));

        boolean sized = legal.canBet() || legal.canRaise();
        betRaise.setEnabled(sized);
        slider.setEnabled(sized && legal.minTotal().isLessThan(legal.maxTotal()));
        amountField.setEnabled(sized);
        presets.forEach(button -> button.setEnabled(sized));
        if (sized) {
            step = BetSizing.step(context.bigBlind());
            syncing = true;
            slider.setMaximum(Math.max(1, BetSizing.notches(legal.minTotal(), legal.maxTotal(), step)));
            syncing = false;
            if (decisionId != decision) {
                setAmount(legal.minTotal());
            } else {
                setAmount(amount);
            }
        } else {
            betRaise.setText(legal.canBet() ? "BET" : "RAISE");
            amountField.setText("");
        }
        decision = decisionId;
        revalidate();
    }

    private void setAmount(Chips requested) {
        amount = BetSizing.clamp(requested, legal.minTotal(), legal.maxTotal());
        syncing = true;
        slider.setValue(BetSizing.nearestNotch(amount, legal.minTotal(), legal.maxTotal(), step));
        syncing = false;
        amountField.setText(amount.toString());
        refreshBetLabel();
    }

    private void refreshBetLabel() {
        if (amount.equals(legal.maxTotal())) {
            betRaise.setText("ALL-IN " + amount);
        } else {
            betRaise.setText((legal.canBet() ? "BET " : "RAISE TO ") + amount);
        }
        betRaise.revalidate();
    }

    private void onSlider() {
        if (syncing || legal == null) {
            return;
        }
        int notch = slider.getValue();
        amount = notch >= slider.getMaximum() ? legal.maxTotal() : BetSizing.atNotch(notch, legal.minTotal(), legal.maxTotal(), step);
        amountField.setText(amount.toString());
        refreshBetLabel();
    }

    private void onAmountTyped() {
        if (legal == null || !amountField.isEnabled()) {
            return;
        }
        try {
            setAmount(Chips.parse(amountField.getText()));
            setStatus(" ", false);
        } catch (InvalidInputException e) {
            setStatus(e.getMessage(), true);
            setAmount(amount);
        }
    }

    private void preset(double potFraction) {
        if (legal == null) {
            return;
        }
        setAmount(BetSizing.potFraction(potFraction, context.pot(), context.currentBet(), legal.toCall(), legal.minTotal(), legal.maxTotal()));
    }

    private void betOrRaise() {
        if (amount.equals(legal.maxTotal())) {
            actions.act(ActionType.ALL_IN, null);
        } else {
            actions.act(legal.canBet() ? ActionType.BET : ActionType.RAISE, amount);
        }
    }

    private JPanel betPanel() {
        JPanel panel = new JPanel(new BorderLayout(18, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
        toAct.setPreferredSize(new Dimension(230, 50));
        panel.add(toAct, BorderLayout.WEST);

        JPanel sizing = new JPanel();
        sizing.setOpaque(false);
        sizing.setLayout(new BoxLayout(sizing, BoxLayout.Y_AXIS));
        JPanel presetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        presetRow.setOpaque(false);
        addPreset(presetRow, "Min", () -> setAmount(legal.minTotal()));
        addPreset(presetRow, "⅓ Pot", () -> preset(1.0 / 3));
        addPreset(presetRow, "½ Pot", () -> preset(0.5));
        addPreset(presetRow, "¾ Pot", () -> preset(0.75));
        addPreset(presetRow, "Pot", () -> preset(1.0));
        addPreset(presetRow, "All-in", () -> setAmount(legal.maxTotal()));
        presetRow.setAlignmentX(LEFT_ALIGNMENT);
        sizing.add(presetRow);
        sizing.add(Box.createVerticalStrut(6));

        JPanel sliderRow = new JPanel(new BorderLayout(10, 0));
        sliderRow.setOpaque(false);
        slider.setOpaque(false);
        slider.setUI(new FeltSliderUI(slider));
        slider.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        slider.addChangeListener(event -> onSlider());
        sliderRow.add(slider, BorderLayout.CENTER);
        amountField.setFont(Theme.font(Font.BOLD, 15f));
        amountField.setHorizontalAlignment(SwingConstants.RIGHT);
        amountField.setBackground(Theme.BACKGROUND);
        amountField.setForeground(Theme.TEXT);
        amountField.setCaretColor(Theme.TEXT);
        amountField.setDisabledTextColor(Theme.MUTED);
        amountField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER), BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        amountField.addActionListener(event -> onAmountTyped());
        amountField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                onAmountTyped();
            }
        });
        amountField.setPreferredSize(new Dimension(90, 32));
        sliderRow.add(amountField, BorderLayout.EAST);
        sliderRow.setAlignmentX(LEFT_ALIGNMENT);
        sliderRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        sizing.add(sliderRow);
        panel.add(sizing, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        for (PokerButton button : List.of(fold, checkCall, betRaise)) {
            button.setPreferredSize(new Dimension(button == betRaise ? 190 : 160, 62));
            buttons.add(button);
        }
        fold.addActionListener(event -> actions.act(ActionType.FOLD, null));
        checkCall.addActionListener(event -> actions.act(legal.canCheck() ? ActionType.CHECK : ActionType.CALL, null));
        betRaise.addActionListener(event -> betOrRaise());
        panel.add(buttons, BorderLayout.EAST);
        return panel;
    }

    private void addPreset(JPanel row, String text, Runnable action) {
        PokerButton button = new PokerButton(text, Theme.PANEL_RAISED, 12.5f);
        button.addActionListener(event -> action.run());
        presets.add(button);
        row.add(button);
    }

    // ------------------------------------------------------------------ other modes

    private JPanel idlePanel() {
        JPanel panel = centeredRow();
        panel.add(label("No hand in progress. Pick your two hole cards to deal the next hand.", 16f, Font.PLAIN, Theme.TEXT));
        PokerButton deal = new PokerButton("Deal hand", Theme.RAISE, 17f);
        deal.setPreferredSize(new Dimension(170, 52));
        deal.addActionListener(event -> actions.deal());
        panel.add(deal);
        return panel;
    }

    private JPanel boardPanel() {
        JPanel panel = centeredRow();
        panel.add(boardPrompt);
        boardButton.setPreferredSize(new Dimension(170, 52));
        boardButton.addActionListener(event -> actions.pickBoard());
        panel.add(boardButton);
        return panel;
    }

    private JPanel showdownPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
        panel.add(label("Showdown: reveal each hand (it settles by itself once all are known), muck, or pick the winner.",
                14f, Font.PLAIN, Theme.TEXT), BorderLayout.NORTH);
        showdownSeats.setOpaque(false);
        panel.add(showdownSeats, BorderLayout.CENTER);
        return panel;
    }

    private void rebuildShowdown() {
        showdownSeats.removeAll();
        for (PlayerState player : context.livePlayers()) {
            JPanel seat = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            seat.setBackground(Theme.PANEL_RAISED);
            seat.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
            boolean hero = player.seat() == context.heroSeat();
            seat.add(label((hero ? "You" : "Seat " + player.seat()) + " (" + player.position() + ")", 13f, Font.BOLD, Theme.TEXT));
            if (!player.hasKnownCards()) {
                seat.add(small("Show…", Theme.CALL, () -> actions.showCards(player.seat())));
            }
            seat.add(small("Muck", Theme.FOLD, () -> actions.muck(player.seat())));
            seat.add(small("Wins", Theme.RAISE, () -> actions.declareWinner(player.seat())));
            showdownSeats.add(seat);
        }
        showdownSeats.revalidate();
        showdownSeats.repaint();
    }

    private JPanel completePanel() {
        JPanel panel = centeredRow();
        panel.add(result);
        PokerButton next = new PokerButton("Next hand", Theme.RAISE, 17f);
        next.setPreferredSize(new Dimension(170, 52));
        next.addActionListener(event -> actions.deal());
        panel.add(next);
        return panel;
    }

    private String describeResult() {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Integer, Chips> won : context.winnings().entrySet()) {
            boolean hero = won.getKey() == context.heroSeat();
            parts.add((hero ? "You win " : "Seat " + won.getKey() + " wins ") + won.getValue());
        }
        return parts.isEmpty() ? "Hand complete" : String.join("   ·   ", parts);
    }

    private PokerButton small(String text, Color color, Runnable action) {
        PokerButton button = new PokerButton(text, color, 12.5f);
        button.addActionListener(event -> action.run());
        return button;
    }

    private static JPanel centeredRow() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 8));
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel label(String text, float size, int style, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(style, size));
        label.setForeground(color);
        return label;
    }

    /** A flat slider: dark track, green fill up to the thumb, round white thumb. */
    private static final class FeltSliderUI extends BasicSliderUI {

        FeltSliderUI(JSlider slider) {
            super(slider);
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(20, 20);
        }

        @Override
        public void paintTrack(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Rectangle track = trackRect;
            double y = track.getCenterY() - 3;
            g.setColor(Theme.BACKGROUND);
            g.fill(new RoundRectangle2D.Double(track.x, y, track.width, 6, 6, 6));
            if (slider.isEnabled()) {
                g.setColor(Theme.RAISE);
                g.fill(new RoundRectangle2D.Double(track.x, y, thumbRect.getCenterX() - track.x, 6, 6, 6));
            }
            g.dispose();
        }

        @Override
        public void paintThumb(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Rectangle thumb = thumbRect;
            g.setColor(slider.isEnabled() ? Color.WHITE : Theme.MUTED);
            g.fill(new Ellipse2D.Double(thumb.x + 1, thumb.getCenterY() - 9, 18, 18));
            g.dispose();
        }

        @Override
        public void paintFocus(Graphics graphics) {
        }
    }
}
