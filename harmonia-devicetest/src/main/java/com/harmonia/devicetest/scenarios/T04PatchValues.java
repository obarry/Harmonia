package com.harmonia.devicetest.scenarios;

import com.harmonia.devicetest.DeviceTest;
import com.harmonia.devicetest.TestContext;
import com.harmonia.wavestation.model.Dumps;
import com.harmonia.wavestation.model.Part;
import com.harmonia.wavestation.model.Patch;
import com.harmonia.wavestation.model.Performance;
import com.harmonia.wavestation.sysex.WavestationBank;

/**
 * T04: the values of a patch are decoded correctly: structure (number of waves), wave sources,
 * vector mix points. Confirms the encodings deduced from Edisyn (Number_Of_Waves, Mix_X/Y).
 */
public class T04PatchValues implements DeviceTest {

    @Override
    public String id() {
        return "T04";
    }

    @Override
    public String title() {
        return "Valeurs d'un patch (structure, ondes, vector mix)";
    }

    @Override
    public String purpose() {
        return "Lit le patch de la part 1 et compare avec les pages PATCH, WAVES et MIX ENV du SR. Confirme le "
                + "codage du nombre d'ondes et des coordonnées du vector mix (centre 127).";
    }

    @Override
    public void run(TestContext ctx) {
        Performance perf = ctx.shared("performance");
        WavestationBank bank = WavestationBank.RAM1;
        int number = 0;
        if (perf != null && perf.part(1).isUsed()) {
            Part part = perf.part(1);
            bank = part.getBank();
            number = part.getPatchNumber();
        } else {
            ctx.console().info("  (pas de performance lue en T03 : patch RAM1:00)");
        }
        String label = bank.label() + ":" + String.format("%02d", number);
        byte[] dump = ctx.await(ctx.device().requestPatch(bank, number), "lecture du patch " + label);
        if (dump == null) {
            return;
        }
        ctx.saveDump(id() + "-patch-" + bank.label() + "-" + number + ".syx", dump, "dump du patch");
        Patch patch = Dumps.decodePatch(dump);
        ctx.share("patchDump", dump);
        ctx.show(Texts.patch(patch));

        ctx.console().step("Sur le SR : depuis EDIT PERF, part 1 sélectionnée, appuyez sur EDIT (niveau Edit Patch).");
        ctx.console().step("Page PATCH : regardez la structure (nombre d'ondes) et le nom.");
        ctx.ask("Le SR indique-t-il le patch « " + patch.getName() + " » avec " + patch.getWaveCount() + " onde(s) ?");
        ctx.console().step("Page WAVES : avec haut / bas, parcourez les ondes et comparez la source et l'accord.");
        ctx.ask("Les sources (onde PCM ou wave sequence) et l'accord des ondes correspondent-ils ?");

        if (patch.getWaveCount() > 1) {
            ctx.show(Texts.mixEnvelope(patch));
            ctx.console().step("Page MIX ENV : avec haut / bas, parcourez les points 0 à 4 et comparez AC / BD.");
            ctx.ask("Les coordonnées AC / BD des 5 points correspondent-elles ?");
            ctx.photo(id() + "-mix-env", "l'écran MIX ENV d'un point dont AC et BD ne sont pas nuls");
        } else {
            ctx.console().info("  Patch à une onde : pas de vector mix à comparer.");
        }
    }
}
