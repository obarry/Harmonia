package com.harmonia.ui.shell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;

import com.harmonia.ui.mock.MockParameter;
import com.harmonia.ui.theme.HarmoniaColors;
import com.harmonia.ui.theme.HarmoniaLaf;
import com.harmonia.ui.widget.Knob;
import com.harmonia.ui.widget.SectionPanel;

/**
 * Right panel: details of the parameter being edited (official name and number, Wavestation
 * page, range, value, original value).
 */
public class InspectorPanel extends SectionPanel {

    private final JLabel name = new JLabel("—");
    private final JLabel official = new JLabel(" ");
    private final JLabel value = new JLabel("—", SwingConstants.CENTER);
    private final JProgressBar bar = new JProgressBar(0, 1000);
    private final JLabel original = valueLabel();
    private final JLabel range = valueLabel();
    private final JLabel page = valueLabel();
    private Knob current;

    public InspectorPanel() {
        super("Inspecteur");
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        name.setFont(name.getFont().deriveFont(Font.BOLD, 14f));
        name.setForeground(Color.WHITE);
        official.setFont(HarmoniaLaf.monoFont(Font.PLAIN, 11f));
        official.setForeground(HarmoniaColors.TEXT_DIM);

        value.setFont(HarmoniaLaf.monoFont(Font.BOLD, 26f));
        value.setForeground(HarmoniaColors.WRITE);
        value.setOpaque(true);
        value.setBackground(new Color(0x0F1012));
        value.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        value.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        bar.putClientProperty("FlatLaf.style", "foreground: #ff8a3d; background: #2a2c31");
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        bar.setPreferredSize(new Dimension(100, 6));

        JPanel details = new JPanel(new GridLayout(3, 2, 0, 6));
        details.setOpaque(false);
        details.add(keyLabel("Original"));
        details.add(original);
        details.add(keyLabel("Plage"));
        details.add(range);
        details.add(keyLabel("Page SR"));
        details.add(page);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 8, 0));
        buttons.setOpaque(false);
        JButton reset = new JButton("↺ Original");
        reset.setToolTipText("Revenir à la valeur d'origine (double-clic sur le bouton rotatif)");
        reset.addActionListener(e -> {
            if (current != null) {
                current.setValue(current.getDefaultValue());
            }
        });
        JButton random = new JButton("⚄ Randomiser");
        random.setToolTipText("Bientôt : variation aléatoire du paramètre");
        random.setEnabled(false);
        buttons.add(reset);
        buttons.add(random);

        for (Component c : new Component[] { name, official }) {
            ((javax.swing.JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);

        p.add(name);
        p.add(Box.createVerticalStrut(4));
        p.add(official);
        p.add(Box.createVerticalStrut(12));
        p.add(value);
        p.add(Box.createVerticalStrut(8));
        p.add(bar);
        p.add(Box.createVerticalStrut(14));
        p.add(details);
        p.add(Box.createVerticalStrut(14));
        p.add(buttons);
        getContent().add(p, BorderLayout.NORTH);
    }

    /** Shows the knob's parameter. The knob may carry a {@link MockParameter} client property. */
    public void show(Knob knob) {
        current = knob;
        MockParameter param = (MockParameter) knob.getClientProperty(MockParameter.class);
        name.setText(knob.getLabel());
        if (param != null) {
            official.setText("#" + param.number() + " · " + param.officialName());
            range.setText(param.rangeText());
            page.setText(param.page());
        } else {
            official.setText(" ");
            range.setText(knob.getMinimum() + " … " + knob.getMaximum());
            page.setText("—");
        }
        original.setText(Integer.toString(knob.getDefaultValue()));
        refreshValue();
    }

    /** Updates the displayed value if the given knob is the inspected one. */
    public void valueChanged(Knob knob) {
        if (knob == current) {
            refreshValue();
        }
    }

    private void refreshValue() {
        value.setText(current.getValueText());
        int span = current.getMaximum() - current.getMinimum();
        bar.setValue((int) Math.round(1000.0 * (current.getValue() - current.getMinimum()) / span));
    }

    private static JLabel keyLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(HarmoniaColors.TEXT_DIM);
        return l;
    }

    private static JLabel valueLabel() {
        JLabel l = new JLabel("—", SwingConstants.RIGHT);
        l.setFont(HarmoniaLaf.monoFont(Font.BOLD, 12f));
        return l;
    }
}
