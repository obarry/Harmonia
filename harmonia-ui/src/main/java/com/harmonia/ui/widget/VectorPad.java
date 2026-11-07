package com.harmonia.ui.widget;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.EventListener;

import javax.swing.JComponent;
import javax.swing.event.EventListenerList;

import com.harmonia.ui.theme.HarmoniaColors;

/**
 * Interactive vector mix pad: the 5 points of the Wavestation Mix Envelope (MIX ENV page).
 * <p>
 * Orientation and coordinates follow the Reference Guide (MIXENV page): wave A on the left,
 * B at the top, C on the right, D at the bottom; AC is the horizontal axis and BD the vertical
 * one, both from -127 to +127. Points outside the diamond are clipped to its edge, as the device
 * does. Point 0 is the mix at note on, 3 the sustain mix, 4 the release mix.
 * <ul>
 * <li>Drag a point to move it; click a point to select it; double-click: back to the centre
 * (equal mix, the "Center Point?" command of the device).</li>
 * <li>Keyboard: 0-4 select a point, arrows move it (Shift: by 10).</li>
 * </ul>
 * With a 2-wave patch only the AC axis is used; with a single wave the pad is disabled.
 */
public class VectorPad extends JComponent {

    /** Receives the changes made on the pad. */
    public interface Listener extends EventListener {
        /** A point was moved to (ac, bd). */
        void pointChanged(int point, int ac, int bd);

        /** A point was selected. */
        void pointSelected(int point);
    }

    public static final int POINTS = 5;
    public static final int MAX = 127;
    private static final int HANDLE_RADIUS = 9;
    private static final String[] WAVES = { "A", "B", "C", "D" };

    private final int[] ac = new int[POINTS];
    private final int[] bd = new int[POINTS];
    private int selected = 0;
    private int waveCount = 4;
    private int loopStart = -1;     // -1 = no loop, else loop from this point to point 3
    private int dragging = -1;
    private final EventListenerList listeners = new EventListenerList();

    public VectorPad() {
        setOpaque(false);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        installInput();
    }

    // ------------------------------------------------------------------ model

    public void addListener(Listener l) {
        listeners.add(Listener.class, l);
    }

    public int getAc(int point) {
        return ac[point];
    }

    public int getBd(int point) {
        return bd[point];
    }

    public int getSelectedPoint() {
        return selected;
    }

    /** Moves a point (clipped to the diamond) and notifies the listeners if it changed. */
    public void setPoint(int point, int newAc, int newBd) {
        int[] p = clipToDiamond(newAc, waveCount == 4 ? newBd : 0);
        if (p[0] != ac[point] || p[1] != bd[point]) {
            ac[point] = p[0];
            bd[point] = p[1];
            repaint();
            for (Listener l : listeners.getListeners(Listener.class)) {
                l.pointChanged(point, p[0], p[1]);
            }
        }
    }

    public void setSelectedPoint(int point) {
        if (point >= 0 && point < POINTS && point != selected) {
            selected = point;
            repaint();
            for (Listener l : listeners.getListeners(Listener.class)) {
                l.pointSelected(point);
            }
        }
    }

    /** Number of waves of the patch structure: 1, 2 (A and C) or 4. */
    public void setWaveCount(int waveCount) {
        this.waveCount = waveCount;
        setEnabled(waveCount > 1);
        if (waveCount == 2) {
            for (int i = 0; i < POINTS; i++) {
                bd[i] = 0;
            }
        }
        repaint();
    }

    /** First point of the loop (0, 1 or 2; the loop always ends on point 3), or -1 for no loop. */
    public void setLoopStart(int loopStart) {
        this.loopStart = loopStart;
        repaint();
    }

    /** Mix percentages A, B, C, D of a point. */
    public int[] getMix(int point) {
        return mixPercentages(ac[point], bd[point]);
    }

    /**
     * Clips (ac, bd) to the diamond |ac| + |bd| <= 127, moving the point towards the centre as the
     * device does (a corner of the square, e.g. AC=-127 BD=+127, becomes the middle of the A-B edge).
     */
    public static int[] clipToDiamond(int ac, int bd) {
        double a = Math.max(-MAX, Math.min(MAX, ac));
        double b = Math.max(-MAX, Math.min(MAX, bd));
        double sum = Math.abs(a) + Math.abs(b);
        if (sum > MAX) {
            a = a * MAX / sum;
            b = b * MAX / sum;
        }
        return new int[] { (int) Math.round(a), (int) Math.round(b) };
    }

