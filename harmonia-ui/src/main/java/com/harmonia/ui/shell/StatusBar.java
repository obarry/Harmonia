package com.harmonia.ui.shell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import com.harmonia.ui.theme.HarmoniaColors;
import com.harmonia.ui.widget.LedIndicator;

/**
 * Bottom status bar: connection state on the left, exchanges in progress with the device in the
 * middle (a discreet text with animated dots: "Lecture de RAM2 : patchs ..."), edit state on the
 * right.
 */
public class StatusBar extends JPanel {

    private static final String[] DOTS = { ".  ", ".. ", "..." };

    private final LedIndicator connectionLed = new LedIndicator(HarmoniaColors.LED_GREEN, 8);
    private final JLabel connection = label();
    private final JLabel right = label();
    private final JLabel activity = label();
    /** Activities in progress, by key; the most recent one is shown. */
    private final Map<String, String> activities = new LinkedHashMap<>();
    private final Timer animation = new Timer(400, e -> animate());
    private int frame;

    public StatusBar() {
        super(new BorderLayout());
        setBackground(new Color(0x0A0B0C));
        setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 14));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(connectionLed);
        left.add(connection);
        add(left, BorderLayout.WEST);
        activity.setForeground(HarmoniaColors.ACCENT);
        activity.setHorizontalAlignment(JLabel.CENTER);
        add(activity, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    /**
     * Shows an exchange in progress with the device (Swing thread). The text is shown with animated
     * dots until {@link #endActivity} is called with the same key; several activities can overlap.
     */
    public void startActivity(String key, String text) {
        activities.remove(key);
        activities.put(key, text);
        frame = 0;
        animate();
        animation.start();
    }

    public void endActivity(String key) {
        if (activities.remove(key) != null) {
            animate();
        }
    }

    /** Text currently shown in the middle, without the dots (for tests). */
    String activityText() {
        return activities.isEmpty() ? "" : activities.values().stream().reduce((a, b) -> b).orElse("");
    }

    private void animate() {
        if (activities.isEmpty()) {
            animation.stop();
            activity.setText("");
            return;
        }
        activity.setText(activityText() + " " + DOTS[frame++ % DOTS.length]);
    }

    public void setConnection(boolean connected, String text) {
        connectionLed.setOn(connected);
        connection.setText(text);
    }

    public void setRightText(String text) {
        right.setText(text);
    }

    private static JLabel label() {
        JLabel l = new JLabel();
        l.setForeground(HarmoniaColors.TEXT_DIM);
        l.setFont(l.getFont().deriveFont(12f));
        return l;
    }
}
