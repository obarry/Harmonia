package com.harmonia.ui.shell;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.harmonia.ui.theme.HarmoniaColors;
import com.harmonia.ui.widget.PianoKeyboard;
import com.harmonia.ui.widget.SectionPanel;

/** Bottom panel: on-screen keyboard with octave shift. */
public class KeyboardPanel extends SectionPanel {

    private final PianoKeyboard keyboard = new PianoKeyboard();
    private final JLabel octave = new JLabel();

    public KeyboardPanel() {
        super(null);
        setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("CLAVIER");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 10f));
        title.setForeground(HarmoniaColors.TEXT_DIM);
        JPanel shift = new JPanel();
        shift.setOpaque(false);
        shift.setLayout(new BoxLayout(shift, BoxLayout.X_AXIS));
        shift.add(smallButton("−", "Octave plus basse", -12));
        shift.add(Box.createHorizontalStrut(4));
        shift.add(smallButton("+", "Octave plus haute", 12));
        for (javax.swing.JComponent c : new javax.swing.JComponent[] { title, octave, shift }) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            controls.add(c);
            controls.add(Box.createVerticalStrut(5));
        }
        controls.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
        updateOctave();

        getContent().add(controls, BorderLayout.WEST);
        getContent().add(keyboard, BorderLayout.CENTER);
    }

    public PianoKeyboard getKeyboard() {
        return keyboard;
    }

    private JButton smallButton(String text, String tooltip, int delta) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setFocusable(false);
        b.putClientProperty("FlatLaf.style", "margin: 0,8,0,8");
        b.addActionListener(e -> {
            keyboard.setLowestNote(keyboard.getLowestNote() + delta);
            updateOctave();
        });
        return b;
    }

    private void updateOctave() {
        octave.setText("Départ " + PianoKeyboard.noteName(keyboard.getLowestNote()) + " · vél. "
                + keyboard.getVelocity());
        octave.setForeground(HarmoniaColors.TEXT);
    }
}
