package com.harmonia.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StatusBarTest {

    @Test
    void latestActivityIsShownUntilItEnds() {
        StatusBar bar = new StatusBar();
        assertEquals("", bar.activityText());
        bar.startActivity("sync", "Lecture du SR : RAM1");
        bar.startActivity("read", "Lecture du patch RAM2:03");
        assertEquals("Lecture du patch RAM2:03", bar.activityText());
        bar.startActivity("sync", "Lecture du SR : RAM2");
        assertEquals("Lecture du SR : RAM2", bar.activityText());
        bar.endActivity("sync");
        assertEquals("Lecture du patch RAM2:03", bar.activityText());
        bar.endActivity("read");
        assertEquals("", bar.activityText());
    }
}
