package com.harmonia.ui.widget;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;

import com.harmonia.ui.theme.HarmoniaColors;
import com.harmonia.ui.theme.HarmoniaLaf;

/**
 * Two-line backlit LCD, as on the Wavestation SR front panel.
 * <p>
 * Line 1 shows the page name and the current item (e.g. {@code "FILTER   1:12 WA"}), line 2 the
 * edited value or the item name, just as the device would display them.
 */
public class LcdDisplay extends JComponent {

    private final int columns;
    private String line1 = "";
    private String line2 = "";
    private final Font font = HarmoniaLaf.monoFont(Font.BOLD, 16f);

    public LcdDisplay(int columns) {
        this.columns = columns;
        setOpaque(false);
    }

    public void setLines(String line1, String line2) {
        this.line1 = fit(line1);
        this.line2 = fit(line2);
        repaint();
    }

    public void setLine2(String line2) {
        this.line2 = fit(line2);
        repaint();
    }

    public String getLine1() {
        return line1;
    }

    public String getLine2() {
        return line2;
    }

    private String fit(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > columns ? s.substring(0, columns) : s;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(font);
        int w = fm.charWidth('M') * columns + 40;
        return new Dimension(w, 68);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();

        // Bezel and glass
        g2.setColor(new Color(0x050607));
        g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 10, 10));
        g2.setPaint(new GradientPaint(0, 6, HarmoniaColors.LCD_TOP, 0, h - 6, HarmoniaColors.LCD_BOTTOM));
        g2.fill(new RoundRectangle2D.Float(6, 6, w - 12, h - 12, 5, 5));

        // Text with a soft glow (drawn twice, the first time slightly transparent and offset)
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int x = 18;
        int y1 = h / 2 - 4;
        int y2 = y1 + fm.getAscent() + 2;
        drawGlow(g2, line1, x, y1, HarmoniaColors.LCD_TEXT);
        drawGlow(g2, line2, x, y2, HarmoniaColors.LCD_TEXT_DIM);
        g2.dispose();
    }

    private static void drawGlow(Graphics2D g2, String text, int x, int y, Color color) {
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
        g2.drawString(text, x + 1, y + 1);
        g2.drawString(text, x - 1, y);
        g2.setColor(color);
        g2.drawString(text, x, y);
    }
}
