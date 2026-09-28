package com.pokerassistant.gui;

import com.pokerassistant.strategy.Recommendation;

import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Font;

/** The assistant's side panel: the recommended action, the numbers behind it and the reasoning. */
@SuppressWarnings("serial")
final class AdvicePanel extends JPanel {

    private final JEditorPane pane = new JEditorPane("text/html", "");

    AdvicePanel() {
        super(new BorderLayout(0, 8));
        setBackground(Theme.PANEL);
        setBorder(BorderFactory.createEmptyBorder(14, 16, 10, 16));
        JLabel title = new JLabel("ASSISTANT");
        title.setFont(Theme.font(Font.BOLD, 12f));
        title.setForeground(Theme.MUTED);
        add(title, BorderLayout.NORTH);
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(null);
        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        Theme.darkScrollBars(scroll);
        add(scroll, BorderLayout.CENTER);
        waiting("Deal a hand to get advice.");
    }

    void waiting(String message) {
        render("<p class='muted'>" + escape(message) + "</p>");
    }

    void thinking() {
        render("<p class='muted'>Calculating equity…</p>");
    }

    void unavailable(String message) {
        render("<p class='warn'>No advice: " + escape(message) + "</p>");
    }

    void show(Recommendation recommendation) {
        String color = switch (recommendation.action()) {
            case FOLD -> "#ff7a70";
            case CHECK, CALL -> "#6fb2ff";
            case BET, RAISE, ALL_IN -> "#4cd18a";
        };
        StringBuilder html = new StringBuilder();
        html.append("<div class='headline' style='color:").append(color).append("'>")
                .append(escape(recommendation.headline())).append("</div>");
        html.append("<table cellspacing='0' cellpadding='2'>");
        for (Recommendation.Stat stat : recommendation.stats()) {
            html.append("<tr><td class='label' valign='top'>").append(escape(stat.label()))
                    .append("</td><td>").append(escape(stat.value())).append("</td></tr>");
        }
        html.append("</table>");
        html.append("<p class='section'>WHY</p><ul>");
        for (String reason : recommendation.reasons()) {
            html.append("<li>").append(escape(reason)).append("</li>");
        }
        html.append("</ul>");
        if (recommendation.alternative() != null) {
            html.append("<p class='section'>ALTERNATIVE</p><p>").append(escape(recommendation.alternative())).append("</p>");
        }
        render(html.toString());
    }

    private void render(String body) {
        pane.setText("""
                <html><head><style>
                body { font-family: 'Segoe UI', sans-serif; font-size: 11pt; color: #e9edf2; margin: 0; }
                .headline { font-size: 19pt; font-weight: bold; margin-bottom: 8px; }
                .label { color: #8e9aab; padding-right: 10px; }
                .section { color: #8e9aab; font-size: 9pt; font-weight: bold; margin-top: 10px; margin-bottom: 2px; }
                .muted { color: #8e9aab; }
                .warn { color: #f5c451; }
                ul { margin-left: 16px; margin-top: 2px; }
                li { margin-bottom: 4px; }
                </style></head><body>""" + body + "</body></html>");
        pane.setCaretPosition(0);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
