package com.harmonia.ui.editor;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

import com.harmonia.ui.mock.MockParameter;
import com.harmonia.ui.theme.HarmoniaColors;
import com.harmonia.ui.widget.EnvelopeEditor;
import com.harmonia.ui.widget.Knob;
import com.harmonia.ui.widget.SectionPanel;
import com.harmonia.ui.widget.VectorPad;
import com.harmonia.wavestation.model.Patch;

/**
 * Patch editor: the four wave cards, the vector mix pad, the envelopes (Amp Env and Env 1),
 * the filter and LFO 1. Values are mock data until the model is connected (phase 6).
 */
public class PatchEditorPanel extends JPanel implements EditorView {

    private static final String[] WAVES = { "A", "B", "C", "D" };
    private static final String[] WAVE_NAMES = { "Glass 1", "Vocal Aah", "WS 07 Spinner", "Breath Noise" };
    /** Parameter number of WAVEx_LEVEL; coarse and fine tuning follow (Reference Guide, appendix 4). */
    private static final int[] WAVE_LEVEL_PARAM = { 150, 157, 164, 171 };
    /** Initial mix envelope points (AC, BD), mock values. */
    private static final int[][] MIX_POINTS = { { -70, 20 }, { -25, -75 }, { 35, -45 }, { -5, 85 }, { 60, 25 } };

    private static final String AMP = "AMP ENV";
    private static final String ENV1 = "ENV1";

    private final KnobFactory knobs;
    private final ParameterSink sink;
    private final Map<String, List<SectionPanel>> sectionsByPage = new HashMap<>();
    private final JLabel[] waveBadges = new JLabel[4];
    private final JLabel[] waveTitles = new JLabel[4];
    /** Knobs bound to a field of the patch structure, filled by {@link #showPatch}. */
    private final List<Binding> bindings = new ArrayList<>();
    private Patch patch;
    private String selectedWave = "A";

    /**
     * A knob bound to a field of the patch structure. A per-wave field ("Cutoff") is read in the
     * wave selected with A B C D All; a fixed field ("A.Wave_Coarse", "Mix_Rate1") always the same.
     */
    private record Binding(Knob knob, String field, boolean perWave) {
    }

    /** Structure fields of the filter and LFO knobs (Reference Guide, appendix 4, 5.2). */
    private static final Map<MockParameter, String> FIELDS = Map.of(
            MockParameter.CUTOFF, "Cutoff",
            MockParameter.TRACKING, "Key_Filter",
            MockParameter.EXCITER, "Filter_Exciter",
            MockParameter.FILTER_SRC1_AMT, "S1_Filter_Amt",
            MockParameter.LFO1_RATE, "Lfo1_Rate",
            MockParameter.LFO1_AMOUNT, "Lfo1_Amt",
            MockParameter.LFO1_DELAY, "Lfo1_Delay",
            MockParameter.LFO1_FADE, "Lfo1_Fade");
    private final VectorPad vectorPad = new VectorPad();
    private final CardLayout envelopeCards = new CardLayout();
    private final JPanel envelopeArea = new JPanel(envelopeCards);
    private final Map<String, JToggleButton> envelopeTabs = new HashMap<>();

    /**
     * @param knobs creates the knobs wired to the window
     * @param sink  receives the changes made on the vector pad
     */
    public PatchEditorPanel(KnobFactory knobs, ParameterSink sink) {
        super(new GridBagLayout());
        this.knobs = knobs;
        this.sink = sink;
        setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 10, 10);

        // Row 0: the four waves
        JPanel waves = new JPanel(new GridLayout(1, 4, 10, 0));
        waves.setOpaque(false);
        SectionPanel[] waveCards = new SectionPanel[4];
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

        // Rows 1-2, left: vector mix
        SectionPanel vector = vectorSection();
        c.gridy = 1;
        c.gridwidth = 1;
        c.gridheight = 2;
        c.weightx = 0.36;
        c.weighty = 1;
        add(vector, c);
        register("MIX ENV", vector);
        register("MIX MOD", vector);

