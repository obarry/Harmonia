package com.harmonia.ui.shell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import com.harmonia.ui.EditorSection;
import com.harmonia.ui.theme.HarmoniaColors;

/**
 * Second bar under the tabs:
 * <ul>
 * <li>breadcrumb: where we are in the device hierarchy (the "stairways" of the EDIT button),</li>
 * <li>page chips: the Wavestation SR pages of the current section,</li>
 * <li>wave selector A B C D All, as on the device Edit Patch level.</li>
 * </ul>
 */
public class PageBar extends JPanel {

    private static final String CHIP_STYLE = "arc: 999; margin: 0,6,0,6; font: -3 bold; "
            + "selectedBackground: #1f3b38; selectedForeground: #bff7ef";

    private final JLabel breadcrumb = new JLabel();
    private final JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
    private final JPanel waveSelector = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
    private final List<JToggleButton> pageButtons = new ArrayList<>();
    private final List<JToggleButton> waveButtons = new ArrayList<>();
    private final Consumer<String> onPage;
    private String wave = "A";

    public PageBar(Consumer<String> onPage, Consumer<String> onWave) {
        super(new BorderLayout(16, 0));
        this.onPage = onPage;
        setBackground(new Color(0x111214));
        setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));

        breadcrumb.setFont(breadcrumb.getFont().deriveFont(Font.PLAIN, 12f));
        breadcrumb.setForeground(HarmoniaColors.TEXT_DIM);
        chips.setOpaque(false);

        waveSelector.setOpaque(false);
        JLabel waveLabel = new JLabel("Wave");
        waveLabel.setForeground(HarmoniaColors.TEXT_DIM);
        waveSelector.add(waveLabel);
        ButtonGroup waves = new ButtonGroup();
        for (String w : new String[] { "A", "B", "C", "D", "All" }) {
            JToggleButton b = new JToggleButton(w, w.equals(wave));
            b.putClientProperty("FlatLaf.style", "arc: 6; margin: 1,7,1,7; font: -1 bold; "
                    + "selectedBackground: #2ec4b6; selectedForeground: #08201e");
            b.setFocusable(false);
            b.setToolTipText("All".equals(w) ? "Éditer toutes les ondes à la fois" : "Éditer l'onde " + w);
            b.addActionListener(e -> {
                wave = w;
                onWave.accept(w);
            });
            waves.add(b);
            waveButtons.add(b);
            waveSelector.add(b);
        }

        add(breadcrumb, BorderLayout.WEST);
        add(chips, BorderLayout.CENTER);
        add(waveSelector, BorderLayout.EAST);
    }

    /** Shows the pages of a section and selects the given page. */
    public void setSection(EditorSection section, String selectedPage) {
        chips.removeAll();
        pageButtons.clear();
        ButtonGroup group = new ButtonGroup();
        for (String page : section.pages()) {
            JToggleButton chip = new JToggleButton(page, page.equals(selectedPage));
            chip.putClientProperty("FlatLaf.style", CHIP_STYLE);
            chip.setFocusable(false);
            chip.addActionListener(e -> onPage.accept(page));
            group.add(chip);
            pageButtons.add(chip);
            chips.add(chip);
        }
        waveSelector.setVisible(section == EditorSection.PATCH);
        chips.revalidate();
        chips.repaint();
    }

    public void selectPage(String page) {
        for (JToggleButton b : pageButtons) {
            if (b.getText().equals(page)) {
                b.setSelected(true);
            }
        }
    }

    /** Breadcrumb, e.g. "PERF RAM1:00 Voyager › Part 1 › PATCH RAM1:12 Glass Choir". */
    public void setBreadcrumb(String... levels) {
        StringBuilder html = new StringBuilder("<html>");
        for (int i = 0; i < levels.length; i++) {
            boolean last = i == levels.length - 1;
            html.append(last ? "<b><font color='#ffffff'>" : "")
                    .append(escape(levels[i]))
                    .append(last ? "</font></b>" : "&nbsp;&nbsp;<font color='#5d616a'>›</font>&nbsp;&nbsp;");
        }
        breadcrumb.setText(html.append("</html>").toString());
    }

    public String getWave() {
        return wave;
    }

    /** Selects the next (+1) or previous (-1) wave, like the UP/DOWN cursors on the device. */
    public void stepWave(int delta) {
        int i = 0;
        for (int k = 0; k < waveButtons.size(); k++) {
            if (waveButtons.get(k).isSelected()) {
                i = k;
            }
        }
        waveButtons.get(Math.floorMod(i + delta, waveButtons.size())).doClick();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
