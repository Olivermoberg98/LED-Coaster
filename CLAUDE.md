# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

Three loosely-coupled parts, no shared build:

- `Software/LED_coaster/` — ESP32-C3 firmware (PlatformIO + Arduino framework, FastLED, NimBLE-Arduino). The coaster itself.
- `Software/App/Coaster_app/` — Android app (Kotlin, classic Views + XML layouts, **no Compose**) that drives coasters over BLE GATT.
- `Hardware/PCB/` — KiCad project for the coaster board. `Hardware/PCB/ORDERING.md` is the live checklist for taking the board through layout to a JLCPCB order — check it for current progress before doing PCB work.

`Software/LED_coaster/.pio/` is gitignored build output containing full copies of FastLED and NimBLE-Arduino. Exclude it when searching — it dwarfs the actual source (5 files).

## Build / run

Firmware (from `Software/LED_coaster/`):
```bash
pio run                      # build
pio run -t upload            # flash
pio device monitor           # serial log over USB-C (baud is ignored)
```

`Serial` is the ESP32-C3's USB-Serial-JTAG peripheral, not a UART — `platformio.ini`
sets `ARDUINO_USB_CDC_ON_BOOT=1` and `ARDUINO_USB_MODE=1`, and both are required
(`HWCDC.h` only declares `Serial` inside `#if ARDUINO_USB_MODE`). So flashing and
logging both work over the USB-C connector alone; the `Serial.begin(9600)` in
`main.cpp` keeps its argument only because HWCDC ignores it. UART0 on IO20/IO21 is
still reachable as `Serial0`, wired to the `J4` header — which is DNP, so nothing is
fitted there unless you solder it yourself. Never add `while (!Serial)`: on battery
with no USB host it would hang the coaster forever.

Android app (from `Software/App/Coaster_app/`, use `gradlew.bat` on Windows):
```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew test                                             # JVM unit tests
./gradlew test --tests "com.example.myemptyapp.ExampleUnitTest"   # single test
./gradlew connectedAndroidTest                             # instrumented, needs a device
./gradlew lint
```
Only the stub `Example*Test` files exist; there is no real test suite. minSdk 31 / targetSdk 34.

## The BLE contract (the thing that spans both halves)

App and firmware agree on a hand-rolled binary protocol over a single writable GATT characteristic. Changing either side without the other silently breaks things — the firmware just logs "Checksum mismatch" and drops the packet.

