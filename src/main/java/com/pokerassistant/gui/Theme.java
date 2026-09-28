package com.pokerassistant.gui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/** Colours and fonts shared by the table window: a dark client with a green felt, like online poker rooms. */
final class Theme {

    static final Color BACKGROUND = new Color(0x12161c);
    static final Color BACKGROUND_GLOW = new Color(0x1d2530);
    static final Color PANEL = new Color(0x1b2129);
    static final Color PANEL_RAISED = new Color(0x252d38);
    static final Color BORDER = new Color(0x333d4a);
    static final Color TEXT = new Color(0xe9edf2);
    static final Color MUTED = new Color(0x8e9aab);

    static final Color FELT = new Color(0x1f8150);
    static final Color FELT_EDGE = new Color(0x0e4d2d);
    static final Color RAIL = new Color(0x4a2f1b);
    static final Color RAIL_EDGE = new Color(0x24160c);

    static final Color GOLD = new Color(0xf5c451);
    static final Color WIN = new Color(0x4cd18a);
    static final Color ERROR = new Color(0xff7a70);

    static final Color FOLD = new Color(0xc0453c);
    static final Color CALL = new Color(0x2f7fd6);
    static final Color RAISE = new Color(0x2c9a57);
    static final Color NEUTRAL = new Color(0x3a4452);

    private static final String FAMILY = "Segoe UI";

    private Theme() {
    }

    static Font font(int style, float size) {
        return new Font(FAMILY, style, 1).deriveFont(size);
    }

    /** Thin dark scroll bars in place of the light default ones. */
    static void darkScrollBars(JScrollPane scroll) {
        for (JScrollBar bar : List.of(scroll.getVerticalScrollBar(), scroll.getHorizontalScrollBar())) {
            bar.setUI(new DarkScrollBarUI());
            bar.setOpaque(false);
            bar.setPreferredSize(new Dimension(8, 8));
            bar.setUnitIncrement(16);
        }
    }

    private static final class DarkScrollBarUI extends BasicScrollBarUI {

        @Override
        protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
        }

        @Override
        protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
            if (bounds.isEmpty()) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(isThumbRollover() ? MUTED : BORDER);
            g.fill(new RoundRectangle2D.Double(bounds.x + 1, bounds.y + 1, bounds.width - 2, bounds.height - 2, 6, 6));
            g.dispose();
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return invisibleButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return invisibleButton();
        }

        private static JButton invisibleButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            return button;
        }
    }
}
