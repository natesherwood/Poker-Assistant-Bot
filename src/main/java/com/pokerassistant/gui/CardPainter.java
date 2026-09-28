package com.pokerassistant.gui;

import com.pokerassistant.cards.Card;
import com.pokerassistant.cards.Rank;
import com.pokerassistant.cards.Suit;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumMap;
import java.util.Map;

/**
 * Draws playing cards with vector suit shapes, so they look the same on every font and size. Uses a
 * four-colour deck (clubs green, diamonds blue), as most online rooms offer, to avoid misreading suits.
 */
final class CardPainter {

    private static final Map<Suit, Shape> SUITS = new EnumMap<>(Suit.class);

    static {
        SUITS.put(Suit.HEARTS, heart());
        SUITS.put(Suit.DIAMONDS, diamond());
        SUITS.put(Suit.CLUBS, club());
        SUITS.put(Suit.SPADES, spade());
    }

    private CardPainter() {
    }

    static Color suitColor(Suit suit) {
        return switch (suit) {
            case SPADES -> new Color(0x1c1f24);
            case HEARTS -> new Color(0xd3312f);
            case DIAMONDS -> new Color(0x1f64cc);
            case CLUBS -> new Color(0x1d8a3c);
        };
    }

    static String rankText(Rank rank) {
        return rank == Rank.TEN ? "10" : String.valueOf(rank.symbol());
    }

    /** The suit symbol scaled into a {@code size} x {@code size} box at (x, y). */
    static Shape suit(Suit suit, double x, double y, double size) {
        AffineTransform transform = new AffineTransform(size, 0, 0, size, x, y);
        return transform.createTransformedShape(SUITS.get(suit));
    }

    static void paintFace(Graphics2D g, Card card, double x, double y, double w, double h) {
        RoundRectangle2D outline = outline(x, y, w, h);
        g.setPaint(new GradientPaint((float) x, (float) y, Color.WHITE, (float) x, (float) (y + h), new Color(0xe9ebef)));
        g.fill(outline);
        g.setColor(new Color(0, 0, 0, 90));
        g.setStroke(new BasicStroke(1f));
        g.draw(outline);

        Color color = suitColor(card.suit());
        g.setColor(color);
        String rank = rankText(card.rank());
        float rankSize = (float) (h * (rank.length() > 1 ? 0.30 : 0.36));
        g.setFont(Theme.font(Font.BOLD, rankSize));
        g.drawString(rank, (float) (x + w * 0.09), (float) (y + h * 0.33));
        g.fill(suit(card.suit(), x + w * 0.10, y + h * 0.40, w * 0.24));
        g.fill(suit(card.suit(), x + w * 0.42, y + h * 0.50, w * 0.50));
    }

    static void paintBack(Graphics2D g, double x, double y, double w, double h) {
        RoundRectangle2D outline = outline(x, y, w, h);
        g.setPaint(new GradientPaint((float) x, (float) y, new Color(0xb3322c), (float) (x + w), (float) (y + h), new Color(0x6d1512)));
        g.fill(outline);
        Shape clip = g.getClip();
        double inset = w * 0.1;
        RoundRectangle2D inner = new RoundRectangle2D.Double(x + inset, y + inset, w - 2 * inset, h - 2 * inset, w * 0.1, w * 0.1);
        g.clip(inner);
        g.setColor(new Color(255, 255, 255, 40));
        g.setStroke(new BasicStroke(1.2f));
        for (double d = -h; d < w + h; d += w * 0.16) {
            g.draw(new java.awt.geom.Line2D.Double(x + d, y, x + d + h, y + h));
            g.draw(new java.awt.geom.Line2D.Double(x + d + h, y, x + d, y + h));
        }
        g.setClip(clip);
        g.setColor(new Color(255, 255, 255, 150));
        g.draw(inner);
        g.setColor(new Color(0, 0, 0, 110));
        g.draw(outline);
    }

    /** An empty place for a card; highlighted when clicking it will pick cards. */
    static void paintSlot(Graphics2D g, double x, double y, double w, double h, boolean clickable, boolean hover) {
        RoundRectangle2D outline = outline(x, y, w, h);
        g.setColor(hover ? new Color(255, 255, 255, 50) : new Color(0, 0, 0, 45));
        g.fill(outline);
        Color edge = clickable ? Theme.GOLD : new Color(255, 255, 255, 70);
        g.setColor(hover ? edge.brighter() : edge);
        g.setStroke(new BasicStroke(clickable ? 2f : 1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{5f, 4f}, 0f));
        g.draw(outline);
        if (clickable) {
            double arm = Math.min(w, h) * 0.16;
            double cx = x + w / 2;
            double cy = y + h / 2;
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new java.awt.geom.Line2D.Double(cx - arm, cy, cx + arm, cy));
            g.draw(new java.awt.geom.Line2D.Double(cx, cy - arm, cx, cy + arm));
        }
    }

    private static RoundRectangle2D outline(double x, double y, double w, double h) {
        return new RoundRectangle2D.Double(x, y, w, h, w * 0.16, w * 0.16);
    }

    // Suit shapes in a unit square.

    private static Shape heart() {
        Area area = new Area(new Ellipse2D.Double(0.02, 0.05, 0.5, 0.5));
        area.add(new Area(new Ellipse2D.Double(0.48, 0.05, 0.5, 0.5)));
        area.add(new Area(triangle(0.05, 0.42, 0.95, 0.42, 0.5, 0.97)));
        return area;
    }

    private static Shape diamond() {
        Path2D path = new Path2D.Double();
        path.moveTo(0.5, 0);
        path.lineTo(0.9, 0.5);
        path.lineTo(0.5, 1);
        path.lineTo(0.1, 0.5);
        path.closePath();
        return path;
    }

    private static Shape spade() {
        Area area = new Area(new Ellipse2D.Double(0.02, 0.33, 0.5, 0.45));
        area.add(new Area(new Ellipse2D.Double(0.48, 0.33, 0.5, 0.45)));
        area.add(new Area(triangle(0.5, 0.0, 0.06, 0.52, 0.94, 0.52)));
        area.add(new Area(triangle(0.5, 0.6, 0.28, 1.0, 0.72, 1.0)));
        return area;
    }

    private static Shape club() {
        Area area = new Area(new Ellipse2D.Double(0.28, 0.02, 0.44, 0.44));
        area.add(new Area(new Ellipse2D.Double(0.03, 0.36, 0.44, 0.44)));
        area.add(new Area(new Ellipse2D.Double(0.53, 0.36, 0.44, 0.44)));
        area.add(new Area(triangle(0.5, 0.45, 0.28, 1.0, 0.72, 1.0)));
        return area;
    }

    private static Path2D triangle(double x1, double y1, double x2, double y2, double x3, double y3) {
        Path2D path = new Path2D.Double();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.closePath();
        return path;
    }
}
