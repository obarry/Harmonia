package com.harmonia.ui.shell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.harmonia.ui.mock.MockData;
import com.harmonia.ui.theme.HarmoniaColors;
import com.harmonia.ui.theme.HarmoniaLaf;
import com.harmonia.ui.widget.SectionPanel;

/**
 * Left panel: bank selector (RAM 1-3, ROM 4-11, CARD), type filter (Perf / Patch / WaveSeq),
 * search field and the list of sounds of the selected bank.
 * <p>
 * Uses {@link MockData} until the librarian is connected to the device (phase 4).
 */
public class LibrarianPanel extends SectionPanel {

    /** Kind of sound listed. */
    public enum Kind {
        PERF("Perf"), PATCH("Patch"), WAVESEQ("WaveSeq");

        final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /** One line of the list. */
    public record Entry(String bank, int number, String name) {
        String id() {
            return bank + ":" + String.format("%02d", number);
        }
    }

    private String bank = "RAM1";
    private Kind kind = Kind.PATCH;
    private final DefaultListModel<Entry> model = new DefaultListModel<>();
    private final JList<Entry> list = new JList<>(model);
    private final JTextField search = new JTextField();
    private final JLabel footer = new JLabel();
    private final List<JToggleButton> bankButtons = new java.util.ArrayList<>();

    /**
     * @param onSelect called with the kind and the entry when the user selects a sound
     */
    public LibrarianPanel(BiConsumer<Kind, Entry> onSelect) {
        super("Librarian");
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(bankGrid());
        top.add(Box.createVerticalStrut(10));
        top.add(kindSelector());
        top.add(Box.createVerticalStrut(10));
        search.putClientProperty("JTextField.placeholderText", "Rechercher un son…");
        search.putClientProperty("JTextField.showClearButton", true);
        search.setMaximumSize(new Dimension(Integer.MAX_VALUE, search.getPreferredSize().height));
        search.setAlignmentX(Component.LEFT_ALIGNMENT);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                reload();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                reload();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                reload();
            }
        });
        top.add(search);
        top.add(Box.createVerticalStrut(8));

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new EntryRenderer());
        list.setBackground(HarmoniaColors.PANEL);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                onSelect.accept(kind, list.getSelectedValue());
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        footer.setForeground(HarmoniaColors.TEXT_DIM);
        footer.setFont(footer.getFont().deriveFont(11f));
        footer.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 0));

        getContent().add(top, BorderLayout.NORTH);
        getContent().add(scroll, BorderLayout.CENTER);
        getContent().add(footer, BorderLayout.SOUTH);
        reload();
    }

    private JPanel bankGrid() {
        JPanel grid = new JPanel(new GridLayout(3, 4, 5, 5));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        ButtonGroup group = new ButtonGroup();
        for (String b : MockData.BANKS) {
            JToggleButton chip = new JToggleButton(b, b.equals(bank));
            chip.putClientProperty("FlatLaf.style", "arc: 6; margin: 2,2,2,2; font: -2 bold; "
                    + "selectedBackground: #1f3b38; selectedForeground: #bff7ef");
            chip.setFocusable(false);
            chip.setToolTipText(b.startsWith("ROM") ? "Banque d'usine (lecture seule)" : "Banque modifiable");
            chip.addActionListener(e -> {
                bank = b;
                reload();
            });
            group.add(chip);
            bankButtons.add(chip);
            grid.add(chip);
        }
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
        return grid;
    }

    private JPanel kindSelector() {
        JPanel p = new JPanel(new GridLayout(1, 3, 6, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        ButtonGroup group = new ButtonGroup();
        for (Kind k : Kind.values()) {
            JToggleButton b = new JToggleButton(k.label, k == kind);
            b.putClientProperty("FlatLaf.style", "arc: 999; margin: 2,6,2,6; font: -1");
            b.setFocusable(false);
            b.addActionListener(e -> {
                kind = k;
                reload();
            });
            group.add(b);
            p.add(b);
        }
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private void reload() {
        List<String> names = switch (kind) {
            case PERF -> MockData.performances(bank);
            case PATCH -> MockData.patches(bank);
            case WAVESEQ -> MockData.waveSequences(bank);
        };
        String filter = search.getText().trim().toLowerCase(Locale.ROOT);
        model.clear();
        for (int i = 0; i < names.size(); i++) {
            if (filter.isEmpty() || names.get(i).toLowerCase(Locale.ROOT).contains(filter)) {
                model.addElement(new Entry(bank, i, names.get(i)));
            }
        }
        String what = switch (kind) {
            case PERF -> "performances";
            case PATCH -> "patchs";
            case WAVESEQ -> "wave sequences";
        };
        footer.setText(names.size() + " " + what + " · banque " + bank
                + (bank.startsWith("ROM") ? " · lecture seule" : ""));
    }

    /** Selects an entry by number (used by the BANK and +1/-1 front panel buttons). */
    public void selectNumber(int number) {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).number() == number) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    /** Moves the selection by delta entries (wrapping), like +1/YES and -1/NO on the PERFORM page. */
    public void step(int delta) {
        if (model.isEmpty()) {
            return;
        }
        int i = Math.floorMod(Math.max(0, list.getSelectedIndex()) + delta, model.size());
        list.setSelectedIndex(i);
        list.ensureIndexIsVisible(i);
    }

    /** Selects the next bank, like the BANK button of the device. */
    public void nextBank() {
        int i = MockData.BANKS.indexOf(bank);
        bankButtons.get((i + 1) % bankButtons.size()).doClick();
    }

    public String getBank() {
        return bank;
    }

    /** Renderer: number in monospace, name, accent background for the selection. */
    private static final class EntryRenderer extends JPanel implements ListCellRenderer<Entry> {
        private final JLabel number = new JLabel();
        private final JLabel name = new JLabel();

        EntryRenderer() {
            super(new FlowLayout(FlowLayout.LEFT, 10, 5));
            number.setFont(HarmoniaLaf.monoFont(Font.BOLD, 11f));
            add(number);
            add(name);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Entry> list, Entry value, int index,
                boolean isSelected, boolean cellHasFocus) {
            number.setText(String.format("%02d", value.number()));
            name.setText(value.name());
            setBackground(isSelected ? new Color(0x1D3532) : HarmoniaColors.PANEL);
            number.setForeground(isSelected ? HarmoniaColors.ACCENT : HarmoniaColors.TEXT_FAINT);
            name.setForeground(isSelected ? Color.WHITE : HarmoniaColors.TEXT);
            return this;
        }
    }
}
