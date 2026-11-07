package com.harmonia.ui.widget;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.EventListener;

import javax.swing.JComponent;
import javax.swing.event.EventListenerList;

import com.harmonia.ui.theme.HarmoniaColors;

/**
 * Graphical editor of a Wavestation envelope: 4 times and 4 or 5 levels (0-99).
 * <ul>
 * <li>{@link Type#AMP}: Amp Env, levels 0-3; the release (time 4) always goes back to silence.</li>
 * <li>{@link Type#ENV1}: Envelope 1, levels 0-4 (adjustable release level).</li>
 * </ul>
 * Level 3 is the sustain level, held while the key is down. Dragging a breakpoint vertically
 * changes its level, horizontally the time of the segment that leads to it. As on the device,
 * a greater time means a slower (longer) segment.
 */
public class EnvelopeEditor extends JComponent {

    /** Envelope type. */
    public enum Type {
        AMP(4), ENV1(5);

        final int levels;

        Type(int levels) {
            this.levels = levels;
        }
    }

    /** Receives the changes made with the mouse. */
    public interface Listener extends EventListener {
        void levelChanged(int index, int value);

        void timeChanged(int index, int value);
    }

    public static final int TIMES = 4;
    private static final int MAX = 99;
    private static final int PAD = 14;
    private static final int HANDLE = 6;

    private final Type type;
    private final int[] levels;
    private final int[] times = new int[TIMES];
    private final EventListenerList listeners = new EventListenerList();
    private int dragging = -1;      // index of the dragged breakpoint (1..)

    public EnvelopeEditor(Type type) {
        this.type = type;
        this.levels = new int[type.levels];
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        installInput();
    }

    public Type getType() {
        return type;
    }

    public void addListener(Listener l) {
        listeners.add(Listener.class, l);
    }

    public int getLevel(int i) {
        return levels[i];
    }

    public int getTime(int i) {
        return times[i];
    }

    public int getLevelCount() {
        return levels.length;
    }

    /** Sets a level without notifying the listeners (used to follow the knobs). */
    public void setLevel(int i, int value) {
        levels[i] = clamp(value);
        repaint();
    }

