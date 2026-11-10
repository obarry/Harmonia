package com.harmonia.ui.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import com.harmonia.ui.theme.HarmoniaColors;
import com.harmonia.ui.theme.HarmoniaLaf;
import com.harmonia.ui.widget.Knob;
import com.harmonia.ui.widget.SectionPanel;
import com.harmonia.wavestation.model.Part;
import com.harmonia.wavestation.model.PartField;
import com.harmonia.wavestation.model.Performance;
import com.harmonia.wavestation.model.PerformanceEffects;

/**
 * Performance editor: the 8 parts in a table, and the settings of the selected part (level,
 * effect bus, transposition, detune, delay, key and velocity zones, modes) that can be changed;
 * each change is applied to the performance and reported to the {@link PartListener}, which sends
 * it to the device in real time. Double-clicking a part opens its patch, like the EDIT button on
 * the EDIT PERF page of the device. The effects are shown above the table.
 */
public class PerformanceEditorPanel extends JPanel implements EditorView {

    /** Receives the changes made to the parts. */
    public interface PartListener {
        /** A setting of a part has changed (already applied to the part). */
        void partChanged(Part part, PartField field, int value);

        /** A part has been selected (it becomes the current part of the device). */
        default void partSelected(Part part) {
        }
    }

    private static final String[] COLUMNS = { "Part", "Patch", "Nom du patch", "Niveau", "Clavier", "Vélocité",
            "Transpo", "Désacc.", "Mode" };

    /** Settings edited with a knob, in display order. */
    private static final List<PartField> KNOBS = List.of(PartField.LEVEL, PartField.OUTPUT, PartField.TRANSPOSE,
            PartField.DETUNE, PartField.DELAY, PartField.LOW_KEY, PartField.HIGH_KEY, PartField.LOW_VELOCITY,
            PartField.HIGH_VELOCITY);
    /** Settings chosen in a list. */
    private static final List<PartField> CHOICES = List.of(PartField.VOICE_MODE, PartField.NOTE_PRIORITY,
            PartField.PLAY_MODE);

    private final SectionPanel section = new SectionPanel("Performance");
    private final JLabel title = new JLabel();
    private final JLabel effects = new JLabel();
    private final PartsModel model = new PartsModel();
    private final JTable table = new JTable(model);
    private final JLabel empty = new JLabel();
    private final JPanel content = new JPanel(new BorderLayout());
    private final JLabel partTitle = new JLabel();
    private final Map<PartField, Knob> knobs = new EnumMap<>(PartField.class);
    private final Map<PartField, JComboBox<String>> choices = new EnumMap<>(PartField.class);
    private final JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
    private final Function<Part, String> patchName;
    private PartListener listener = (part, field, value) -> { };
    private Performance performance;
    private Part selected;
    /** True while the controls are being set from a part: no change is reported. */
    private boolean updating;

