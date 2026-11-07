# LED Coaster

[![CI](https://github.com/Olivermoberg98/LED-Coaster/actions/workflows/ci.yml/badge.svg)](https://github.com/Olivermoberg98/LED-Coaster/actions/workflows/ci.yml)

A battery-powered drink coaster with two rings of addressable RGB LEDs, controlled
from an Android phone over Bluetooth LE. The repository holds the whole project:
ESP32-C3 firmware, the Android app, the KiCad board and the 3D-printed enclosure.

- **Coaster:** 90 mm round board, ESP32-C3, 10 LEDs on the front and 20 around
  the back, a 1S Li-Po charged over USB-C, and a slide switch.
- **App:** finds coasters, sets each ring's pattern (fixed, pulse, chaser,
  rainbow) and colour, shows battery and charge state, and runs party games
  across several coasters at once.
- **Firmware:** reports battery voltage, percentage and charger state to the
  app, caps LED current as the cell runs down, and shuts off before the cell is
  empty.

## How it fits together

```
 Android app  ── BLE write:  Package 1 (rings on/off), Package 2 (pattern + colour) ──▶  coaster
              ◀── BLE notify: Package 3 (battery voltage, %, charger state) ──────────
```

The coaster is a GATT server with one command characteristic and one status
characteristic. Every packet carries a one-byte checksum, and the firmware drops
any packet that fails to parse. Byte layouts, UUIDs and the rules both sides
must keep in step are in [CLAUDE.md](CLAUDE.md#the-ble-contract-the-thing-that-spans-both-halves).

| Folder | Contents |
|---|---|
| [`Software/LED_coaster/`](Software/LED_coaster/) | Firmware: PlatformIO, Arduino, FastLED, NimBLE |
| [`Software/App/Coaster_app/`](Software/App/Coaster_app/) | Android app: Kotlin, Jetpack Compose, Nordic BLE library |
| [`Hardware/PCB/`](Hardware/PCB/) | KiCad project, JLCPCB production files, board checker; see its [README](Hardware/PCB/README.md) |
| [`Hardware/3d_printing/`](Hardware/3d_printing/) | Enclosure: STEP source plus print-ready STL/3MF for the top shell and bottom piece |

[CLAUDE.md](CLAUDE.md) and [the app's CLAUDE.md](Software/App/Coaster_app/CLAUDE.md)
are the detailed developer guides: architecture, protocol, power budget,
bring-up measurements and the battery discharge curve. They are written as
instructions for the Claude Code assistant but read fine as engineering notes.

## Building

### Firmware

Needs [PlatformIO](https://platformio.org/install/cli). Flashing and the serial
log both go over the coaster's USB-C port; no adapter is needed.

```sh
cd Software/LED_coaster
pio run                  # build
pio run -t upload        # flash
pio device monitor       # serial log
```

Every coaster needs its own ID. Set `coasterID` in
[`src/main.cpp`](Software/LED_coaster/src/main.cpp) before flashing each board.
It becomes the Bluetooth name `Coaster-<id>`. Two extra environments exist for
testing: `battery-fake` cycles through every battery and charger state for app
development, and `rundown` logs a full discharge to flash for fitting the
battery curve.

### Android app

Needs Android Studio, or the Android SDK with JDK 17. The app runs on
Android 12 (API 31) and newer, and needs a real phone, because the emulator has
no Bluetooth.

```sh
cd Software/App/Coaster_app
./gradlew assembleDebug  # build (gradlew.bat on Windows)
./gradlew installDebug   # install on a connected phone
./gradlew test lint      # unit tests and lint
```

### PCB

Open `Hardware/PCB/LED_Coaster.kicad_pro` in KiCad 10. The board was assembled
by JLCPCB from the files in `production/`, with the back side soldered by
hand. [`Hardware/PCB/README.md`](Hardware/PCB/README.md) covers the design
rules, part choices and assembly.

### Enclosure

The STL and 3MF files are already oriented for printing; the top shell prints
top face down. The coasters use black PLA, so use PETG if one must take hot
drinks. For the top shell's 0.5 mm LED slits, print at 0.12 mm layers and
keep X-Y hole compensation at 0, because the board is a deliberately snug fit.
Clear tape inside the top shell keeps condensation out of the slits.

## Using it

1. Switch the coaster on. It advertises as `Coaster-<id>`.
2. In the app, tap **New device**, pick the coaster, and it connects. Saved
   coasters reconnect from the dropdown.
3. Choose which rings are on, a pattern and a colour. The rings only light
   while the app is connected.
4. **Go to Games** to place several coasters on circles (long-press and drag),
   then start *Nattduellen* or *Random drink*.

Plug in USB-C to charge. The small LED on the front is on while charging, blinks
slowly when the battery is full, and blinks fast on a fault. A charge from empty
takes a little over 3 hours. Leaving it plugged in is fine: the charger stops
when the battery is full and only starts again once it has dropped to about 90%.

Charge a flat coaster soon. Even switched off, the charger chip draws a little
from the battery, so one left flat drains far enough within weeks for the
battery's own protection to cut it off, and it then reads 0 V. Plugging in USB
usually brings it back. Replace the battery if it is swollen or gets warm while
charging. For storage longer than a few weeks, leave it about half charged.

## Status

Five boards have been made, and the front side is verified on hardware: the
ESP32-C3, USB flashing and logging, BLE control from the app, the inner LED
ring, charging, and battery and charger reporting. A full discharge and a full
charge from empty have been logged to calibrate the percentage and check the
voltage reading. Still to do:

- Solder and verify the outer ring (`D1`–`D20`) and its capacitors.
- Test the full 30-LED load with the firmware's current cap on a part-empty
  cell, where a brown-out would show up.
- See the charger's low-battery and temperature-fault states on real hardware.

## Safety

- The battery connector has no reverse-polarity protection. Check the polarity
  of a new battery lead (pin 1 GND, pin 2 +) before plugging it in.
- Use a **protected** single-cell Li-Po. The firmware's low-voltage cut-off
  does not replace the cell's own protection.
- The charger IC gets hot to the touch while charging (up to about 0.8 W at a
  low cell). That is normal.

## License

[MIT](LICENSE)
