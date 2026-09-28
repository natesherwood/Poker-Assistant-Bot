package com.pokerassistant.gui;

import com.pokerassistant.cards.Card;
import com.pokerassistant.cards.CardMask;
import com.pokerassistant.game.Chips;
import com.pokerassistant.game.HandContext;
import com.pokerassistant.game.HandState;
import com.pokerassistant.game.HandStateMachine;
import com.pokerassistant.game.PlayerState;
import com.pokerassistant.game.Pot;
import com.pokerassistant.game.TableConfig;

import javax.swing.JComponent;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The poker table: felt, seats around it (hero always at the bottom), board, pot, bets and the dealer
 * button. Card places that need input are drawn as gold dashed slots; clicking one tells the
 * {@link Listener}, which opens the card picker.
 */
@SuppressWarnings("serial")
final class TableView extends JComponent {

    interface Listener {
        /** Hero's hole-card slots: deals a new hand when none is running, otherwise sets hero's cards. */
        void heroCardsClicked();

        /** The next board cards are due. */
        void boardClicked();

        /** An opponent's face-down cards at showdown: enter what they show. */
        void seatCardsClicked(int seat);
    }

    private record Hotspot(Rectangle2D area, Runnable action) {
    }

    private final Listener listener;
    private final List<Hotspot> hotspots = new ArrayList<>();
    private TableConfig table;
    private HandStateMachine hand;
    private Point mouse;

    TableView(Listener listener) {
        this.listener = listener;
        setPreferredSize(new Dimension(900, 640));
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                mouse = event.getPoint();
                setCursor(Cursor.getPredefinedCursor(hotspotAt(mouse).isPresent() ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                mouse = null;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                hotspotAt(event.getPoint()).ifPresent(hotspot -> hotspot.action().run());
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    /** Shows {@code hand}, or just the seated table between hands when {@code hand} is null. */
    void display(TableConfig table, HandStateMachine hand) {
        this.table = table;
        this.hand = hand;
        repaint();
    }

    private Optional<Hotspot> hotspotAt(Point point) {
        return hotspots.stream().filter(hotspot -> hotspot.area().contains(point)).findFirst();
    }

    private boolean hovered(Rectangle2D area) {
        return mouse != null && area.contains(mouse);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        hotspots.clear();

        double w = getWidth();
        double h = getHeight();
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w / 2, h * 0.45), (float) Math.max(w, h) * 0.7f,
                new float[]{0f, 1f}, new Color[]{Theme.BACKGROUND_GLOW, Theme.BACKGROUND}));
        g.fill(new Rectangle2D.Double(0, 0, w, h));
        if (table == null) {
            g.dispose();
            return;
        }

        double scale = Math.max(0.65, Math.min(1.35, Math.min(w / 1000, h / 680)));
        double tableW = w * 0.78;
        double tableH = Math.min(h * 0.58, tableW * 0.5);
        double cx = w / 2;
        // Seats above the table carry cards over their boxes, so the whole layout sits a little low.
        double cy = h * 0.5 + 12 * scale;
        paintFelt(g, cx, cy, tableW, tableH, scale);
        paintCenter(g, cx, cy, scale);

        List<TableConfig.Seat> seats = table.seats();
        int heroIndex = 0;
        for (int i = 0; i < seats.size(); i++) {
            if (seats.get(i).number() == table.heroSeat()) {
                heroIndex = i;
            }
        }
        for (int i = 0; i < seats.size(); i++) {
            int fromHero = Math.floorMod(i - heroIndex, seats.size());
            double angle = Math.PI / 2 + fromHero * 2 * Math.PI / seats.size();
            Point2D.Double at = new Point2D.Double(cx + (tableW / 2 + 12 * scale) * Math.cos(angle), cy + (tableH / 2 + 22 * scale) * Math.sin(angle));
            paintSeat(g, seats.get(i), at, new Point2D.Double(cx, cy), scale);
        }
        g.dispose();
    }

    private void paintFelt(Graphics2D g, double cx, double cy, double tableW, double tableH, double scale) {
        double x = cx - tableW / 2;
        double y = cy - tableH / 2;
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Double(x - 4, y + 10 * scale, tableW + 8, tableH + 8, tableH, tableH));
        g.setPaint(new GradientPaint(0, (float) y, Theme.RAIL, 0, (float) (y + tableH), Theme.RAIL_EDGE));
        g.fill(new RoundRectangle2D.Double(x, y, tableW, tableH, tableH, tableH));

