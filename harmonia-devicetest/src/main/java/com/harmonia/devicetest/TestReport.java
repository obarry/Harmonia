package com.harmonia.devicetest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Report of a test session, written as Markdown ({@code report.md}) in the session folder, next
 * to the dumps and photos collected. It is rewritten after each test, so a session stopped in
 * the middle still leaves a report.
 */
public class TestReport {

    /** Result of a test. */
    public enum Status {
        PASSED("✅ Réussi"), FAILED("❌ Échec"), INCONCLUSIVE("❔ Non concluant"),
        SKIPPED("⏭ Passé"), ERROR("⚠ Erreur");

        final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Record of one test: automatic checks, answers of the tester, notes, files. */
    public static final class Entry {
        final String id;
        final String title;
        final String purpose;
        final List<String> lines = new ArrayList<>();
        final List<String> files = new ArrayList<>();
        boolean failed;
        boolean unsure;
        Status forced;

        Entry(String id, String title, String purpose) {
            this.id = id;
            this.title = title;
            this.purpose = purpose;
        }

        private int failures;

        public void check(String description, boolean ok) {
            lines.add((ok ? "\u2705 " : "\u274C ") + description);
            failed |= !ok;
            if (!ok) {
                failures++;
            }
        }

        /** Number of failed automatic checks so far. */
        public int failures() {
            return failures;
        }

        public void answer(String question, Console.Answer answer) {
            String a = switch (answer) {
                case YES -> "oui";
                case NO -> "**non**";
                case DONT_KNOW -> "je ne sais pas";
            };
            lines.add("👤 " + question + " → " + a);
            failed |= answer == Console.Answer.NO;
            unsure |= answer == Console.Answer.DONT_KNOW;
        }

        public void note(String text) {
            lines.add("📝 " + text);
        }

        /** Preformatted block (tables, dumps...). */
        public void block(String text) {
            lines.add("```\n" + text.stripTrailing() + "\n```");
        }

        public void file(String name, String description) {
            files.add("[" + name + "](" + name + ") — " + description);
        }

        public void force(Status status) {
            forced = status;
        }

        public Status status() {
            if (forced != null) {
                return forced;
            }
            return failed ? Status.FAILED : unsure ? Status.INCONCLUSIVE : Status.PASSED;
        }
    }

    private final Path folder;
    private final LocalDateTime start = LocalDateTime.now();
    private final Map<String, String> context = new LinkedHashMap<>();
    private final List<Entry> entries = new ArrayList<>();

    public TestReport(Path folder) {
        this.folder = folder;
    }

    public Path folder() {
        return folder;
    }

    /** Test environment (ports, channel, device version...), shown at the top of the report. */
    public void context(String key, String value) {
        context.put(key, value);
    }

    public Entry begin(String id, String title, String purpose) {
        Entry e = new Entry(id, title, purpose);
        entries.add(e);
        return e;
    }

    public List<Entry> entries() {
        return entries;
    }

    public Path file() {
        return folder.resolve("report.md");
    }

    /** Writes (or rewrites) report.md. */
    public void write() {
        StringBuilder md = new StringBuilder();
        md.append("# Harmonia — test avec le Wavestation SR\n\n");
        md.append("Session du ").append(start.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).append("\n\n");
        md.append("| Élément | Valeur |\n|---------|--------|\n");
        context.forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v).append(" |\n"));
        md.append("\n## Résumé\n\n| Test | Titre | Résultat |\n|------|-------|----------|\n");
        for (Entry e : entries) {
            md.append("| ").append(e.id).append(" | ").append(e.title).append(" | ").append(e.status().label())
                    .append(" |\n");
        }
        for (Entry e : entries) {
            md.append("\n## ").append(e.id).append(" — ").append(e.title).append("\n\n");
            md.append("*").append(e.purpose).append("*\n\n");
            md.append("**Résultat : ").append(e.status().label()).append("**\n\n");
            for (String line : e.lines) {
                md.append(line.startsWith("```") ? line + "\n\n" : "- " + line + "\n");
            }
            if (!e.files.isEmpty()) {
                md.append("\nFichiers :\n\n");
                e.files.forEach(f -> md.append("- ").append(f).append('\n'));
            }
        }
        try {
            Files.createDirectories(folder);
            Files.writeString(file(), md, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
