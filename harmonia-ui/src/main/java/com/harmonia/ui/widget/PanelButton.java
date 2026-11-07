package com.harmonia.ui.widget;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;

import javax.swing.JComponent;
import javax.swing.event.EventListenerList;

import com.harmonia.ui.theme.HarmoniaColors;

/**
 * Round rubber button of the rack front panel, with its label underneath and an optional LED
 * above it (PERF, MULTI, EDIT and WRITE/COMPARE have one on the Wavestation SR).
 */
public class PanelButton extends JComponent {

    private final String label;
    private final Color ledColor;
    private boolean ledOn;
    private boolean pressed;
    private boolean hover;
    private final EventListenerList listeners = new EventListenerList();

    /**
     * @param label    text printed under the button
     * @param ledColor LED colour, or {@code null} for a button without LED
     */
    public PanelButton(String label, Color ledColor) {
        this.label = label;
        this.ledColor = ledColor;
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                boolean fire = pressed && contains(e.getPoint());
                pressed = false;
                repaint();
                if (fire) {
                    fireAction();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        };
        addMouseListener(mouse);
    }

    public String getLabel() {
        return label;
    }

    public void setLedOn(boolean on) {
        if (ledColor != null && ledOn != on) {
            ledOn = on;
            repaint();
        }
    }

    public boolean isLedOn() {
        return ledOn;
    }

    public void addActionListener(ActionListener l) {
        listeners.add(ActionListener.class, l);
    }

    /** Simulates a click (used by keyboard shortcuts). */
    public void doClick() {
        fireAction();
    }

    private void fireAction() {
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, label);
        for (ActionListener l : listeners.getListeners(ActionListener.class)) {
            l.actionPerformed(e);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(66, 44);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        float cx = getWidth() / 2f;

        // LED
        if (ledColor != null) {
            g2.setColor(ledOn ? ledColor : ledColor.darker().darker().darker());
            g2.fill(new Ellipse2D.Float(cx - 3, 1, 6, 6));
        }

        // Button cap
        float rx = 12;
        float ry = 9;
        float cy = 19 + (pressed ? 1 : 0);
        Color top = hover ? new Color(0x55585F) : new Color(0x4A4D55);
        g2.setPaint(new RadialGradientPaint(cx - 3, cy - 4, 16, new float[] { 0f, 1f },
                new Color[] { pressed ? new Color(0x2E3035) : top, new Color(0x1C1D21) }));
        g2.fill(new Ellipse2D.Float(cx - rx, cy - ry, 2 * rx, 2 * ry));
        g2.setColor(Color.BLACK);
        g2.draw(new Ellipse2D.Float(cx - rx, cy - ry, 2 * rx, 2 * ry));

        // Label
        Font f = (getFont() != null ? getFont() : new Font(Font.SANS_SERIF, Font.PLAIN, 12)).deriveFont(Font.BOLD, 9f);
        g2.setFont(f);
        g2.setColor(HarmoniaColors.TEXT_DIM);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(label, cx - fm.stringWidth(label) / 2f, getHeight() - 3);
        g2.dispose();
    }
}
