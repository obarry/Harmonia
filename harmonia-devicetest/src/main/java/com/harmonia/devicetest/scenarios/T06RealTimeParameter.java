package com.harmonia.devicetest.scenarios;

import com.harmonia.devicetest.DeviceTest;
import com.harmonia.devicetest.TestContext;

/** T06: parameter changes sent by SysEx act on the device edit buffer in real time. */
public class T06RealTimeParameter implements DeviceTest {

    /** FILTER_MOD_CUTOFF in the parameter table (Reference Guide, appendix 4, 5.15). */
    private static final int CUTOFF = 93;

    @Override
    public String id() {
        return "T06";
    }

    @Override
    public String title() {
        return "Paramètre en temps réel (Cutoff)";
    }

    @Override
    public String purpose() {
        return "Envoie plusieurs valeurs du paramètre n° 93 (FILTER_MOD_CUTOFF) et vérifie que le SR les applique "
                + "à l'onde en cours d'édition. Nécessite MIDI › Param sur RECEIVE ou RECV & TRANS.";
    }

    @Override
    public void run(TestContext ctx) throws InterruptedException {
        ctx.console().step("Sur le SR : MIDI/GLOBAL › page MIDI, vérifiez que Param est sur RECEIVE ou RECV & TRANS.");
        ctx.console().step("Puis : PERF › EDIT › part 1 › EDIT (Edit Patch) › page FILTER, onde A sélectionnée.");
        ctx.console().step("Gardez une note enfoncée (ou jouez régulièrement) pendant le test.");
        ctx.console().waitEnter("Prêt");
        for (int value : new int[] { 10, 50, 99 }) {
            ctx.device().setParameter(CUTOFF, value);
            Thread.sleep(300);
            ctx.ask("Cutoff = " + value + " : l'écran FILTER affiche-t-il " + value
                    + " et le son change-t-il (plus sombre à 10, plus brillant à 99) ?");
        }
        ctx.console().step("Ces changements ne sont que dans le tampon d'édition : quittez sans écrire (WRITE) "
                + "pour retrouver le patch d'origine.");
    }
}