    /**
     * Mix percentages A, B, C, D (sum 100) at a position of the diamond.
     * <p>
     * Bilinear interpolation between the four corners of the diamond. It gives the reference values
     * of the Reference Guide: centre = 25 % each, a corner = 100 % of its wave, the middle of an
     * edge = 50 % / 50 %.
     */
    public static int[] mixPercentages(int ac, int bd) {
        int[] p = clipToDiamond(ac, bd);
        double x = p[0] / (double) MAX;
        double y = p[1] / (double) MAX;
        double u = (x + y + 1) / 2;   // 0 on the A-D edge, 1 on the B-C edge
        double v = (x - y + 1) / 2;   // 0 on the A-B edge, 1 on the C-D edge
        double[] w = { (1 - u) * (1 - v), u * (1 - v), u * v, (1 - u) * v };
        int[] pct = new int[4];
        int sum = 0;
        int largest = 0;
        for (int i = 0; i < 4; i++) {
            pct[i] = (int) Math.round(w[i] * 100);
            sum += pct[i];
            if (w[i] > w[largest]) {
                largest = i;
            }
        }
        pct[largest] += 100 - sum; // rounding correction, the total is always 100 %
        return pct;
    }

    /**
     * Percentage of wave A in a 2-wave patch (the rest is wave C): -127 = all A, +127 = all C,
     * 0 = equal mix (Reference Guide, MIXENV page).
     */
    public static int mixTwoWaves(int ac) {
        int a = Math.max(-MAX, Math.min(MAX, ac));
        return (int) Math.round((MAX - a) * 100.0 / (2 * MAX));
    }

    // ------------------------------------------------------------------ geometry

    private double radius() {
        return Math.min(getWidth(), getHeight() - 30) / 2.0 - 26;
    }

    private double centerX() {
        return getWidth() / 2.0;
    }

    private double centerY() {
        return (getHeight() - 30) / 2.0;
    }

    private Point toScreen(int a, int b) {
        double r = radius();
        return new Point((int) Math.round(centerX() + a * r / MAX), (int) Math.round(centerY() - b * r / MAX));
    }

    private int[] toModel(int x, int y) {
        double r = radius();
        return new int[] { (int) Math.round((x - centerX()) * MAX / r), (int) Math.round((centerY() - y) * MAX / r) };
    }

