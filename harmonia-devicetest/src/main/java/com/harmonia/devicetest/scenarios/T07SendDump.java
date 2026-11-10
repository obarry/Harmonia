package com.harmonia.devicetest.scenarios;

import com.harmonia.devicetest.DeviceTest;
import com.harmonia.devicetest.TestContext;
import com.harmonia.wavestation.sysex.WavestationBank;

/** T07: a patch dump sent to the device is acknowledged (Data Load Complete). */
public class T07SendDump implements DeviceTest {

    @Override
    public String id() {
        return "T07";
    }

    @Override
    public String title() {
        return "Envoi d'un patch (Send) et accusé de réception";
    }

    @Override
    public String purpose() {
        return "Renvoie au SR le patch lu en T04 (dans son tampon d'édition, sans rien écrire en mémoire) et vérifie "
                + "que le SR répond « Data Load Complete ».";
    }

    @Override
    public void run(TestContext ctx) {
        byte[] dump = ctx.shared("patchDump");
        if (dump == null) {
            dump = ctx.await(ctx.device().requestPatch(WavestationBank.RAM1, 0), "lecture du patch RAM1:00");
            if (dump == null) {
                return;
            }
        }
        ctx.succeeds(ctx.device().sendDump(dump), "accusé « Data Load Complete » après l'envoi du patch");
        ctx.ask("Le SR a-t-il affiché un message de réception SysEx réussie (ou rien d'anormal) ?");
    }
}
