package com.harmonia.ui.widget;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.function.IntFunction;

import javax.swing.JComponent;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;

import com.harmonia.ui.theme.HarmoniaColors;

/**
 * Rotary knob, the main editing control of Harmonia.
 * <ul>
 * <li>Drag up/down to change the value (hold Shift for fine steps).</li>
 * <li>Mouse wheel: +/- 1 step.</li>
 * <li>Double-click: back to the default value.</li>
 * <li>Keyboard when focused: arrows +/- 1, Page Up/Down +/- 10, Home/End min/max.</li>
 * </ul>
 * A bipolar knob (minimum below zero, e.g. -99..+99) draws its value arc from the centre.
 */
public class Knob extends JComponent {

    private static final double START_ANGLE = 225;   // degrees, Java2D convention (0 = 3 o'clock, CCW)
    private static final double SWEEP = 270;
    private static final int PIXELS_PER_RANGE = 200;  // drag distance for the full range

    private final String label;
    private final int min;
    private final int max;
    private final int defaultValue;
    private int value;
    private Color accent = HarmoniaColors.ACCENT;
    private IntFunction<String> formatter;
    private int diameter = 34;

    private final EventListenerList listeners = new EventListenerList();

    // Drag state
    private int dragStartY;
    private int dragStartValue;

    public Knob(String label, int min, int max, int defaultValue) {
        if (min >= max) {
            throw new IllegalArgumentException("min must be lower than max");
        }
        this.label = label;
        this.min = min;
        this.max = max;
        this.defaultValue = clamp(defaultValue, min, max);
        this.value = this.defaultValue;
        this.formatter = this::signedIfBipolar;
        setFocusable(true);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
        setToolTipText(label);
        installInputHandlers();
    }

    // ------------------------------------------------------------------ model

    public int getValue() {
        return value;
    }

    /** Sets the value (clamped to the range) and notifies listeners if it changed. */
    public void setValue(int newValue) {
        int v = clamp(newValue, min, max);
        if (v != value) {
            value = v;
            repaint();
            fireStateChanged();
        }
    }

    public int getMinimum() {
        return min;
    }

    public int getMaximum() {
        return max;
    }

    public int getDefaultValue() {
        return defaultValue;
    }

    public String getLabel() {
        return label;
    }

    public boolean isBipolar() {
        return min < 0 && max > 0;
    }

    /** Text shown under the knob, e.g. "+12" or "72". */
    public String getValueText() {
        return formatter.apply(value);
    }

    public Knob setFormatter(IntFunction<String> formatter) {
        this.formatter = formatter;
        repaint();
        return this;
    }

    public Knob setAccent(Color accent) {
        this.accent = accent;
        repaint();
        return this;
    }

    public Knob setDiameter(int diameter) {
        this.diameter = diameter;
        revalidate();
        repaint();
        return this;
    }

    public void addChangeListener(ChangeListener l) {
        listeners.add(ChangeListener.class, l);
    }

    public void removeChangeListener(ChangeListener l) {
        listeners.remove(ChangeListener.class, l);
    }

    private void fireStateChanged() {
        ChangeEvent e = new ChangeEvent(this);
        for (ChangeListener l : listeners.getListeners(ChangeListener.class)) {
            l.stateChanged(e);
        }
    }

    /** Position of the value in the range, 0.0 .. 1.0. */
    double getNormalizedValue() {
        return (value - min) / (double) (max - min);
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private String signedIfBipolar(int v) {
        return isBipolar() && v > 0 ? "+" + v : Integer.toString(v);
    }

    // ------------------------------------------------------------------ input

    private void installInputHandlers() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                dragStartY = e.getY();
                dragStartValue = value;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                double range = max - min;
                double factor = e.isShiftDown() ? 0.2 : 1.0;
                int delta = (int) Math.round((dragStartY - e.getY()) * range / PIXELS_PER_RANGE * factor);
                setValue(dragStartValue + delta);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    setValue(defaultValue);
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                setValue(value - e.getWheelRotation());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_UP, KeyEvent.VK_RIGHT -> setValue(value + 1);
                    case KeyEvent.VK_DOWN, KeyEvent.VK_LEFT -> setValue(value - 1);
                    case KeyEvent.VK_PAGE_UP -> setValue(value + 10);
                    case KeyEvent.VK_PAGE_DOWN -> setValue(value - 10);
                    case KeyEvent.VK_HOME -> setValue(min);
                    case KeyEvent.VK_END -> setValue(max);
                    default -> {
                        return;
                    }
                }
                e.consume();
            }
        });

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }

    // ------------------------------------------------------------------ painting

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Math.max(diameter + 26, 64), diameter + 14 + 32);
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

        double cx = getWidth() / 2.0;
        double r = diameter / 2.0;
        double cy = r + 7;
        double ringR = r + 5;

        // Track
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(0x33363D));
        g2.draw(new Arc2D.Double(cx - ringR, cy - ringR, 2 * ringR, 2 * ringR, START_ANGLE, -SWEEP, Arc2D.OPEN));

        // Value arc (from the start, or from the centre for bipolar knobs)
        double valueAngle = START_ANGLE - SWEEP * getNormalizedValue();
        double fromAngle = isBipolar() ? START_ANGLE - SWEEP * ((0 - min) / (double) (max - min)) : START_ANGLE;
        g2.setColor(isEnabled() ? accent : HarmoniaColors.TEXT_FAINT);
        g2.draw(new Arc2D.Double(cx - ringR, cy - ringR, 2 * ringR, 2 * ringR, fromAngle, valueAngle - fromAngle, Arc2D.OPEN));

        // Body
        g2.setPaint(new RadialGradientPaint((float) (cx - r * 0.25), (float) (cy - r * 0.3), (float) (r * 1.6),
                new float[] { 0f, 1f }, new Color[] { new Color(0x4A4D55), new Color(0x1C1D21) }));
        g2.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g2.setColor(hasFocus() ? accent : new Color(0x0B0C0E));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));

        // Pointer
        double a = Math.toRadians(valueAngle);
        double px = cx + (r - 4) * Math.cos(a);
        double py = cy - (r - 4) * Math.sin(a);
        g2.setColor(new Color(0xE8E8E8));
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Line2D.Double(cx, cy, px, py));

        // Label and value
        Font base = getFont() != null ? getFont() : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        int textTop = (int) (cy + ringR + 4);
        drawCentered(g2, label.toUpperCase(), base.deriveFont(Font.PLAIN, 10f), HarmoniaColors.TEXT_DIM, cx, textTop + 10);
        drawCentered(g2, getValueText(), base.deriveFont(Font.BOLD, 11.5f), HarmoniaColors.TEXT, cx, textTop + 24);

        g2.dispose();
    }

    private static void drawCentered(Graphics2D g2, String text, Font font, Color color, double cx, int baseline) {
        g2.setFont(font);
        g2.setColor(color);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), baseline);
    }
}
