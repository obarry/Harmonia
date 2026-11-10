package com.harmonia.devicetest.scenarios;

import java.util.List;

import com.harmonia.devicetest.DeviceTest;
import com.harmonia.devicetest.TestContext;
import com.harmonia.midi.MidiBytes;
import com.harmonia.wavestation.sysex.MessageType;
import com.harmonia.wavestation.sysex.WavestationBank;
import com.harmonia.wavestation.sysex.WavestationSysex;

/** T09: what the device sends when it is used from its front panel. */
public class T09FollowDevice implements DeviceTest {

    @Override
    public String id() {
        return "T09";
    }

    @Override
    public String title() {
        return "Messages envoyés par le SR depuis sa façade";
    }

    @Override
    public String purpose() {
        return "Enregistre ce que le SR envoie quand on change de performance (Bank Select + Program Change) et "
                + "quand on modifie un paramètre (SysEx, si MIDI › Param = RECV & TRANS) : base du suivi façade.";
    }

    @Override
    public void run(TestContext ctx) throws InterruptedException {
        ctx.console().step("Sur le SR : PERF, puis changez de performance avec +1/YES (et une fois avec BANK).");
        ctx.console().step("Vous avez 20 secondes après Entrée.");
        ctx.console().waitEnter("Prêt");
        ctx.clearReceived();
        List<byte[]> messages = ctx.collect(20_000);
        StringBuilder sb = new StringBuilder("Reçu :\n");
        int bank = 0;
        int programs = 0;
        for (byte[] m : messages) {
            String text = MidiBytes.toHex(m, 12);
            if (m.length == 3 && (m[0] & 0xF0) == 0xB0 && m[1] == 0x20) {
                bank = m[2];
                text += "  Bank select " + bank;
            } else if (m.length == 2 && (m[0] & 0xF0) == 0xC0) {
                programs++;
                int program = m[1] & 0x7F;
                text += "  Program change " + program + " = " + WavestationBank.fromMidi(bank, program)
                        .map(b -> b.label() + ":" + String.format("%02d", program % 50)).orElse("?");
            }
            sb.append("  ").append(text).append('\n');
        }
        ctx.show(sb.toString());
        ctx.check(programs + " changement(s) de programme reçu(s)", programs > 0);
        ctx.ask("Les performances décodées ci-dessus sont-elles celles que vous avez choisies ?");

        ctx.console().step("Sur le SR : MIDI/GLOBAL › MIDI, Param sur RECV & TRANS. Puis Edit Patch › FILTER,");
        ctx.console().step("modifiez Cutoff avec +1/YES et -1/NO. Vous avez 20 secondes après Entrée.");
        ctx.console().waitEnter("Prêt");
        ctx.clearReceived();
        StringBuilder params = new StringBuilder("Paramètres reçus :\n");
        int count = 0;
        for (byte[] m : ctx.collect(20_000)) {
            MessageType t = WavestationSysex.type(m).orElse(null);
            if (t == MessageType.PARAMETER_CHANGE || t == MessageType.PARAMETER_CHANGE_EXPANDED
                    || t == MessageType.PARAMETER_CHANGE_SR) {
                count++;
                params.append("  ").append(WavestationSysex.describe(m)).append('\n');
            }
        }
        ctx.show(params.toString());
        ctx.check(count + " changement(s) de paramètre reçu(s)", count > 0);
        ctx.ask("Les valeurs reçues (#93 = Cutoff) correspondent-elles à celles affichées par le SR ?");
    }
}
