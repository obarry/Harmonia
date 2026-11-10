package com.harmonia.devicetest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;

class ConsoleTest {

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private Console console(String input) {
        return new Console(new BufferedReader(new StringReader(input)), new PrintStream(output, true));
    }

    @Test
    void yesNoAcceptsFrenchAndEnglishAndAsksAgain() {
        Console c = console("peut-être\no\nnon\n?\n");
        assertEquals(Console.Answer.YES, c.yesNo("Ça marche ?"));
        assertEquals(Console.Answer.NO, c.yesNo("Ça marche ?"));
        assertEquals(Console.Answer.DONT_KNOW, c.yesNo("Ça marche ?"));
        assertTrue(output.toString().contains("Répondez o (oui)"));
    }

    @Test
    void textWithDefault() {
        Console c = console("\nRAM2:05\n");
        assertEquals("RAM1:00", c.text("Performance", "RAM1:00"));
        assertEquals("RAM2:05", c.text("Performance", "RAM1:00"));
    }

    @Test
    void chooseByNumberOrDefault() {
        Console c = console("\n7\n2\n");
        assertEquals(0, c.choose("Port", List.of("A", "B"), 0));
        assertEquals(1, c.choose("Port", List.of("A", "B"), 0)); // 7 is refused, then 2
    }

    @Test
    void runOrSkip() {
        Console c = console("\ns\n");
        assertTrue(c.runOrSkip("Lancer ?"));
        assertFalse(c.runOrSkip("Lancer ?"));
    }

    @Test
    void qAndEndOfInputQuit() {
        assertThrows(Console.QuitException.class, () -> console("q\n").yesNo("?"));
        assertThrows(Console.QuitException.class, () -> console("").text("?"));
    }
}