    private int pointAt(int x, int y) {
        for (int i = POINTS - 1; i >= 0; i--) {
            if (toScreen(ac[i], bd[i]).distance(x, y) <= HANDLE_RADIUS + 3) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ input

    private void installInput() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (!isEnabled()) {
                    return;
                }
                int p = pointAt(e.getX(), e.getY());
                if (p >= 0) {
                    setSelectedPoint(p);
                    dragging = p;
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragging >= 0) {
                    int[] m = toModel(e.getX(), e.getY());
                    setPoint(dragging, m[0], m[1]);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = -1;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int p = pointAt(e.getX(), e.getY());
                if (isEnabled() && e.getClickCount() == 2 && p >= 0) {
                    setPoint(p, 0, 0);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!isEnabled()) {
                    return;
                }
                int step = e.isShiftDown() ? 10 : 1;
                int k = e.getKeyCode();
                if (k >= KeyEvent.VK_0 && k <= KeyEvent.VK_4) {
                    setSelectedPoint(k - KeyEvent.VK_0);
                } else if (k == KeyEvent.VK_LEFT) {
                    setPoint(selected, ac[selected] - step, bd[selected]);
                } else if (k == KeyEvent.VK_RIGHT) {
                    setPoint(selected, ac[selected] + step, bd[selected]);
                } else if (k == KeyEvent.VK_UP) {
                    setPoint(selected, ac[selected], bd[selected] + step);
                } else if (k == KeyEvent.VK_DOWN) {
                    setPoint(selected, ac[selected], bd[selected] - step);
                } else {
                    return;
                }
                e.consume();
            }
        });
    }

    // ------------------------------------------------------------------ painting

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(280, 300);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font base = getFont() != null ? getFont() : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        double cx = centerX();
        double cy = centerY();
        double r = radius();

        // Diamond, axes and grid
        Path2D diamond = new Path2D.Double();
        diamond.moveTo(cx - r, cy);
        diamond.lineTo(cx, cy - r);
        diamond.lineTo(cx + r, cy);
        diamond.lineTo(cx, cy + r);
        diamond.closePath();
        g2.setColor(new Color(0x121316));
        g2.fill(diamond);
        g2.setColor(new Color(0x25272C));
        g2.drawLine((int) (cx - r), (int) cy, (int) (cx + r), (int) cy);
        if (waveCount == 4) {
            g2.drawLine((int) cx, (int) (cy - r), (int) cx, (int) (cy + r));
        }
        g2.setColor(hasFocus() ? HarmoniaColors.ACCENT.darker() : new Color(0x3A3D44));
        g2.draw(diamond);

        // Wave letters: A left, B top, C right, D bottom
        g2.setFont(base.deriveFont(Font.BOLD, 14f));
        drawLetter(g2, 0, cx - r - 16, cy + 5, true);
        drawLetter(g2, 1, cx - 4, cy - r - 9, waveCount == 4);
        drawLetter(g2, 2, cx + r + 7, cy + 5, true);
        drawLetter(g2, 3, cx - 4, cy + r + 20, waveCount == 4);

        if (!isEnabled()) {
            g2.setFont(base.deriveFont(Font.ITALIC, 12f));
            g2.setColor(HarmoniaColors.TEXT_DIM);
            String msg = "Patch à une seule onde : pas de vector mix";
            g2.drawString(msg, (float) (cx - g2.getFontMetrics().stringWidth(msg) / 2.0), (float) cy - 20);
            g2.dispose();
            return;
        }

        // Envelope path: 0 -> 1 -> 2 -> 3 (note held), then 3 -> 4 dashed (release)
        Point[] s = new Point[POINTS];
        for (int i = 0; i < POINTS; i++) {
            s[i] = toScreen(ac[i], bd[i]);
        }
        g2.setColor(HarmoniaColors.WRITE);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 1; i <= 3; i++) {
            g2.drawLine(s[i - 1].x, s[i - 1].y, s[i].x, s[i].y);
        }
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] { 5, 4 }, 0));
        g2.drawLine(s[3].x, s[3].y, s[4].x, s[4].y);
        if (loopStart >= 0 && loopStart < 3) {
            g2.setColor(new Color(HarmoniaColors.ACCENT.getRed(), HarmoniaColors.ACCENT.getGreen(),
                    HarmoniaColors.ACCENT.getBlue(), 120));
            g2.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = loopStart + 1; i <= 3; i++) {
                g2.drawLine(s[i - 1].x, s[i - 1].y, s[i].x, s[i].y);
            }
        }

        // Handles
        g2.setStroke(new BasicStroke(2f));
        g2.setFont(base.deriveFont(Font.BOLD, 10f));
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i < POINTS; i++) {
            Ellipse2D handle = new Ellipse2D.Double(s[i].x - HANDLE_RADIUS, s[i].y - HANDLE_RADIUS,
                    2 * HANDLE_RADIUS, 2 * HANDLE_RADIUS);
            boolean sel = i == selected;
            g2.setColor(sel ? HarmoniaColors.WRITE : HarmoniaColors.PANEL);
            g2.fill(handle);
            g2.setColor(HarmoniaColors.WRITE);
            g2.draw(handle);
            g2.setColor(sel ? new Color(0x1A0F06) : HarmoniaColors.WRITE);
            String n = Integer.toString(i);
            g2.drawString(n, s[i].x - fm.stringWidth(n) / 2f, s[i].y + fm.getAscent() / 2f - 1);
        }

        // Readout of the selected point, as on the device screens (AC/BD and mix percentages)
        String readout;
        if (waveCount == 4) {
            int[] mix = getMix(selected);
            readout = String.format("Pt %d  AC %+d  BD %+d   \u2192   A %d%%  B %d%%  C %d%%  D %d%%", selected,
                    ac[selected], bd[selected], mix[0], mix[1], mix[2], mix[3]);
        } else {
            int a = mixTwoWaves(ac[selected]);
            readout = String.format("Pt %d  AC %+d   \u2192   A %d%%  C %d%%", selected, ac[selected], a, 100 - a);
        }
        g2.setFont(base.deriveFont(Font.BOLD, 11f));
        g2.setColor(HarmoniaColors.TEXT);
        fm = g2.getFontMetrics();
        g2.drawString(readout, (float) (cx - fm.stringWidth(readout) / 2.0), getHeight() - 8);
        g2.dispose();
    }

    private void drawLetter(Graphics2D g2, int wave, double x, double y, boolean active) {
        g2.setColor(active ? HarmoniaColors.ACCENT : HarmoniaColors.TEXT_FAINT);
        g2.drawString(WAVES[wave], (float) x, (float) y);
    }
}
