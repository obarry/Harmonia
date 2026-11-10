package com.harmonia.devicetest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.harmonia.app.ConnectionSettings;
import com.harmonia.app.DeviceSession;
import com.harmonia.app.SoundLibrary;
import com.harmonia.devicetest.scenarios.T01Identification;
import com.harmonia.devicetest.scenarios.T02SelectPerformance;
import com.harmonia.devicetest.scenarios.T03PerformanceParts;
import com.harmonia.devicetest.scenarios.T04PatchValues;
import com.harmonia.devicetest.scenarios.T05BankRoundTrip;
import com.harmonia.devicetest.scenarios.T06RealTimeParameter;
import com.harmonia.devicetest.scenarios.T07SendDump;
import com.harmonia.devicetest.scenarios.T08WritePatch;
import com.harmonia.devicetest.scenarios.T09FollowDevice;
import com.harmonia.devicetest.scenarios.T10KeyboardThru;
import com.harmonia.devicetest.scenarios.T11PartParameters;
import com.harmonia.midi.MidiConnection;
import com.harmonia.midi.MidiPorts;

/**
 * Interactive tests with a real Wavestation SR, in a console.
 * <p>
 * Harmonia sends and reads; the tester follows the instructions on the device and answers the
 * questions. Everything is recorded in a Markdown report, with the dumps received and the photos
 * of the device screen, in {@code device-tests/<date>/}.
 * <p>
 * Usage: {@code DeviceTestRunner [--list] [--only T02,T04] [--out folder]}
 */
public final class DeviceTestRunner {

    /** All tests, in the order they are run. */
    public static List<DeviceTest> allTests() {
        return List.of(new T01Identification(), new T02SelectPerformance(), new T03PerformanceParts(),
                new T04PatchValues(), new T05BankRoundTrip(), new T06RealTimeParameter(), new T07SendDump(),
                new T08WritePatch(), new T09FollowDevice(), new T10KeyboardThru(), new T11PartParameters());
    }

    private final Console console;
    private final DeviceSession session = new DeviceSession();
    private final SoundLibrary library = new SoundLibrary();

    DeviceTestRunner(Console console) {
        this.console = console;
    }