    /**
     * @param patchName gives the name of the patch played by a part (or null if unknown)
     * @param onOpen    called when the user opens a part (double-click)
     */
    public PerformanceEditorPanel(Function<Part, String> patchName, Consumer<Part> onOpen) {
        super(new BorderLayout());
        this.patchName = patchName;
        setOpaque(false);

        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setForeground(Color.WHITE);
        effects.setForeground(HarmoniaColors.TEXT_DIM);
        effects.setBorder(BorderFactory.createEmptyBorder(4, 0, 12, 0));
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        header.add(title);
        header.add(effects);

        table.setRowHeight(30);
        table.setShowVerticalLines(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setBackground(HarmoniaColors.PANEL);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new CellRenderer());
        int[] widths = { 50, 90, 200, 70, 110, 90, 70, 70, 120 };
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                selectPart(row >= 0 ? model.parts.get(row) : null);
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && row >= 0 && performance != null) {
                    Part part = model.parts.get(row);
                    if (part.isUsed()) {
                        onOpen.accept(part);
                    }
                }
            }
        });

        empty.setForeground(HarmoniaColors.TEXT_DIM);
        empty.setHorizontalAlignment(SwingConstants.CENTER);
        empty.setText("<html><center>Sélectionnez une performance dans le Librarian.<br><br>"
                + "Connecté au SR, elle est lue depuis l'appareil ; « Lire la banque » charge aussi "
                + "les noms des patchs.</center></html>");

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        content.setOpaque(false);
        content.add(header, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(createPartEditor(), BorderLayout.SOUTH);
        section.getContent().add(empty, BorderLayout.CENTER);
        add(section, BorderLayout.CENTER);
    }

    private JComponent createPartEditor() {
        partTitle.setFont(partTitle.getFont().deriveFont(Font.BOLD, 13f));
        partTitle.setForeground(HarmoniaColors.ACCENT);
        controls.setOpaque(false);
        for (PartField f : KNOBS) {
            int initial = f == PartField.HIGH_KEY || f == PartField.HIGH_VELOCITY ? f.max() : f == PartField.LEVEL ? 99
                    : Math.max(f.min(), 0);
            Knob k = new Knob(shortLabel(f), f.min(), f.max(), initial);
            k.setFormatter(f::format);
            k.setToolTipText(f.label() + " (" + f.parameter().officialName() + ")");
            k.addChangeListener(e -> changed(f, k.getValue()));
            knobs.put(f, k);
            controls.add(k);
        }
        controls.add(Box.createHorizontalStrut(10));
        for (PartField f : CHOICES) {
            JComboBox<String> combo = new JComboBox<>();
            for (int v = f.min(); v <= f.max(); v++) {
                combo.addItem(f.format(v));
            }
            combo.setToolTipText(f.label() + " (" + f.parameter().officialName() + ")");
            combo.addActionListener(e -> {
                if (combo.getSelectedIndex() >= 0) {
                    changed(f, f.min() + combo.getSelectedIndex());
                }
            });
            choices.put(f, combo);
            JPanel box = new JPanel(new BorderLayout(0, 2));
            box.setOpaque(false);
            JLabel label = new JLabel(f.label());
            label.setForeground(HarmoniaColors.TEXT_DIM);
            label.setFont(label.getFont().deriveFont(11f));
            box.add(label, BorderLayout.NORTH);
            box.add(combo, BorderLayout.CENTER);
            controls.add(box);
        }
        JLabel hint = new JLabel("Double-clic sur une part : ouvrir son patch (comme EDIT sur l'appareil). "
                + "Les réglages sont envoyés au SR en temps réel.");
        hint.setForeground(HarmoniaColors.TEXT_FAINT);

        JPanel editor = new JPanel(new BorderLayout(0, 6));
        editor.setOpaque(false);
        editor.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, HarmoniaColors.PANEL.brighter()),
                BorderFactory.createEmptyBorder(10, 2, 0, 0)));
        editor.add(partTitle, BorderLayout.NORTH);
        editor.add(controls, BorderLayout.CENTER);
        editor.add(hint, BorderLayout.SOUTH);
        return editor;
    }

    private static String shortLabel(PartField f) {
        return switch (f) {
            case OUTPUT -> "FX bus";
            case TRANSPOSE -> "Transpo";
            case DETUNE -> "Désacc.";
            case LOW_KEY -> "Note min";
            case HIGH_KEY -> "Note max";
            case LOW_VELOCITY -> "Vél. min";
            case HIGH_VELOCITY -> "Vél. max";
            default -> f.label();
        };
    }

    public void setPartListener(PartListener listener) {
        this.listener = listener;
    }

    @Override
    public JComponent component() {
        return this;
    }

    /** Shows a performance (null: back to the empty state). The first used part is selected. */
    public void showPerformance(String reference, Performance perf) {
        this.performance = perf;
        section.getContent().removeAll();
        if (perf == null) {
            selected = null;
            section.getContent().add(empty, BorderLayout.CENTER);
        } else {
            title.setText(reference + "  " + perf.getName());
            PerformanceEffects fx = perf.getEffects();
            effects.setText("Effets :  " + fx.summary());
            model.setParts(perf.getParts());
            int first = 0;
            for (int i = 0; i < model.parts.size(); i++) {
                if (model.parts.get(i).isUsed()) {
                    first = i;
                    break;
                }
            }
            table.getSelectionModel().setSelectionInterval(first, first);
            section.getContent().add(content, BorderLayout.CENTER);
        }
        section.revalidate();
        section.repaint();
    }

    /** Selected part, or null. */
    public Part getSelectedPart() {
        return selected;
    }

    /** Refreshes the patch names (after a bank has been read). */
    public void refresh() {
        model.fireTableRowsUpdated(0, Math.max(0, model.getRowCount() - 1));
        if (selected != null) {
            updatePartTitle();
        }
    }

    private void selectPart(Part part) {
        if (part == selected) {
            return;
        }
        selected = part;
        updating = true;
        try {
            boolean used = part != null && part.isUsed();
            for (Map.Entry<PartField, Knob> e : knobs.entrySet()) {
                e.getValue().setEnabled(used);
                if (used) {
                    e.getValue().setValue(part.get(e.getKey()));
                }
            }
            for (Map.Entry<PartField, JComboBox<String>> e : choices.entrySet()) {
                e.getValue().setEnabled(used);
                if (used) {
                    PartField f = e.getKey();
                    e.getValue().setSelectedIndex(f.clamp(part.get(f)) - f.min());
                }
            }
            updateUnisonControls();
            updatePartTitle();
        } finally {
            updating = false;
        }
        if (part != null && part.isUsed()) {
            listener.partSelected(part);
        }
    }

    private void updatePartTitle() {
        if (selected == null) {
            partTitle.setText(" ");
        } else if (!selected.isUsed()) {
            partTitle.setText("Part " + selected.getNumber() + " : inutilisée");
        } else {
            String name = patchName.apply(selected);
            partTitle.setText("Part " + selected.getNumber() + "  ·  " + selected.patchReference()
                    + (name != null ? "  " + name : ""));
        }
    }

    /** Note priority only matters in the unison modes. */
    private void updateUnisonControls() {
        boolean unison = selected != null && selected.isUsed()
                && selected.get(PartField.VOICE_MODE) != 1;
        choices.get(PartField.NOTE_PRIORITY).setEnabled(unison);
    }

    private void changed(PartField field, int value) {
        if (updating || selected == null || !selected.isUsed() || selected.get(field) == value) {
            return;
        }
        selected.set(field, value);
        int row = selected.getNumber() - 1;
        model.fireTableRowsUpdated(row, row);
        if (field == PartField.VOICE_MODE) {
            updateUnisonControls();
        }
        listener.partChanged(selected, field, selected.get(field));
    }

    /** Changes a setting of the selected part as if the user had done it (for tests). */
    void edit(PartField field, int value) {
        if (knobs.containsKey(field)) {
            knobs.get(field).setValue(value);
        } else {
            choices.get(field).setSelectedIndex(value - field.min());
        }
    }

    /** Number of a row's part (for tests). */
    Part partAt(int row) {
        return model.parts.get(row);
    }

    void selectRow(int row) {
        table.getSelectionModel().setSelectionInterval(row, row);
    }

    private final class PartsModel extends AbstractTableModel {
        private List<Part> parts = List.of();

        void setParts(List<Part> parts) {
            this.parts = parts;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return parts.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            Part p = parts.get(row);
            if (!p.isUsed() && column > 1) {
                return "";
            }
            return switch (column) {
                case 0 -> Integer.toString(p.getNumber());
                case 1 -> p.patchReference();
                case 2 -> {
                    String name = patchName.apply(p);
                    yield name != null ? name : "(non lu)";
                }
                case 3 -> Integer.toString(p.getLevel());
                case 4 -> PartField.LOW_KEY.format(p.getLowKey()) + " – " + PartField.HIGH_KEY.format(p.getHighKey());
                case 5 -> p.getLowVelocity() + " – " + p.getHighVelocity();
                case 6 -> PartField.TRANSPOSE.format(p.getTranspose());
                case 7 -> PartField.DETUNE.format(p.getDetune());
                default -> PartField.VOICE_MODE.format(p.get(PartField.VOICE_MODE));
            };
        }
    }

    private final class CellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            super.getTableCellRendererComponent(t, value, isSelected, false, row, column);
            boolean used = model.parts.get(row).isUsed();
            setFont(column == 1 || column >= 3 ? HarmoniaLaf.monoFont(Font.PLAIN, 12f)
                    : getFont().deriveFont(column == 2 ? Font.BOLD : Font.PLAIN));
            setForeground(!used ? HarmoniaColors.TEXT_FAINT
                    : column == 1 ? HarmoniaColors.ACCENT : isSelected ? Color.WHITE : HarmoniaColors.TEXT);
            setBackground(isSelected ? new Color(0x1D3532) : HarmoniaColors.PANEL);
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            return this;
        }
    }
}
