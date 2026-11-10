package com.harmonia.devicetest.scenarios;

import java.util.List;

import com.harmonia.devicetest.DeviceTest;
import com.harmonia.devicetest.TestContext;
import com.harmonia.wavestation.param.Parameter;

/**
 * T11: part parameters sent in real time, after selecting the part with CURRENT_PART. Checks the
 * numbering of CURRENT_PART (1-8 as on the device, or 0-7) and of CURRENT_WAVE (0-3 or 1-4).
 */
public class T11PartParameters implements DeviceTest {

    @Override
    public String id() {
        return "T11";
    }

    @Override
    public String title() {
        return "Paramètres d'une part (CURRENT_PART)";
    }

    @Override
    public String purpose() {
        return "Change le niveau et la transposition d'une part précise en envoyant d'abord CURRENT_PART, "
                + "comme l'éditeur de performance de Harmonia, et vérifie la numérotation des parts et des ondes.";
    }

    @Override
    public void run(TestContext ctx) throws InterruptedException {
        ctx.console().step("Sur le SR : MIDI/GLOBAL › page MIDI, Param sur RECEIVE ou RECV & TRANS.");
        ctx.console().step("Choisissez une performance qui a au moins 2 parts (par exemple une performance en split "
                + "ou en layer), puis allez sur la page EDIT PERF qui montre les niveaux (LEVEL) des parts.");
        ctx.console().waitEnter("Prêt");

        ctx.device().forgetEditContext();
        ctx.device().setPartParameter(2, Parameter.PART_VOLUME, 20);
        Thread.sleep(400);
        List<String> options = List.of("La part 1", "La part 2", "La part 3", "Aucune", "Je ne sais pas");
        int changed = ctx.console().choose("Niveau 20 envoyé après CURRENT_PART = 2. Quelle part a maintenant "
                + "le niveau 20 ?", options, 1);
        ctx.entry().note("CURRENT_PART = 2, PART_VOLUME = 20 → " + options.get(changed));
        switch (changed) {
            case 1 -> ctx.check("CURRENT_PART est numéroté 1-8, comme sur l'appareil", true);
            case 2 -> ctx.check("CURRENT_PART est numéroté 0-7 (Harmonia doit envoyer part - 1)", false);
            case 3 -> ctx.check("Le SR applique PART_VOLUME après CURRENT_PART", false);
            default -> ctx.entry().note("Numérotation de CURRENT_PART non déterminée");
        }
        if (changed == 0) {
            ctx.check("Le SR applique CURRENT_PART (seule la part courante a changé)", false);
        }

        ctx.device().setPartParameter(1, Parameter.PART_TRANSPOSE, 12);
        Thread.sleep(400);
        ctx.ask("Transposition +12 envoyée à la part 1 : la part 1 joue-t-elle une octave plus haut "
                + "(et la page TRANSPOSE de la part 1 affiche-t-elle +12) ?");
        ctx.device().setPartParameter(1, Parameter.PART_TRANSPOSE, 0);

        ctx.console().step("Ouvrez maintenant le patch de la part 1 (EDIT), page WAVES.");
        ctx.console().waitEnter("Prêt");
        ctx.device().setWaveParameter(1, 1, Parameter.WAVE_LEVEL, 0);
        Thread.sleep(400);
        List<String> waves = List.of("L'onde A", "L'onde B", "L'onde C", "Aucune", "Je ne sais pas");
        int wave = ctx.console().choose("Niveau 0 envoyé après CURRENT_WAVE = 1. Quelle onde a maintenant le "
                + "niveau 0 ?", waves, 1);
        ctx.entry().note("CURRENT_WAVE = 1, WAVE_LEVEL = 0 → " + waves.get(wave));
        switch (wave) {
            case 1 -> ctx.check("CURRENT_WAVE est numéroté 0-3 (A = 0)", true);
            case 0 -> ctx.check("CURRENT_WAVE est numéroté 1-4 (Harmonia doit envoyer onde + 1)", false);
            case 3 -> ctx.check("Le SR applique WAVE_LEVEL après CURRENT_WAVE", false);
            default -> ctx.entry().note("Numérotation de CURRENT_WAVE non déterminée");
        }
        ctx.console().step("Ces changements ne sont que dans le tampon d'édition : rechargez la performance "
                + "sans écrire pour retrouver l'original.");
    }
}
