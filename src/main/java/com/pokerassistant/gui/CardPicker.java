package com.pokerassistant.gui;

import com.pokerassistant.cards.Card;
import com.pokerassistant.cards.Rank;
import com.pokerassistant.cards.Suit;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A modal deck of all 52 cards: click cards to select them; the dialog closes by itself once
 * {@code count} are chosen. Cards already in play are greyed out and can't be picked.
 */
@SuppressWarnings("serial")
final class CardPicker extends JDialog {

    private static final Suit[] ROWS = {Suit.SPADES, Suit.HEARTS, Suit.DIAMONDS, Suit.CLUBS};
    private static final int CARD_W = 50;
    private static final int CARD_H = 70;

    private final int count;
    private final List<Card> selected = new ArrayList<>();
    private final JLabel hint = new JLabel();
    private boolean confirmed;

    private CardPicker(Window owner, String title, int count, long unavailable) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.count = count;
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Theme.PANEL);
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 14, 16));

        JLabel heading = new JLabel(title);
        heading.setFont(Theme.font(Font.BOLD, 17f));
        heading.setForeground(Theme.TEXT);
        root.add(heading, BorderLayout.NORTH);

        JPanel deck = new JPanel(new GridLayout(ROWS.length, Rank.values().length, 6, 8));
        deck.setOpaque(false);
        for (Suit suit : ROWS) {
            for (int r = Rank.values().length - 1; r >= 0; r--) {
                Card card = Card.of(Rank.ofOrdinal(r), suit);
                deck.add(new Cell(card, (unavailable & card.mask()) == 0));
            }
        }
        root.add(deck, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        hint.setFont(Theme.font(Font.PLAIN, 13f));
        hint.setForeground(Theme.MUTED);
        hint.setPreferredSize(new Dimension(420, 20));
        footer.add(hint, BorderLayout.WEST);
        PokerButton cancel = new PokerButton("Cancel", Theme.NEUTRAL, 13f);
        cancel.addActionListener(event -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        footer.add(buttons, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(event -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setContentPane(root);
        updateHint();
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Shows the picker and waits; empty when the user cancels. */
    static Optional<List<Card>> pick(Component parent, String title, int count, long unavailable) {
        CardPicker picker = new CardPicker(SwingUtilities.getWindowAncestor(parent), title, count, unavailable);
        picker.setVisible(true);
        return picker.confirmed ? Optional.of(List.copyOf(picker.selected)) : Optional.empty();
    }

    private void toggle(Cell cell) {
        if (confirmed) {
            return;
        }
        if (selected.remove(cell.card)) {
            cell.repaint();
        } else if (selected.size() < count) {
            selected.add(cell.card);
            cell.repaint();
        }
        updateHint();
        if (selected.size() == count) {
            confirmed = true;
            Timer close = new Timer(160, event -> dispose());
            close.setRepeats(false);
            close.start();
        }
    }

    private void updateHint() {
        String chosen = selected.isEmpty() ? "" : "   Selected: " + String.join(" ", selected.stream().map(Card::toString).toList());
        hint.setText("Pick %d card%s (%d of %d)%s".formatted(count, count == 1 ? "" : "s", selected.size(), count, chosen));
    }

    /** One clickable card of the deck. */
    @SuppressWarnings("serial")
    private final class Cell extends JComponent {

        private final Card card;
        private final boolean available;
        private boolean hover;

        Cell(Card card, boolean available) {
            this.card = card;
            this.available = available;
            setPreferredSize(new Dimension(CARD_W, CARD_H));
            setToolTipText(available ? card.toString() : card + " is already in play");
            if (available) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent event) {
                        toggle(Cell.this);
                    }

                    @Override
                    public void mouseEntered(MouseEvent event) {
                        hover = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent event) {
                        hover = false;
                        repaint();
                    }
                });
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean chosen = selected.contains(card);
            double lift = chosen ? 0 : hover ? 2 : 4;
            if (!available) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
            }
            CardPainter.paintFace(g, card, 1, lift + 1, getWidth() - 2, getHeight() - 6);
            if (chosen) {
                g.setColor(Theme.GOLD);
                g.setStroke(new BasicStroke(3f));
                g.draw(new RoundRectangle2D.Double(1.5, 1.5, getWidth() - 3, getHeight() - 7, 8, 8));
            }
            g.dispose();
        }
    }
}
