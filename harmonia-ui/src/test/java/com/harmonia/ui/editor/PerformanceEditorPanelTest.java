package com.harmonia.ui.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.harmonia.wavestation.model.Layouts;
import com.harmonia.wavestation.model.Part;
import com.harmonia.wavestation.model.PartField;
import com.harmonia.wavestation.model.Performance;
import com.harmonia.wavestation.model.Struct;
import com.harmonia.wavestation.sysex.WavestationBank;

class PerformanceEditorPanelTest {

    private final List<String> changes = new ArrayList<>();
    private final List<Integer> selections = new ArrayList<>();

    private Performance performance() {
        Performance perf = new Performance(new Struct(Layouts.PERFORMANCE));
        perf.setName("Test");
        for (int i = 1; i <= 8; i++) {
            perf.part(i).set("Bank_Num", -1);
            perf.part(i).set("Patch_Num", -1);
        }
        for (int i = 2; i <= 3; i++) {
            Part p = perf.part(i);
            p.setPatch(WavestationBank.RAM1, i);
            p.set(PartField.LEVEL, 80);
            p.set(PartField.HIGH_KEY, 127);
            p.set(PartField.LOW_VELOCITY, 1);
            p.set(PartField.HIGH_VELOCITY, 127);
            p.set(PartField.VOICE_MODE, 1);
            p.set(PartField.PLAY_MODE, 3);
        }
        return perf;
    }

    private PerformanceEditorPanel panel() {
        PerformanceEditorPanel panel = new PerformanceEditorPanel(p -> "Patch " + p.getPatchNumber(), p -> { });
        panel.setPartListener(new PerformanceEditorPanel.PartListener() {
            @Override
            public void partChanged(Part part, PartField field, int value) {
                changes.add(part.getNumber() + ":" + field + "=" + value);
            }

            @Override
            public void partSelected(Part part) {
                selections.add(part.getNumber());
            }
        });
        return panel;
    }

    @Test
    void firstUsedPartIsSelectedWithoutReportingChanges() {
        PerformanceEditorPanel panel = panel();
        panel.showPerformance("RAM1:00", performance());
        assertEquals(2, panel.getSelectedPart().getNumber());
        assertEquals(List.of(2), selections);
        assertTrue(changes.isEmpty(), changes.toString());
    }

    @Test
    void editsAreAppliedToThePartAndReported() {
        PerformanceEditorPanel panel = panel();
        Performance perf = performance();
        panel.showPerformance("RAM1:00", perf);
        panel.selectRow(2);
        assertEquals(3, panel.getSelectedPart().getNumber());
        panel.edit(PartField.LEVEL, 42);
        panel.edit(PartField.TRANSPOSE, -12);
        panel.edit(PartField.VOICE_MODE, 3);
        assertEquals(List.of("3:LEVEL=42", "3:TRANSPOSE=-12", "3:VOICE_MODE=3"), changes);
        Performance copy = new Performance(perf.toBytes());
        assertEquals(42, copy.part(3).getLevel());
        assertEquals(-12, copy.part(3).getTranspose());
        assertEquals(Part.VoiceMode.UNISON_LEGATO, copy.part(3).getVoiceMode());
        assertEquals(Part.PlayMode.BOTH, copy.part(3).getPlayMode());
        assertEquals(80, copy.part(2).getLevel());
    }
}
