package com.harmonia.ui.shell;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.harmonia.ui.EditorSection;
import com.harmonia.ui.editor.EditorView;
import com.harmonia.ui.editor.KnobFactory;
import com.harmonia.ui.editor.PatchEditorPanel;
import com.harmonia.ui.editor.PlaceholderEditor;
import com.harmonia.ui.mock.MockParameter;
import com.harmonia.ui.shell.MidiMonitorPanel.Direction;
import com.harmonia.ui.widget.Knob;
import com.harmonia.ui.widget.PianoKeyboard;

/**
 * Main window of Harmonia: assembles the front panel, the navigation bars, the librarian, the
 * editors, the inspector, the MIDI monitor, the keyboard and the status bar, and implements the
 * navigation logic of the Wavestation SR (sections, pages, EDIT "stairways", +1/-1).
 * <p>
 * Phase 5: everything runs on mock data and nothing is sent to the device; the MIDI monitor
 * shows the messages that <i>would</i> be sent.
 */
public class MainWindow extends JFrame {

    private static final int MIDI_CHANNEL = 0;

    private final FrontPanel frontPanel = new FrontPanel(this::onFrontPanel);
    private final NavigationBar navigationBar = new NavigationBar(this::showSection, this::onAction);
    private final PageBar pageBar = new PageBar(this::showPage, this::onWave);
    private final LibrarianPanel librarian = new LibrarianPanel(this::onLibrarianSelection);
    private final InspectorPanel inspector = new InspectorPanel();
    private final MidiMonitorPanel monitor = new MidiMonitorPanel();
    private final KeyboardPanel keyboardPanel = new KeyboardPanel();
    private final StatusBar statusBar = new StatusBar();

    private final CardLayout editorCards = new CardLayout();
    private final JPanel editorArea = new JPanel(editorCards);
    private final Map<EditorSection, EditorView> editors = new EnumMap<>(EditorSection.class);
    private final Map<EditorSection, String> currentPage = new EnumMap<>(EditorSection.class);
    private final PatchEditorPanel patchEditor;

    private EditorSection section = EditorSection.PATCH;
    private Knob lastKnob;
    private boolean modified;

    // Current items (mock), as "bank:number name"
    private String performanceBank = "RAM1";
    private int performanceNumber = 0;
    private String performanceName = "Ski Jam";
    private String patchBank = "RAM1";
    private int patchNumber = 2;
    private String patchName = "Glass Choir";

