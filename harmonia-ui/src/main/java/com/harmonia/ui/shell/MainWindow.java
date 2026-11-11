package com.harmonia.ui.shell;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.harmonia.app.DeviceSession;
import com.harmonia.app.LibraryStore;
import com.harmonia.app.LibrarySync;
import com.harmonia.app.SoundLibrary;
import com.harmonia.midi.MidiBytes;
import com.harmonia.midi.MidiConnection;
import com.harmonia.ui.EditorSection;
import com.harmonia.ui.editor.EditorView;
import com.harmonia.ui.editor.KnobFactory;
import com.harmonia.ui.editor.PatchEditorPanel;
import com.harmonia.ui.editor.PerformanceEditorPanel;
import com.harmonia.ui.editor.PlaceholderEditor;
import com.harmonia.ui.mock.MockParameter;
import com.harmonia.ui.shell.MidiMonitorPanel.Direction;
import com.harmonia.ui.widget.Knob;
import com.harmonia.ui.widget.PianoKeyboard;
import com.harmonia.wavestation.device.WavestationDevice;
import com.harmonia.wavestation.model.Dumps;
import com.harmonia.wavestation.model.Part;
import com.harmonia.wavestation.model.PartField;
import com.harmonia.wavestation.model.Patch;
import com.harmonia.wavestation.model.Performance;
import com.harmonia.wavestation.param.Parameter;
import com.harmonia.wavestation.sysex.MessageType;
import com.harmonia.wavestation.sysex.WavestationBank;
import com.harmonia.wavestation.sysex.WavestationSysex;

/**
 * Main window of Harmonia: assembles the front panel, the navigation bars, the librarian, the
 * editors, the inspector, the MIDI monitor, the keyboard and the status bar, and implements the
 * navigation logic of the Wavestation SR (sections, pages, EDIT "stairways", +1/-1).
 * <p>
 * Phase 2: when a device is connected ({@link DeviceSession}), knob changes are sent as SysEx
 * parameter changes, notes are played on the device, Get / Send / Write talk to it and the MIDI
 * monitor shows the real traffic. Without a device, the monitor shows the messages that
 * <i>would</i> be sent. The sounds shown are still mock data until the model exists (phase 3).
 */
public class MainWindow extends JFrame {

    private final FrontPanel frontPanel = new FrontPanel(this::onFrontPanel);
    private final NavigationBar navigationBar = new NavigationBar(this::showSection, this::onAction);
    private final PageBar pageBar = new PageBar(this::showPage, this::onWave);
    private final LibrarianPanel librarian = new LibrarianPanel(this::onLibrarianSelection, this::readBank,
            this::readAllBanks);
    private final InspectorPanel inspector = new InspectorPanel();
    private final MidiMonitorPanel monitor = new MidiMonitorPanel();
    private final KeyboardPanel keyboardPanel = new KeyboardPanel();
    private final StatusBar statusBar = new StatusBar();
    private final DeviceSession session = new DeviceSession();
    private final SoundLibrary library = new SoundLibrary();
    /** Copy of the device memory saved between sessions. */
    private final LibraryStore store = LibraryStore.defaultStore();
    /** Background reading of banks in progress, or null. */
    private LibrarySync sync;

    private final CardLayout editorCards = new CardLayout();
    private final JPanel editorArea = new JPanel(editorCards);
    private final Map<EditorSection, EditorView> editors = new EnumMap<>(EditorSection.class);
    private final Map<EditorSection, String> currentPage = new EnumMap<>(EditorSection.class);
    private final PatchEditorPanel patchEditor;
    private final PerformanceEditorPanel performanceEditor;
    /** True while values read from the device are shown: knob changes must not be sent back. */
    private boolean loading;

    private EditorSection section = EditorSection.PATCH;
    private Knob lastKnob;
    private boolean modified;
    /** Last patch dump received with Get, sent back with Send. */
    private byte[] lastPatchDump;

    // Current items (mock), as "bank:number name"
    private String performanceBank = "RAM1";
    private int performanceNumber = 0;
    private String performanceName = "Ski Jam";
    private String patchBank = "RAM1";
    private int patchNumber = 2;
    private String patchName = "Glass Choir";
    private int currentPart = 1;

