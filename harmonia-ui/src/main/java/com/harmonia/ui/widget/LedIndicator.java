package com.harmonia.ui.widget;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;

import javax.swing.JComponent;
import javax.swing.Timer;

import com.harmonia.ui.theme.HarmoniaColors;

/**
 * Small LED, like the ones on the Wavestation SR front panel.
 * <p>
 * {@link #flash()} lights it briefly: used to show MIDI activity.
 */
public class LedIndicator extends JComponent {

    private static final int FLASH_MS = 80;

    private final Color color;
    private final int size;
    private boolean on;
    private final Timer flashTimer;

    public LedIndicator(Color color, int size) {
        this.color = color;
        this.size = size;
        setOpaque(false);
        flashTimer = new Timer(FLASH_MS, e -> setOn(false));
        flashTimer.setRepeats(false);
    }

    public LedIndicator(Color color) {
        this(color, 8);
    }

    public boolean isOn() {
        return on;
    }

    public void setOn(boolean on) {
        if (this.on != on) {
            this.on = on;
            repaint();
        }
    }

    /** Lights the LED for a short moment (restarts the delay if already lit). */
    public void flash() {
        setOn(true);
        flashTimer.restart();
    }

    @Override
    public Dimension getPreferredSize() {
        int s = size + 8; // room for the glow
        return new Dimension(s, s);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = size / 2f;
        if (on) {
            // Glow
            g2.setPaint(new RadialGradientPaint(cx, cy, r + 4, new float[] { 0f, 1f },
                    new Color[] { withAlpha(color, 110), withAlpha(color, 0) }));
            g2.fill(new Ellipse2D.Float(cx - r - 4, cy - r - 4, 2 * r + 8, 2 * r + 8));
            g2.setColor(color);
        } else {
            g2.setColor(color.darker().darker().darker());
        }
        g2.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
        g2.setColor(HarmoniaColors.PANEL_SUNKEN);
        g2.draw(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
        g2.dispose();
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
