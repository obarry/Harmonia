package com.harmonia.devicetest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;

/**
 * Text dialogue with the tester: instructions, questions, choices.
 * <p>
 * Reads from any reader so that the dialogue can be scripted in unit tests.
 */
public class Console {

    /** Answer to a yes / no question. */
    public enum Answer {
        YES, NO, DONT_KNOW
    }

    /** Thrown when the tester asks to quit (q), or when the input ends. */
    public static class QuitException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private final BufferedReader in;
    private final PrintStream out;

    public Console(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    public PrintStream out() {
        return out;
    }

    public void title(String text) {
        out.println();
        out.println("=".repeat(Math.max(20, text.length())));
        out.println(text);
        out.println("=".repeat(Math.max(20, text.length())));
    }

    public void info(String text) {
        out.println(text);
    }

    /** An instruction for the tester, to be done on the device. */
    public void step(String instruction) {
        out.println("  → " + instruction);
    }

    /** Waits for Enter. */
    public void waitEnter(String prompt) {
        out.print("  [" + prompt + " : Entrée] ");
        read();
    }

    /** Yes / no question: o (oui), n (non), ? (je ne sais pas); q quits. */
    public Answer yesNo(String question) {
        while (true) {
            out.print("  ? " + question + " [o/n/?] ");
            String a = read().toLowerCase(Locale.ROOT);
            switch (a) {
                case "o", "oui", "y", "yes" -> {
                    return Answer.YES;
                }
                case "n", "non", "no" -> {
                    return Answer.NO;
                }
                case "?", "" -> {
                    return Answer.DONT_KNOW;
                }
                default -> out.println("    Répondez o (oui), n (non) ou ? (je ne sais pas).");
            }
        }
    }

    /** Free text (may be empty). */
    public String text(String question) {
        out.print("  ? " + question + " ");
        return read();
    }

    /** Text with a default value used when the answer is empty. */
    public String text(String question, String defaultValue) {
        out.print("  ? " + question + " [" + defaultValue + "] ");
        String a = read();
        return a.isEmpty() ? defaultValue : a;
    }

    /** Choice in a numbered list; returns the index. Enter selects the default. */
    public int choose(String question, List<String> options, int defaultIndex) {
        out.println("  ? " + question);
        for (int i = 0; i < options.size(); i++) {
            out.println("     " + (i + 1) + ". " + options.get(i) + (i == defaultIndex ? "  (défaut)" : ""));
        }
        while (true) {
            out.print("    Choix [" + (defaultIndex + 1) + "] ");
            String a = read();
            if (a.isEmpty() && defaultIndex >= 0) {
                return defaultIndex;
            }
            try {
                int n = Integer.parseInt(a);
                if (n >= 1 && n <= options.size()) {
                    return n - 1;
                }
            } catch (NumberFormatException e) {
                // asked again below
            }
            out.println("    Entrez un numéro entre 1 et " + options.size() + ".");
        }
    }

    /** Before a test: run it (o), skip it (s) or quit (q). */
    public boolean runOrSkip(String question) {
        while (true) {
            out.print("  ? " + question + " [o = lancer / s = passer / q = quitter] ");
            String a = read().toLowerCase(Locale.ROOT);
            if (a.isEmpty() || a.equals("o") || a.equals("oui")) {
                return true;
            }
            if (a.equals("s")) {
                return false;
            }
            out.println("    Répondez o, s ou q.");
        }
    }

    /** Reads a line; "q" or the end of input quits. */
    private String read() {
        try {
            String line = in.readLine();
            if (line == null) {
                throw new QuitException();
            }
            line = line.trim();
            if (line.equalsIgnoreCase("q")) {
                throw new QuitException();
            }
            return line;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
