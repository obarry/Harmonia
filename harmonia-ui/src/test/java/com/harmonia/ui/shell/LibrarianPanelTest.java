package com.harmonia.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class LibrarianPanelTest {

    private final List<String> selections = new ArrayList<>();
    private final String[] names = new String[35];

    @Test
    void refreshUpdatesTheNamesInPlaceWithoutSelectingAgain() {
        LibrarianPanel panel = new LibrarianPanel((kind, e) -> selections.add(e.id()), b -> { }, () -> { });
        panel.setNameSource((bank, kind) -> Optional.of(Arrays.asList(names)));
        panel.selectNumber(12);
        assertEquals(List.of("RAM1:12"), selections);
        assertEquals("?", panel.nameAt(12));

        names[12] = "Lead Synth";      // the sound has just been read from the device
        panel.refresh();
        assertEquals("Lead Synth", panel.nameAt(12));
        assertEquals(List.of("RAM1:12"), selections);   // no second selection event
    }
}
