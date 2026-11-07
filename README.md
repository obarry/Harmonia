# Harmonia

**Éditeur / librarian MIDI pour le KORG Wavestation SR**, écrit en Java.

Harmonia permet de piloter tous les réglages du Wavestation SR depuis l'ordinateur : charger et sauver les sons, éditer performances, patchs, wave sequences et effets **en temps réel** en jouant au clavier MIDI, dans une interface moderne inspirée de la façade de l'appareil.

![Maquette cible de Harmonia](docs/images/harmonia-mockup.png)

*Maquette de l'interface cible.*

## État du projet

🚧 **En cours de refonte.** Le code actuel est une preuve de concept (Swing + FlatLaf). La feuille de route complète est dans [`docs/DESIGN_ROADMAP.md`](docs/DESIGN_ROADMAP.md).

| Phase | Contenu | État |
|-------|---------|------|
| P0 | Remise à plat du dépôt | ✅ |
| P1 | Passage à Maven + CI GitHub Actions | ✅ |
| P2 | Couche MIDI fiable (SysEx, file d'envoi, détection) | ⏳ |
| P3 | Modèle et codecs Wavestation | ⏳ |
| P4 | Librarian (banques, fichiers `.syx`) | ⏳ |
| P5 | Shell UI, thème et façade | 🔨 en cours (branche `phase5-ui-shell`) |
| P6 | Éditeur de patch en temps réel | ⏳ |
| P7 | Performances, effets, multisets | ⏳ |
| P8 | Wave sequences | ⏳ |
| P9 | Finitions et release 1.0 | ⏳ |

## Prérequis

- JDK 21 ou plus récent, Maven 3.6.3 ou plus récent
- Un KORG Wavestation SR relié à l'ordinateur par une interface MIDI (IN et OUT)
- Sur le SR : canal de base connu, **MIDI › Param** sur `RECEIVE` ou `RECV & TRANS`, **Memory Protect** désactivé pour écrire en RAM

## Construire et lancer

```
mvn install
mvn -pl harmonia-ui exec:java
```

Dans Eclipse : *File › Import › Maven › Existing Maven Projects*, sélectionner le dossier racine (les 5 projets sont importés), puis lancer `com.harmonia.ui.HarmoniaApp` du module `harmonia-ui`.

L'ancienne preuve de concept reste disponible : `com.harmonia.wavestation.gui.WaveStationEditor`.

## Raccourcis

La façade dessinée en haut de la fenêtre est fonctionnelle : ses boutons agissent comme sur l'appareil.

| Action | Bouton de façade | Clavier |
|--------|------------------|---------|
| Changer de section | PERF, MULTI, MIDI/GLOBAL | Ctrl+1 … Ctrl+7 |
| Page suivante / précédente | PAGE+ / PAGE− | Ctrl+PgDn / Ctrl+PgUp |
| Descendre d'un niveau (performance → patch → wave sequence) | EDIT | Ctrl+E |
| Valeur +1 / −1 (ou son suivant / précédent) | +1/YES, −1/NO | Flèches sur le bouton rotatif sélectionné |
| Banque suivante | BANK | Ctrl+B |
| Écrire | WRITE/COMPARE | Ctrl+Shift+W |
| Onde suivante / précédente | Curseurs haut / bas | — |
| Paramètre suivant / précédent | Curseurs gauche / droite | Tab / Shift+Tab |

Boutons rotatifs : glisser vers le haut ou le bas (Shift = pas fin), molette, double-clic = valeur par défaut, PgUp/PgDn = ±10.

**Logo** : Harmonia n'embarque pas le logo Korg. Pour l'afficher sur la façade, placez votre propre image dans `~/.harmonia/logo.png` (sous Windows : `C:\Users\<vous>\.harmonia\logo.png`).

## Modules

| Module | Rôle |
|--------|------|
| `harmonia-midi` | Transport MIDI : ports, réception, SysEx |
| `harmonia-wavestation` | Profil Wavestation SR : modèle, codecs SysEx, fichiers `.syx` |
| `harmonia-app` | Services applicatifs : session, undo, bibliothèque |
| `harmonia-ui` | Interface Swing + FlatLaf |

## Documentation

- [Analyse, design et feuille de route](docs/DESIGN_ROADMAP.md)
- Les manuels officiels Korg (Player's Guide, Reference Guide, Performance Notes) sont protégés par le droit d'auteur : placez-les localement dans `docs/reference/`, dossier ignoré par Git.

## Remerciements

- [Edisyn](https://github.com/eclab/edisyn) de Sean Luke, référence précieuse pour le SysEx du Wavestation SR.

## Licence

[Apache License 2.0](LICENSE)

---

*Harmonia n'est pas affilié à KORG Inc. « KORG » et « Wavestation » sont des marques de leurs propriétaires respectifs.*