        // Row 1, right: envelopes
        SectionPanel envelopes = envelopeSection();
        c.gridx = 1;
        c.gridheight = 1;
        c.weightx = 0.64;
        c.weighty = 1;      // the envelope takes all the extra height
        add(envelopes, c);
        register(AMP, envelopes);
        register("AMP MOD", envelopes);
        register(ENV1, envelopes);
        register("ENV1MOD", envelopes);

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
        c.weighty = 0;      // fixed height: knobs aligned with the mix envelope knobs
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
        if (AMP.equals(page) || "AMP MOD".equals(page)) {
            showEnvelope(AMP);
        } else if (ENV1.equals(page) || "ENV1MOD".equals(page)) {
            showEnvelope(ENV1);
        }
    }

    /** Highlights the badge of the edited wave ("A".."D", or "All"). */
    public void setSelectedWave(String wave) {
        for (int i = 0; i < 4; i++) {
            setBadge(waveBadges[i], "All".equals(wave) || WAVES[i].equals(wave));
        }
        selectedWave = "All".equals(wave) ? "A" : wave;
        if (patch != null) {
            showValues();
        }
    }

    /**
     * Shows the values of a patch read from the device: knobs, envelopes, vector pad, wave names.
     * The knobs notify their changes as usual: the caller must not send them back to the device.
     */
    public void showPatch(Patch newPatch) {
        this.patch = newPatch;
        int count = newPatch.getWaveCount();
        for (int i = 0; i < 4; i++) {
            String letter = WAVES[i];
            boolean active = count == 4 || i == 0 || (count == 2 && i == 2);
            String source = active ? newPatch.wave(letter).describeSource() : "inutilisée";
            waveTitles[i].setText(waveTitle(letter, source, active));
        }
        vectorPad.setWaveCount(count);
        for (int point = 0; point < VectorPad.POINTS; point++) {
            vectorPad.setPoint(point, newPatch.getMixAc(point), newPatch.getMixBd(point));
        }
        showValues();
    }

    public Patch getPatch() {
        return patch;
    }

    private void showValues() {
        for (Binding b : bindings) {
            String field = b.perWave() ? selectedWave + "." + b.field() : b.field();
            b.knob().setValue(patch.struct().get(field));
        }
    }

    private static String waveTitle(String letter, String source, boolean active) {
        return "<html><font color='#8b8f97' size='2'><b>WAVE " + letter + "</b></font><br>"
                + "<font color='" + (active ? "#ffffff" : "#5d616a") + "'><b>" + source + "</b></font></html>";
    }

    private Knob bind(Knob knob, String field, boolean perWave) {
        bindings.add(new Binding(knob, field, perWave));
        return knob;
    }

    public VectorPad getVectorPad() {
        return vectorPad;
    }

    private void register(String page, SectionPanel... sections) {
        sectionsByPage.computeIfAbsent(page, k -> new ArrayList<>()).addAll(List.of(sections));
    }

    // ------------------------------------------------------------------ waves

    private SectionPanel waveCard(int i) {
        SectionPanel card = new SectionPanel(null);
        JLabel badge = new JLabel(WAVES[i], SwingConstants.CENTER);
        badge.setOpaque(true);
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 15f));
        badge.setPreferredSize(new Dimension(28, 28));
        waveBadges[i] = badge;
        setBadge(badge, i == 0);

        JLabel title = new JLabel(waveTitle(WAVES[i], WAVE_NAMES[i], true));
        waveTitles[i] = title;
        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        head.setOpaque(false);
        head.add(badge);
        head.add(title);

        int level = WAVE_LEVEL_PARAM[i];
        String w = WAVES[i];
        JPanel row = new JPanel(new GridLayout(1, 3));
        row.setOpaque(false);
        row.add(bind(knobs.create(new MockParameter("Coarse", "WAVE" + w + "_TUNE_COARSE", level + 1, "WAVES",
                -24, 24, 0)), w + ".Wave_Coarse", false));
        row.add(bind(knobs.create(new MockParameter("Fine", "WAVE" + w + "_TUNE_FINE", level + 2, "WAVES",
                -99, 99, 0)), w + ".Wave_Fine", false));
        row.add(bind(knobs.create(new MockParameter("Level", "WAVE" + w + "_LEVEL", level, "WAVES", 0, 99, 99),
                HarmoniaColors.WRITE), w + ".Indiv_Level", false));
        row.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        card.getContent().add(head, BorderLayout.NORTH);
        card.getContent().add(row, BorderLayout.CENTER);
        return card;
    }

    private static void setBadge(JLabel badge, boolean on) {
        badge.setBackground(on ? HarmoniaColors.ACCENT : HarmoniaColors.CONTROL);
        badge.setForeground(on ? new Color(0x08201E) : HarmoniaColors.TEXT_DIM);
    }

    // ------------------------------------------------------------------ vector mix

    private SectionPanel vectorSection() {
        SectionPanel s = new SectionPanel("Vector mix");
        s.setHeaderRight(note("Loop 1↔3 · INF"));
        for (int i = 0; i < VectorPad.POINTS; i++) {
            vectorPad.setPoint(i, MIX_POINTS[i][0], MIX_POINTS[i][1]);
        }
        vectorPad.setLoopStart(1);
        vectorPad.setSelectedPoint(3);
        vectorPad.addListener(new VectorPad.Listener() {
            @Override
            public void pointChanged(int point, int ac, int bd) {
                String lcd = String.format("Pt:%d AC:%+d BD:%+d", point, ac, bd);
                sink.parameterChanged(MockParameter.MIX_AC, String.format("%+d", ac), lcd);
                sink.parameterChanged(MockParameter.MIX_BD, String.format("%+d", bd), lcd);
            }

            @Override
            public void pointSelected(int point) {
                int[] mix = vectorPad.getMix(point);
                sink.parameterChanged(MockParameter.MIX_POINT, Integer.toString(point),
                        String.format("A%02d B%02d C%02d D%02d", mix[0], mix[1], mix[2], mix[3]));
            }
        });

        JPanel times = new JPanel(new GridLayout(1, 4));
        times.setOpaque(false);
        for (MockParameter p : new MockParameter[] { MockParameter.MIX_TIME_1, MockParameter.MIX_TIME_2,
                MockParameter.MIX_TIME_3, MockParameter.MIX_TIME_4 }) {
            times.add(bind(knobs.create(p), "Mix_Rate" + p.label().substring(5), false));
        }
        s.getContent().add(vectorPad, BorderLayout.CENTER);
        s.getContent().add(times, BorderLayout.SOUTH);
        return s;
    }

    // ------------------------------------------------------------------ envelopes

    private SectionPanel envelopeSection() {
        SectionPanel s = new SectionPanel(null);
        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tabs.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (String name : new String[] { AMP, ENV1 }) {
            JToggleButton tab = new JToggleButton(name, AMP.equals(name));
            tab.putClientProperty("FlatLaf.style", "arc: 999; margin: 1,10,1,10; font: -2 bold; "
                    + "selectedBackground: #2a2c31; selectedForeground: #ffffff");
            tab.setFocusable(false);
            tab.addActionListener(e -> envelopeCards.show(envelopeArea, name));
            group.add(tab);
            envelopeTabs.put(name, tab);
            tabs.add(tab);
        }
        tabs.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        envelopeArea.setOpaque(false);
        envelopeArea.add(envelopeCard(EnvelopeEditor.Type.AMP,
                new MockParameter[] { MockParameter.AMP_LEVEL_0, MockParameter.AMP_LEVEL_1, MockParameter.AMP_LEVEL_2,
                        MockParameter.AMP_LEVEL_3 },
                new MockParameter[] { MockParameter.AMP_TIME_1, MockParameter.AMP_TIME_2, MockParameter.AMP_TIME_3,
                        MockParameter.AMP_TIME_4 }), AMP);
        envelopeArea.add(envelopeCard(EnvelopeEditor.Type.ENV1,
                new MockParameter[] { MockParameter.ENV1_LEVEL_0, MockParameter.ENV1_LEVEL_1, MockParameter.ENV1_LEVEL_2,
                        MockParameter.ENV1_LEVEL_3, MockParameter.ENV1_LEVEL_4 },
                new MockParameter[] { MockParameter.ENV1_TIME_1, MockParameter.ENV1_TIME_2, MockParameter.ENV1_TIME_3,
                        MockParameter.ENV1_TIME_4 }), ENV1);

        s.getContent().add(tabs, BorderLayout.NORTH);
        s.getContent().add(envelopeArea, BorderLayout.CENTER);
        return s;
    }

    private void showEnvelope(String name) {
        envelopeCards.show(envelopeArea, name);
        envelopeTabs.get(name).setSelected(true);
    }

    /**
     * Envelope editor and its knobs, kept in sync both ways: dragging a breakpoint turns the
     * matching knob (which notifies the window), turning a knob moves the breakpoint.
     */
    private JPanel envelopeCard(EnvelopeEditor.Type type, MockParameter[] levels, MockParameter[] times) {
        EnvelopeEditor editor = new EnvelopeEditor(type);
        Knob[] levelKnobs = new Knob[levels.length];
        Knob[] timeKnobs = new Knob[times.length];

        // Knobs in the device order: time 1, level 1, time 2, level 2... with level 0 first
        JPanel row = new JPanel(new GridLayout(1, levels.length + times.length));
        row.setOpaque(false);
        for (int i = 0; i < levels.length; i++) {
            levelKnobs[i] = bind(knobs.create(levels[i]),
                    (type == EnvelopeEditor.Type.AMP ? "AEG_Level" : "EG_Level") + i, true);
            editor.setLevel(i, levelKnobs[i].getValue());
            int index = i;
            levelKnobs[i].addChangeListener(e -> editor.setLevel(index, levelKnobs[index].getValue()));
        }
        for (int i = 0; i < times.length; i++) {
            timeKnobs[i] = bind(knobs.create(times[i], HarmoniaColors.WRITE),
                    (type == EnvelopeEditor.Type.AMP ? "AEG_Rate" : "EG_Rate") + (i + 1), true);
            editor.setTime(i, timeKnobs[i].getValue());
            int index = i;
            timeKnobs[i].addChangeListener(e -> editor.setTime(index, timeKnobs[index].getValue()));
        }
        row.add(levelKnobs[0]);
        for (int i = 0; i < times.length; i++) {
            row.add(timeKnobs[i]);
            if (i + 1 < levels.length) {
                row.add(levelKnobs[i + 1]);
            }
        }

        editor.addListener(new EnvelopeEditor.Listener() {
            @Override
            public void levelChanged(int index, int value) {
                levelKnobs[index].setValue(value);
            }

            @Override
            public void timeChanged(int index, int value) {
                timeKnobs[index].setValue(value);
            }
        });

        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setOpaque(false);
        card.add(editor, BorderLayout.CENTER);
        card.add(row, BorderLayout.SOUTH);
        return card;
    }

    // ------------------------------------------------------------------ helpers

    private SectionPanel knobSection(String title, MockParameter... params) {
        SectionPanel s = new SectionPanel(title);
        JPanel row = new JPanel(new GridLayout(1, params.length));
        row.setOpaque(false);
        for (MockParameter p : params) {
            row.add(bind(knobs.create(p, p == MockParameter.CUTOFF ? HarmoniaColors.WRITE : null), FIELDS.get(p), true));
        }
        s.getContent().add(row, BorderLayout.NORTH);
        return s;
    }

    private static JLabel note(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 10.5f));
        l.setForeground(HarmoniaColors.ACCENT);
        return l;
    }
}
