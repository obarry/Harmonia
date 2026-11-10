package com.harmonia.devicetest.scenarios;

import java.util.List;

import com.harmonia.devicetest.Console;
import com.harmonia.devicetest.DeviceTest;
import com.harmonia.devicetest.TestContext;
import com.harmonia.wavestation.model.Performance;
import com.harmonia.wavestation.sysex.WavestationBank;

/**
 * T02: Bank Select + Program Change select the right performance, and the SysEx bank numbering
 * reads the same performance: the name read by SysEx must be the one shown by the device.
 */
public class T02SelectPerformance implements DeviceTest {

    private record Target(WavestationBank bank, int number) {
    }

    private static final List<Target> TARGETS = List.of(new Target(WavestationBank.RAM1, 0),
            new Target(WavestationBank.RAM2, 49), new Target(WavestationBank.RAM3, 10),
            new Target(WavestationBank.ROM4, 0), new Target(WavestationBank.ROM9, 25),
            new Target(WavestationBank.ROM11, 7));

    @Override
    public String id() {
        return "T02";
    }

    @Override
    public String title() {
        return "Sélection des performances et numérotation des banques";
    }

    @Override
    public String purpose() {
        return "Pour plusieurs banques : Harmonia fait jouer une performance (Bank Select + Program Change) "
                + "puis lit son nom par SysEx. Le SR doit afficher cette performance, avec ce nom : cela valide "
                + "les deux numérotations des banques (MIDI et SysEx).";
    }

    @Override
    public void run(TestContext ctx) throws InterruptedException {
        ctx.console().step("Mettez le SR en mode PERF (bouton PERF), page PERFORM.");
        ctx.console().waitEnter("Prêt");
        for (Target t : TARGETS) {
            String ref = t.bank().label() + ":" + String.format("%02d", t.number());
            ctx.device().selectPerformance(t.bank(), t.number());
            Thread.sleep(400);
            Performance perf = ctx.await(ctx.library().loadPerformance(ctx.device(), t.bank(), t.number()),
                    "lecture SysEx de la performance " + ref);
            String name = perf != null ? perf.getName() : "?";
            ctx.console().info("  " + ref + " : nom lu par SysEx « " + name + " »");
            ctx.ask("Le SR affiche-t-il la performance " + t.bank().shortLabel() + ":" + String.format("%02d",
                    t.number()) + " « " + name + " » ?");
        }
        if (ctx.console().yesNo("Une carte programme est-elle insérée dans le SR ?") == Console.Answer.YES) {
            ctx.device().selectPerformance(WavestationBank.CARD, 0);
            Thread.sleep(400);
            Performance perf = ctx.await(ctx.library().loadPerformance(ctx.device(), WavestationBank.CARD, 0),
                    "lecture SysEx de la performance CARD:00");
            ctx.ask("Le SR affiche-t-il C:00 « " + (perf != null ? perf.getName() : "?") + " » ?");
        }
    }
}
