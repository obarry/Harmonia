package com.harmonia.ui.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.BorderFactory;
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
import com.harmonia.ui.widget.PianoKeyboard;
import com.harmonia.ui.widget.SectionPanel;
import com.harmonia.wavestation.model.Part;
import com.harmonia.wavestation.model.Performance;

/**
 * Performance view (first version, read only): the 8 parts with the patch each one plays, its
 * level, key and velocity zones and transposition. Double-clicking a part opens its patch, like
 * the EDIT button on the EDIT PERF page of the device.
 * <p>
 * Editing of the parts and the graphical zones come with phase 7.
 */
public class PerformanceEditorPanel extends JPanel implements EditorView {

    private static final String[] COLUMNS = { "Part", "Patch", "Nom du patch", "Niveau", "Clavier", "Vélocité",
            "Transpo", "Désacc.", "Mode" };

    private final SectionPanel section = new SectionPanel("Performance");
    private final JLabel title = new JLabel();
    private final PartsModel model = new PartsModel();
    private final JTable table = new JTable(model);
    private final JLabel empty = new JLabel();
    private final JPanel content = new JPanel(new BorderLayout());
    private final Function<Part, String> patchName;
    private Performance performance;

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
        title.setBorder(BorderFactory.createEmptyBorder(0, 2, 12, 0));

        table.setRowHeight(34);
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
        JLabel hint = new JLabel("Double-clic sur une part : ouvrir son patch (comme EDIT sur l'appareil)");
        hint.setForeground(HarmoniaColors.TEXT_FAINT);
        hint.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 0));

        content.setOpaque(false);
        content.add(title, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(hint, BorderLayout.SOUTH);
        section.getContent().add(empty, BorderLayout.CENTER);
        add(section, BorderLayout.CENTER);
    }

    @Override
    public JComponent component() {
        return this;
    }

    /** Shows a performance (null: back to the empty state). */
    public void showPerformance(String reference, Performance perf) {
        this.performance = perf;
        section.getContent().removeAll();
        if (perf == null) {
            section.getContent().add(empty, BorderLayout.CENTER);
        } else {
            title.setText(reference + "  " + perf.getName());
            model.setParts(perf.getParts());
            section.getContent().add(content, BorderLayout.CENTER);
        }
        section.revalidate();
        section.repaint();
    }

    /** Refreshes the patch names (after a bank has been read). */
    public void refresh() {
        model.fireTableDataChanged();
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
                case 4 -> PianoKeyboard.noteName(p.getLowKey()) + " – " + PianoKeyboard.noteName(p.getHighKey());
                case 5 -> p.getLowVelocity() + " – " + p.getHighVelocity();
                case 6 -> signed(p.getTranspose());
                case 7 -> signed(p.getDetune());
                default -> switch (p.getVoiceMode()) {
                    case POLY -> "Poly";
                    case UNISON_RETRIGGER -> "Uni retrig";
                    case UNISON_LEGATO -> "Uni legato";
                    default -> "?";
                };
            };
        }

        private String signed(int v) {
            return v > 0 ? "+" + v : Integer.toString(v);
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