    public MainWindow() {
        super("Harmonia — KORG Wavestation SR");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        KnobFactory knobs = new KnobFactory(this::onKnobFocus, this::onKnobChange);
        patchEditor = new PatchEditorPanel(knobs, this::onEditorParameter);
        registerEditors();

        setContentPane(buildContent());
        installShortcuts();
        wireKeyboard();

        statusBar.setConnection(false,
                "Mode démonstration · aucun appareil connecté (la couche MIDI arrive en phase 2)");
        monitor.log(Direction.INFO, "Shell UI phase 5, données factices");
        monitor.log(Direction.INFO, "OUT simulés : rien n'est envoyé");

        showSection(EditorSection.PATCH);
        showPage("WAVES");
        SwingUtilities.invokeLater(() -> librarian.selectNumber(patchNumber));
        setMinimumSize(new Dimension(1280, 820));
        setSize(1600, 1000);
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------ layout

    private JPanel buildContent() {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(frontPanel);
        top.add(navigationBar);
        top.add(pageBar);

        librarian.setPreferredSize(new Dimension(290, 400));
        JPanel right = new JPanel(new BorderLayout(0, 10));
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(330, 400));
        right.add(inspector, BorderLayout.NORTH);
        right.add(monitor, BorderLayout.CENTER);

        editorArea.setOpaque(false);
        JPanel center = new JPanel(new BorderLayout(10, 0));
        center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        center.add(librarian, BorderLayout.WEST);
        center.add(editorArea, BorderLayout.CENTER);
        center.add(right, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout());
        JPanel keyboardWrapper = new JPanel(new BorderLayout());
        keyboardWrapper.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        keyboardPanel.setPreferredSize(new Dimension(100, 96));
        keyboardWrapper.add(keyboardPanel);
        bottom.add(keyboardWrapper, BorderLayout.CENTER);
        bottom.add(statusBar, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout());
        root.add(top, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        return root;
    }

    private void registerEditors() {
        editors.put(EditorSection.LIBRARIAN, new PlaceholderEditor(EditorSection.LIBRARIAN,
                "Vue complète des banques : lecture et envoi des banques entières, comparaison, copie entre banques, "
                        + "bibliothèque locale de fichiers .syx et sauvegarde complète de l'appareil.",
                "phase 4"));
        editors.put(EditorSection.PERFORMANCE, new PlaceholderEditor(EditorSection.PERFORMANCE,
                "Les 8 parts de la performance côte à côte (patch, niveau, sortie, transposition, désaccord, délai, "
                        + "mode) et la vue graphique des zones clavier et vélocité (splits et layers).",
                "phase 7"));
        editors.put(EditorSection.PATCH, patchEditor);
        editors.put(EditorSection.WAVE_SEQ, new PlaceholderEditor(EditorSection.WAVE_SEQ,
                "Éditeur timeline des wave sequences : un bloc par pas (durée, crossfade, niveau), boucle visuelle, "
                        + "compteur des 500 pas de la banque.",
                "phase 8"));
        editors.put(EditorSection.EFFECTS, new PlaceholderEditor(EditorSection.EFFECTS,
                "Sélection des effets FX1 et FX2, routage série ou parallèle et bus A–D en schéma interactif, "
                        + "mixages et paramètres de chaque type d'effet.",
                "phase 7"));
        editors.put(EditorSection.MULTISET, new PlaceholderEditor(EditorSection.MULTISET,
                "Les 32 multisets : 16 canaux MIDI avec performance, niveau et pan, et les effets propres au multiset.",
                "phase 7"));
        editors.put(EditorSection.GLOBAL, new PlaceholderEditor(EditorSection.GLOBAL,
                "Réglages MIDI et globaux de l'appareil : canal de base, mode MIDI, filtres de réception, remap, "
                        + "pédales, gammes micro-tonales et Performance Map.",
                "phase 9"));
        editors.forEach((s, view) -> {
            editorArea.add(view.component(), s.name());
            currentPage.put(s, s.pages().isEmpty() ? null : s.pages().get(0));
        });
    }

    // ------------------------------------------------------------------ navigation

    /** Shows a section (tab), as the PERF, MULTI and MIDI/GLOBAL buttons do on the device. */
    public void showSection(EditorSection s) {
        section = s;
        editorCards.show(editorArea, s.name());
        navigationBar.select(s);
        pageBar.setSection(s, currentPage.get(s));
        frontPanel.setLed(FrontPanel.Button.PERF, s != EditorSection.MULTISET);
        frontPanel.setLed(FrontPanel.Button.MULTI, s == EditorSection.MULTISET);
        frontPanel.setLed(FrontPanel.Button.EDIT, nextLevel(s) != null);
        updateBreadcrumb();
        updateLcd(null);
    }

    /** Selects a Wavestation page of the current section (page chips, PAGE+ and PAGE-). */
    public void showPage(String page) {
        currentPage.put(section, page);
        pageBar.selectPage(page);
        editors.get(section).showPage(page);
        updateLcd(null);
    }

    private void stepPage(int delta) {
        List<String> pages = section.pages();
        if (pages.isEmpty()) {
            return;
        }
        int i = Math.max(0, pages.indexOf(currentPage.get(section)));
        showPage(pages.get(Math.floorMod(i + delta, pages.size())));
    }

    /** The level reached with EDIT from a section (the "stairways" of the device), or null. */
    private static EditorSection nextLevel(EditorSection s) {
        return switch (s) {
            case LIBRARIAN -> EditorSection.PERFORMANCE;
            case PERFORMANCE -> EditorSection.PATCH;
            case PATCH -> EditorSection.WAVE_SEQ;
            default -> null;
        };
    }

    private void onFrontPanel(FrontPanel.Button button) {
        switch (button) {
            case PERF -> showSection(EditorSection.PERFORMANCE);
            case MULTI -> showSection(EditorSection.MULTISET);
            case MIDI_GLOBAL -> showSection(EditorSection.GLOBAL);
            case EDIT -> {
                EditorSection next = nextLevel(section);
                if (next != null) {
                    showSection(next);
                }
            }
            case PAGE_NEXT -> stepPage(1);
            case PAGE_PREVIOUS -> stepPage(-1);
            case YES -> increment(1);
            case NO -> increment(-1);
            case BANK -> librarian.nextBank();
            case WRITE_COMPARE -> onAction(NavigationBar.Action.WRITE);
            case CURSOR_LEFT -> KeyboardFocusManager.getCurrentKeyboardFocusManager().focusPreviousComponent(lastKnob);
            case CURSOR_RIGHT -> KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent(lastKnob);
            case CURSOR_UP -> pageBar.stepWave(-1);
            case CURSOR_DOWN -> pageBar.stepWave(1);
        }
    }

    /** +1/YES and -1/NO: change the selected parameter, or the selected sound in the librarian. */
    private void increment(int delta) {
        if (lastKnob != null && lastKnob.isShowing()) {
            lastKnob.setValue(lastKnob.getValue() + delta);
        } else {
            librarian.step(delta);
        }
    }

    private void onWave(String wave) {
        patchEditor.setSelectedWave(wave);
        updateBreadcrumb();
        updateLcd(null);
    }

    private void onLibrarianSelection(LibrarianPanel.Kind kind, LibrarianPanel.Entry entry) {
        switch (kind) {
            case PERF -> {
                performanceBank = entry.bank();
                performanceNumber = entry.number();
                performanceName = entry.name();
            }
            case PATCH -> {
                patchBank = entry.bank();
                patchNumber = entry.number();
                patchName = entry.name();
            }
            case WAVESEQ -> {
                // wave sequence editor: phase 8
            }
        }
        modified = false;
        frontPanel.setLed(FrontPanel.Button.WRITE_COMPARE, false);
        updateBreadcrumb();
        updateLcd(null);
        logOut(kind == LibrarianPanel.Kind.PERF ? 0x19 : 0x10, entry.bank(), entry.number(),
                "Requête " + (kind == LibrarianPanel.Kind.PERF ? "performance " : "patch ") + entry.id());
    }

    private void onAction(NavigationBar.Action action) {
        switch (action) {
            case GET -> logOut(0x10, patchBank, patchNumber, "Requête patch " + itemId(patchBank, patchNumber));
            case SEND -> monitor.log(Direction.OUT, "F0 42 3" + MIDI_CHANNEL + " 28 40 … (861 o)  Dump patch");
            case COMPARE -> monitor.log(Direction.INFO, "Compare : bascule version éditée / original (phase 6)");
            case WRITE -> {
                logOut(0x11, patchBank, patchNumber, "Écriture patch " + itemId(patchBank, patchNumber));
                modified = false;
                frontPanel.setLed(FrontPanel.Button.WRITE_COMPARE, false);
                updateStatus();
            }
        }
    }

    // ------------------------------------------------------------------ knobs

    private void onKnobFocus(Knob knob) {
        lastKnob = knob;
        inspector.show(knob);
        MockParameter p = (MockParameter) knob.getClientProperty(MockParameter.class);
        if (p != null && section.pages().contains(p.page())) {
            showPage(p.page());
        }
        updateLcd(knob);
    }

    /** Changes made with the graphical editors (vector pad). */
    private void onEditorParameter(MockParameter p, String value, String lcdText) {
        if (section == EditorSection.PATCH && !p.page().equals(currentPage.get(section))) {
            showPage(p.page());
        }
        frontPanel.getLcd().setLine2(lcdText);
        if (p != MockParameter.MIX_POINT) {
            modified = true;
            frontPanel.setLed(FrontPanel.Button.WRITE_COMPARE, true);
            updateStatus();
        }
        if (navigationBar.isLiveEdit()) {
            monitor.logCoalesced(p.officialName(), Direction.OUT,
                    parameterChange(p.number(), value) + "  " + p.officialName());
            frontPanel.getMidiLed().flash();
        }
    }

    private void onKnobChange(Knob knob) {
        if (knob != lastKnob) {
            onKnobFocus(knob);
        }
        inspector.valueChanged(knob);
        updateLcd(knob);
        modified = true;
        frontPanel.setLed(FrontPanel.Button.WRITE_COMPARE, true);
        updateStatus();

        MockParameter p = (MockParameter) knob.getClientProperty(MockParameter.class);
        if (p != null && navigationBar.isLiveEdit()) {
            monitor.logCoalesced(p.officialName(), Direction.OUT, parameterChange(p.number(), knob.getValueText())
                    + "  " + p.officialName());
            frontPanel.getMidiLed().flash();
        }
    }

    /**
     * Parameter change message (Reference Guide, appendix 4, 2.1.17): function 41, 42 or 43
     * according to the number, number on two 7-bit bytes, value as ASCII text.
     */
    static String parameterChange(int number, String value) {
        int function = number >= 407 ? 0x43 : number >= 380 ? 0x42 : 0x41;
        StringBuilder sb = new StringBuilder(String.format("%02X %02X %02X", function, number & 0x7F, number >> 7));
        for (byte b : value.getBytes(StandardCharsets.US_ASCII)) {
            sb.append(String.format(" %02X", b == ' ' ? 0x7F : b));
        }
        return sb.append(" 00").toString();
    }

    // ------------------------------------------------------------------ display

    private void updateLcd(Knob knob) {
        String line1;
        String line2;
        switch (section) {
            case PATCH -> {
                String page = currentPage.get(section);
                line1 = String.format("%-11s%s W%s", page, shortId(patchBank, patchNumber), shortWave());
                line2 = knob != null ? knob.getLabel() + ":" + knob.getValueText() : patchName;
            }
            case PERFORMANCE -> {
                line1 = String.format("%-11s%s", currentPage.get(section), itemId(performanceBank, performanceNumber));
                line2 = performanceName;
            }
            case LIBRARIAN -> {
                line1 = "PERFORM    " + itemId(performanceBank, performanceNumber);
                line2 = performanceName;
            }
            default -> {
                String page = currentPage.get(section);
                line1 = page != null ? page : section.lcdName();
                line2 = "";
            }
        }
        frontPanel.getLcd().setLines(line1, line2);
    }

    private String shortWave() {
        String w = pageBar.getWave();
        return "All".equals(w) ? "*" : w;
    }

    private void updateBreadcrumb() {
        String perf = "PERF " + itemId(performanceBank, performanceNumber) + " " + performanceName;
        String patch = "PATCH " + itemId(patchBank, patchNumber) + " " + patchName;
        switch (section) {
            case PERFORMANCE -> pageBar.setBreadcrumb(perf);
            case PATCH -> pageBar.setBreadcrumb(perf, "Part 1", patch, "Wave " + pageBar.getWave());
            case WAVE_SEQ -> pageBar.setBreadcrumb(perf, "Part 1", patch, "Wave " + pageBar.getWave(), "WSEQ");
            default -> pageBar.setBreadcrumb(section.title());
        }
        updateStatus();
    }

    private void updateStatus() {
        statusBar.setRightText((navigationBar.isLiveEdit() ? "Live edit ON" : "Live edit OFF")
                + " · 40 ms/param · Patch " + itemId(patchBank, patchNumber)
                + (modified ? " modifié ●" : " à jour"));
    }

    private static String itemId(String bank, int number) {
        return bank + ":" + String.format("%02d", number);
    }

    /** Short form used on the device LCD: "1:12" for RAM1, "C:03" for the card. */
    private static String shortId(String bank, int number) {
        String b = bank.equals("CARD") ? "C" : bank.replaceAll("\\D", "");
        return b + ":" + String.format("%02d", number);
    }

    private void logOut(int function, String bank, int number, String comment) {
        monitor.log(Direction.OUT, String.format("F0 42 3%X 28 %02X %02X %02X F7  %s", MIDI_CHANNEL, function,
                sysexBank(bank), number, comment));
        frontPanel.getMidiLed().flash();
    }

    /** Bank number used in SysEx messages (see DESIGN_ROADMAP.md, section 5.2). */
    static int sysexBank(String bank) {
        return switch (bank) {
            case "RAM1" -> 0;
            case "RAM2" -> 1;
            case "ROM11" -> 2;
            case "CARD" -> 3;
            case "RAM3" -> 4;
            default -> Integer.parseInt(bank.substring(3)) + 1; // ROM4..ROM10 -> 5..11
        };
    }

    // ------------------------------------------------------------------ keyboard

    private void wireKeyboard() {
        keyboardPanel.getKeyboard().addNoteListener(new PianoKeyboard.NoteListener() {
            @Override
            public void noteOn(int note, int velocity) {
                monitor.log(Direction.OUT, String.format("9%X %02X %02X  Note on %s", MIDI_CHANNEL, note, velocity,
                        PianoKeyboard.noteName(note)));
                frontPanel.getMidiLed().flash();
            }

            @Override
            public void noteOff(int note) {
                monitor.log(Direction.OUT, String.format("8%X %02X 00  Note off %s", MIDI_CHANNEL, note,
                        PianoKeyboard.noteName(note)));
            }
        });
    }

    /**
     * Keyboard shortcuts:
     * Ctrl+1..7 sections, Ctrl+PgDn / Ctrl+PgUp pages (PAGE+ / PAGE-), Ctrl+E EDIT,
     * Ctrl+B BANK, Ctrl+Shift+W WRITE.
     */
    private void installShortcuts() {
        JComponent root = getRootPane();
        EditorSection[] sections = EditorSection.values();
        for (int i = 0; i < sections.length; i++) {
            EditorSection s = sections[i];
            bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, InputEvent.CTRL_DOWN_MASK), "section" + i,
                    () -> showSection(s));
        }
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, InputEvent.CTRL_DOWN_MASK), "pageNext",
                () -> onFrontPanel(FrontPanel.Button.PAGE_NEXT));
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, InputEvent.CTRL_DOWN_MASK), "pagePrevious",
                () -> onFrontPanel(FrontPanel.Button.PAGE_PREVIOUS));
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK), "edit",
                () -> onFrontPanel(FrontPanel.Button.EDIT));
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK), "bank",
                () -> onFrontPanel(FrontPanel.Button.BANK));
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
                "write", () -> onFrontPanel(FrontPanel.Button.WRITE_COMPARE));
    }

    private static void bind(JComponent c, KeyStroke key, String name, Runnable action) {
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, name);
        c.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }
}
