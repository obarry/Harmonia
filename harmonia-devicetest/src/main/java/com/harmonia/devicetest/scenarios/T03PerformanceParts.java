package com.harmonia.devicetest.scenarios;

import com.harmonia.devicetest.DeviceTest;
import com.harmonia.devicetest.TestContext;
import com.harmonia.wavestation.model.Dumps;
import com.harmonia.wavestation.model.Performance;
import com.harmonia.wavestation.sysex.WavestationBank;

/** T03: the parts of a performance are decoded correctly (patch, bank, zones, transposition). */
public class T03PerformanceParts implements DeviceTest {

    @Override
    public String id() {
        return "T03";
    }

    @Override
    public String title() {
        return "Parts d'une performance";
    }

    @Override
    public String purpose() {
        return "Lit une performance et compare ses parts décodées (patch joué et sa banque, niveau, zones, "
                + "transposition) avec la page EDIT PERF du SR. Valide la structure « part » et les banques étendues.";
    }

    @Override
    public void run(TestContext ctx) {
        String answer = ctx.console().text("Performance à tester (banque:numéro), de préférence avec plusieurs parts",
                "RAM1:00");
        String[] ref = answer.split(":");
        WavestationBank bank = WavestationBank.fromLabel(ref[0].trim()).orElse(WavestationBank.RAM1);
        int number = Integer.parseInt(ref.length > 1 ? ref[1].trim() : "0");
        String label = bank.label() + ":" + String.format("%02d", number);

        ctx.device().selectPerformance(bank, number);
        byte[] dump = ctx.await(ctx.device().requestPerformance(bank, number), "lecture de la performance " + label);
        if (dump == null) {
            return;
        }
        ctx.saveDump(id() + "-performance-" + bank.label() + "-" + number + ".syx", dump, "dump de la performance");
        Performance perf = Dumps.decodePerformance(dump);
        ctx.share("performance", perf);
        ctx.show(Texts.parts(perf));

        ctx.console().step("Sur le SR : la performance " + label + " est sélectionnée. Appuyez sur EDIT (page EDIT PERF).");
        ctx.console().step("Avec les curseurs haut / bas, parcourez les parts et comparez au tableau ci-dessus.");
        ctx.ask("Le nombre de parts utilisées est-il le même ?");
        ctx.ask("Pour chaque part, la banque et le numéro du patch sont-ils les mêmes ?");
        ctx.console().step("Page ZONES : comparez les plages de clavier et de vélocité.");
        ctx.ask("Les zones clavier et vélocité correspondent-elles ?");
        ctx.photo(id() + "-edit-perf", "l'écran EDIT PERF de la part 1");
    }
}