    public MainWindow() {
        super("Harmonia — KORG Wavestation SR");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        KnobFactory knobs = new KnobFactory(this::onKnobFocus, this::onKnobChange);
        patchEditor = new PatchEditorPanel(knobs, this::onEditorParameter);
        performanceEditor = new PerformanceEditorPanel(this::partPatchName, this::openPart);
        performanceEditor.setPartListener(new PerformanceEditorPanel.PartListener() {
            @Override
            public void partChanged(Part part, PartField field, int value) {
                onPartChanged(part, field, value);
            }

            @Override
            public void partSelected(Part part) {
                currentPart = part.getNumber();
                updateBreadcrumb();
            }
        });
        librarian.setNameSource(this::namesFromLibrary);
        library.addListener(bank -> SwingUtilities.invokeLater(() -> {
            librarian.refresh();
            performanceEditor.refresh();
        }));
        registerEditors();

        setContentPane(buildContent());
        loadStore();
        installShortcuts();
        wireKeyboard();

        monitor.setOnActivity(() -> frontPanel.getMidiLed().flash());
        monitor.log(Direction.INFO, "Sons affichés : données factices (phase 3)");
        session.addListener(new DeviceSession.Listener() {
            @Override
            public void connectionChanged(boolean connected, String description) {
                SwingUtilities.invokeLater(() -> onConnectionChanged(connected, description));
            }

            /*
             * Called on MIDI threads, possibly hundreds of times per second when playing fast:
             * the monitor batches the lines itself, and the Swing thread is only used for the
             * few messages that change the display (performance changes, keys).
             */
            @Override
            public void message(MidiConnection.Direction direction, byte[] message) {
                logMidi(direction, message);
                if (direction == MidiConnection.Direction.IN && isPerformanceChange(message)) {
                    device().ifPresent(WavestationDevice::forgetEditContext);
                    SwingUtilities.invokeLater(() -> followDevice(message));
                } else if (direction == MidiConnection.Direction.IN && changesEditContext(message)) {
                    // part or wave chosen on the device front panel: select ours again before editing
                    device().ifPresent(WavestationDevice::forgetEditContext);
                }
            }

            @Override
            public void keyboard(byte[] message) {
                // Light the on-screen keys played on the master keyboard (notes only)
                if (PianoKeyboard.affectsKeys(message)) {
                    SwingUtilities.invokeLater(() -> keyboardPanel.getKeyboard().showMidi(message));
                }
            }
        });
        if (!session.connectWithSavedSettings()) {
            onConnectionChanged(false, null);
        }
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (sync != null) {
                    sync.cancel();
                }
                saveStore();
                session.close();
            }
        });

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
        editors.put(EditorSection.PERFORMANCE, performanceEditor);
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
                loadPerformance();
            }
            case PATCH -> {
                patchBank = entry.bank();
                patchNumber = entry.number();
                patchName = entry.name();
                loadPatch();
            }
            case WAVESEQ -> {
                // wave sequence editor: phase 8
            }
        }
        modified = false;
        frontPanel.setLed(FrontPanel.Button.WRITE_COMPARE, false);
        updateBreadcrumb();
        updateLcd(null);
    }

    // ------------------------------------------------------------------ sounds read from the device

    private Optional<List<String>> namesFromLibrary(String bankLabel, LibrarianPanel.Kind kind) {
        Optional<WavestationBank> bank = WavestationBank.fromLabel(bankLabel);
        if (bank.isEmpty()) {
            return Optional.empty();
        }
        return switch (kind) {
            case PERF -> library.performanceNames(bank.get());
            case PATCH -> library.patchNames(bank.get());
            case WAVESEQ -> library.waveSequenceNames(bank.get());
        };
    }

    // ------------------------------------------------------------------ copy of the device memory

    /**
     * Startup: the banks saved at the end of the previous session are loaded, so that every sound
     * is known at once, without asking the device.
     */
    private void loadStore() {
        int banks = store.load(library).size();
        if (banks > 0) {
            monitor.log(Direction.INFO, banks + " banques chargées depuis la copie locale du SR (" + store.folder() + ")");
        }
    }

    /** End of session: everything known is saved (banks read entirely are already saved). */
    private void saveStore() {
        for (WavestationBank bank : WavestationBank.values()) {
            try {
                store.save(library, bank);
            } catch (RuntimeException e) {
                // the bank will be read again from the device next time
            }
        }
    }

    /** "Relire": reads a bank again from the device, in the background. */
    private void readBank(String bankLabel) {
        startSync(List.of(WavestationBank.fromLabel(bankLabel).orElseThrow()), "Relecture de " + bankLabel);
    }

    /** "Tout relire": the device may have been changed without Harmonia; every bank is read again. */
    private void readAllBanks() {
        if (device().isEmpty()) {
            monitor.log(Direction.INFO, "Connectez le SR (bouton MIDI…) pour relire ses banques");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this, "Relire toutes les banques du SR (environ "
                + LibrarySync.estimatedSeconds(LibrarySync.ORDER.size()) / 60 + " minutes, en arrière-plan) ?\n"
                + "Utile si le SR a été modifié sans Harmonia. Vous pouvez continuer à travailler pendant ce temps.",
                "Tout relire", JOptionPane.OK_CANCEL_OPTION);
        if (answer == JOptionPane.OK_OPTION) {
            store.invalidate();
            startSync(LibrarySync.ORDER, "Relecture de toutes les banques");
        }
    }

    /** At connection: reads in the background the banks that are not known yet (first use). */
    private void syncMissingBanks() {
        List<WavestationBank> missing = LibrarySync.missing(library, store);
        if (!missing.isEmpty()) {
            startSync(missing, "Première lecture de la mémoire du SR : " + missing.size() + " banques, environ "
                    + Math.max(1, LibrarySync.estimatedSeconds(missing.size()) / 60) + " min");
        }
    }

    /** Reads banks in the background, with a discreet progress text in the status bar. */
    private void startSync(List<WavestationBank> banks, String title) {
        if (device().isEmpty()) {
            monitor.log(Direction.INFO, "Connectez le SR (bouton MIDI…) pour lire ses banques");
            return;
        }
        if (sync != null) {
            monitor.log(Direction.INFO, "Une lecture est déjà en cours");
            return;
        }
        monitor.log(Direction.INFO, title);
        librarian.setReadEnabled(false);
        LibrarySync job = new LibrarySync(device().get(), library, store);
        sync = job;
        statusBar.startActivity("sync", "Lecture du SR");
        job.run(banks, new LibrarySync.Listener() {
            @Override
            public void reading(WavestationBank bank, LibrarySync.Step step, int done, int total) {
                SwingUtilities.invokeLater(() -> statusBar.startActivity("sync", "Lecture du SR : " + bank.label()
                        + ", " + step.label() + " (" + (done + 1) + "/" + total + ")"));
            }

            @Override
            public void bankRead(WavestationBank bank) {
                SwingUtilities.invokeLater(() -> monitor.log(Direction.INFO, "Banque " + bank.label() + " lue et enregistrée"));
            }
        }).thenAccept(result -> SwingUtilities.invokeLater(() -> {
            sync = null;
            statusBar.endActivity("sync");
            librarian.setReadEnabled(true);
            StringBuilder sb = new StringBuilder(result.cancelled() ? "Lecture interrompue" : "Lecture terminée");
            sb.append(" : ").append(result.read().size()).append(" banque(s) lue(s)");
            if (!result.absent().isEmpty()) {
                sb.append(", pas de carte");
            }
            if (!result.failed().isEmpty()) {
                sb.append(", échec pour ").append(result.failed().stream().map(WavestationBank::label).toList());
            }
            monitor.log(Direction.INFO, sb.toString());
        }));
    }

    /**
     * The performance editor changes the performance it shows: it works on a copy, so that the
     * library stays what the device memory holds until the performance is written.
     */
    private static Performance editableCopy(Performance perf) {
        return new Performance(perf.toBytes());
    }

    /** After a Write: the written location is read back, so that the copy stays aligned. */
    private void readBack(WavestationBank bank, int number, boolean patch) {
        if (device().isEmpty()) {
            return;
        }
        if (patch) {
            device().get().requestPatch(bank, number).thenAccept(d -> {
                library.putPatch(bank, number, Dumps.decodePatch(d));
                store.save(library, bank);
            });
        } else {
            device().get().requestPerformance(bank, number).thenAccept(d -> {
                library.putPerformance(bank, number, Dumps.decodePerformance(d));
                store.save(library, bank);
            });
        }
    }

    /** Last Bank Select (controller 32) received from the device, for {@link #followDevice}. */
    private int deviceMidiBank;
    /** True while a performance chosen on the device is shown: it must not be selected again. */
    private boolean followingDevice;

    /**
     * Follows the performance changes made on the device front panel: the SR sends Bank Select
     * then Program Change (Player's Guide, 4.8); Harmonia shows the same performance.
     */
    private void followDevice(byte[] m) {
        int ch = channel();
        if (m.length == 3 && (m[0] & 0xFF) == (0xB0 | ch) && m[1] == 0x20) {
            deviceMidiBank = m[2];
        } else if (m.length == 2 && (m[0] & 0xFF) == (0xC0 | ch)) {
            int program = m[1] & 0x7F;
            WavestationBank.fromMidi(deviceMidiBank, program).ifPresent(bank -> {
                followingDevice = true;
                try {
                    librarian.show(LibrarianPanel.Kind.PERF, bank.label(), program % WavestationBank.PERFORMANCES);
                } finally {
                    followingDevice = false;
                }
            });
        }
    }

    /**
     * Shows the selected performance (from the library, or read from the device) and makes the
     * device play it (Bank Select + Program Change).
     */
    private void loadPerformance() {
        WavestationBank bank = WavestationBank.fromLabel(performanceBank).orElseThrow();
        if (device().isPresent() && !followingDevice) {
            device().get().selectPerformance(bank, performanceNumber);
        }
        String reference = itemId(performanceBank, performanceNumber);
        Optional<Performance> known = library.performance(bank, performanceNumber);
        if (known.isPresent()) {
            performanceEditor.showPerformance(reference, editableCopy(known.get()));
        } else if (device().isPresent()) {
            String activity = "read:" + reference;
            statusBar.startActivity(activity, "Lecture de la performance " + reference);
            library.loadPerformance(device().get(), bank, performanceNumber)
                    .whenComplete((perf, error) -> SwingUtilities.invokeLater(() -> {
                        statusBar.endActivity(activity);
                        if (error != null) {
                            reportError("Lecture de la performance " + reference, error);
                        } else if (reference.equals(itemId(performanceBank, performanceNumber))) {
                            performanceName = perf.getName();
                            performanceEditor.showPerformance(reference, editableCopy(perf));
                            updateBreadcrumb();
                            updateLcd(null);
                        }
                    }));
        } else {
            performanceEditor.showPerformance(reference, null);
        }
    }

    /** Shows the selected patch in the patch editor: from the library, or read from the device. */
    private void loadPatch() {
        WavestationBank bank = WavestationBank.fromLabel(patchBank).orElseThrow();
        String reference = itemId(patchBank, patchNumber);
        Optional<Patch> known = library.patch(bank, patchNumber);
        if (known.isPresent()) {
            displayPatch(known.get());
        } else if (device().isPresent()) {
            String activity = "read:" + reference;
            statusBar.startActivity(activity, "Lecture du patch " + reference);
            library.loadPatch(device().get(), bank, patchNumber)
                    .whenComplete((patch, error) -> SwingUtilities.invokeLater(() -> {
                        statusBar.endActivity(activity);
                        if (error != null) {
                            reportError("Lecture du patch " + reference, error);
                        } else if (reference.equals(itemId(patchBank, patchNumber))) {
                            displayPatch(patch);
                        }
                    }));
        }
    }

    private void displayPatch(Patch patch) {
        loading = true;
        try {
            patchEditor.showPatch(patch);
        } finally {
            loading = false;
        }
        patchName = patch.getName();
        modified = false;
        frontPanel.setLed(FrontPanel.Button.WRITE_COMPARE, false);
        if (lastKnob != null) {
            inspector.show(lastKnob);
        }
        updateBreadcrumb();
        updateLcd(null);
    }

    private String partPatchName(Part part) {
        return part.isUsed() ? library.patch(part.getBank(), part.getPatchNumber()).map(Patch::getName).orElse(null)
                : null;
    }

    /** Double-click on a part: open its patch, like EDIT on the EDIT PERF page. */
    private void openPart(Part part) {
        patchBank = part.getBank().label();
        patchNumber = part.getPatchNumber();
        String name = partPatchName(part);
        patchName = name != null ? name : "?";
        currentPart = part.getNumber();
        showSection(EditorSection.PATCH);
        loadPatch();
    }

    private void onAction(NavigationBar.Action action) {
        switch (action) {
            case CONNECT -> new ConnectionDialog(this, session).setVisible(true);
            case GET -> getPatch();
            case SEND -> sendPatch();
            case COMPARE -> monitor.log(Direction.INFO, "Compare : bascule version éditée / original (phase 6)");
            case WRITE -> writePatch();
        }
    }

    // ------------------------------------------------------------------ device

    private void onConnectionChanged(boolean connected, String description) {
        statusBar.setConnection(connected, connected ? description
                : "Mode démonstration · aucun appareil connecté (bouton MIDI pour se connecter)");
        monitor.log(Direction.INFO, connected ? "Connecté" : "Non connecté : messages OUT simulés");
        keyboardPanel.getKeyboard().releaseAll();
        if (connected) {
            syncMissingBanks();
        } else if (sync != null) {
            sync.cancel();
        }
    }

    private Optional<WavestationDevice> device() {
        return session.device();
    }

    private WavestationBank patchBank() {
        return WavestationBank.fromLabel(patchBank).orElse(WavestationBank.RAM1);
    }

    /** Get: reads the current patch from the device again (shown in the editor, dump kept for Send). */
    private void getPatch() {
        WavestationBank bank = patchBank();
        if (device().isEmpty()) {
            simulated(WavestationSysex.patchRequest(channel(), bank, patchNumber));
            return;
        }
        device().get().requestPatch(bank, patchNumber).whenComplete((dump, error) -> SwingUtilities.invokeLater(() -> {
            if (error != null) {
                reportError("Get", error);
            } else {
                lastPatchDump = dump;
                monitor.log(Direction.INFO, "Reçu : " + WavestationSysex.describe(dump));
                Patch patch = Dumps.decodePatch(dump);
                library.putPatch(bank, patchNumber, patch);
                displayPatch(patch);
            }
        }));
    }

    /** Send: sends the last received patch back to the device edit buffer. */
    private void sendPatch() {
        if (lastPatchDump == null) {
            monitor.log(Direction.INFO, "Rien à envoyer : faites d'abord Get");
            return;
        }
        if (device().isEmpty()) {
            simulated(lastPatchDump);
            return;
        }
        device().get().sendDump(lastPatchDump).whenComplete((ok, error) -> SwingUtilities.invokeLater(() -> {
            if (error != null) {
                reportError("Send", error);
            } else {
                monitor.log(Direction.INFO, "Patch chargé dans le tampon d'édition (Data Load Complete)");
            }
        }));
    }

    /** Write: stores the device edit buffer in the current memory location, after confirmation. */
    private void writePatch() {
        WavestationBank bank = patchBank();
        String target = itemId(patchBank, patchNumber);
        if (device().isEmpty()) {
            simulated(WavestationSysex.patchWrite(channel(), bank, patchNumber));
            return;
        }
        if (!bank.isWritable()) {
            monitor.log(Direction.INFO, bank.label() + " est une banque ROM : choisissez RAM1-3 ou la carte");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Écrire le tampon d'édition de l'appareil dans " + target + " ?\nLe patch actuellement en "
                        + target + " sera remplacé.",
                "Write", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        device().get().writePatch(bank, patchNumber).whenComplete((ok, error) -> SwingUtilities.invokeLater(() -> {
            if (error != null) {
                reportError("Write", error);
            } else {
                monitor.log(Direction.INFO, "Écrit dans " + target + " (Write Complete)");
                readBack(bank, patchNumber, true);
                modified = false;
                frontPanel.setLed(FrontPanel.Button.WRITE_COMPARE, false);
                updateStatus();
            }
        }));
    }

    private void reportError(String action, Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        String text = cause instanceof java.util.concurrent.TimeoutException ? "pas de réponse de l'appareil"
                : cause.getMessage();
        monitor.log(Direction.INFO, action + " : échec, " + text);
    }

    private int channel() {
        return session.getSettings().channel();
    }

    /**
     * Sends a parameter change, or shows it as simulated when no device is connected. Patch
     * parameters apply to the current part (the one whose patch is open): the device selects it
     * first (CURRENT_PART).
     */
    private void sendParameter(MockParameter p, String value) {
        if (!navigationBar.isLiveEdit()) {
            return;
        }
        Parameter parameter = Parameter.of(p.number());
        if (device().isPresent()) {
            // logged by the session listener
            if (parameter.scope() == Parameter.Scope.PART) {
                device().get().setPartParameter(currentPart, parameter, value);
            } else {
                device().get().setParameter(p.number(), value);
            }
        } else {
            byte[] m = WavestationSysex.parameterChange(channel(), p.number(), value);
            monitor.logCoalesced("param:" + p.number(), Direction.OUT,
                    MidiBytes.toHex(m) + "  " + p.officialName() + scopeText(parameter) + " (simulé)");
            frontPanel.getMidiLed().flash();
        }
    }

    private String scopeText(Parameter parameter) {
        return parameter.scope() == Parameter.Scope.PART ? " · part " + currentPart : "";
    }

    /** A part setting changed in the performance editor: sent to the device in real time. */
    private void onPartChanged(Part part, PartField field, int value) {
        currentPart = part.getNumber();
        modified = true;
        frontPanel.setLed(FrontPanel.Button.WRITE_COMPARE, true);
        updateStatus();
        frontPanel.getLcd().setLine2(("P" + part.getNumber() + " " + field.label() + ":" + field.format(value)));
        if (!navigationBar.isLiveEdit()) {
            return;
        }
        if (device().isPresent()) {
            device().get().setPartParameter(part.getNumber(), field.parameter(), value);
        } else {
            byte[] m = WavestationSysex.parameterChange(channel(), field.parameter().number(),
                    WavestationSysex.signed(value));
            monitor.logCoalesced("param:" + field.parameter().number() + "@" + part.getNumber(), Direction.OUT,
                    MidiBytes.toHex(m) + "  " + field.parameter().officialName() + " · part " + part.getNumber()
                            + " (simulé)");
            frontPanel.getMidiLed().flash();
        }
    }

    /** Parameter change received for CURRENT_PART or CURRENT_WAVE (front panel of the device). */
    private static boolean changesEditContext(byte[] m) {
        if (m.length < 8 || (m[0] & 0xFF) != 0xF0 || m[1] != 0x42 || m[3] != 0x28 || m[4] != 0x41) {
            return false;
        }
        int number = m[5] | m[6] << 7;
        return number == Parameter.CURRENT_PART.number() || number == Parameter.CURRENT_WAVE.number();
    }

    private void simulated(byte[] message) {
        monitor.log(Direction.OUT, MidiBytes.toHex(message, 12) + "  " + WavestationSysex.describe(message)
                + " (simulé)");
        frontPanel.getMidiLed().flash();
    }

    /** Bank Select LSB or Program Change: what the device sends when its performance changes. */
    private static boolean isPerformanceChange(byte[] m) {
        return (m.length == 2 && (m[0] & 0xF0) == 0xC0) || (m.length == 3 && (m[0] & 0xF0) == 0xB0 && m[1] == 0x20);
    }

    /**
     * Shows a message of the real MIDI traffic in the monitor. Any thread. Streams of the same
     * kind (a parameter being turned, aftertouch, pitch bend, a controller) update a single line.
     */
    private void logMidi(MidiConnection.Direction direction, byte[] message) {
        Direction d = direction == MidiConnection.Direction.IN ? Direction.IN : Direction.OUT;
        String text = MidiBytes.toHex(message, 12);
        String detail = "";
        String key = null;
        int status = message[0] & 0xFF;
        int type = status & 0xF0;
        Optional<MessageType> sysex = WavestationSysex.type(message);
        if (sysex.isPresent()) {
            detail = WavestationSysex.describe(message);
            MessageType t = sysex.get();
            if (t == MessageType.PARAMETER_CHANGE || t == MessageType.PARAMETER_CHANGE_EXPANDED
                    || t == MessageType.PARAMETER_CHANGE_SR) {
                key = d + ":param:" + ((message[5] & 0x7F) | ((message[6] & 0x7F) << 7));
            }
        } else if (message.length == 2 && type == 0xC0) {
            detail = "Program change " + (message[1] & 0x7F);
        } else if (message.length == 3 && type == 0xB0 && (message[1] == 0x00 || message[1] == 0x20)) {
            detail = "Bank select " + (message[1] == 0 ? "MSB " : "LSB ") + message[2];
        } else if (message.length == 3 && (type == 0x80 || type == 0x90)) {
            boolean on = type == 0x90 && message[2] != 0;
            detail = "Note " + (on ? "on " : "off ") + PianoKeyboard.noteName(message[1] & 0x7F);
        } else if (message.length == 3 && type == 0xB0) {
            detail = "Contrôleur " + message[1] + " = " + message[2];
            key = d + ":cc:" + status + ":" + message[1];
        } else if (type == 0xD0 || type == 0xA0) {
            detail = "Aftertouch";
            key = d + ":at:" + status;
        } else if (type == 0xE0) {
            detail = "Pitch bend";
            key = d + ":pb:" + status;
        }
        monitor.post(d, text, detail, key);
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
        if (loading) {
            return;
        }
        if (section == EditorSection.PATCH && !p.page().equals(currentPage.get(section))) {
            showPage(p.page());
        }
        frontPanel.getLcd().setLine2(lcdText);
        if (p != MockParameter.MIX_POINT) {
            modified = true;
            frontPanel.setLed(FrontPanel.Button.WRITE_COMPARE, true);
            updateStatus();
        }
        sendParameter(p, value);
    }

    private void onKnobChange(Knob knob) {
        if (loading) {
            return;
        }
        if (knob != lastKnob) {
            onKnobFocus(knob);
        }
        inspector.valueChanged(knob);
        updateLcd(knob);
        modified = true;
        frontPanel.setLed(FrontPanel.Button.WRITE_COMPARE, true);
        updateStatus();

        MockParameter p = (MockParameter) knob.getClientProperty(MockParameter.class);
        if (p != null) {
            sendParameter(p, knob.getValueText());
        }
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
            case PATCH -> pageBar.setBreadcrumb(perf, "Part " + currentPart, patch, "Wave " + pageBar.getWave());
            case WAVE_SEQ -> pageBar.setBreadcrumb(perf, "Part " + currentPart, patch, "Wave " + pageBar.getWave(), "WSEQ");
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
        String b = WavestationBank.fromLabel(bank).map(WavestationBank::shortLabel).orElse(bank);
        return b + ":" + String.format("%02d", number);
    }


    // ------------------------------------------------------------------ keyboard

    /** On-screen keyboard: notes go to the device, or are shown as simulated. */
    private void wireKeyboard() {
        keyboardPanel.getKeyboard().addNoteListener(new PianoKeyboard.NoteListener() {
            @Override
            public void noteOn(int note, int velocity) {
                play(new byte[] { (byte) (0x90 | channel()), (byte) note, (byte) velocity });
            }

            @Override
            public void noteOff(int note) {
                play(new byte[] { (byte) (0x80 | channel()), (byte) note, 0 });
            }
        });
    }

    private void play(byte[] message) {
        Optional<MidiConnection> connection = session.connection();
        if (connection.isPresent()) {
            connection.get().getQueue().sendNow(message); // logged by the session listener
        } else {
            boolean on = (message[0] & 0xF0) == 0x90;
            monitor.log(Direction.OUT, MidiBytes.toHex(message) + "  Note " + (on ? "on " : "off ")
                    + PianoKeyboard.noteName(message[1]) + " (simulé)");
            frontPanel.getMidiLed().flash();
        }
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
