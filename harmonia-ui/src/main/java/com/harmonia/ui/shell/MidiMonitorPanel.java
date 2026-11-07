package com.harmonia.ui.shell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;

import com.harmonia.ui.theme.HarmoniaColors;
import com.harmonia.ui.theme.HarmoniaLaf;
import com.harmonia.ui.widget.SectionPanel;

/**
 * MIDI monitor: last messages exchanged with the device and the keyboard, colour-coded by
 * direction. Decoding of Wavestation messages arrives with the MIDI layer (phase 2).
 */
public class MidiMonitorPanel extends SectionPanel {

    /** Message direction. */
    public enum Direction {
        IN("IN", HarmoniaColors.LED_GREEN),
        OUT("OUT", HarmoniaColors.LED_AMBER),
        THRU("THRU", HarmoniaColors.LED_BLUE),
        INFO("", HarmoniaColors.ACCENT);

        final String label;
        final Color color;

        Direction(String label, Color color) {
            this.label = label;
            this.color = color;
        }
    }

    private record Line(Direction direction, String text, String key) {
    }

    private static final int MAX_LINES = 500;

    private final DefaultListModel<Line> model = new DefaultListModel<>();
    private final JList<Line> list = new JList<>(model);

    public MidiMonitorPanel() {
        super("Moniteur MIDI");
        list.setCellRenderer(new LineRenderer());
        list.setBackground(HarmoniaColors.PANEL);
        list.setFocusable(false);
        JScrollPane scroll = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        getContent().add(scroll, BorderLayout.CENTER);
    }

    /** Adds a line at the bottom and scrolls to it. Must be called on the Swing thread. */
    public void log(Direction direction, String text) {
        logCoalesced(null, direction, text);
    }

    /**
     * Like {@link #log}, but if the last line has the same key it is replaced instead of adding a
     * new one: turning a knob shows one line per parameter, not hundreds (the SysEx queue will
     * merge successive values the same way).
     */
    public void logCoalesced(String key, Direction direction, String text) {
        Line line = new Line(direction, text, key);
        if (key != null && !model.isEmpty() && key.equals(model.lastElement().key())) {
            model.set(model.size() - 1, line);
            return;
        }
        model.addElement(line);
        while (model.size() > MAX_LINES) {
            model.remove(0);
        }
        list.ensureIndexIsVisible(model.size() - 1);
    }

    private static final class LineRenderer extends JPanel implements ListCellRenderer<Line> {
        private final JLabel direction = new JLabel();
        private final JLabel text = new JLabel();

        LineRenderer() {
            super(new FlowLayout(FlowLayout.LEFT, 6, 2));
            setOpaque(false);
            Font mono = HarmoniaLaf.monoFont(Font.PLAIN, 11.5f);
            direction.setFont(mono.deriveFont(Font.BOLD));
            text.setFont(mono);
            text.setForeground(new Color(0xB9BCC3));
            direction.setPreferredSize(new java.awt.Dimension(36, 16));
            add(direction);
            add(text);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Line> list, Line value, int index,
                boolean isSelected, boolean cellHasFocus) {
            direction.setText(value.direction().label);
            direction.setForeground(value.direction().color);
            text.setText(value.text());
            text.setForeground(value.direction() == Direction.INFO ? HarmoniaColors.ACCENT : new Color(0xB9BCC3));
            return this;
        }
    }
}
