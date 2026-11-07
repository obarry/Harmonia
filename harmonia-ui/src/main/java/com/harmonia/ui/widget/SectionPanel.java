package com.harmonia.ui.widget;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.harmonia.ui.theme.HarmoniaColors;

/**
 * Rounded dark panel with an upper-case title ("LIBRARIAN", "VECTOR MIX"...).
 * <p>
 * Content goes in {@link #getContent()}; an optional component can be placed at the right of
 * the title. {@link #setHighlighted(boolean)} draws an accent border, used to show which section
 * matches the current Wavestation page.
 */
public class SectionPanel extends JPanel {

    private static final int ARC = 14;

    private final JPanel header = new JPanel(new BorderLayout());
    private final JLabel titleLabel = new JLabel();
    private final JPanel content = new JPanel(new BorderLayout());
    private boolean highlighted;

    public SectionPanel(String title) {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        titleLabel.setText(spaced(title));
        titleLabel.setForeground(HarmoniaColors.TEXT_DIM);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 11f));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        header.add(titleLabel, BorderLayout.WEST);
        content.setOpaque(false);

        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        header.setVisible(title != null && !title.isEmpty());
    }

    /** Letter-spaced upper-case title, imitating the mockup typography. */
    private static String spaced(String title) {
        if (title == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : title.toUpperCase().toCharArray()) {
            sb.append(c).append('\u200A'); // hair space
        }
        return sb.toString().trim();
    }

    public JPanel getContent() {
        return content;
    }

    public void setHeaderRight(JComponent c) {
        header.add(c, BorderLayout.EAST);
    }

    public void setHighlighted(boolean highlighted) {
        if (this.highlighted != highlighted) {
            this.highlighted = highlighted;
            repaint();
        }
    }

    public boolean isHighlighted() {
        return highlighted;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, ARC, ARC);
        g2.setColor(HarmoniaColors.PANEL);
        g2.fill(shape);
        Color border = highlighted ? HarmoniaColors.ACCENT : new Color(0x1F2125);
        g2.setColor(border);
        g2.draw(shape);
        g2.dispose();
    }
}
