package com.harmonia.ui.shell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.Timer;

import com.harmonia.ui.theme.HarmoniaColors;
import com.harmonia.ui.theme.HarmoniaLaf;
import com.harmonia.ui.widget.SectionPanel;

/**
 * MIDI monitor: last messages exchanged with the device and the keyboard, colour-coded by
 * direction, with their decoding on a second line.
 * <p>
 * Built to stay fluid when hundreds of messages arrive per second (fast playing, aftertouch):
 * <ul>
 * <li>messages can be posted from any thread; they are added to the list in batches, 20 times
 * per second, by a Swing timer (one repaint per batch, not per message);</li>
 * <li>a message with a key replaces the previous line with the same key (a knob being turned,
 * a stream of aftertouch or pitch bend), instead of adding hundreds of lines;</li>
 * <li>rows have a fixed height and plain labels (no HTML), so adding a line costs nothing.</li>
 * </ul>
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

    /** A line: bytes (or text) on the first row, decoding on the second. */
    record Line(Direction direction, String text, String detail, String key) {
    }

    private static final int MAX_LINES = 300;
    private static final int FLUSH_MS = 50;
    /** Characters per row before an information line is wrapped. */
    private static final int WRAP = 34;

    private final DefaultListModel<Line> model = new DefaultListModel<>();
    private final JList<Line> list = new JList<>(model);
    private final ConcurrentLinkedQueue<Line> pending = new ConcurrentLinkedQueue<>();
    private Runnable onActivity = () -> { };

    public MidiMonitorPanel() {
        super("Moniteur MIDI");
        list.setCellRenderer(new LineRenderer());
        list.setBackground(HarmoniaColors.PANEL);
        list.setFocusable(false);
        list.setFixedCellHeight(34);
        JScrollPane scroll = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        getContent().add(scroll, BorderLayout.CENTER);
        new Timer(FLUSH_MS, e -> flush()).start();
    }

    /** Called once per batch of OUT / IN messages (to flash the MIDI LED). */
    public void setOnActivity(Runnable onActivity) {
        this.onActivity = onActivity;
    }

    /** Adds a line ("bytes  description": split at the first double space). Any thread. */
    public void log(Direction direction, String text) {
        logCoalesced(null, direction, text);
    }

    /**
     * Like {@link #log}, but if the last line has the same key it is replaced instead of adding a
     * new one. Any thread.
     */
    public void logCoalesced(String key, Direction direction, String text) {
        int split = text.indexOf("  ");
        if (split > 0) {
            post(direction, text.substring(0, split), text.substring(split + 2).trim(), key);
        } else if (text.length() > WRAP && text.lastIndexOf(' ', WRAP) > 0) {
            int cut = text.lastIndexOf(' ', WRAP);   // long information: wrapped on the second row
            post(direction, text.substring(0, cut), text.substring(cut + 1), key);
        } else {
            post(direction, text, "", key);
        }
    }

    /** Adds a line with its decoding. Any thread: the line appears at the next batch. */
    public void post(Direction direction, String text, String detail, String key) {
        pending.add(new Line(direction, text, detail, key));
    }

    /** Moves the pending lines into the list (Swing thread, from the timer). */
    void flush() {
        if (pending.isEmpty()) {
            return;
        }
        List<Line> batch = new ArrayList<>();
        Line l;
        while ((l = pending.poll()) != null) {
            batch.add(l);
        }
        boolean traffic = false;
        for (Line line : batch) {
            traffic |= line.direction() != Direction.INFO;
            int last = model.size() - 1;
            if (line.key() != null && last >= 0 && line.key().equals(model.get(last).key())) {
                model.set(last, line);
            } else {
                model.addElement(line);
            }
        }
        int excess = model.size() - MAX_LINES;
        if (excess > 0) {
            model.removeRange(0, excess - 1);
        }
        list.ensureIndexIsVisible(model.size() - 1);
        if (traffic) {
            onActivity.run();
        }
    }

    /** Number of lines shown (for tests). */
    int lineCount() {
        return model.size();
    }

    private static final class LineRenderer extends JPanel implements ListCellRenderer<Line> {
        private final JLabel direction = new JLabel();
        private final JLabel text = new JLabel();
        private final JLabel detail = new JLabel();

        LineRenderer() {
            super(new BorderLayout(6, 0));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            Font mono = HarmoniaLaf.monoFont(Font.PLAIN, 11.5f);
            direction.setFont(mono.deriveFont(Font.BOLD));
            direction.setVerticalAlignment(JLabel.TOP);
            direction.setPreferredSize(new Dimension(36, 14));
            text.setFont(mono);
            detail.setFont(mono);
            detail.setForeground(HarmoniaColors.TEXT_DIM);
            JPanel lines = new JPanel(new GridLayout(2, 1));
            lines.setOpaque(false);
            lines.add(text);
            lines.add(detail);
            add(direction, BorderLayout.WEST);
            add(lines, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Line> list, Line value, int index,
                boolean isSelected, boolean cellHasFocus) {
            direction.setText(value.direction().label);
            direction.setForeground(value.direction().color);
            text.setText(value.text());
            text.setForeground(value.direction() == Direction.INFO ? HarmoniaColors.ACCENT : new Color(0xB9BCC3));
            detail.setText(value.detail());
            return this;
        }
    }
}
