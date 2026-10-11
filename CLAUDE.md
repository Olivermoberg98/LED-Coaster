# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

Three loosely-coupled parts, no shared build:

- `Software/LED_coaster/` — ESP32-C3 firmware (PlatformIO + Arduino framework, FastLED, NimBLE-Arduino). The coaster itself.
- `Software/App/Coaster_app/` — Android app (Kotlin, Jetpack Compose) that drives coasters over BLE GATT. Its own guide is `Software/App/Coaster_app/CLAUDE.md`.
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
./gradlew test --tests "com.olivermoberg.ledcoaster.games.GamesTest"   # single test class
./gradlew connectedAndroidTest                             # instrumented, needs a device
./gradlew lint
```
JVM tests: `PacketsTest` freezes the exact Package 1/2 bytes and `GamesTest` runs the games in virtual time. `lint` passes against `app/lint-baseline.xml`. minSdk 31 / targetSdk 34, applicationId `com.olivermoberg.ledcoaster`.

## The BLE contract (the thing that spans both halves)

App and firmware agree on a hand-rolled binary protocol: app → coaster over a writable GATT characteristic, coaster → app over a notify characteristic (Package 3, below). Changing either side without the other silently breaks things — the firmware just logs "Checksum mismatch" and drops the packet.

- Service UUID `00001801-0000-1000-8000-008051234567`, characteristic `00001234-0000-1000-8000-001122334455`. Hardcoded in **two** places: [BLEHandler.cpp](Software/LED_coaster/src/BLEHandler.cpp#L24-L28) and [CoasterUuids.kt](Software/App/Coaster_app/app/src/main/java/com/olivermoberg/ledcoaster/protocol/CoasterUuids.kt), the app's only copy, which also holds the Package 3 status UUID.
- **Package 1** (ring enable): `[0x01, outerChecked, innerChecked, checksum]`, checksum = sum of the *first three* bytes % 256.
- **Package 2** (pattern + color): `[0x02] + "PATTERN" + 0x2C(',') + "R,G,B" + checksum`, checksum = sum of *all preceding* bytes % 256. Pattern and color arrive as ASCII, parsed with `find(',')`/`stoi`.
- `CharacteristicCallbacks::onWrite` calls both `handlePackage1` and `handlePackage2` on every write; each returns early if the command byte doesn't match. Neither validates length before indexing.
- Pattern name strings must match on both sides: `stringToPatternType` in [patterns.cpp](Software/LED_coaster/src/patterns.cpp#L12-L24) vs the `dropdown_items` array in [arrays.xml](Software/App/Coaster_app/app/src/main/res/values/arrays.xml) and the app's `Pattern` enum (a unit test checks that the two agree). Unknown names fall back to `FIXED` rather than erroring.

### Coaster → app: battery status (Package 3)

The coaster pushes battery status on a second characteristic of the same
service, `00001235-0000-1000-8000-001122334455`, READ | NOTIFY. READ always
returns the latest packet. The packet is 13 bytes, multi-byte fields
little-endian, built in `updateBatteryStatus()` in [main.cpp](Software/LED_coaster/src/main.cpp):

| Byte | Field | Type | Values |
|---|---|---|---|
| 0 | Type | u8 | `0x03` |
| 1 | Version | u8 | `0x01`; the app ignores unknown versions |
| 2–3 | Battery voltage | u16 | mV |
| 4 | Percent | u8 | 0–100; `0xFF` = unknown |
| 5 | Charger state | u8 | enum below |
| 6 | Flags | u8 | bit 0 low battery, bit 1 USB power present, bit 2 fake data, bits 3–7 = 0 |
| 7 | Raw status pins | u8 | bit 0 `PG`, bit 1 `STAT1`, bit 2 `STAT2` (pin levels) |
| 8–11 | Uptime | u32 | seconds since boot; a drop means the coaster reset |
| 12 | Checksum | u8 | sum of bytes 0–11 % 256 |

Charger state: 0 unknown, 1 on battery, 2 charging, 3 charge complete,
4 low battery (LBO), 5 temperature fault, 6 no battery present — the
`ChargerState` enum in `main.cpp`, decoded from the pins as in the table under
"Battery and charger reporting". The reported state only changes after a new
reading holds for two consecutive 2 s reads.

The low-battery flag is set by the firmware, not the app: LBO **or** below
`LOW_BATTERY_WARN_MV` (3580 mV, about 15% on the fitted curve).

Pushes are sent from `loop()`, never from a NimBLE callback: when a client
subscribes, every 30 s while connected, and immediately when the reported
charger state changes. The values themselves refresh every 2 s. At the
firmware cut-off (3300 mV) the coaster pushes one last packet and goes silent in
deep sleep, so the app sees an ordinary disconnect.

The standard Battery Service `0x180F` / Battery Level `0x2A19` (READ | NOTIFY,
u8 percent) carries the same percent and is pushed together with Package 3, so
generic tools such as nRF Connect show it without a decoder. Package 3 is the
app's source of truth.

`pio run -e battery-fake -t upload` flashes a build where Package 3 cycles
through every charger state and a falling voltage, one step per 5 s, with flag
bit 2 set — for testing the app's battery UI without real charger events.

## Firmware architecture

- Two WS2812-protocol rings driven independently: inner = GPIO0/10 LEDs, outer =
  GPIO1/20 LEDs (`patterns.h`). The fitted part is **not** a WS2812B — it is
  TUOZHAN **TZ-5050S2RGB-5V-I4-H1** (`C26167850`, TZ2812 die). It was chosen
  because every WS2812B is MSL 5a and rated 240 °C peak, which JLCPCB's 255 °C
  Economic reflow rejects; the TZ part is MSL 3 and rated 250 °C. It is a drop-in:
  same 1=VDD 2=DOUT 3=GND 4=DIN pinout, **GRB** order, 800 kbit/s, and bit timing
  (T0H 0.2–0.35 µs, T1H 0.55–1.2 µs) that FastLED's 250/875 ns `WS2812` driver sits
  inside — so `addLeds<WS2812, ..., GRB>` is correct and needs no change. Two
  differences that matter: **reset is 80 µs** (not the 280 µs of newer WS2812B
  revisions), so the tight `FastLED.show()` loop needs no added delay; and drive is
  **12 mA/channel**, not ~20. Each ring has a `colors_*` source array (the user's chosen color, replicated per pixel) and a `led_output_*` array (what patterns actually write, after brightness modulation). Patterns take `ledsIn`/`ledsOut` for exactly this reason.
- **`inner_needs_update` / `outer_needs_update`** exist because `FIXED` is a static frame — the main loop skips re-rendering it (just `delay(50)`) until something sets the flag: a new packet, a connect transition, or a ring being re-enabled. Any new code path that changes ring colors must set these or the change won't appear.
- `BLEHandler` runs a `ConnectionState` machine (`DISCONNECTED → CONNECTING → CONNECTED → DISCONNECTING`). Pattern processing only runs while `CONNECTED` (`shouldProcessPatterns()`), so the loop body in `main.cpp` is dead until a phone connects. The `CONNECTING` state exists solely to fire `onConnectPattern` once.
- Power saving is deliberate and easy to undo by accident: TX power `ESP_PWR_LVL_N0`, advertising at 0.5–1 s (800–1600 units) for `ADVERTISING_TIMEOUT_MS` (2 min) after boot or a disconnect, then at 2.0–2.2 s (3200–3520) until a central connects — it never stops while unconnected, because the app reconnects to known coasters by address, and `esp_pm_configure` enables automatic light sleep (10–160 MHz). Note `esp_pm_config_esp32c3_t` is C3-specific — porting to another chip requires changing it.
- **Each physical coaster needs a unique `coasterID`**, hardcoded at [main.cpp:12](Software/LED_coaster/src/main.cpp#L12). It becomes the advertised name `Coaster-<id>`, and the app recovers the ID with `substringAfterLast('-')` (`CoasterConnection.coasterId`) to label the game circles.

## Android app architecture

Details are in `Software/App/Coaster_app/CLAUDE.md`. What matters across the BLE contract:

- **One connection per coaster, shared by both screens.** `CoasterRepository`
  (app-scoped, held by `CoasterApp`) hands out a `CoasterConnection` per MAC
  address. Each wraps a Nordic Android-BLE-Library `BleManager`, so every GATT
  operation goes through one request queue. Writes use `WRITE_TYPE_DEFAULT`
  (with response); there is no bonding.
- **Packet encoding exists once**, in `protocol/Packets.kt`, and is locked
  byte-for-byte by `PacketsTest`.
- **Discovery** is a BLE scan filtered on the service UUID, so the UUID must
  stay in the primary advertising packet; the name comes from the scan
  response. Saved coasters are address → name in the `BluetoothDevices`
  SharedPreferences.
- **Reconnecting relies on the coaster never stopping advertising.** The app
  connects saved coasters by address when one is dropped on a game circle.
  Direct connects time out after 15 s.
- **Connections outlive screens.** Leaving the games screen keeps coasters
  connected. They drop when unassigned from a circle, on Disconnect, or when
  the user leaves the app.
- **Games** (`games/NattDuellen`, `games/RandomDrink`) are coroutines in
  `GameViewModel`. They turn a coaster "off" with `FIXED` + `"0,0,0"`, not
  Package 1, and each colour write waits for the coaster's acknowledgement.
- Package 3 is decoded into `CoasterConnection.batteryStatus` and shown as a
  status line on the main screen and a percent on each game circle, with a
  low-battery Snackbar.

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

## Bring-up status of the fabricated board (first boards, 2026-09-30)

The first JLCPCB boards are in hand and partially hand-soldered. What has been
verified on real hardware, and what is still untested because the parts are not
fitted yet:

**Working.** The ESP32-C3 boots and enumerates over the USB-C connector alone as
`USB Serial Device`, VID `303A` / PID `1001` (USB-Serial-JTAG). `pio run -t
upload --upload-port COMx` flashes and verifies. `Serial` logging over that same
connector works — automatic light sleep does *not* kill the CDC output, so the
`esp_pm_configure` call and USB logging coexist fine. BLE advertises, a phone
connects, and the full `DISCONNECTED → CONNECTING → CONNECTED → DISCONNECTING`
cycle runs. Both packages arrive and parse with no checksum mismatches. The
**inner ring (`small_ring`, GPIO0, 10 LEDs)** lights and changes colour and
pattern from the app — so WS2812 timing, the `GRB` order and the whole app →
BLE → pattern → LED path are all good.

Because the ESP32 enumerated at all, the power path is also confirmed: `SW1` is
on, `Q1` (the LED-rail P-FET) conducts, and `U4` (the LDO, whose enable follows
that rail) is up.

**Battery and charger — verified 2026-10-08.** `J1` is the only back-side part
fitted (JST-EH 2.5 mm, **pin 1 = GND, pin 2 = `+BATT`**; `U3` has no
reverse-polarity protection, so check a new battery lead with a meter first).
With a protected 1S LiPo:

- Battery only, SW1 off: `+BATT`/`+SYS` at cell voltage, `+LED_PWR` and `+3V3`
  at 0 V. SW1 on: the ESP32 boots on battery, the app connects, the inner ring
  lights.
- USB in: the cell voltage jumps ~110 mV and charging starts with SW1 on or off
  (`U3` sits upstream of the switch). `U3` gets very hot to the touch — expected,
  it dissipates (5 V − V_bat) × 0.5 A ≈ 0.75 W at a low cell — but it never
  thermally faulted or dropped out of `charging`.
- The full `charging` → `charge complete` sequence was observed on
  `STAT1`/`STAT2`/`PG`, decoded by the firmware (see the TODO section).
- Pulling USB while connected with the ring lit: the coaster carried on, on
  battery, with no reset.

**Untested — parts not fitted.** The rest of the back side, including the
**outer ring (`large_ring`, GPIO1, 20 LEDs)** and its decoupling caps. The
`low battery` (LBO) and `temperature fault` states have not been seen, and no
run-down on battery has been done. The plan is to finish battery reporting
before soldering the back side.

Net/pin agreement between the fabricated board and the firmware was checked
against `LED_Coaster.kicad_sch` and matches: `small_ring` → IO0, `large_ring` →
IO1, `BAT_SENSE` → IO4, `STAT1`/`STAT2`/`PG` → IO5/IO6/IO10, `D34` → IO7.

Gotchas that cost time during bring-up:

- **Advertising slows down after 2 minutes** (`ADVERTISING_TIMEOUT_MS`), from
  0.5–1 s to 2.0–2.2 s, but never stops while unconnected. BLEHandler owns every
  restart: NimBLE's own advertise-on-disconnect is turned off, and the
  advertising data is set once in `begin()`.
- **The boot log is unreachable in practice.** A chip reset re-enumerates the
  USB CDC device, which invalidates any open port handle, and re-attach takes
  longer than it takes `setup()` to finish. So `Started Advertising (low power
  mode)` is already gone by the time a monitor can attach. Diagnose with later
  prints instead.
- **Patterns only run while connected** (`shouldProcessPatterns()`), so the
  rings stay dark on USB power alone. An LED check needs the app connected;
  there is no boot self-test.
- **LED draw is capped at 900 mA** in `setup()`
  (`setMaxPowerInVoltsAndMilliamps`), and the cap must stay. At 12 mA/channel all 30 TZ-5050S2RGB at full white draw **1.08 A** (a WS2812B
  build would have been 1.8 A), ~1.17 A with the ESP32 on BLE. That still
  exceeds the <1 A system load the MCP73871 datasheet recommends, and the sag
  is what bites: 200 mΩ BAT→SYS plus ~65 mΩ through `Q1` drops ~306 mV, so at
  a 3.6 V cell `+SYS` falls to 3.37 V and **`U4` drops out — the ESP32 resets
  mid-use**. The 900 mA cap holds `+SYS` at 3.40 V down to a 3.6 V cell and
  costs ~17% of peak white, which is barely visible. It is untested under load:
  with only the 10-LED inner ring fitted (0.36 A) it never engages. Below a
  3.60 V cell `updateLedCap()` lowers the cap linearly to 300 mA at 3.40 V
  (re-applied only after a 50 mV move, and it re-renders FIXED frames). All
  cap figures are FastLED's own current estimate, which assumes WS2812B-like
  draw rather than the TZ part's 12 mA/channel, not a measured current.
- **`coasterID` is hardcoded** at [main.cpp:12](Software/LED_coaster/src/main.cpp#L12).
  The first board was flashed as `05`, so it advertises `Coaster-05`; its base
  MAC is `f8:5b:1b:eb:1a:14`. Give every further board its own ID before
  flashing.

## Battery and charger reporting (firmware reads it; app shows it)

**Hardware — done and verified.** A 470k/470k divider runs from `+BATT` to
**GPIO4** (`ADC1_CH4`), buffered by C21 100nF, halving the cell so 3.0–4.2 V
arrives as 1.5–2.1 V. `U3`'s three open-drain status outputs go to the ESP32 —
`STAT1`/`LBO` → **GPIO5**, `STAT2` → **GPIO6**, `PG` → **GPIO10**. The status
LEDs `D33`/`D35` and `R5`/`R16` are gone from the board; they must never come
back, because they pulled `STAT1`/`STAT2` to `+5V`, which would destroy a 3.6 V-max
ESP32 pin. The charge indicator is instead **`D34`** on the front, driven by the
firmware from **GPIO7** (U1 pin 6, through `R12` 1k to GND, ~1.3 mA):
`updateChargeLed()` shows the debounced charger state — on while charging,
1 s blink at charge complete, 5 Hz blink on temperature fault or no battery,
off on battery so it costs nothing there.

**Firmware — done, serial and BLE.** `main.cpp` prints a line every 2 s
from `loop()`, whether or not a phone is connected:
`Battery: 3794 mV, 37%, charger: charging (PG=0 STAT1=0 STAT2=1)`.

- `readBatteryMillivolts()` averages 16 `analogReadMilliVolts(4)` readings and
  doubles them. In this Arduino core (2.0.17) that call already applies the
  `esp_adc_cal` calibration; attenuation is set to `ADC_11db` in `setup()`.
  Readings are stable to ±2 mV.
- `batteryPercent()` interpolates a table fitted to this cell from the
  2026-10-10 run-down below, with 0% at 3.30 V. It was measured under a steady
  ~150 mA load, so it reads somewhat optimistic at idle (no LEDs, higher
  terminal voltage) and high while charging.
- `chargerState()` reads the three pins as `INPUT_PULLUP` (High-Z reads HIGH)
  and decodes per the MCP73871 datasheet Table 5-1:

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

**First full charge, 2026-10-08** (firmware readings, SW1 on, phone not
connected, ESP32 idle on `+SYS`). This is a *charging* curve — terminal voltage
under ~0.5 A — so it cannot be used directly as a state-of-charge table:

| min | mV | min | mV | min | mV |
|---|---|---|---|---|---|
| 0 | 3798 | 50 | 3994 | 100 | 4142 |
| 10 | 3884 | 60 | 4028 | 110 | 4172 |
| 20 | 3922 | 70 | 4074 | 120 | 4180 |
| 30 | 3946 | 80 | 4110 | ~130 | 4192 |
| 40 | 3970 | 90 | 4126 | end | 4180, `charge complete` |

The cell started at 3.49 V resting. It was ~3.6 V once USB was in, before the
firmware was flashed; the first logged reading is 3798 mV.

**First run-down, 2026-10-10** (`rundown` build, full charge, phone not
connected, inner ring at a constant dim white). 378 one-minute samples, no
resets, ended by the build's own 3.2 V stop after 6.3 h:

| h | mV | h | mV |
|---|---|---|---|
| 0 | 4152 | 4 | 3790 |
| 1 | 4008 | 5 | 3636 |
| 2 | 3944 | 5.83 | 3498, first below 3500 |
| 3 | 3858 | 6.24 | 3300 (0%) |

Percent was taken as remaining time to 3.30 V, which holds at constant load.
The knee is at ~3.5 V: below it only ~25 minutes remain. Capacity to 3.30 V is
**~935 mAh assuming a ~150 mA load** (ring ~120 mA + ESP32 ~30 mA, estimated,
not measured), so the 1500 mAh cell is at roughly 60% of its rating. `LBO`
was never reached.

To repeat it: `pio run -e rundown -t upload`. That build lights the inner ring
at `CRGB(85, 85, 85)` from boot, appends `uptime_s, mv, charger_state, flags`
(8 bytes) to `/rundown.bin` on LittleFS once a minute while not on USB, and
below 3.2 V for three readings writes a final sample with flag `0x80`, clears
the LEDs and deep-sleeps (wake it with SW1 off/on). Over USB serial, `dump`
prints the log as CSV and `clear` wipes it; clear before starting a run.

Tools and data live in `Software/LED_coaster/tools/rundown/`:
`rundown_dump.py [COM6]` saves the log to `runs/<date>.csv` (needs pyserial;
PlatformIO's Python has it), and `rundown_fit.py runs/<date>.csv --compare
runs/2026-10-10.csv` prints the run summary and a drop-in `batteryPercent()`
table and saves a plot next to the CSV, with earlier runs overlaid. Keep every
run's CSV and plot in `runs/` so a weakening cell shows up over time.

Still to do:

- **Voltage accuracy — accepted without a correction.** At `charge complete`
  the firmware read 4180 mV against 4.09 V on a multimeter at `C7` pad 1,
  probably not taken at the same moment. No simultaneous pairs were taken.
  The fitted table and thresholds are all in firmware millivolts, so they stay
  self-consistent; only absolute voltages are uncertain, by up to ~90 mV. If a
  correction is ever added in `readBatteryMillivolts()`, shift the table,
  `LOW_BATTERY_WARN_MV` and the planned cut-off by the same amount.
- **Percentage curve — done** from the 2026-10-10 run-down above. Refit on a
  new cell or a second board with the `rundown` build.
- **BLE status path — firmware side done.** The coaster publishes Package 3;
  see "Coaster → app: battery status" under "The BLE contract".
- **App — done.** `protocol/` decodes Package 3 into
  `CoasterConnection.batteryStatus` (a StateFlow, null until the first valid
  packet). The main screen shows a status line, each game circle a percent,
  and a low battery raises a Snackbar; see `Software/App/Coaster_app/CLAUDE.md`.
  Run-down logging for the percentage fit is expected on the firmware side
  (see "Percentage curve" above); the app has no logger.

Notes that matter for firmware:

- SW1 no longer cuts power on its own — it gates a P-FET that switches the LED
  rail, and the LDO's enable pin follows that rail, so the ESP32 does lose power
  when the switch is off. There is no graceful-shutdown hook; power just goes.
- The board has no hardware low-battery cut-off; `LBO` only reports. The
  firmware stops instead: below `CUTOFF_MV` (3300 mV, 0% on the fitted curve)
  for three readings on battery, `checkCutoff()` pushes a last Package 3, turns
  the LEDs off and deep-sleeps with no wake source; SW1 off/on restarts it.
  Deep sleep still draws a little (ESP32, LDO, charger ~30 µA, divider), so the
  cell keeps draining slowly until switched off. Always use a protected cell.
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
  keeping system load under 1 A. All 30 TZ-5050S2RGB at full white is ~1.08 A,
  ~1.17 A with the ESP32 — still over, and it sags `+SYS` enough to drop `U4`
  out below a ~3.7 V cell. Global brightness limiting in firmware is the
  practical mitigation; see the bring-up note above for the numbers.

## My working preferences

- Keep responses concise, no unnecessary explanation.
- Prefer minimal diffs — don't refactor or reformat code beyond what's asked.
- Never create new files unless explicitly requested.
- Ask before adding new dependencies.