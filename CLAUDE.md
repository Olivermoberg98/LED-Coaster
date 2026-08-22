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
pio device monitor -b 9600   # serial log (Serial.begin(9600) in main.cpp)
```

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

## TODO: battery level reporting (hardware done, software not started)

Hardware support was added on the `fix/charging-and-power-path` branch and is
**not yet implemented in firmware or app**. The board now has a 470k/470k divider
from `+BATT` to **GPIO4** (`ADC1_CH4`), buffered by C21 100nF, halving the cell so
3.0–4.2 V arrives as 1.5–2.1 V.

Still to do:

- **Firmware** (`Software/LED_coaster/`): read GPIO4 on ADC1 with
  `ADC_ATTEN_DB_12`. The ESP32-C3's ADC has a real offset error — use the
  `esp_adc_cal` / calibration API, not raw counts, or readings will be off by
  tens of mV. Multiply by 2 to recover cell voltage. Average several samples;
  the LED rail is noisy while patterns run. Map voltage to a percentage with a
  Li-Po curve, not a linear 3.0–4.2 V ramp — the curve is very flat from 3.7–4.0 V.
- **BLE contract**: there is no way to report this yet. Adding it means a new
  package type (`0x03`?) or a second, notify-capable characteristic. Whichever is
  chosen must land in **both** halves at once — see "The BLE contract" above; the
  firmware silently drops mismatched packets.
- **App** (`Software/App/Coaster_app/`): surface the level per coaster.
  `GameActivity` already tracks devices individually via `CoasterDevice`, so the
  natural home is a field there plus an indicator on each circle.

Notes that matter for firmware:

- SW1 no longer cuts power on its own — it gates a P-FET that switches the LED
  rail, and the LDO's enable pin follows that rail, so the ESP32 does lose power
  when the switch is off. There is no graceful-shutdown hook; power just goes.
- The divider draws ~4.5 µA continuously from the cell even when switched off,
  because it sits on `+BATT` upstream of the switch. That is small next to the
  charger IC's own ~30 µA quiescent draw, but it means the battery does slowly
  drain in storage.
- The MCP73871's internal BAT→SYS path is ~200 mΩ and the datasheet recommends
  keeping system load under 1 A. All 30 WS2812B at full white is ~1.8 A, which
  exceeds that and will sag `+SYS`. Global brightness limiting in firmware is the
  practical mitigation.

## My working preferences

- Keep responses concise, no unnecessary explanation.
- Prefer minimal diffs — don't refactor or reformat code beyond what's asked.
- Never create new files unless explicitly requested.
- Ask before adding new dependencies.