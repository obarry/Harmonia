package com.harmonia.ui.shell;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.harmonia.ui.theme.HarmoniaColors;
import com.harmonia.ui.widget.Knob;
import com.harmonia.ui.widget.LcdDisplay;
import com.harmonia.ui.widget.LedIndicator;
import com.harmonia.ui.widget.PanelButton;

/**
 * Rack front panel of the Wavestation SR, redrawn as the header of Harmonia.
 * <p>
 * It is not only decorative: every button acts in Harmonia (see {@link Button}) and the LCD
 * shows what the device would display. Layout from left to right, as on the device: volume,
 * cursors, MIDI LED, LCD, the 10 buttons, the name and the two card slots.
 */
public class FrontPanel extends JPanel {

    /** Front panel buttons. Names follow the device. */
    public enum Button {
        PERF, MULTI, EDIT, PAGE_NEXT, YES, BANK, WRITE_COMPARE, MIDI_GLOBAL, PAGE_PREVIOUS, NO,
        CURSOR_UP, CURSOR_DOWN, CURSOR_LEFT, CURSOR_RIGHT
    }

    /** Where the user can put his own logo image (not shipped with Harmonia, see README). */
    public static final File LOGO_FILE = new File(System.getProperty("user.home"), ".harmonia/logo.png");

    private static final int HEIGHT = 104;
    private static final int EAR_WIDTH = 34;

    private final LcdDisplay lcd = new LcdDisplay(24);
    private final LedIndicator midiLed = new LedIndicator(HarmoniaColors.LED_GREEN, 7);
    private final Map<Button, PanelButton> buttons = new EnumMap<>(Button.class);
    private final CardSlots cardSlots = new CardSlots();
    private final Consumer<Button> listener;

