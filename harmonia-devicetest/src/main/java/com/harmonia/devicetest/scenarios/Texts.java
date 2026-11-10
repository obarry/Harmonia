package com.harmonia.devicetest.scenarios;

import java.util.List;

import com.harmonia.wavestation.model.Part;
import com.harmonia.wavestation.model.Patch;
import com.harmonia.wavestation.model.PatchWave;
import com.harmonia.wavestation.model.Performance;

/** Text summaries of the model, shown to the tester and copied in the report. */
final class Texts {

    private static final String[] NOTES = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };

    private Texts() {
    }

    static String note(int n) {
        return NOTES[n % 12] + (n / 12 - 1);
    }

    static String signed(int v) {
        return v > 0 ? "+" + v : Integer.toString(v);
    }

    static String parts(Performance perf) {
        StringBuilder sb = new StringBuilder(String.format("Performance « %s »%n", perf.getName()));
        sb.append("Part  Patch      Niveau  Clavier      Vélocité  Transpo  Mode\n");
        for (Part p : perf.getParts()) {
            if (!p.isUsed()) {
                sb.append(String.format("%-5d -%n", p.getNumber()));
                continue;
            }
            sb.append(String.format("%-5d %-10s %-7d %-12s %-9s %-8s %s%n", p.getNumber(), p.patchReference(),
                    p.getLevel(), note(p.getLowKey()) + "-" + note(p.getHighKey()),
                    p.getLowVelocity() + "-" + p.getHighVelocity(), signed(p.getTranspose()), p.getVoiceMode()));
        }
        return sb.toString();
    }

    static String patch(Patch patch) {
        StringBuilder sb = new StringBuilder(String.format("Patch « %s » : %d onde(s) (Number_Of_Waves = %d), "
                + "hard sync %s%n", patch.getName(), patch.getWaveCount(), patch.struct().get("Number_Of_Waves"),
                patch.isHardSync() ? "oui" : "non"));
        sb.append("Onde  Source            Coarse  Fine  Level  Cutoff\n");
        for (PatchWave w : patch.getActiveWaves()) {
            sb.append(String.format("%-5s %-17s %-7s %-5s %-6d %d%n", w.getLetter(), w.describeSource(),
                    signed(w.getCoarse()), signed(w.getFine()), w.getLevel(), w.getCutoff()));
        }
        return sb.toString();
    }

    static String mixEnvelope(Patch patch) {
        StringBuilder sb = new StringBuilder("Point  AC     BD\n");
        for (int i = 0; i < 5; i++) {
            sb.append(String.format("%-6d %-6s %s%n", i, signed(patch.getMixAc(i)),
                    patch.getWaveCount() == 4 ? signed(patch.getMixBd(i)) : "-"));
        }
        sb.append(String.format("Times : %d %d %d %d    Loop (brut) : %d    Repeats : %d%n", patch.getMixTime(1),
                patch.getMixTime(2), patch.getMixTime(3), patch.getMixTime(4), patch.getMixLoop(),
                patch.getMixRepeats()));
        return sb.toString();
    }

    static String firstNames(String title, List<String> names, int count) {
        StringBuilder sb = new StringBuilder(title).append('\n');
        for (int i = 0; i < Math.min(count, names.size()); i++) {
            sb.append(String.format("  %02d %s%n", i, names.get(i)));
        }
        return sb.toString();
    }
}
