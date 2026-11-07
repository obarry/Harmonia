package com.harmonia.ui.shell;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import com.harmonia.ui.EditorSection;
import com.harmonia.ui.theme.HarmoniaColors;

/**
 * Tab bar: one tab per {@link EditorSection}, the device actions (Get, Send, Compare, Write) and
 * the "Live edit" switch.
 */
public class NavigationBar extends JPanel {

    /** Actions on the device, at the right of the tabs. */
    public enum Action {
        GET, SEND, COMPARE, WRITE
    }

    private final Map<EditorSection, JToggleButton> tabs = new EnumMap<>(EditorSection.class);
    private final JToggleButton liveEdit = new JToggleButton("●  Live edit");

    public NavigationBar(Consumer<EditorSection> onSection, Consumer<Action> onAction) {
        super(new BorderLayout());
        setBackground(new java.awt.Color(0x141518));
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new java.awt.Color(0x0A0B0C)));

        JPanel tabPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        tabPanel.setOpaque(false);
        tabPanel.setBorder(BorderFactory.createEmptyBorder(4, 300, 0, 0)); // aligned with the editor area
        ButtonGroup group = new ButtonGroup();
        for (EditorSection section : EditorSection.values()) {
            JToggleButton tab = new JToggleButton(section.title());
            tab.putClientProperty("JButton.buttonType", "tab");
            tab.putClientProperty("FlatLaf.style", "margin: 8,16,8,16");
            tab.setFont(tab.getFont().deriveFont(Font.PLAIN, 13f));
            tab.setFocusable(false);
            tab.addActionListener(e -> onSection.accept(section));
            group.add(tab);
            tabs.put(section, tab);
            tabPanel.add(tab);
        }

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 7));
        actions.setOpaque(false);
        actions.add(actionButton("⟲ Get", "Lire le son depuis l'appareil", Action.GET, onAction));
        actions.add(actionButton("Send ⟶", "Envoyer le son dans le tampon d'édition de l'appareil", Action.SEND, onAction));
        actions.add(actionButton("A/B", "Comparer avec l'original (WRITE/COMPARE)", Action.COMPARE, onAction));
        JButton write = actionButton("Write", "Écrire le son dans la mémoire de l'appareil", Action.WRITE, onAction);
        write.setForeground(HarmoniaColors.WRITE);
        actions.add(write);

        liveEdit.setSelected(true);
        liveEdit.setFocusable(false);
        liveEdit.setToolTipText("Envoyer chaque modification à l'appareil en temps réel");
        liveEdit.putClientProperty("FlatLaf.style",
                "arc: 999; margin: 3,12,3,12; selectedBackground: #10261f; selectedForeground: #7fe8ae");
        actions.add(liveEdit);

        add(tabPanel, BorderLayout.WEST);
        add(actions, BorderLayout.EAST);
    }

    private static JButton actionButton(String text, String tooltip, Action action, Consumer<Action> onAction) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setFocusable(false);
        b.putClientProperty("FlatLaf.style", "margin: 3,10,3,10");
        b.addActionListener(e -> onAction.accept(action));
        return b;
    }

    /** Selects the tab without firing the listener (the window drives the navigation). */
    public void select(EditorSection section) {
        tabs.get(section).setSelected(true);
    }

    public boolean isLiveEdit() {
        return liveEdit.isSelected();
    }
}
