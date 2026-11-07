# Harmonia

**Éditeur / librarian MIDI pour le KORG Wavestation SR**, écrit en Java.

Harmonia permet de piloter tous les réglages du Wavestation SR depuis l'ordinateur : charger et sauver les sons, éditer performances, patchs, wave sequences et effets **en temps réel** en jouant au clavier MIDI, dans une interface moderne inspirée de la façade de l'appareil.

![Maquette cible de Harmonia](docs/images/harmonia-mockup.png)

*Maquette de l'interface cible.*

## État du projet

🚧 **En cours de refonte.** Le code actuel est une preuve de concept (Swing, sans dépendance). La feuille de route complète est dans [`docs/DESIGN_ROADMAP.md`](docs/DESIGN_ROADMAP.md).

| Phase | Contenu | État |
|-------|---------|------|
| P0 | Remise à plat du dépôt | ✅ en cours |
| P1 | Passage à Maven + CI GitHub Actions | ⏳ |
| P2 | Couche MIDI fiable (SysEx, file d'envoi, détection) | ⏳ |
| P3 | Modèle et codecs Wavestation | ⏳ |
| P4 | Librarian (banques, fichiers `.syx`) | ⏳ |
| P5 | Shell UI, thème et façade | ⏳ |
| P6 | Éditeur de patch en temps réel | ⏳ |
| P7 | Performances, effets, multisets | ⏳ |
| P8 | Wave sequences | ⏳ |
| P9 | Finitions et release 1.0 | ⏳ |

## Prérequis

- Java (JDK) récent
- Un KORG Wavestation SR relié à l'ordinateur par une interface MIDI (IN et OUT)
- Sur le SR : canal de base connu, **MIDI › Param** sur `RECEIVE` ou `RECV & TRANS`, **Memory Protect** désactivé pour écrire en RAM

## Lancer la preuve de concept

Ouvrir le projet dans Eclipse puis exécuter `com.harmonia.wavestation.gui.WaveStationEditor`.

## Documentation

- [Analyse, design et feuille de route](docs/DESIGN_ROADMAP.md)
- Les manuels officiels Korg (Player's Guide, Reference Guide, Performance Notes) sont protégés par le droit d'auteur : placez-les localement dans `docs/reference/`, dossier ignoré par Git.

## Remerciements

- [Edisyn](https://github.com/eclab/edisyn) de Sean Luke, référence précieuse pour le SysEx du Wavestation SR.

## Licence

[Apache License 2.0](LICENSE)

---

*Harmonia n'est pas affilié à KORG Inc. « KORG » et « Wavestation » sont des marques de leurs propriétaires respectifs.*
