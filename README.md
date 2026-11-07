# Harmonia

[![Build](https://github.com/obarry/Harmonia/actions/workflows/build.yml/badge.svg)](https://github.com/obarry/Harmonia/actions/workflows/build.yml)

**MIDI editor / librarian for the KORG Wavestation SR**, written in Java.

Harmonia lets you control every setting of the Wavestation SR from your computer: load and save sounds, and edit performances, patches, wave sequences and effects **in real time** while playing a MIDI keyboard, in a modern interface inspired by the front panel of the device.

![Harmonia main window](docs/images/harmonia-screenshot.png)

*Current state of the user interface (phase 5, mock data: nothing is sent to the device yet).*

## Project status

🚧 **Being rebuilt.** The user interface shell is in place; the MIDI layer and the Wavestation model come next. The full roadmap is in [`docs/DESIGN_ROADMAP.md`](docs/DESIGN_ROADMAP.md) *(in French, English translation in progress)*.

| Phase | Content | Status |
|-------|---------|--------|
| P0 | Repository cleanup | ✅ |
| P1 | Maven build + GitHub Actions CI | ✅ |
| P2 | Reliable MIDI layer (SysEx, send queue, device detection) | ⏳ |
| P3 | Wavestation model and codecs | ⏳ |
| P4 | Librarian (banks, `.syx` files) | ⏳ |
| P5 | UI shell, theme and front panel | 🔨 in progress (branch `phase5-ui-shell`) |
| P6 | Real-time patch editor | ⏳ |
| P7 | Performances, effects, multisets | ⏳ |
| P8 | Wave sequences | ⏳ |
| P9 | Polish and 1.0 release | ⏳ |

## Requirements

- JDK 21 or later, Maven 3.6.3 or later
- A KORG Wavestation SR connected to the computer through a MIDI interface (IN and OUT)
- On the SR: a known basic channel, **MIDI › Param** set to `RECEIVE` or `RECV & TRANS`, **Memory Protect** off to write to RAM

## Build and run

```
mvn install
mvn -pl harmonia-ui exec:java
```

In Eclipse: *File › Import › Maven › Existing Maven Projects*, select the root folder (5 projects are imported), then run `com.harmonia.ui.HarmoniaApp` from the `harmonia-ui` module.

The former proof of concept is still available: `com.harmonia.wavestation.gui.WaveStationEditor`.

## Using the interface

The front panel drawn at the top of the window is functional: its buttons work as on the device, and the LCD shows what the device would display.

| Action | Front panel button | Keyboard |
|--------|--------------------|----------|
| Change section | PERF, MULTI, MIDI/GLOBAL | Ctrl+1 … Ctrl+7 |
| Next / previous page | PAGE+ / PAGE− | Ctrl+PgDn / Ctrl+PgUp |
| Go down one level (performance → patch → wave sequence) | EDIT | Ctrl+E |
| Value +1 / −1 (or next / previous sound) | +1/YES, −1/NO | Arrow keys on the selected knob |
| Next bank | BANK | Ctrl+B |
| Write | WRITE/COMPARE | Ctrl+Shift+W |
| Next / previous wave | Cursor up / down | — |
| Next / previous parameter | Cursor left / right | Tab / Shift+Tab |

- **Knobs**: drag up or down (Shift for fine steps), mouse wheel, double-click for the default value, PgUp/PgDn for ±10.
- **Vector mix pad**: drag the 5 points of the mix envelope; double-click a point to centre it (equal mix); keys 0–4 select a point and the arrow keys move it. The mix percentages A/B/C/D are shown under the pad.
- **Envelopes**: drag a breakpoint vertically to change its level, horizontally to change the time of the segment that leads to it. The knobs follow, and vice versa.

**Logo**: Harmonia does not ship the Korg logo. To show a logo on the front panel, put your own image in `~/.harmonia/logo.png` (on Windows: `C:\Users\<you>\.harmonia\logo.png`).

## Modules

| Module | Role |
|--------|------|
| `harmonia-midi` | MIDI transport: ports, reception, SysEx |
| `harmonia-wavestation` | Wavestation SR profile: model, SysEx codecs, `.syx` files |
| `harmonia-app` | Application services: session, undo, library |
| `harmonia-ui` | Swing + FlatLaf user interface |

## Documentation

- [Analysis, design and roadmap](docs/DESIGN_ROADMAP.md) *(French)*
- The official Korg manuals (Player's Guide, Reference Guide, Performance Notes) are copyrighted: keep them locally in `docs/reference/`, a folder ignored by Git.

## Acknowledgements

- [Edisyn](https://github.com/eclab/edisyn) by Sean Luke, a valuable reference for the Wavestation SR SysEx implementation.

## License

[Apache License 2.0](LICENSE)

---

*Harmonia is not affiliated with KORG Inc. "KORG" and "Wavestation" are trademarks of their respective owners.*
