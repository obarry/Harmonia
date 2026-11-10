package com.harmonia.devicetest.scenarios;

import java.util.List;

import com.harmonia.devicetest.DeviceTest;
import com.harmonia.devicetest.TestContext;
import com.harmonia.devicetest.TestReport;

/** T10: the master keyboard is forwarded to the device (MIDI thru). */
public class T10KeyboardThru implements DeviceTest {

    @Override
    public String id() {
        return "T10";
    }

    @Override
    public String title() {
        return "Clavier maître (MIDI thru)";
    }

    @Override
    public String purpose() {
        return "Vérifie que les notes jouées sur le clavier maître arrivent dans Harmonia et sont transmises au SR.";
    }

    @Override
    public void run(TestContext ctx) throws InterruptedException {
        if (!ctx.session().getSettings().hasKeyboard()) {
            ctx.console().info("  Aucun clavier maître configuré : test passé.");
            ctx.entry().force(TestReport.Status.SKIPPED);
            return;
        }
        ctx.console().step("Jouez quelques notes et accords sur le clavier maître pendant 10 secondes après Entrée.");
        ctx.console().waitEnter("Prêt");
        ctx.clearReceived();
        List<byte[]> notes = ctx.collectKeyboard(10_000);
        long noteOns = notes.stream().filter(m -> m.length == 3 && (m[0] & 0xF0) == 0x90 && m[2] != 0).count();
        ctx.check(noteOns + " note(s) reçue(s) du clavier", noteOns > 0);
        ctx.ask("Le SR a-t-il joué ces notes, sans note bloquée ?");
    }
}
