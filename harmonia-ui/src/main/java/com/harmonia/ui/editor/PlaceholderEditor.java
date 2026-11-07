package com.harmonia.ui.editor;

import java.awt.BorderLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.harmonia.ui.EditorSection;
import com.harmonia.ui.theme.HarmoniaColors;
import com.harmonia.ui.widget.SectionPanel;

/** Temporary view for the sections not built yet: says what will be there and in which phase. */
public class PlaceholderEditor extends JPanel implements EditorView {

    public PlaceholderEditor(EditorSection section, String description, String phase) {
        super(new BorderLayout());
        setOpaque(false);
        SectionPanel panel = new SectionPanel(section.title());
        StringBuilder html = new StringBuilder("<html><div style='width:520px'>")
                .append("<p style='font-size:13px; color:#d8dade'>").append(description).append("</p><br>")
                .append("<p style='color:#8b8f97'>Arrive en <b style='color:#2ec4b6'>").append(phase)
                .append("</b>.</p>");
        if (!section.pages().isEmpty()) {
            html.append("<br><p style='color:#8b8f97'>Pages du Wavestation SR couvertes : <b>")
                    .append(String.join(" · ", section.pages())).append("</b></p>");
        }
        JLabel text = new JLabel(html.append("</div></html>").toString());
        text.setFont(text.getFont().deriveFont(Font.PLAIN, 13f));
        text.setForeground(HarmoniaColors.TEXT);
        text.setVerticalAlignment(JLabel.TOP);
        text.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        panel.getContent().add(text, BorderLayout.CENTER);
        add(panel, BorderLayout.CENTER);
    }

    @Override
    public JComponent component() {
        return this;
    }
}
