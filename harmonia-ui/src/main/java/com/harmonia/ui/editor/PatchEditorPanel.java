package com.harmonia.ui.editor;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import com.harmonia.ui.mock.MockParameter;
import com.harmonia.ui.theme.HarmoniaColors;
import com.harmonia.ui.widget.SectionPanel;

/**
 * Patch editor (first version of the shell): the four wave cards, the vector mix, the amp
 * envelope, the filter and LFO 1.
 * <p>
 * The graphical vector pad and envelope editor come next in phase 5; values are mock data until
 * phase 6.
 */
public class PatchEditorPanel extends JPanel implements EditorView {

    private static final String[] WAVES = { "A", "B", "C", "D" };
    private static final String[] WAVE_NAMES = { "Glass 1", "Vocal Aah", "WS 07 Spinner", "Breath Noise" };
    /** Parameter number of WAVEx_LEVEL; coarse and fine tuning follow (Reference Guide, appendix 4). */
    private static final int[] WAVE_LEVEL_PARAM = { 150, 157, 164, 171 };

    private final KnobFactory knobs;
    private final Map<String, List<SectionPanel>> sectionsByPage = new HashMap<>();
    private final SectionPanel[] waveCards = new SectionPanel[4];
    private final JLabel[] waveBadges = new JLabel[4];

    public PatchEditorPanel(KnobFactory knobs) {
        super(new GridBagLayout());
        this.knobs = knobs;
        setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 10, 10);

        // Row 0: the four waves
        JPanel waves = new JPanel(new GridLayout(1, 4, 10, 0));
        waves.setOpaque(false);
        for (int i = 0; i < 4; i++) {
            waveCards[i] = waveCard(i);
            waves.add(waveCards[i]);
        }
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        c.weighty = 0;
        add(waves, c);
        register("WAVES", waveCards);

        // Row 1-2, left: vector mix
        SectionPanel vector = vectorSection();
        c.gridy = 1;
        c.gridwidth = 1;
        c.gridheight = 2;
        c.weightx = 0.35;
        c.weighty = 1;
        add(vector, c);
        register("MIX ENV", vector);
        register("MIX MOD", vector);

        // Row 1, right: amp envelope
        SectionPanel amp = knobSection("Amp Env", MockParameter.AMP_RATE_1, MockParameter.AMP_RATE_2,
                MockParameter.AMP_LEVEL_2, MockParameter.AMP_RATE_3, MockParameter.AMP_LEVEL_3, MockParameter.AMP_RATE_4);
        amp.setHeaderRight(note("éditeur graphique : étape suivante"));
        c.gridx = 1;
        c.gridheight = 1;
        c.weightx = 0.65;
        c.weighty = 0.5;
        add(amp, c);
        register("AMP ENV", amp);