    /** Sets a time (index 0..3 for times 1..4) without notifying the listeners. */
    public void setTime(int i, int value) {
        times[i] = clamp(value);
        repaint();
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(MAX, v));
    }

    // ------------------------------------------------------------------ geometry

    /**
     * Screen positions of the breakpoints: start, end of time 1, 2, 3, then the release end.
     * The sustain plateau (between the end of time 3 and note off) has a fixed width.
     */
    Point[] breakpoints() {
        double w = getWidth() - 2.0 * PAD;
        double h = getHeight() - 2.0 * PAD - 14;
        double sustain = w * 0.16;
        double unit = (w - sustain) / (4 * (MAX + 12));     // each segment: (time + 12) units
        double x = PAD;
        Point[] p = new Point[6];
        p[0] = new Point((int) x, y(levels[0], h));
        for (int i = 0; i < 3; i++) {
            x += (times[i] + 12) * unit;
            p[i + 1] = new Point((int) x, y(levels[i + 1], h));
        }
        x += sustain;
        p[4] = new Point((int) x, y(levels[3], h));                 // note off
        x += (times[3] + 12) * unit;
        int release = type == Type.ENV1 ? levels[4] : 0;
        p[5] = new Point((int) x, y(release, h));
        return p;
    }

    private int y(int level, double h) {
        return (int) Math.round(PAD + h * (1 - level / (double) MAX));
    }

    private int levelAt(int y) {
        double h = getHeight() - 2.0 * PAD - 14;
        return clamp((int) Math.round((1 - (y - PAD) / h) * MAX));
    }

    // ------------------------------------------------------------------ input

    private void installInput() {
        MouseAdapter mouse = new MouseAdapter() {
            private int startX;
            private int startTime;

            @Override
            public void mousePressed(MouseEvent e) {
                Point[] p = breakpoints();
                dragging = -1;
                for (int i = 0; i < p.length; i++) {
                    if (draggable(i) && p[i].distance(e.getPoint()) <= HANDLE + 4) {
                        dragging = i;
                        startX = e.getX();
                        startTime = timeIndex(i) >= 0 ? times[timeIndex(i)] : 0;
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragging < 0) {
                    return;
                }
                int li = levelIndex(dragging);
                if (li >= 0) {
                    int v = levelAt(e.getY());
                    if (v != levels[li]) {
                        levels[li] = v;
                        for (Listener l : listeners.getListeners(Listener.class)) {
                            l.levelChanged(li, v);
                        }
                    }
                }
                int ti = timeIndex(dragging);
                if (ti >= 0) {
                    double unit = (getWidth() - 2.0 * PAD) * 0.84 / (4 * (MAX + 12));
                    int v = clamp(startTime + (int) Math.round((e.getX() - startX) / unit));
                    if (v != times[ti]) {
                        times[ti] = v;
                        for (Listener l : listeners.getListeners(Listener.class)) {
                            l.timeChanged(ti, v);
                        }
                    }
                }
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = -1;
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** Level edited by a breakpoint, or -1 (the note-off point and the Amp Env end have none). */
    private int levelIndex(int breakpoint) {
        return switch (breakpoint) {
            case 0, 1, 2, 3 -> breakpoint;
            case 5 -> type == Type.ENV1 ? 4 : -1;
            default -> -1;
        };
    }

    /** Time edited by a breakpoint (the segment that leads to it), or -1. */
    private static int timeIndex(int breakpoint) {
        return switch (breakpoint) {
            case 1, 2, 3 -> breakpoint - 1;
            case 5 -> 3;
            default -> -1;
        };
    }

    private boolean draggable(int breakpoint) {
        return breakpoint != 4;
    }

    // ------------------------------------------------------------------ painting

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(520, 150);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Point[] p = breakpoints();
        int bottom = getHeight() - PAD - 14;

        // Grid
        g2.setColor(new Color(0x1F2125));
        for (int i = 1; i < 4; i++) {
            int gy = PAD + (bottom - PAD) * i / 4;
            g2.drawLine(PAD, gy, getWidth() - PAD, gy);
        }
        // Note off marker
        g2.setColor(new Color(0x3A3D44));
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] { 3, 3 }, 0));
        g2.drawLine(p[4].x, PAD, p[4].x, bottom);

        // Area and curve
        Path2D curve = new Path2D.Double();
        curve.moveTo(p[0].x, p[0].y);
        for (int i = 1; i < p.length; i++) {
            curve.lineTo(p[i].x, p[i].y);
        }
        Path2D area = new Path2D.Double(curve);
        area.lineTo(p[5].x, bottom);
        area.lineTo(p[0].x, bottom);
        area.closePath();
        Color a = HarmoniaColors.ACCENT;
        g2.setPaint(new GradientPaint(0, PAD, new Color(a.getRed(), a.getGreen(), a.getBlue(), 90), 0, bottom,
                new Color(a.getRed(), a.getGreen(), a.getBlue(), 0)));
        g2.fill(area);
        g2.setColor(a);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(curve);

        // Handles
        for (int i = 0; i < p.length; i++) {
            if (!draggable(i)) {
                continue;
            }
            g2.setColor(i == dragging ? HarmoniaColors.WRITE : a);
            g2.fill(new Ellipse2D.Double(p[i].x - HANDLE / 2.0 - 1, p[i].y - HANDLE / 2.0 - 1, HANDLE + 2, HANDLE + 2));
        }

        // Segment labels
        Font f = (getFont() != null ? getFont() : new Font(Font.SANS_SERIF, Font.PLAIN, 12)).deriveFont(Font.BOLD, 9.5f);
        g2.setFont(f);
        g2.setColor(HarmoniaColors.TEXT_FAINT);
        String[] labels = { "T1", "T2", "T3", "SUSTAIN", "T4" };
        int[][] spans = { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 }, { 4, 5 } };
        for (int i = 0; i < labels.length; i++) {
            int mid = (p[spans[i][0]].x + p[spans[i][1]].x) / 2;
            int tw = g2.getFontMetrics().stringWidth(labels[i]);
            g2.drawString(labels[i], mid - tw / 2, getHeight() - 4);
        }
        g2.dispose();
    }
}
