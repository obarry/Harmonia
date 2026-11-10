package com.harmonia.devicetest.scenarios;

import com.harmonia.devicetest.DeviceTest;
import com.harmonia.devicetest.TestContext;
import com.harmonia.midi.DeviceIdentity;
import com.harmonia.wavestation.sysex.WavestationSysex;

/** T01: the SR answers the Device Inquiry with its channel and system version. */
public class T01Identification implements DeviceTest {

    @Override
    public String id() {
        return "T01";
    }

    @Override
    public String title() {
        return "Identification de l'appareil";
    }

    @Override
    public String purpose() {
        return "Vérifie que le SR répond à la demande d'identification MIDI (Device Inquiry) "
                + "et que son canal et sa version sont bien décodés.";
    }

    @Override
    public void run(TestContext ctx) {
        DeviceIdentity id = ctx.await(ctx.device().identify(), "réponse à la demande d'identification");
        if (id == null) {
            ctx.console().step("Vérifiez les câbles MIDI IN / OUT et que le SR est allumé.");
            return;
        }
        ctx.show(String.format("Fabricant %02X, famille %02X, membre %02X, canal %d, version %s", id.manufacturer(),
                id.family(), id.member(), id.channel() + 1, id.versionText()));
        ctx.check("le membre de la famille est le SR (02)", id.member() == WavestationSysex.MEMBER_SR);
        ctx.check("le canal répondu (" + (id.channel() + 1) + ") est celui de la connexion ("
                + (ctx.device().getChannel() + 1) + ")", id.channel() == ctx.device().getChannel());
        ctx.console().step("Au démarrage, le SR affiche sa version système (ou page MIDI/GLOBAL › GLOBAL).");
        ctx.ask("La version affichée par le SR correspond-elle à " + id.versionText() + " ?");
    }
}