        double rail = 16 * scale;
        RoundRectangle2D felt = new RoundRectangle2D.Double(x + rail, y + rail, tableW - 2 * rail, tableH - 2 * rail, tableH - 2 * rail, tableH - 2 * rail);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy - tableH * 0.1), (float) (tableW * 0.55),
                new float[]{0f, 1f}, new Color[]{Theme.FELT, Theme.FELT_EDGE}));
        g.fill(felt);
        g.setColor(new Color(255, 255, 255, 28));
        g.setStroke(new BasicStroke(2f));
        double line = 22 * scale;
        g.draw(new RoundRectangle2D.Double(x + rail + line, y + rail + line, tableW - 2 * (rail + line), tableH - 2 * (rail + line),
                tableH - 2 * (rail + line), tableH - 2 * (rail + line)));
    }

    private void paintCenter(Graphics2D g, double cx, double cy, double scale) {
        double cardW = 58 * scale;
        double cardH = 81 * scale;
        double gap = 8 * scale;
        double left = cx - (5 * cardW + 4 * gap) / 2;
        double top = cy - cardH / 2;

        if (hand == null) {
            for (int i = 0; i < 5; i++) {
                CardPainter.paintSlot(g, left + i * (cardW + gap), top, cardW, cardH, false, false);
            }
            caption(g, "Click your card slots or press Deal to start a hand", cx, top + cardH + 30 * scale, scale, Theme.TEXT);
            return;
        }

        HandContext context = hand.context();
        List<Card> board = context.board();
        int due = switch (hand.state()) {
            case AWAITING_FLOP -> 3;
            case AWAITING_TURN, AWAITING_RIVER -> 1;
            default -> 0;
        };
        Rectangle2D dueArea = null;
        for (int i = board.size(); i < board.size() + due; i++) {
            Rectangle2D slot = new Rectangle2D.Double(left + i * (cardW + gap), top, cardW, cardH);
            dueArea = dueArea == null ? slot : dueArea.createUnion(slot);
        }
        if (dueArea != null) {
            hotspots.add(new Hotspot(dueArea, listener::boardClicked));
        }
        boolean dueHover = dueArea != null && hovered(dueArea);
        for (int i = 0; i < 5; i++) {
            double x = left + i * (cardW + gap);
            if (i < board.size()) {
                CardPainter.paintFace(g, board.get(i), x, top, cardW, cardH);
            } else {
                boolean isDue = i < board.size() + due;
                CardPainter.paintSlot(g, x, top, cardW, cardH, isDue, isDue && dueHover);
            }
        }

        Chips pot = hand.isComplete() ? context.finalPot() : context.pot();
        pill(g, "Pot  " + pot, cx, top - 22 * scale, scale, Theme.GOLD, Font.BOLD, 16f);

        String caption = switch (hand.state()) {
            case AWAITING_FLOP -> "Click the board to deal the flop";
            case AWAITING_TURN -> "Click the board to deal the turn";
            case AWAITING_RIVER -> "Click the board to deal the river";
            case SHOWDOWN -> "Showdown: click face-down cards to reveal them, or pick the winner below";
            case COMPLETE -> "Hand complete";
            default -> hand.state().description();
        };
        List<Pot> pots = context.pots();
        boolean anyAllIn = context.livePlayers().stream().anyMatch(PlayerState::isAllIn);
        if (!hand.isComplete() && anyAllIn && pots.size() > 1) {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < pots.size(); i++) {
                parts.add((i == 0 ? "Main " : "Side " + i + " ") + pots.get(i).amount());
            }
            caption = String.join("  ·  ", parts) + "     " + caption;
        }
        caption(g, caption, cx, top + cardH + 28 * scale, scale, due > 0 || hand.state() == HandState.SHOWDOWN ? Theme.GOLD : Theme.TEXT);
    }

    private void paintSeat(Graphics2D g, TableConfig.Seat seat, Point2D.Double at, Point2D.Double center, double scale) {
        boolean isHero = seat.number() == table.heroSeat();
        Optional<PlayerState> player = hand == null ? Optional.empty() : hand.context().player(seat.number());
        boolean dealtIn = player.isPresent();
        boolean sittingOut = hand == null ? seat.stack().isZero() : !dealtIn;
        boolean folded = player.map(PlayerState::hasFolded).orElse(false);
        boolean acting = hand != null && hand.context().playerToAct().map(p -> p.seat() == seat.number()).orElse(false);
        Chips won = hand == null ? Chips.ZERO : hand.context().winnings().getOrDefault(seat.number(), Chips.ZERO);

        double boxW = (isHero ? 168 : 146) * scale;
        double boxH = 52 * scale;
        double boxX = at.x - boxW / 2;
        double boxY = at.y - boxH / 2;

        Composite normal = g.getComposite();
        if (folded || sittingOut) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        }

        paintHoleCards(g, seat.number(), isHero, player, sittingOut, at.x, boxY, scale);

        RoundRectangle2D box = new RoundRectangle2D.Double(boxX, boxY, boxW, boxH, 14 * scale, 14 * scale);
        if (acting) {
            g.setColor(new Color(Theme.GOLD.getRed(), Theme.GOLD.getGreen(), Theme.GOLD.getBlue(), 70));
            g.fill(new RoundRectangle2D.Double(boxX - 5, boxY - 5, boxW + 10, boxH + 10, 20 * scale, 20 * scale));
        }
        g.setPaint(new GradientPaint(0, (float) boxY, Theme.PANEL_RAISED, 0, (float) (boxY + boxH), Theme.PANEL));
        g.fill(box);
        g.setStroke(new BasicStroke(acting ? 2.5f : 1.3f));
        g.setColor(acting ? Theme.GOLD : won.isPositive() ? Theme.WIN : isHero ? new Color(0x4f6480) : Theme.BORDER);
        g.draw(box);

        String name = (isHero ? "YOU" : "Seat " + seat.number()) + player.map(p -> "  ·  " + p.position()).orElse("");
        g.setFont(Theme.font(Font.PLAIN, 12.5f * (float) scale));
        g.setColor(Theme.MUTED);
        centered(g, name, at.x, boxY + 19 * scale);

        String stack;
        Color stackColor = Theme.TEXT;
        if (sittingOut) {
            stack = "Sitting out";
            stackColor = Theme.MUTED;
        } else if (folded) {
            stack = player.get().stack() + "  (folded)";
        } else if (player.map(PlayerState::isAllIn).orElse(false) && !hand.isComplete()) {
            stack = "ALL-IN";
            stackColor = Theme.GOLD;
        } else {
            stack = player.map(p -> p.stack().toString()).orElse(seat.stack().toString());
        }
        g.setFont(Theme.font(Font.BOLD, 16f * (float) scale));
        g.setColor(stackColor);
        centered(g, stack, at.x, boxY + 40 * scale);
        g.setComposite(normal);

        double dx = center.x - at.x;
        double dy = center.y - at.y;
        double length = Math.hypot(dx, dy);
        double ux = dx / length;
        double uy = dy / length;
        double reach = Math.min(length * 0.42, 118 * scale);
        Point2D.Double betAt = new Point2D.Double(at.x + ux * reach, at.y + uy * reach);
        if (player.isPresent() && player.get().streetBet().isPositive()) {
            paintChips(g, betAt, player.get().streetBet().toString(), scale, Theme.TEXT);
        } else if (won.isPositive()) {
            paintChips(g, betAt, "+" + won, scale, Theme.WIN);
        }
        int button = hand == null ? table.buttonSeat() : hand.context().buttonSeat();
        if (button == seat.number()) {
            double side = (isHero ? 104 : 62) * scale;
            paintDealerButton(g, at.x + ux * reach * 0.55 - uy * side, at.y + uy * reach * 0.55 + ux * side, scale);
        }
    }

    private void paintHoleCards(Graphics2D g, int seat, boolean isHero, Optional<PlayerState> player, boolean sittingOut, double cx, double boxY, double scale) {
        if (sittingOut) {
            return;
        }
        // Face-down opponent cards tuck behind the seat box; known cards stand clear of it so they can be read.
        boolean faceUp = isHero || player.map(PlayerState::hasKnownCards).orElse(false);
        double cardW = (isHero ? 64 : faceUp ? 46 : 40) * scale;
        double cardH = (isHero ? 90 : faceUp ? 64 : 56) * scale;
        double gap = faceUp ? 4 * scale : -cardW * 0.28;
        double top = boxY - cardH * (faceUp ? 0.9 : 0.62);
        double left = cx - (2 * cardW + gap) / 2;
        Rectangle2D area = new Rectangle2D.Double(left, top, 2 * cardW + gap, cardH);

        if (player.isEmpty()) {
            if (isHero && hand == null) {
                hotspots.add(new Hotspot(area, listener::heroCardsClicked));
                boolean hover = hovered(area);
                CardPainter.paintSlot(g, left, top, cardW, cardH, true, hover);
                CardPainter.paintSlot(g, left + cardW + gap, top, cardW, cardH, true, hover);
            }
            return;
        }
        PlayerState state = player.get();
        if (state.hasFolded() && !isHero) {
            return;
        }
        HandState handState = hand.state();
        if (state.hasKnownCards()) {
            List<Card> cards = CardMask.toList(state.holeCards());
            if (isHero && !hand.isComplete()) {
                hotspots.add(new Hotspot(area, listener::heroCardsClicked));
            }
            for (int i = 0; i < cards.size(); i++) {
                CardPainter.paintFace(g, cards.get(i), left + i * (cardW + gap), top, cardW, cardH);
            }
        } else if (isHero) {
            boolean clickable = !hand.isComplete() && !state.hasFolded();
            if (clickable) {
                hotspots.add(new Hotspot(area, listener::heroCardsClicked));
            }
            boolean hover = clickable && hovered(area);
            CardPainter.paintSlot(g, left, top, cardW, cardH, clickable, hover);
            CardPainter.paintSlot(g, left + cardW + gap, top, cardW, cardH, clickable, hover);
        } else if (!hand.isComplete()) {
            boolean clickable = handState == HandState.SHOWDOWN;
            if (clickable) {
                hotspots.add(new Hotspot(area, () -> listener.seatCardsClicked(seat)));
            }
            CardPainter.paintBack(g, left, top, cardW, cardH);
            CardPainter.paintBack(g, left + cardW + gap, top, cardW, cardH);
            if (clickable) {
                g.setColor(hovered(area) ? Theme.GOLD.brighter() : Theme.GOLD);
                g.setStroke(new BasicStroke(2f));
                g.draw(new RoundRectangle2D.Double(left - 3, top - 3, area.getWidth() + 6, cardH + 6, 10, 10));
            }
        }
    }

    private void paintChips(Graphics2D g, Point2D.Double at, String text, double scale, Color textColor) {
        double r = 9 * scale;
        Color[] colors = {new Color(0x2b6fd6), new Color(0xd23b33), new Color(0x1d1f24)};
        for (int i = 0; i < 3; i++) {
            double y = at.y - i * 3.5 * scale;
            Ellipse2D chip = new Ellipse2D.Double(at.x - r - 16 * scale, y - r, 2 * r, 2 * r);
            g.setColor(colors[i]);
            g.fill(chip);
            g.setColor(new Color(255, 255, 255, 190));
            g.setStroke(new BasicStroke(1.6f * (float) scale, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{3f, 3f}, 0f));
            g.draw(new Ellipse2D.Double(at.x - r * 0.72 - 16 * scale, y - r * 0.72, 1.44 * r, 1.44 * r));
        }
        g.setFont(Theme.font(Font.BOLD, 14f * (float) scale));
        FontMetrics metrics = g.getFontMetrics();
        double textW = metrics.stringWidth(text);
        double pillX = at.x - 4 * scale;
        RoundRectangle2D pill = new RoundRectangle2D.Double(pillX, at.y - 11 * scale, textW + 14 * scale, 22 * scale, 22 * scale, 22 * scale);
        g.setColor(new Color(0, 0, 0, 140));
        g.fill(pill);
        g.setColor(textColor);
        g.drawString(text, (float) (pillX + 7 * scale), (float) (at.y + metrics.getAscent() / 2.0 - 2 * scale));
    }

    private void paintDealerButton(Graphics2D g, double x, double y, double scale) {
        double r = 13 * scale;
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new Ellipse2D.Double(x - r + 1, y - r + 2, 2 * r, 2 * r));
        g.setPaint(new GradientPaint((float) x, (float) (y - r), Color.WHITE, (float) x, (float) (y + r), new Color(0xd7dbe0)));
        g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        g.setColor(new Color(0x1c1f24));
        g.setFont(Theme.font(Font.BOLD, 14f * (float) scale));
        FontMetrics metrics = g.getFontMetrics();
        g.drawString("D", (float) (x - metrics.stringWidth("D") / 2.0), (float) (y + metrics.getAscent() / 2.0 - 2 * scale));
    }

    private void pill(Graphics2D g, String text, double cx, double cy, double scale, Color color, int style, float size) {
        g.setFont(Theme.font(style, size * (float) scale));
        FontMetrics metrics = g.getFontMetrics();
        double textW = metrics.stringWidth(text);
        double padding = 14 * scale;
        double height = metrics.getHeight() + 6 * scale;
        g.setColor(new Color(0, 0, 0, 120));
        g.fill(new RoundRectangle2D.Double(cx - textW / 2 - padding, cy - height / 2, textW + 2 * padding, height, height, height));
        g.setColor(color);
        g.drawString(text, (float) (cx - textW / 2), (float) (cy - height / 2 + 3 * scale + metrics.getAscent()));
    }

    private void caption(Graphics2D g, String text, double cx, double y, double scale, Color color) {
        g.setFont(Theme.font(Font.PLAIN, 14f * (float) scale));
        g.setColor(color);
        centered(g, text, cx, y);
    }

    private static void centered(Graphics2D g, String text, double cx, double baseline) {
        g.drawString(text, (float) (cx - g.getFontMetrics().stringWidth(text) / 2.0), (float) baseline);
    }
}
