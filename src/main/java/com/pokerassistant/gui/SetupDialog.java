package com.pokerassistant.gui;

import com.pokerassistant.error.PokerAssistantException;
import com.pokerassistant.game.Chips;
import com.pokerassistant.game.TableConfig;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Optional;

/** Edits the table between hands: players, stacks, blinds, ante, hero's seat and the button. */
@SuppressWarnings("serial")
final class SetupDialog extends JDialog {

    private final JSpinner players;
    private final JTextField stack;
    private final JTextField smallBlind;
    private final JTextField bigBlind;
    private final JTextField ante;
    private final JSpinner hero;
    private final JSpinner button;
    private final JLabel error = new JLabel(" ");
    private TableConfig result;

    private SetupDialog(Component parent, TableConfig current) {
        super(SwingUtilities.getWindowAncestor(parent), "Table setup", ModalityType.APPLICATION_MODAL);
        Chips deepest = current.seats().stream().map(TableConfig.Seat::stack).max(Chips::compareTo).orElse(Chips.of(100));
        players = new JSpinner(new SpinnerNumberModel(current.seats().size(), TableConfig.MIN_SEATS, TableConfig.MAX_SEATS, 1));
        stack = new JTextField(deepest.toString(), 8);
        smallBlind = new JTextField(current.smallBlind().toString(), 8);
        bigBlind = new JTextField(current.bigBlind().toString(), 8);
        ante = new JTextField(current.ante().toString(), 8);
        hero = new JSpinner(new SpinnerNumberModel(current.heroSeat(), 1, TableConfig.MAX_SEATS, 1));
        button = new JSpinner(new SpinnerNumberModel(current.buttonSeat(), 1, TableConfig.MAX_SEATS, 1));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.PANEL);
        form.setBorder(BorderFactory.createEmptyBorder(16, 18, 8, 18));
        int row = 0;
        row(form, row++, "Players", players);
        row(form, row++, "Starting stack (every seat)", stack);
        row(form, row++, "Small blind", smallBlind);
        row(form, row++, "Big blind", bigBlind);
        row(form, row++, "Ante", ante);
        row(form, row++, "Your seat", hero);
        row(form, row, "Button seat", button);

        error.setForeground(Theme.ERROR);
        error.setFont(Theme.font(Font.PLAIN, 12.5f));
        error.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 18));

        PokerButton ok = new PokerButton("Apply", Theme.RAISE, 14f);
        PokerButton cancel = new PokerButton("Cancel", Theme.NEUTRAL, 14f);
        ok.addActionListener(event -> apply());
        cancel.addActionListener(event -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        buttons.setBackground(Theme.PANEL);
        buttons.add(cancel);
        buttons.add(ok);

        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(Theme.PANEL);
        south.add(error, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.PANEL);
        root.add(form, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(ok);
        pack();
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    /** Shows the dialog; the new table, or empty when cancelled. */
    static Optional<TableConfig> edit(Component parent, TableConfig current) {
        SetupDialog dialog = new SetupDialog(parent, current);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    private void apply() {
        try {
            result = TableConfig.uniform((Integer) players.getValue(), Chips.parse(stack.getText()),
                    Chips.parse(smallBlind.getText()), Chips.parse(bigBlind.getText()), Chips.parse(ante.getText()),
                    (Integer) button.getValue(), (Integer) hero.getValue());
            dispose();
        } catch (PokerAssistantException e) {
            error.setText(e.getMessage());
            pack();
        }
    }

    private static void row(JPanel form, int row, String text, JComponent field) {
        JLabel label = new JLabel(text);
        label.setForeground(Theme.TEXT);
        label.setFont(Theme.font(Font.PLAIN, 13.5f));
        field.setFont(Theme.font(Font.PLAIN, 13.5f));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.insets = new Insets(4, 0, 4, 14);
        constraints.anchor = GridBagConstraints.WEST;
        form.add(label, constraints);
        constraints.gridx = 1;
        constraints.insets = new Insets(4, 0, 4, 0);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, constraints);
    }
}