- Service UUID `00001801-0000-1000-8000-008051234567`, characteristic `00001234-0000-1000-8000-001122334455`. Hardcoded in **three** places: [BLEHandler.cpp](Software/LED_coaster/src/BLEHandler.cpp#L24-L28), [MainActivity.kt](Software/App/Coaster_app/app/src/main/java/com/example/myemptyapp/MainActivity.kt#L63-L64), and `CoasterDevice` in [GameActivity.kt](Software/App/Coaster_app/app/src/main/java/com/example/myemptyapp/GameActivity.kt#L559-L560).
- **Package 1** (ring enable): `[0x01, outerChecked, innerChecked, checksum]`, checksum = sum of the *first three* bytes % 256.
- **Package 2** (pattern + color): `[0x02] + "PATTERN" + 0x2C(',') + "R,G,B" + checksum`, checksum = sum of *all preceding* bytes % 256. Pattern and color arrive as ASCII, parsed with `find(',')`/`stoi`.
- `CharacteristicCallbacks::onWrite` calls both `handlePackage1` and `handlePackage2` on every write; each returns early if the command byte doesn't match. Neither validates length before indexing.
- Pattern name strings must match on both sides: `stringToPatternType` in [patterns.cpp](Software/LED_coaster/src/patterns.cpp#L12-L24) vs the `dropdown_items` array in [arrays.xml](Software/App/Coaster_app/app/src/main/res/values/arrays.xml). Unknown names fall back to `FIXED` rather than erroring.

## Firmware architecture

- Two WS2812 rings driven independently: inner = GPIO0/10 LEDs, outer = GPIO1/20 LEDs (`patterns.h`). Each ring has a `colors_*` source array (the user's chosen color, replicated per pixel) and a `led_output_*` array (what patterns actually write, after brightness modulation). Patterns take `ledsIn`/`ledsOut` for exactly this reason.
- **`inner_needs_update` / `outer_needs_update`** exist because `FIXED` is a static frame — the main loop skips re-rendering it (just `delay(50)`) until something sets the flag: a new packet, a connect transition, or a ring being re-enabled. Any new code path that changes ring colors must set these or the change won't appear.
- `BLEHandler` runs a `ConnectionState` machine (`DISCONNECTED → CONNECTING → CONNECTED → DISCONNECTING`). Pattern processing only runs while `CONNECTED` (`shouldProcessPatterns()`), so the loop body in `main.cpp` is dead until a phone connects. The `CONNECTING` state exists solely to fire `onConnectPattern` once.
- Power saving is deliberate and easy to undo by accident: TX power `ESP_PWR_LVL_N0`, slow advertising intervals (800–1600), advertising self-stops after `ADVERTISING_TIMEOUT_MS` (2 min) if nobody connects, and `esp_pm_configure` enables automatic light sleep (10–160 MHz). Note `esp_pm_config_esp32c3_t` is C3-specific — porting to another chip requires changing it.
- **Each physical coaster needs a unique `coasterID`**, hardcoded at [main.cpp:12](Software/LED_coaster/src/main.cpp#L12). It becomes the advertised name `Coaster-<id>`, and `GameActivity` recovers the ID with `substringAfterLast('-')` to label the circles.

## Android app architecture

Two activities, two *different* BLE connection models:

- **`MainActivity`** — single-coaster control. Classic discovery (`BluetoothAdapter.startDiscovery()` + a `BroadcastReceiver` on `ACTION_FOUND`), one `bluetoothGatt`/`targetCharacteristic` pair held directly on the activity. Known devices are persisted as address→name in the `BluetoothDevices` SharedPreferences and re-offered in a spinner. Selecting a pattern in the mode spinner shows/hides color-picker buttons; picking a color sends Package 2.
- **`GameActivity`** — multi-coaster. Receives `"name|address"` strings via the `connectedDevices` intent extra, wraps each in a **`CoasterDevice`** (defined at the bottom of `GameActivity.kt`) that owns its own GATT connection and its own `sendPackage1`/`sendPackage2`. Devices are drag-and-dropped from a RecyclerView onto dynamically laid-out circles; `ringDeviceMap` (circle position → device) and `assignedDevices` track placement, and reducing the circle count disconnects the devices that fall off the end.
- Games (`nattDuellen`, `drinkGame`) are `Handler.postDelayed` chains, with `currentGameRunnable` as the single cancel handle. They turn a coaster "off" by sending `FIXED` with `"0,0,0"` rather than by disabling rings via Package 1.
- `CoasterDevice.sendPackage*` branch on API 33 for the new vs deprecated `writeCharacteristic` overloads; `MainActivity` only uses the deprecated form.
- `gradle/libs.versions.toml` contains several IDE-generated aliases with the literal version `"your_version_here"` (`*-vyourversionhere`). They are unreferenced — never point a dependency at one.

## PCB analysis tooling — use this before answering questions about the board

`Hardware/PCB/tools/pcb_check.py` reads `LED_Coaster.kicad_pcb` directly. Pure
stdlib Python, no KiCad install needed. **Run it rather than re-deriving board
geometry by hand** — the parsing has several non-obvious traps and getting one
wrong produces confident, wrong answers.

```bash
cd Hardware/PCB/tools
python pcb_check.py nets                  # split nets + stranded pads
python pcb_check.py net "+LED_PWR"        # one net: groups and every copper item
python pcb_check.py shorts                # foreign copper overlapping a pad
python pcb_check.py rings                 # LED chain continuity, ring uniformity
python pcb_check.py zones                 # zone net/layer/priority/fill state
python pcb_check.py widths                # track widths actually used per net
python pcb_check.py placement             # positions, angles, sides, off-board
python pcb_check.py all
```

KiCad's DRC remains the authority on manufacturability. This answers the
questions DRC doesn't — *is this net actually one island, and which pad is
stranded* — and answers them without opening the GUI.

**Traps it encodes** (the header comment in the file lists all six):

- **Back-side pads are not X-mirrored.** `B.Cu` footprints use the same
  local→board transform as `F.Cu`. Mirroring swaps pad 3 for pad 4 and invents
  shorts that do not exist. This one caused a long chain of wrong conclusions —
  it made a uniform LED ring look like it fanned through five orientations.
- A track end inside a **via's pad radius** is connected, not merely near it.
- Zone fills carry a **per-polygon layer** tag; one zone can span both sides.
- Two same-net zones whose **fills touch are one island**; priority decides
  overlaps, so a higher-priority zone can orphan a lower one.
- Pads **sharing a number** on one footprint are internally connected.
- Fine-pitch rectangular pads must not be approximated by their circumradius.

Board constants (outline centre and radius) are at the top of the file; update
them if the outline ever changes.

## TODO: battery and charger reporting (software not started)

Two hardware paths feed this and **neither is implemented in firmware or app**.

**Battery level — hardware done.** A 470k/470k divider runs from `+BATT` to
**GPIO4** (`ADC1_CH4`), buffered by C21 100nF, halving the cell so 3.0–4.2 V
arrives as 1.5–2.1 V.

**Charger status — hardware specified, not yet routed.** `Hardware/PCB/ORDERING.md`
Phase 5 §10 is the spec: `U3`'s three open-drain status outputs go to the ESP32 —
`STAT1`/`LBO` → **GPIO5**, `STAT2` → **GPIO6**, `PG` → **GPIO10** — and the status
LEDs `D33`/`D35` with `R5`/`R16` are deleted. Deleting them is required, not
cosmetic: they pull `STAT1`/`STAT2` to `+5V`, which would destroy a 3.6 V-max
ESP32 pin.

Still to do:

- **Firmware** (`Software/LED_coaster/`): read GPIO4 on ADC1 with
  `ADC_ATTEN_DB_12`. The ESP32-C3's ADC has a real offset error — use the
  `esp_adc_cal` / calibration API, not raw counts, or readings will be off by
  tens of mV. Multiply by 2 to recover cell voltage. Average several samples;
  the LED rail is noisy while patterns run. Map voltage to a percentage with a
  Li-Po curve, not a linear 3.0–4.2 V ramp — the curve is very flat from 3.7–4.0 V.
- **Firmware — charger status**: read the three pins as `INPUT_PULLUP`
  (High-Z reads HIGH) and decode per the MCP73871 datasheet Table 5-1:

  | `PG` | `STAT1` | `STAT2` | State |
  |---|---|---|---|
  | L | L | H | charging |
  | L | H | L | charge complete |
  | L | L | L | temperature fault |
  | L | H | H | no battery present |
  | H | L | H | **low battery** (under 3.1 V, LBO) |
  | H | H | H | no input power, running on battery |

  `PG` is not optional — charging and low-battery share the same
  `STAT1`/`STAT2` pair and differ only in `PG`. Note `PG` is *pseudo*
  open-drain with a diode path back to `IN`, so the internal pull-up
  back-feeds ~58 µA into `+5V` when USB is unplugged; harmless, see
  ORDERING.md §10.
- **BLE contract — the blocker for both.** Every package today is
  app→coaster; there is no coaster→app path at all. Adding one means a new
  package type (`0x03`?) on the existing characteristic for the app to read, or
  a second notify-capable characteristic so the coaster can push. Notify suits
  status better. Carry battery level *and* charger state in one package so the
  contract changes once. Whichever is chosen must land in **both** halves at
  once — see "The BLE contract" above; the firmware silently drops mismatched
  packets.
- **App** (`Software/App/Coaster_app/`): surface level and charger state per
  coaster. `GameActivity` already tracks devices individually via
  `CoasterDevice`, so the natural home is a field there plus an indicator on
  each circle.

Notes that matter for firmware:

- SW1 no longer cuts power on its own — it gates a P-FET that switches the LED
  rail, and the LDO's enable pin follows that rail, so the ESP32 does lose power
  when the switch is off. There is no graceful-shutdown hook; power just goes.
- The divider draws ~4.5 µA continuously from the cell even when switched off,
  because it sits on `+BATT` upstream of the switch. That is small next to the
  charger IC's own ~30 µA quiescent draw, but it means the battery does slowly
  drain in storage.
- `U3` is strapped for USB-port mode (`SEL` low), a 500 mA input limit
  (`PROG2` high), 500 mA charge current (`R2` 2k on `PROG1`), 50 mA termination
  (`R15` 20k on `PROG3`), no thermistor (`R14` 10k on `THERM`), and the safety
  timer **disabled** (`TE` high) — so a timer fault never occurs and both
  status outputs low means a temperature fault.
- The MCP73871's internal BAT→SYS path is ~200 mΩ and the datasheet recommends
  keeping system load under 1 A. All 30 WS2812B at full white is ~1.8 A, which
  exceeds that and will sag `+SYS`. Global brightness limiting in firmware is the
  practical mitigation.

## My working preferences

- Keep responses concise, no unnecessary explanation.
- Prefer minimal diffs — don't refactor or reformat code beyond what's asked.
- Never create new files unless explicitly requested.
- Ask before adding new dependencies.