    public static void main(String[] args) {
        PrintStream out = System.out;
        Console console = new Console(new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset())),
                out);
        List<String> arguments = Arrays.asList(args);
        if (arguments.contains("--list")) {
            allTests().forEach(t -> out.println(t.id() + "  " + t.title() + "\n      " + t.purpose()));
            return;
        }
        Set<String> only = option(arguments, "--only").map(v -> Arrays.stream(v.split(","))
                .map(String::trim).map(String::toUpperCase).collect(Collectors.toSet())).orElse(Set.of());
        Path folder = option(arguments, "--out").map(Path::of).orElse(Path.of("device-tests",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))));
        int failures = new DeviceTestRunner(console).run(folder, only);
        System.exit(failures == 0 ? 0 : 1);
    }

    private static java.util.Optional<String> option(List<String> args, String name) {
        int i = args.indexOf(name);
        return i >= 0 && i + 1 < args.size() ? java.util.Optional.of(args.get(i + 1)) : java.util.Optional.empty();
    }

    /** Runs the tests (all, or only the given ids); returns the number of failed tests. */
    int run(Path folder, Set<String> only) {
        TestReport report = new TestReport(folder);
        TestContext ctx = new TestContext(console, session, library, report);
        console.title("Harmonia — tests avec le Wavestation SR");
        console.info("Répondez aux questions par o (oui), n (non) ou ? (je ne sais pas). q quitte à tout moment.");
        console.info("Rapport : " + report.file().toAbsolutePath());
        try {
            if (!connect(report)) {
                report.write();
                return 1;
            }
            session.addListener(new DeviceSession.Listener() {
                @Override
                public void connectionChanged(boolean connected, String description) {
                }

                @Override
                public void message(MidiConnection.Direction direction, byte[] message) {
                    if (direction == MidiConnection.Direction.IN) {
                        ctx.received(message);
                    }
                }

                @Override
                public void keyboard(byte[] message) {
                    ctx.keyboard(message);
                }
            });
            String tester = console.text("Votre nom (pour le rapport, facultatif) :");
            if (!tester.isEmpty()) {
                report.context("Testeur", tester);
            }
            report.write();

            for (DeviceTest test : allTests()) {
                if (!only.isEmpty() && !only.contains(test.id())) {
                    continue;
                }
                console.title(test.id() + " — " + test.title());
                console.info(test.purpose());
                TestReport.Entry entry = report.begin(test.id(), test.title(), test.purpose());
                ctx.setEntry(entry);
                if (!console.runOrSkip("Lancer ce test ?")) {
                    entry.force(TestReport.Status.SKIPPED);
                } else {
                    runOne(test, ctx, entry);
                }
                console.info("  Résultat : " + entry.status().label());
                report.write();
            }
        } catch (Console.QuitException e) {
            console.info("\nSession interrompue.");
        } finally {
            session.close();
            report.write();
        }
        summary(report);
        return (int) report.entries().stream().filter(e -> e.status() == TestReport.Status.FAILED
                || e.status() == TestReport.Status.ERROR).count();
    }

    private void runOne(DeviceTest test, TestContext ctx, TestReport.Entry entry) {
        try {
            test.run(ctx);
            String comment = console.text("Commentaire pour le rapport (Entrée pour aucun) :");
            if (!comment.isEmpty()) {
                entry.note(comment);
            }
        } catch (Console.QuitException e) {
            throw e;
        } catch (Exception e) {
            entry.note("Exception : " + e);
            entry.force(TestReport.Status.ERROR);
            console.info("  ⚠ " + e);
        }
    }

    /** Opens the connection: saved settings, or ports chosen by the tester. */
    private boolean connect(TestReport report) {
        ConnectionSettings saved = session.getSettings();
        ConnectionSettings settings = saved;
        if (saved.isComplete() && console.yesNo("Utiliser la connexion enregistrée (IN " + saved.input() + ", OUT "
                + saved.output() + ", canal " + (saved.channel() + 1) + ") ?") == Console.Answer.YES) {
            settings = saved;
        } else {
            List<String> inputs = MidiPorts.inputs().stream().map(MidiPorts.Port::name).toList();
            List<String> outputs = MidiPorts.outputs().stream().map(MidiPorts.Port::name).toList();
            if (inputs.isEmpty() || outputs.isEmpty()) {
                console.info("Aucun port MIDI trouvé : branchez l'interface MIDI et relancez.");
                return false;
            }
            String in = inputs.get(console.choose("Entrée MIDI (reliée au MIDI OUT du SR) :", inputs, 0));
            String out = outputs.get(console.choose("Sortie MIDI (reliée au MIDI IN du SR) :", outputs, 0));
            List<String> keyboards = new ArrayList<>(List.of("(aucun)"));
            keyboards.addAll(inputs);
            int k = console.choose("Clavier maître :", keyboards, 0);
            int channel = Integer.parseInt(console.text("Canal de base du SR (1-16)", "1")) - 1;
            settings = new ConnectionSettings(in, out, k == 0 ? "" : keyboards.get(k), channel);
        }
        try {
            session.connect(settings);
        } catch (Exception e) {
            console.info("Connexion impossible : " + e.getMessage());
            return false;
        }
        report.context("Entrée MIDI", settings.input());
        report.context("Sortie MIDI", settings.output());
        report.context("Clavier maître", settings.hasKeyboard() ? settings.keyboard() : "aucun");
        report.context("Canal du SR", Integer.toString(settings.channel() + 1));
        report.context("Java", System.getProperty("java.version") + " · " + System.getProperty("os.name"));
        console.info("Connecté.");
        return true;
    }

    private void summary(TestReport report) {
        console.title("Résumé");
        for (TestReport.Entry e : report.entries()) {
            console.info(String.format("  %-4s %-60s %s", e.id, e.title, e.status().label()));
        }
        console.info("\nRapport : " + report.file().toAbsolutePath());
        console.info("Ajoutez les photos éventuelles dans ce dossier, puis envoyez-moi le rapport.");
    }
}
