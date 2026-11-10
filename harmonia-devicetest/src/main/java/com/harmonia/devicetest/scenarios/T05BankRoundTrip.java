package com.harmonia.devicetest.scenarios;

import java.util.Arrays;
import java.util.List;

import com.harmonia.devicetest.DeviceTest;
import com.harmonia.devicetest.TestContext;
import com.harmonia.wavestation.model.Dumps;
import com.harmonia.wavestation.model.Patch;
import com.harmonia.wavestation.model.Performance;
import com.harmonia.wavestation.model.WaveSequenceBank;
import com.harmonia.wavestation.sysex.WavestationBank;

/** T05: whole bank dumps are received, decoded and re-encoded identically. */
public class T05BankRoundTrip implements DeviceTest {

    @Override
    public String id() {
        return "T05";
    }

    @Override
    public String title() {
        return "Lecture d'une banque complète et aller-retour";
    }

    @Override
    public String purpose() {
        return "Lit les performances, patchs et wave sequences d'une banque (environ 20 s), vérifie les checksums, "
                + "le décodage et le ré-encodage identique octet pour octet. Les dumps sont gardés dans le rapport.";
    }

    @Override
    public void run(TestContext ctx) {
        String answer = ctx.console().text("Banque à lire", "RAM1");
        WavestationBank bank = WavestationBank.fromLabel(answer).orElse(WavestationBank.RAM1);
        int ch = ctx.device().getChannel();

        byte[] perfDump = ctx.await(ctx.device().requestPerformanceBank(bank), "banque de performances " + bank.label());
        if (perfDump != null) {
            ctx.saveDump(id() + "-performances-" + bank.label() + ".syx", perfDump, "banque de performances");
            List<Performance> perfs = Dumps.decodePerformanceBank(perfDump);
            ctx.check("ré-encodage identique des performances",
                    Arrays.equals(perfDump, Dumps.encodePerformanceBank(perfs, ch, bank)));
            ctx.show(Texts.firstNames("Performances :", perfs.stream().map(Performance::getName).toList(), 8));
            ctx.library().putPerformances(bank, perfs);
        }
        byte[] patchDump = ctx.await(ctx.device().requestPatchBank(bank), "banque de patchs " + bank.label());
        if (patchDump != null) {
            ctx.saveDump(id() + "-patches-" + bank.label() + ".syx", patchDump, "banque de patchs");
            List<Patch> patches = Dumps.decodePatchBank(patchDump);
            ctx.check("ré-encodage identique des patchs", Arrays.equals(patchDump, Dumps.encodePatchBank(patches, ch, bank)));
            ctx.show(Texts.firstNames("Patchs :", patches.stream().map(Patch::getName).toList(), 8));
            ctx.library().putPatches(bank, patches);
        }
        byte[] wsDump = ctx.await(ctx.device().requestWaveSequenceBank(bank), "banque de wave sequences " + bank.label());
        if (wsDump != null) {
            ctx.saveDump(id() + "-wavesequences-" + bank.label() + ".syx", wsDump, "banque de wave sequences");
            WaveSequenceBank ws = Dumps.decodeWaveSequenceBank(wsDump);
            ctx.check("ré-encodage identique des wave sequences",
                    Arrays.equals(wsDump, Dumps.encodeWaveSequenceBank(ws, ch, bank)));
            StringBuilder sb = new StringBuilder("Wave sequences (nom, nombre de pas en suivant les liens) :\n");
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("  %02d %-8s %d pas%n", i, ws.getName(i), ws.steps(i).size()));
            }
            ctx.show(sb.toString());
            ctx.console().step("Sur le SR : comparez quelques noms (PERFORM, et Edit Patch › WAVES pour les wave sequences).");
            ctx.ask("Les noms affichés correspondent-ils à ceux du SR ?");
            ctx.console().step("Facultatif : niveau Edit Wave Sequence (page WSEQ) d'une des séquences ci-dessus.");
            ctx.ask("Le nombre de pas d'une séquence vérifiée correspond-il (? si non vérifié) ?");
        }
    }
}
