package com.pokerassistant.gui;

import javax.swing.JButton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/** A flat, rounded action button in one base colour; {@link #setSuggested} rings it in gold. */
@SuppressWarnings("serial")
final class PokerButton extends JButton {

    private final Color base;
    private boolean suggested;

    PokerButton(String text, Color base, float fontSize) {
        super(text);
        this.base = base;
        setFont(Theme.font(Font.BOLD, fontSize));
        setForeground(Color.WHITE);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void setSuggested(boolean suggested) {
        if (this.suggested != suggested) {
            this.suggested = suggested;
            repaint();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        return new Dimension(metrics.stringWidth(getText()) + 34, metrics.getHeight() + 16);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        Color fill = !isEnabled() ? new Color(0x2a3038)
                : getModel().isPressed() ? base.darker()
                : getModel().isRollover() ? brighter(base) : base;
        RoundRectangle2D shape = new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 12, 12);
        g.setPaint(new GradientPaint(0, 0, brighter(fill), 0, h, fill));
        g.fill(shape);
        if (suggested && isEnabled()) {
            g.setColor(Theme.GOLD);
            g.setStroke(new BasicStroke(2.5f));
            g.draw(new RoundRectangle2D.Double(2, 2, w - 4, h - 4, 11, 11));
        }
        g.setFont(getFont());
        FontMetrics metrics = g.getFontMetrics();
        g.setColor(isEnabled() ? getForeground() : new Color(0x646d78));
        String text = getText();
        g.drawString(text, (w - metrics.stringWidth(text)) / 2, (h - metrics.getHeight()) / 2 + metrics.getAscent());
        g.dispose();
    }

    private static Color brighter(Color color) {
        return new Color(Math.min(255, color.getRed() + 22), Math.min(255, color.getGreen() + 22), Math.min(255, color.getBlue() + 22));
    }
}