        // Row 2, right: filter and LFO 1 side by side
        SectionPanel filter = knobSection("Filter", MockParameter.CUTOFF, MockParameter.TRACKING,
                MockParameter.EXCITER, MockParameter.FILTER_SRC1_AMT);
        SectionPanel lfo = knobSection("LFO 1", MockParameter.LFO1_RATE, MockParameter.LFO1_AMOUNT,
                MockParameter.LFO1_DELAY, MockParameter.LFO1_FADE);
        JPanel row = new JPanel(new GridLayout(1, 2, 10, 0));
        row.setOpaque(false);
        row.add(filter);
        row.add(lfo);
        c.gridy = 2;
        add(row, c);
        register("FILTER", filter);
        register("LFO1", lfo);
    }

    @Override
    public JComponent component() {
        return this;
    }

    @Override
    public void showPage(String page) {
        sectionsByPage.values().forEach(list -> list.forEach(s -> s.setHighlighted(false)));
        List<SectionPanel> matching = sectionsByPage.get(page);
        if (matching != null) {
            matching.forEach(s -> s.setHighlighted(true));
        }
    }

    /** Highlights the badge of the edited wave ("A".."D", or "All"). */
    public void setSelectedWave(String wave) {
        for (int i = 0; i < 4; i++) {
            setSelectedWaveBadge(waveBadges[i], "All".equals(wave) || WAVES[i].equals(wave));
        }
    }

    private void register(String page, SectionPanel... sections) {
        sectionsByPage.computeIfAbsent(page, k -> new java.util.ArrayList<>()).addAll(List.of(sections));
    }

    // ------------------------------------------------------------------ sections

    private SectionPanel waveCard(int i) {
        SectionPanel card = new SectionPanel(null);
        JLabel badge = new JLabel(WAVES[i], SwingConstants.CENTER);
        badge.setOpaque(true);
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 15f));
        badge.setPreferredSize(new Dimension(28, 28));
        waveBadges[i] = badge;

        JLabel title = new JLabel("<html><font color='#8b8f97' size='2'><b>WAVE " + WAVES[i] + "</b></font><br>"
                + "<font color='#ffffff'><b>" + WAVE_NAMES[i] + "</b></font></html>");
        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        head.setOpaque(false);
        head.add(badge);
        head.add(title);

        int level = WAVE_LEVEL_PARAM[i];
        String w = WAVES[i];
        JPanel row = new JPanel(new GridLayout(1, 3));
        row.setOpaque(false);
        row.add(knobs.create(new MockParameter("Coarse", "WAVE" + w + "_TUNE_COARSE", level + 1, "WAVES", -24, 24, 0)));
        row.add(knobs.create(new MockParameter("Fine", "WAVE" + w + "_TUNE_FINE", level + 2, "WAVES", -99, 99, 0)));
        row.add(knobs.create(new MockParameter("Level", "WAVE" + w + "_LEVEL", level, "WAVES", 0, 99, 99),
                HarmoniaColors.WRITE));
        row.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        card.getContent().add(head, BorderLayout.NORTH);
        card.getContent().add(row, BorderLayout.CENTER);
        setSelectedWaveBadge(badge, i == 0);
        return card;
    }

    private static void setSelectedWaveBadge(JLabel badge, boolean on) {
        badge.setBackground(on ? HarmoniaColors.ACCENT : HarmoniaColors.CONTROL);
        badge.setForeground(on ? new Color(0x08201E) : HarmoniaColors.TEXT_DIM);
    }

    private SectionPanel vectorSection() {
        SectionPanel s = new SectionPanel("Vector mix");
        s.setHeaderRight(note("pad interactif : étape suivante"));
        JPanel rates = new JPanel(new GridLayout(1, 4));
        rates.setOpaque(false);
        for (MockParameter p : new MockParameter[] { MockParameter.MIX_RATE_1, MockParameter.MIX_RATE_2,
                MockParameter.MIX_RATE_3, MockParameter.MIX_RATE_4 }) {
            rates.add(knobs.create(p));
        }
        s.getContent().add(new VectorPreview(), BorderLayout.CENTER);
        s.getContent().add(rates, BorderLayout.SOUTH);
        return s;
    }

    private SectionPanel knobSection(String title, MockParameter... params) {
        SectionPanel s = new SectionPanel(title);
        JPanel row = new JPanel(new GridLayout(1, params.length));
        row.setOpaque(false);
        for (MockParameter p : params) {
            row.add(knobs.create(p, p == MockParameter.CUTOFF ? HarmoniaColors.WRITE : null));
        }
        s.getContent().add(row, BorderLayout.NORTH);
        return s;
    }

    private static JLabel note(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.ITALIC, 10.5f));
        l.setForeground(HarmoniaColors.TEXT_FAINT);
        return l;
    }

    /**
     * Static preview of the vector mix diamond with the 5 points of the mix envelope.
     * Replaced by the interactive VectorPad widget in the next step.
     */
    private static final class VectorPreview extends JComponent {
        private static final int[][] POINTS = { { 0, 0 }, { -50, -60 }, { 70, -20 }, { 20, 75 }, { -40, 30 } };

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(260, 260);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int r = Math.min(getWidth(), getHeight()) / 2 - 24;
            Polygon diamond = new Polygon(new int[] { cx, cx + r, cx, cx - r }, new int[] { cy - r, cy, cy + r, cy }, 4);
            g2.setColor(new Color(0x121316));
            g2.fill(diamond);
            g2.setColor(new Color(0x3A3D44));
            g2.draw(diamond);
            g2.setColor(new Color(0x25272C));
            g2.drawLine(cx, cy - r, cx, cy + r);
            g2.drawLine(cx - r, cy, cx + r, cy);

            g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
            g2.setColor(HarmoniaColors.ACCENT);
            g2.drawString("A", cx - 4, cy - r - 8);
            g2.drawString("B", cx + r + 8, cy + 5);
            g2.drawString("C", cx - 4, cy + r + 18);
            g2.drawString("D", cx - r - 18, cy + 5);

            // Envelope path, coordinates -127..127 mapped onto the diamond
            double scale = r / 127.0;
            g2.setColor(HarmoniaColors.WRITE);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] { 5, 3 }, 0));
            for (int i = 1; i < POINTS.length; i++) {
                g2.drawLine((int) (cx + POINTS[i - 1][0] * scale), (int) (cy + POINTS[i - 1][1] * scale),
                        (int) (cx + POINTS[i][0] * scale), (int) (cy + POINTS[i][1] * scale));
            }
            g2.setStroke(new BasicStroke(2f));
            g2.setFont(getFont().deriveFont(Font.BOLD, 10f));
            for (int i = 0; i < POINTS.length; i++) {
                double x = cx + POINTS[i][0] * scale;
                double y = cy + POINTS[i][1] * scale;
                g2.setColor(HarmoniaColors.PANEL);
                g2.fill(new Ellipse2D.Double(x - 8, y - 8, 16, 16));
                g2.setColor(HarmoniaColors.WRITE);
                g2.draw(new Ellipse2D.Double(x - 8, y - 8, 16, 16));
                g2.drawString(Integer.toString(i), (float) x - 3, (float) y + 4);
            }
            g2.dispose();
        }
    }
}
