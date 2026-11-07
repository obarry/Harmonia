package com.harmonia.ui.shell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.harmonia.ui.theme.HarmoniaColors;
import com.harmonia.ui.widget.LedIndicator;

/** Bottom status bar: connection state on the left, SysEx queue and edit state on the right. */
public class StatusBar extends JPanel {

    private final LedIndicator connectionLed = new LedIndicator(HarmoniaColors.LED_GREEN, 8);
    private final JLabel connection = label();
    private final JLabel right = label();

    public StatusBar() {
        super(new BorderLayout());
        setBackground(new Color(0x0A0B0C));
        setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 14));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(connectionLed);
        left.add(connection);
        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);
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
