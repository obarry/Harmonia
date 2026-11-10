package com.harmonia.devicetest.scenarios;

import com.harmonia.devicetest.Console;
import com.harmonia.devicetest.DeviceTest;
import com.harmonia.devicetest.TestContext;
import com.harmonia.devicetest.TestReport;
import com.harmonia.wavestation.model.Dumps;
import com.harmonia.wavestation.model.Patch;
import com.harmonia.wavestation.sysex.WavestationBank;

/**
 * T08 (writes to memory): a patch sent to the edit buffer and written to a RAM location can be
 * read back; the original patch is saved first and restored at the end.
 */
public class T08WritePatch implements DeviceTest {

    private static final String TEST_NAME = "HARMONIA TEST";

    @Override
    public String id() {
        return "T08";
    }

    @Override
    public String title() {
        return "Écriture d'un patch en mémoire (modifie la RAM)";
    }

    @Override
    public String purpose() {
        return "Sauvegarde un emplacement RAM, y écrit une copie renommée « " + TEST_NAME + " », la relit, puis "
                + "restaure l'original. Vérifie les commandes Patch Write et les accusés Write Complete.";
    }

    @Override
    public void run(TestContext ctx) {
        ctx.console().step("Ce test écrit dans la mémoire du SR. Memory Protect doit être sur OFF "
                + "(MIDI/GLOBAL › GLOBAL).");
        if (ctx.console().yesNo("Continuer ?") != Console.Answer.YES) {
            ctx.entry().force(TestReport.Status.SKIPPED);
            return;
        }
        String slot = ctx.console().text("Emplacement de patch sans importance (banque RAM:numéro)", "RAM3:34");
        String[] ref = slot.split(":");
        WavestationBank bank = WavestationBank.fromLabel(ref[0].trim()).orElse(WavestationBank.RAM3);
        int number = Integer.parseInt(ref.length > 1 ? ref[1].trim() : "34");
        if (!bank.isWritable()) {
            ctx.check(bank.label() + " est une banque ROM : écriture impossible", false);
            return;
        }
        String label = bank.label() + ":" + String.format("%02d", number);
        int ch = ctx.device().getChannel();

        byte[] original = ctx.await(ctx.device().requestPatch(bank, number), "sauvegarde du patch " + label);
        if (original == null) {
            return;
        }
        ctx.saveDump(id() + "-backup-" + bank.label() + "-" + number + ".syx", original, "sauvegarde de l'original");
        Patch copy = Dumps.decodePatch(original);
        String originalName = copy.getName();
        copy.setName(TEST_NAME);

        ctx.console().step("Si le SR demande une confirmation pendant l'écriture, appuyez sur +1/YES.");
        if (!ctx.succeeds(ctx.device().sendDump(Dumps.encodePatch(copy, ch, bank, number)), "envoi de la copie renommée")
                || !ctx.succeeds(ctx.device().writePatch(bank, number), "écriture dans " + label + " (Write Complete)")) {
            ctx.console().info("  L'original n'a pas été modifié ; sauvegarde gardée dans le rapport.");
            return;
        }
        byte[] reread = ctx.await(ctx.device().requestPatch(bank, number), "relecture de " + label);
        if (reread != null) {
            ctx.check("le patch relu s'appelle « " + TEST_NAME + " »",
                    TEST_NAME.equals(Dumps.decodePatch(reread).getName()));
        }

        ctx.succeeds(ctx.device().sendDump(original), "renvoi de l'original");
        ctx.succeeds(ctx.device().writePatch(bank, number), "restauration de " + label);
        byte[] restored = ctx.await(ctx.device().requestPatch(bank, number), "relecture après restauration");
        if (restored != null) {
            ctx.check("l'original « " + originalName + " » est restauré",
                    originalName.equals(Dumps.decodePatch(restored).getName()));
        }
    }
}