    public FrontPanel(Consumer<Button> listener) {
        this.listener = listener;
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, EAR_WIDTH + 14, 8, EAR_WIDTH + 14));
        setOpaque(false);

        add(volumeKnob());
        add(Box.createHorizontalStrut(14));
        add(cursorPad());
        add(Box.createHorizontalStrut(14));
        add(labelled(midiLed, "MIDI"));
        add(Box.createHorizontalStrut(12));
        add(lcd);
        add(Box.createHorizontalStrut(18));
        add(buttonGrid());
        add(Box.createHorizontalGlue());
        add(nameBlock());
        add(Box.createHorizontalStrut(24));
        add(cardSlots);
    }

    public LcdDisplay getLcd() {
        return lcd;
    }

    public LedIndicator getMidiLed() {
        return midiLed;
    }

    public void setLed(Button button, boolean on) {
        PanelButton b = buttons.get(button);
        if (b != null) {
            b.setLedOn(on);
        }
    }

    public void setProgramCard(String status) {
        cardSlots.programCard = status;
        cardSlots.repaint();
    }

    // ------------------------------------------------------------------ building blocks

    private JComponent volumeKnob() {
        Knob volume = new Knob("Volume", 0, 127, 100);
        volume.setDiameter(26);
        volume.setFormatter(v -> "");
        volume.setToolTipText("Volume (contrôle local, sans effet sur l'appareil)");
        volume.setMaximumSize(volume.getPreferredSize());
        return volume;
    }

    private JComponent cursorPad() {
        JPanel pad = new JPanel(new GridLayout(3, 3, 2, 2));
        pad.setOpaque(false);
        pad.add(Box.createGlue());
        pad.add(arrow("▲", Button.CURSOR_UP));
        pad.add(Box.createGlue());
        pad.add(arrow("◀", Button.CURSOR_LEFT));
        pad.add(Box.createGlue());
        pad.add(arrow("▶", Button.CURSOR_RIGHT));
        pad.add(Box.createGlue());
        pad.add(arrow("▼", Button.CURSOR_DOWN));
        pad.add(Box.createGlue());
        Dimension d = new Dimension(66, 66);
        pad.setPreferredSize(d);
        pad.setMaximumSize(d);
        return labelled(pad, "CURSOR");
    }

    private JButton arrow(String text, Button id) {
        JButton b = new JButton(text);
        b.putClientProperty("FlatLaf.style", "arc: 6; margin: 0,0,0,0; font: -4; borderWidth: 0; focusWidth: 0");
        b.setFocusable(false);
        b.addActionListener(e -> listener.accept(id));
        return b;
    }

    private JComponent buttonGrid() {
        JPanel grid = new JPanel(new GridLayout(2, 5, 4, 2));
        grid.setOpaque(false);
        addButton(grid, Button.PERF, "PERF", HarmoniaColors.LED_GREEN);
        addButton(grid, Button.MULTI, "MULTI", HarmoniaColors.LED_GREEN);
        addButton(grid, Button.EDIT, "EDIT", HarmoniaColors.LED_GREEN);
        addButton(grid, Button.PAGE_NEXT, "PAGE+", null);
        addButton(grid, Button.YES, "+1/YES", null);
        addButton(grid, Button.BANK, "BANK", null);
        addButton(grid, Button.WRITE_COMPARE, "WRITE/CMP", HarmoniaColors.LED_RED);
        addButton(grid, Button.MIDI_GLOBAL, "MIDI/GLB", null);
        addButton(grid, Button.PAGE_PREVIOUS, "PAGE−", null);
        addButton(grid, Button.NO, "−1/NO", null);
        grid.setMaximumSize(grid.getPreferredSize());
        return grid;
    }

    private void addButton(JPanel grid, Button id, String label, Color led) {
        PanelButton b = new PanelButton(label, led);
        b.addActionListener(e -> listener.accept(id));
        buttons.put(id, b);
        grid.add(b);
    }

    private JComponent nameBlock() {
        JPanel block = new JPanel();
        block.setOpaque(false);
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));

        JLabel logo = logoLabel();
        JLabel name = new JLabel("W A V E S T A T I O N   S R");
        name.setFont(name.getFont().deriveFont(Font.BOLD, 20f));
        name.setForeground(new Color(0xE9E9EA));
        JLabel subtitle = new JLabel("H A R M O N I A   ·   E D I T O R  /  L I B R A R I A N");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.BOLD, 9f));
        subtitle.setForeground(HarmoniaColors.TEXT_DIM);

        for (JComponent c : new JComponent[] { logo, name, subtitle }) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            block.add(c);
            block.add(Box.createVerticalStrut(3));
        }
        block.setMaximumSize(block.getPreferredSize());
        return block;
    }

    /** User supplied logo if present, otherwise a discreet dashed placeholder. */
    private JLabel logoLabel() {
        if (LOGO_FILE.isFile()) {
            ImageIcon icon = new ImageIcon(LOGO_FILE.getPath());
            int h = 20;
            int w = Math.max(1, icon.getIconWidth() * h / Math.max(1, icon.getIconHeight()));
            return new JLabel(new ImageIcon(icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH)));
        }
        JLabel placeholder = new JLabel("LOGO");
        placeholder.setFont(placeholder.getFont().deriveFont(Font.BOLD, 9f));
        placeholder.setForeground(HarmoniaColors.TEXT_FAINT);
        placeholder.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(HarmoniaColors.TEXT_FAINT, 3, 2),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        placeholder.setToolTipText("Placez votre image de logo dans " + LOGO_FILE.getPath());
        return placeholder;
    }

    private static JComponent labelled(JComponent c, String text) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        c.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 8.5f));
        l.setForeground(HarmoniaColors.TEXT_DIM);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(Box.createVerticalGlue());
        p.add(c);
        p.add(Box.createVerticalStrut(4));
        p.add(l);
        p.add(Box.createVerticalGlue());
        p.setMaximumSize(new Dimension(p.getPreferredSize().width, Integer.MAX_VALUE));
        return p;
    }

    // ------------------------------------------------------------------ painting

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width, HEIGHT);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g2.setPaint(new GradientPaint(0, 0, HarmoniaColors.RACK_TOP, 0, h, HarmoniaColors.RACK_BOTTOM));
        g2.fillRect(0, 0, w, h);
        // Rack ears with their screw holes
        for (int x : new int[] { 0, w - EAR_WIDTH }) {
            g2.setColor(HarmoniaColors.RACK_EAR);
            g2.fillRect(x, 0, EAR_WIDTH, h);
            g2.setColor(new Color(0x111214));
            g2.drawLine(x == 0 ? EAR_WIDTH : x, 0, x == 0 ? EAR_WIDTH : x, h);
            for (int y : new int[] { 22, h - 22 }) {
                Ellipse2D hole = new Ellipse2D.Float(x + EAR_WIDTH / 2f - 8, y - 5, 16, 10);
                g2.setColor(new Color(0x0B0B0D));
                g2.fill(hole);
                g2.setColor(new Color(0x3A3C42));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(hole);
            }
        }
        g2.setColor(Color.BLACK);
        g2.drawLine(0, h - 1, w, h - 1);
        g2.dispose();
    }

    /** The two card slots of the SR: PCM DATA and PROG DATA. */
    private static final class CardSlots extends JComponent {
        String pcmCard = "—";
        String programCard = "—";

        CardSlots() {
            setOpaque(false);
            setToolTipText("Cartes PCM et programme (état lu sur l'appareil)");
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(190, 60);
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
            Font f = getFont().deriveFont(Font.BOLD, 8.5f);
            drawSlot(g2, f, 0, "PCM DATA", pcmCard);
            drawSlot(g2, f, 98, "PROG DATA", programCard);
            g2.dispose();
        }

        private void drawSlot(Graphics2D g2, Font f, int x, String label, String status) {
            g2.setFont(f);
            g2.setColor(HarmoniaColors.TEXT_DIM);
            g2.drawString(label, x, 16);
            g2.setColor(HarmoniaColors.PANEL_SUNKEN);
            g2.fillRoundRect(x, 24, 88, 8, 3, 3);
            g2.setColor(new Color(0x3A3D44));
            g2.drawRoundRect(x, 24, 88, 8, 3, 3);
            g2.setFont(f.deriveFont(Font.PLAIN, 10f));
            g2.setColor("—".equals(status) ? HarmoniaColors.TEXT_FAINT : HarmoniaColors.ACCENT);
            g2.drawString(status, x, 48);
        }
    }
}
