# LED Coaster — PCB layout & JLCPCB ordering checklist

Working checklist for taking the reworked schematic (branch
`fix/charging-and-power-path`) through layout to a JLCPCB order.

**How to use this:** work top to bottom. Tick boxes as you go. The next unticked
box is the next thing to do. Notes and gotchas live under each step — read them
before doing the step, not after.

**Current status:** layout, DRC and production files are done. The order was
then re-scoped on cost: it is now **Economic PCBA, single-sided, front only**
— 20 BOM lines, 47 placed parts, ~$88 against the $172.46 that Standard
double-sided quoted. `D1`–`D30` are `C55109525` (WS2812B-V7, Economic-eligible,
classic pinout), `C1`/`C3`/`C5` are merged onto the 4.7 µF line, and the 32
back-side parts carry `(dnp yes)`.
Next: **Phase 5 §10** — the charger-status rework, which routes `STAT1`/`STAT2`/
`PG` to `IO5`/`IO6`/`IO10` and deletes `D33`/`D35`/`R5`/`R16`. It leaves the BOM
and the quote unchanged and takes stage-1 hand-soldering down to `J1` alone.
Then F8, Phase 6 (DRC), Phase 7 (regenerate `production/`), and quote. Read the
LED pinout block in Phase 1 before substituting any LED — an SK6812 at 0°
destroys every LED on the board.

---

## Board facts (for reference)

| | |
|---|---|
| Outline | 90 mm circle, centre (150, 80) in PCB coordinates |
| Layers | 2 (F.Cu / B.Cu) |
| Components | both sides physically |
| Front (F.Cu) | 51 parts: ESP32 module, USB-C, power section, small ring `D21–D30`, 4 decoupling caps |
| Back (B.Cu) | 33 parts: large ring `D1–D20`, 8 decoupling caps, status LEDs, battery connector `J1` |
| Peak LED current | ~1.8 A theoretical (30 × WS2812B, full white) |
| Last order's assembly | single-sided, front only, 27 parts |
| **This order's assembly** | **Economic, single-sided front only, 47 parts — see below** |

47 is the front side minus `J4` and `BTN1`-`BTN4`, which are unfitted or
hand-soldered. The placeable back-side parts — `C8`–`C15` and `D1`–`D20`, 28 of
them after the Phase 5 §10 rework deletes `D33`/`D35`/`R5`/`R16` — all carry
`(dnp yes)`, and `J1` is hand-soldered as it always was. `H1`/`H3`/`H4` are
mounting holes and never counted.

**Important — this order changes the assembly model.** The previous order was
*single-sided*: JLCPCB fitted 27 front-side parts, and the 30 WS2812Bs, the
ESP32 module `U1`, the battery connector `J1` and the UART header `J4` were
hand-soldered afterwards.

⚠️ **What actually keeps a part out is `(dnp yes)` — nothing else.** An earlier
revision of this file claimed a missing `MPN` field would also do it, and that
such a part "vanishes silently rather than appearing as a blank line". That is
wrong, and it cost a round of regeneration: `J1` had no `MPN`, was not DNP, and
came out in `bom.csv` as a line with an empty `LCSC Part #` — unsourceable, and
exactly the blank line the old text promised could not happen. The Fabrication
Toolkit filters on its `EXCLUDE DNP` option (set `true` in
`fabrication-toolkit-options.json`) and on that alone.

Set DNP in **both** places, the way `J1` and `J4` now are:

- schematic — the symbol carries `(dnp yes)`
- board — the footprint carries `(attr through_hole dnp)`

The schematic is the one that matters, because *Update PCB from Schematic* will
overwrite a board-only change and quietly put the part back in your BOM.

For this order `U1` and the inner ring `D21`–`D30` are machine-placed; the outer
ring `D1`–`D20` is not. Hand-soldering WS2812Bs is the highest-risk work in the
project — the packages melt before the solder flows, and a single damaged LED
kills every LED downstream of it in the data chain with no easy way to identify
which one — but Economic PCBA is single-side only, and Economic saves roughly
$84 against Standard double-sided on a 5-board run. The compromise keeps the
count that must be hand-soldered at 100 rather than 150, and keeps the parts
that genuinely cannot be hand-soldered on the machine: `U3` is a QFN-20 with a
thermal pad underneath.

Still hand-soldered, deliberately:

| Ref | Why |
|---|---|
| `J1` JST EH 2-pin | through-hole — not SMT-placeable |
| `J4` 1×04 header | through-hole, and a debug header you may not fit at all |
| `BTN1`–`BTN4` | `C318884` went out of stock, and the firmware reads none of them. `BTN3`/`BTN4` are the ESP32 boot and reset buttons, which native USB flashing does not need. Any replacement must match the land pattern: two 1.80 × 1.10 mm pads plus two mechanical, in an 8.6 × 9.3 mm envelope |
| `H1`/`H3`/`H4` | mounting holes, not parts |

**Nothing on the back is required for the front to work.** The charging path,
`U1`, the inner ring, `C16`–`C19` + `C20` and the battery-sense divider are all
front-side, so the hand-soldering splits into two stages:

| Stage | Parts | Count | Gets you |
|---|---|---|---|
| 1 | `J1` | **1** | battery, charging with status in firmware, BLE, inner ring |
| 2 | `D1`–`D20`, `C8`–`C15` | 28 | the outer ring |

`C8`–`C15` are `+LED_PWR` decoupling for the outer ring only — the inner ring
has its own in `C16`–`C19` and `C20` — so they wait for stage 2. Leaving
`D1`–`D20` off during stage 1 also holds peak current to a third, which helps
given the MCP73871's 1 A system-load recommendation.

**Charger state is read in firmware, not off LEDs** — see Phase 5 §10.
`STAT1`/`LBO`, `STAT2` and `PG` route to `IO5`, `IO6` and `IO10`, and the two
status LEDs `D33`/`D35` and their resistors `R5`/`R16` are deleted. That is what
takes stage 1 down to a single through-hole connector. `/BAT_SENSE` already
reaches `U1` pin 3 (GPIO4) for the battery percentage — note that voltage alone
cannot distinguish charging from charged from fault, which is the whole reason
the status pins are worth routing. `D34` on `IO7` is a firmware-driven LED on
the front, machine-placed, and is handy for blinking out charger state during
bring-up before the app work lands.

Previous orders used the **Fabrication Toolkit** KiCad plugin — `fabrication-toolkit-options.json`
in this folder is its config, and `production/` holds its last output. Same route
this time if the plugin works on your KiCad version.

---

## Phase 0 — Install and open

- [x] **Install KiCad 10.0.5** (current stable) from <https://www.kicad.org/download/>

  Get the real desktop application. **VS Code extensions are not enough** — see
  the note at the bottom of this file for why.

  During install, accept the default components. You want the libraries and the
  Plugin and Content Manager included (they are on by default).

- [x] **Get the branch**

  ```bash
  cd c:/dev/LED-Coaster
  git checkout fix/charging-and-power-path
  git pull
  ```

- [x] **Open the project**

  Launch KiCad → *File → Open Project* → `Hardware/PCB/LED_Coaster.kicad_pro`

  The files are in KiCad 8 format. KiCad 10 reads them directly and **may not
  say anything about it** — there is not necessarily an upgrade prompt. The
  format is rewritten to v10 the first time you *save*, which is normal.

- [x] **Commit the format upgrade** — done

  The board and schematic are in v10 format (`(version 20260206)`). The rewrite
  went in as part of commit `e196df2` rather than as a standalone commit, so
  that commit's diff is enormous — that is the format rewrite, not design
  changes.

---

## Phase 1 — Check the schematic

You should not need to *change* anything here, only verify and fill in part
numbers. Open the schematic editor (the first icon in the project window).

- [ ] **Look at the redrawn power section**

  It is in the lower-middle of the sheet, well below the original circuitry —
  scroll down, or press <kbd>Home</kbd> to fit the whole sheet. You will find the
  charger `U3`, LDO `U4`, load switch `Q1`, switch `SW1`, battery connector `J1`,
  the status LEDs, the battery-sense divider, and a bank of 12 decoupling caps
  `C8–C19` laid out in a row.

- [x] **Run ERC** — *Inspect → Electrical Rules Checker → Run ERC*

  **0 errors.** 42 warnings remain and all are pre-existing, expected, and
  safe to ignore:

  | Warning | Count | Why it is fine |
  |---|---|---|
  | `endpoint_off_grid` | ~36 | original LED-ring wires sit on an odd grid. Cosmetic; they are connected. Only fix if you ever redraw that area |
  | `lib_symbol_mismatch` on `Device:LED`, `USB_C_Receptacle` | 4 | the board was drawn with KiCad 8 libraries and you now have KiCad 10 ones. The cached copy in the schematic is what gets used |
  | `lib_symbol_issues` / `footprint_link_issues` on `ESP32-C3-WROOM-02-H4` | 2 | that library lives on your friend's machine, not in this repo. The symbol is cached in the schematic and the footprint is embedded in the board, so nothing is missing — but see the note below |

  Everything that came from the power-path rework has been cleared.

  **Note on the ESP32 library:** the repo is not self-contained for `U1`. It
  works today because both the symbol and footprint are already baked into these
  files, but nobody could add a second ESP32 module without that library. Worth
  fixing at some point by extracting them into `Library.pretty/` and
  `LED_Coaster.kicad_sym`. Not blocking the order.

- [x] **Fill in the missing LCSC part numbers** — done, all six set

  **There is no BOM file to edit.** `production/bom.csv` is *output* from the
  last order and gets overwritten in Phase 7. The part number lives on the
  symbol in the schematic, in a field called **`MPN`**, and the BOM is generated
  from that. A part with an empty `MPN` is *silently left out of the BOM* — it
  does not appear as a blank line, it just vanishes. That is how the WS2812Bs
  and the ESP32 module were excluded last time (deliberately, they were
  hand-soldered).

  | Ref | Value | Part | Verified as |
  |---|---|---|---|
  | `R13`, `R17` | 100k 0805 | `C149504` | `0805W8F1003T5E` |
  | `R15` | 20k 0805 | `C4328` | `0805W8F2002T5E` — code 2002 = 200 × 10² = 20k |
  | `R18`, `R19` | 470k 0805 | `C17709` | `0805W8F4703T5E` |
  | `C20` | 22µF 0805 | `C296305` | YAGEO 22µF 6.3 V X5R |

- [x] **Machine-place the LED ring and the ESP32 module** — `D1–D30` and `U1`
      switched from `(dnp yes)` to populated, with part numbers set

  | Ref | Value | Part | Notes |
  |---|---|---|---|
  | `D1`–`D30` | WS2812B | `C26167850` | TUOZHAN TZ-5050S2RGB-5V-I4-H1 (TZ2812), SMD5050-4P. **MSL 3** — the reason it clears Economic |
  | `U1` | ESP32-C3-WROOM-02-H4 | `C2944070` | Espressif, SMD 20×18 mm. Economic *or* Standard |

  **The LED pinout decides this part, not the price.** The footprint
  `Library:LED_WS2812_5050_handsolder` is wired for the classic WS2812B
  arrangement, and its pads carry these nets:

  | pad | local (mm) | net |
  |---|---|---|
  | 1 | (−2.925, −1.65) | `+LED_PWR` |
  | 2 | (−2.925, +1.65) | DOUT |
  | 3 | (+2.65, +1.65) | `GND` |
  | 4 | (+2.65, −1.65) | DIN |

  `C55109525` is 1 = VDD, 2 = DOUT, 3 = GND, 4 = DIN, so it drops on at **0°**
  with no rotation offset, and the silkscreen pin-1 mark stays truthful — which
  matters, because `D1`–`D20` are hand-soldered by eye.

  ⚠️ **Do not substitute an SK6812 without rotating it.** `C5380881` (OPSCO
  SK6812-B) is also Economic-eligible and slightly cheaper, but its pinout is
  **1 = GND, 2 = DIN, 3 = VDD, 4 = DOUT**. Placed at 0° it puts VDD on the `GND`
  pad and GND on `+LED_PWR` — reversed supply, every LED destroyed at power-up.
  Pads 1/3 and 2/4 are diagonal pairs, so **180°** maps all four signals
  correctly, and it is the only rotation that does. If that part is ever used,
  set `FT Rotation Offset` = 180 on all thirty — and note the silkscreen then
  points at the wrong corner for hand-soldering.

  **Why this part and not a Worldsemi one.** Every WS2812B is **MSL 5a** and
  rated 240 °C peak reflow. Economic PCBA runs a **255 °C** profile and does not
  bake parts, so JLCPCB rejects them at cart time — *"only available for Standard
  PCBA"* — regardless of what the part page's **PCBA Type** field claims. That
  field is catalogue metadata and is **not** what the ordering system enforces;
  `C55109525`, `C5380881` and the two house-brand parts all advertise "Economic
  and Standard" and all fail. **Only the cart is authoritative.**

  The TUOZHAN part clears it because it is **MSL 3**, rated **250 °C** peak for
  lead-free reflow, and qualified at **260 °C × 10 s, twice, 0/22 failures**
  (JESD22-B106). Verified accepted as Economic in the cart.

  From its datasheet, everything else lines up:

  | | |
  |---|---|
  | Pinout | 1 = VDD, 2 = DOU, 3 = GND, 4 = DIN — classic, drops in at **0°** |
  | Reset time | **80 µs**, not 280 µs — no `delay(1)` workaround needed |
  | Bit timing | T0H 0.2–0.35 µs, T1H 0.55–1.2 µs — FastLED's 250/875 ns sits inside both |
  | Colour order | **GRB**, matching `addLeds<WS2812, ..., GRB>` |
  | Drive current | **12 mA/channel** vs ~18–20 — full white drops ~1.8 A → **~1.08 A** |
  | Brightness | green 1300–1800 mcd, blue 500–700 — *brighter* than WS2812B on both |
  | Supply | 3.5–7.5 V, tolerates a flatter cell than the WS2812B's 3.7 V floor |
  | Power-up | dark by default, no flash at boot |

  The lower drive current largely retires the MCP73871 1 A system-load concern
  in CLAUDE.md. Firmware needs **no changes at all**.

  ⚠️ **Hand-soldering limits** (datasheet §2): iron **≤ 315 °C**, **under 3 s**,
  **once only**, and the tip must not touch the resin. See the note on pad
  connection in the stage-2 section — the `GND` pour is set to **solid**, which
  fights those limits.

- [x] **Charger part number corrected — read this one**

  `C637761`, the part number carried over from the original fix spec, is
  **`MCP73871-4CAI/ML`**, not the `-2CC` we designed around. Per the datasheet's
  Product Identification System the leading digit is the charge voltage:

  | Option | V_REG | Timer | LBO |
  |---|---|---|---|
  | `2CC` | **4.20 V** | 6 h | 3.1 V |
  | `4CA` | **4.40 V** | 6 h | disabled |

  Fitting the `-4CA` would have regulated a standard 4.2 V Li-Po to **4.40 V** —
  a real overcharge, not a rounding error. It was also out of stock, which is
  what prompted the search that caught it.

  Now set to **`C5121473` = `MCP73871-2CCI/ML` = 4.20 V**, which is exactly the
  variant the design was built around. Bonus: the `CC` suffix means LBO is
  enabled at 3.1 V, so `STAT1` doubles as a low-battery indicator on the LED.

- [x] **Confirm remaining parts in stock** — checked

  - `C404027` — TLV75533PDBVR — OK
  - `C15127` — AO3401A — OK
  - `C14663` — 100nF 0603 ×12 — OK
  - `C5121473` — MCP73871-2CCI/ML — in stock, replaces the out-of-stock `C637761`

  **Why `C20` changed from 100µF to 22µF:** the 100µF 1210 part had a 66-piece
  minimum order, which is absurd for a 5-board run. It is also not needed. The
  bulk cap cannot fix low-frequency droop — that is set by battery ESR plus the
  charger's ~200 mΩ BAT→SYS path plus the FET, roughly 400 mΩ total, so a 1 A
  step drops ~400 mV no matter what capacitor is fitted. What the bulk cap
  actually does is supply fast edges, and 22µF alongside the twelve distributed
  100nF is comfortably enough for that. 100µF was generous, not necessary. If
  flicker ever shows up on a low battery, add a second 22µF in parallel.

  `C21` was also relabelled 100nF → 0.1uF so it merges with `C2` onto one BOM
  line — same physical part, `C28233`.

---

## Phase 2 — Push the schematic into the PCB

- [x] **Open the PCB editor** and run *Tools → Update PCB from Schematic* (<kbd>F8</kbd>)

  In the dialog, tick:
  - ☑ Delete footprints with no symbols  ← removes the old `D31`/`D32` diodes
  - ☑ Replace footprints with those specified in schematic  ← swaps `U3` and `U4`
  - ☐ Re-link footprints... *(leave off)*

  Click **Update PCB**. New footprints appear in a loose cluster near the board —
  that is normal.

- [x] **Read the report** — **23 added, 2 removed, 2 changed.** (An earlier draft
  of this file predicted 19 added; that was an undercount of `C8`–`C19`, not an
  F8 problem.) Verified afterwards from the file itself:

  | Check | Result |
  |---|---|
  | `U3` | QFN-20 4×4 with thermal vias ✓ |
  | `U4` | SOT-23-5, was SOT-223 ✓ |
  | `D31`/`D32` | removed ✓ |
  | `Q1`, `R17`–`R19`, `C20`/`C21`, `D35` | present ✓ |
  | `D1`–`D30`, `U1` | `attr smd`, DNP cleared, MPNs carried through ✓ |
  | Still DNP | `H1`/`H3`/`H4`/`J4` only — as intended ✓ |
  | Total copper | 413 items before **and** after — F8 deleted no routing ✓ |

  PCB and schematic now both hold 88 parts.

---

## Phase 3 — Clear out the old power routing

**This phase turned out much smaller than first written.** The original draft
said to delete the old `+BATT` traces feeding the rings. **Do not do that** —
see the warning below. The post-F8 file was analysed and there are exactly
**29 stale copper items**, all in one place.

- [x] **Delete the 29 orphaned copper items.** They sit on an *empty* net —
      the leftovers of `D31`/`D32` and the old `U3`/`U4` footprints, which took
      their nets with them when they were removed. Bounding box is
      **x 129.4–147.1, y 41.5–78.8**.

      They are not all on the front. The 29 are 25 F.Cu segments, 2 vias, and
      **2 B.Cu segments** running (147.09, 76.92) → (147.09, 61.06) →
      (140.63, 54.60). Both B.Cu endpoints land inside the bounding box, but a
      selection made with B.Cu hidden leaves that pair behind.

      A rubber band over that area also grabs live `+5V`, `GND` and `+LED_PWR`
      copper, so select by net instead: click one stale track, then right-click
      → *Select → All Tracks in Net*. Every netless copper item on the board is
      in this one cluster, so that selects exactly the 29 and nothing else.

- [x] **Leave the netless B.Cu zone alone.** There is a keepout zone at
      x 185–197, y 68–89 carrying no net and no fill. It is the ESP32 antenna
      keepout and belongs there — it will show up in any hunt for unconnected
      objects.

- [x] **DO NOT delete the ring power traces.** ⚠️ KiCad already did the right
      thing here on its own. The 104 copper items that used to be `+BATT`
      feeding `D1–D30` pin 1 were **automatically re-assigned to `+LED_PWR`**
      during F8, because the pads they attach to changed net and the tracks
      followed. Verified: `+BATT` went 104 → 0 copper items and `+LED_PWR`
      went 0 → 104, with total board copper unchanged at 413.

      Keep it. ⚠️ But note the correction in Phase 5: this copper is **not** a
      working distribution — traced properly it is 28 separate groups of per-LED
      stubs, not a ring. It is still worth keeping as the anchor each hop starts
      from, and deleting it would only make Phase 5 longer.

- [x] **Leave everything else alone** — USB, buttons and the ESP32 are
      untouched by this rework.

---

## Phase 4 — Placement

Every position in this phase was checked against the board file itself: courtyard
overlap against parts on the same side, through-hole pads against parts on the
other side, all four courtyard corners inside the 90 mm circle, and every pad
against existing tracks of a different net. All 34 come back clean.

### 4a — Delete 13 more leftovers first

Phase 3 removed the copper that had lost its net. These 13 items kept theirs, so
they survived — but they are the same kind of leftover, and they run straight
through where the new parts go.

**Seven `+LED_PWR` segments on F.Cu.** They are the old feed from the switch to
the LED ring: one chain from the via at (136.13, 43.42) down to a dead end at
(114.71, 55.66). They are 0.5 mm wide, far too thin for the 1.8 A they would
carry, so Phase 5 replaces them with a fresh 1.0 mm run anyway.

| From | To |
|---|---|
| (136.13, 43.42) | (135.90, 43.42) |
| (135.90, 43.42) | (134.77, 42.29) |
| (134.77, 42.29) | (129.68, 43.42) |
| (129.68, 43.42) | (119.69, 53.41) |
| (119.69, 53.41) | (115.46, 53.41) |
| (115.46, 53.41) | (114.71, 54.16) |
| (114.71, 54.16) | (114.71, 55.66) |

⚠️ **Keep the via at (136.13, 43.42).** That via is where the whole LED ring is
fed from the front side. Phase 5 routes Q1's drain to it.

**Four `GND` segments and two `GND` vias on F.Cu** — the old `C4`/`C5` ground
connections:

| Item | Coordinates |
|---|---|
| segment | (116.79, 55.66) → (118.04, 55.66) |
| segment | (118.04, 55.66) → (118.05, 55.67) |
| segment | (114.75, 58.29) → (113.23, 58.29) |
| segment | (113.23, 58.29) → (112.88, 58.64) |
| via | (112.88, 58.64) |
| via | (118.05, 55.67) |

Keep the GND vias at (124.12, 51.36) and (125.56, 51.34) — they stitch the two
ground pours together and nothing lands on them.

To do it: open the **Selection Filter** panel (bottom left), untick everything
except *Tracks* and *Vias*, zoom into x 112–137 / y 42–59, then click the first
item and <kbd>Shift</kbd>-click the other twelve before pressing <kbd>Del</kbd>.
Copper should read **371 items** afterwards — it is 384 now.

### 4b — How to place a part at an exact position

Do not drag parts by eye — type the numbers in.

1. In the **Selection Filter** panel, tick *Footprints* only. That stops you
   grabbing a track by accident.
2. Click the part in the loose cluster below-left of the board.
3. Press <kbd>E</kbd> to open *Footprint Properties*.
4. Fill in **Position X**, **Position Y** and **Orientation** from the table.
   For the one part marked *Back*, also change **Side** to *Back*.
5. Click OK. The part jumps to its spot.

The Orientation box takes exactly the number in the table — negative values are
fine (`-162` and `198` are the same angle). Check the units selector says **mm**.

If you would rather work visually: <kbd>M</kbd> moves, <kbd>R</kbd> rotates 90°
at a time, <kbd>F</kbd> flips to the other side. Use those to get close, then
still open <kbd>E</kbd> and type the exact numbers.

### 4c — The power section

| Ref | X | Y | Rotation | Side | What it is |
|---|---|---|---|---|---|
| `U3` | 121.00 | 53.00 | 270 | Front | MCP73871 charger |
| `C4` | 128.00 | 48.00 | 0 | Front | +SYS bypass 4.7 µF |
| `C6` | 128.00 | 51.00 | 0 | Front | +5V (USB) bypass 4.7 µF |
| `C7` | 128.00 | 54.00 | 0 | Front | +BATT bypass 4.7 µF |
| `R2` | 124.00 | 57.20 | 0 | Front | PROG1 — USB charge current |
| `R15` | 124.00 | 59.70 | 0 | Front | PROG3 — current select |
| `R14` | 119.50 | 57.20 | 0 | Front | THERM, 10k to GND |
| `Q1` | 133.00 | 51.00 | 0 | Front | AO3401A load switch |
| `R13` | 133.00 | 55.00 | 0 | Front | gate pull-up to +SYS |
| `R17` | 137.50 | 49.50 | 0 | Front | +LED_PWR pulldown |
| `C20` | 137.50 | 52.50 | 0 | Front | 22 µF bulk on +LED_PWR |
| `U4` | 124.00 | 63.50 | 0 | Front | TLV75533 3.3 V LDO |
| `C5` | 119.00 | 63.50 | 0 | Front | +3V3 output cap |
| `SW1` | 136.30 | 38.90 | -162 | Front | slide switch |
| `J1` | 139.24 | 59.12 | 90 | **Back** | JST battery connector |
| `D33` | 159.45 | 39.08 | 103 | **Back** | charge-status LED |
| `R5` | 157.94 | 42.63 | 102 | **Back** | `D33` series resistor |
| `D35` | 162.98 | 40.06 | 108 | **Back** | second status LED |
| `R16` | 161.80 | 43.67 | 108 | **Back** | `D35` series resistor |
| `R18` | 176.80 | 59.40 | 0 | Front | 470k, top of divider |
| `R19` | 176.80 | 61.80 | 0 | Front | 470k, bottom of divider |
| `C21` | 179.60 | 67.80 | 0 | Front | 100 nF sense filter |

Worth knowing about six of them:

- **`U3` at 270°** — this angle puts OUT/IN/CE on the right (facing `Q1` and the
  USB feed), V_BAT and the PROG pins on the bottom (facing `J1`), and the STAT
  pins on the left. Its six thermal vias go right through the board, so the spot
  is picked to clear the back-side LED ring: the vias land 37.7–40.3 mm from the
  board centre and the ring occupies 27.7–36.3 mm.
- **`Q1`** — source (+SYS) faces left toward `U3`, drain (+LED_PWR) faces right
  toward the ring-feed via at (136.13, 43.42).
- **`SW1`** — its old, fabricated position. The body deliberately hangs over the
  board edge so the actuator is reachable. That overhang is not a mistake, and
  all three pads are on the board.
- **`J1`** — it was on the back before F8 moved it to the front, so it needs the
  *Side* dropdown changed as well as a move.
- **The two status LEDs and their resistors moved to the back.** `D33` used to
  sit dead centre on the front, and this file kept it there. The front face is
  almost hidden in the enclosure, so the pair now sits on the **back**, outside
  the LED ring, 42 mm from board centre on the arc just clockwise of the USB-C
  port — about 10 mm from `J3`, so you see them where you plug in.

  The bearings either side are taken: `J3`'s own through-holes span −96° to −84°
  and `SW1`'s mounting pin sits at −113° to −104°, so this group lives at −72° to
  −78°. Their rotations are set so each part points outward along the radius.

  Cost of the move: the two existing `Net-(D33-K)` tracks no longer land on
  anything and the `STAT` runs are now ~40 mm — fine at a few mA. Phase 5 needs
  **4 extra vias**: both anodes to the `+5V` pour on F.Cu, and both `STAT` nets
  through to `U3` on the front.

**The battery-sense divider is grouped by the ESP32, not by the battery.** This
reverses earlier advice in this file, which put `R18`/`R19` near `J1` and only
`C21` near GPIO4. That would leave the divider's 235 kΩ output node running
~40 mm across a board with 1.8 A of LED switching on it — a high-impedance
antenna. Keeping all three together at the ESP32 end means the long trace is
`+BATT` instead: a low-impedance DC rail that does not care. `C21` ends up 3.0 mm
from `U1` pin 3 (GPIO4) at (181.34, 70.27).

### 4d — The twelve decoupling caps

Each one goes in the gap between two adjacent LEDs, on the same side as those
LEDs, with its long axis pointing outward along the radius. Eight on the back
beside the outer ring, four on the front beside the inner ring, as planned
earlier in this file.

| Ref | X | Y | Rotation | Side | Sits between |
|---|---|---|---|---|---|
| `C8` | 181.61 | 74.99 | 9 | Back | `D1` and `D2` |
| `C9` | 172.63 | 57.37 | 45 | Back | `D3` and `D4` |
| `C10` | 144.52 | 45.43 | 99 | Back | `D6` and `D7` |
| `C11` | 127.37 | 57.37 | 135 | Back | `D8` and `D9` |
| `C12` | 118.39 | 85.01 | 189 | Back | `D11` and `D12` |
| `C13` | 127.37 | 102.63 | 225 | Back | `D13` and `D14` |
| `C14` | 155.48 | 114.57 | 279 | Back | `D16` and `D17` |
| `C15` | 172.63 | 102.63 | 315 | Back | `D18` and `D19` |
| `C16` | 166.17 | 74.75 | 18 | Front | `D21` and `D22` |
| `C17` | 150.00 | 63.00 | 90 | Front | `D23` and `D24` |
| `C18` | 133.83 | 85.25 | 198 | Front | `D26` and `D27` |
| `C19` | 150.00 | 97.00 | 270 | Front | `D28` and `D29` |

`C8`–`C15` need *Side* set to **Back**. Each sits about 5 mm from either
neighbour, which is what makes them worth fitting at all.

⚠️ **Back-side rotations are not the mirror of the front-side ones — read this
before typing them in.** Flipping a footprint mirrors it, which reverses the
sense of its angle. Two different rules get confused here:

- To point a part **outward along the radius**: front `rot = -bearing`, back
  `rot = +bearing`.
- To hold a **fixed angle to the neighbouring LEDs**, which is what actually
  looks right on this board: `rot = -bearing` on **both** sides.

The second is the one these tables use, because the outer LED ring does not sit
radially — see the note in Phase 8. The front caps sit at a constant -18° to
their LEDs and the back caps at a constant +9°, and that consistency is what
makes the rings look even.

The angles below are the values **as KiCad stores them**. Set *Side* to Back
first, then type the angle — do **not** press <kbd>F</kbd> afterwards, because
that rewrites the angle as `180 - angle` and silently turns these into the
radial set instead.

`C10` and `C14` are at 98° and 278° rather than the tidy 99°/279° their
neighbours would suggest. The WS2812 hand-solder footprint is asymmetric — its
pads run 2.925 mm one way and 2.65 mm the other — so the real gap between two
LEDs is not quite where the geometry says. One degree of nudge is what it took to
clear the courtyard.

### 4e — Check the placement

- **Nothing left outside the circle.** After all 34, the loose cluster should be
  empty. `SW1`'s body overhanging the edge is the only thing that should look
  like it is hanging off.
- **Look at it in 3D** (*View → 3D Viewer*), front and back. Parts should sit in
  the empty crescent on the left and in the gaps between LEDs — nothing on top of
  anything.
- **Do not read DRC as a verdict yet.** It will report a large number of
  unconnected items, which is correct — Phase 5 has not happened.

---

## Phase 5 — Routing

Nine numbered actions below. Everything before them is background — read it once,
then work the list top to bottom.

### Background (no actions here)

**What still needs connecting**, measured by building the connected components of
every net — tracks, vias and zone fills together — with actions 1–3 done:

| Net | Pads | Groups | Still to do |
|---|---|---|---|
| `+LED_PWR` | 46 | 22 | 9 inner-ring hops + 12 capacitor taps |
| LED data | — | — | 28 `DOUT → DIN` hops |
| `/BAT_SENSE` | 4 | 4 | 3 — action 8 |
| `+5V` | 10 | 3 | `D33`, `D35` anodes — action 8 |
| `+SYS` | 9 | 2 | `R13` — action 8 |
| `+BATT` | 6 | 2 | `R18` — action 8 |
| `GND` | 110 | 2 | `R19.2` — action 8.5 |

`U3`'s own ground pins are **not** on that list. Pads 3, 10 and 11 each already
have a short escape stub out to the pour, and the exposed pad (21) sits under
the fill. Nothing to do there.

**Both LED rings are uniformly oriented.** Every LED sits at exactly 90° to its
own radius, inner and outer alike — spread 0.07° on the outer ring, 0.14° on the
inner. Their `+LED_PWR` pads all land on one clean circle: r = 30.5 mm on the
back, r = 18.8 mm on the front. So both power rings route as plain circles, and
every `DOUT → DIN` hop is a short tidy run — **5.6 mm on the outer ring, 6.0 mm on
the inner**.

An earlier revision of this file claimed the outer ring fanned through five
orientations and that its power pads scattered over 6.6 mm of radius, forcing a
zig-zag. That was wrong — it came from mirroring back-side pad coordinates in the
X axis, which KiCad does not do. Ignore any advice in older commits about the
outer ring being irregular.

**The outer ring's power is already routed.** All twenty `D1`–`D20` power pads sit
in one group with `Q1`, `C20`, `R17` and `U4`, inherited from the existing copper.
Action 4 is a check, not a job. The *data* links were never drawn, and neither was
the inner ring's power.

**The capacitors are clear of the ring.** Every outer-ring `+LED_PWR` hop passes
clear of every decoupling capacitor's GND pad, and the caps sit 0.42–1.21 mm from
their neighbouring LEDs. Nothing has to detour.

**Already connected — leave alone.** `+3V3`, `+5V` to the USB, USB `D+`/`D-`, the
buttons, and both ring *inputs* `/small_ring` and `/large_ring`.

---

### 1. Set the trace widths

*File → Board Setup → Design Rules → Net Classes.* Two things to get right: the
class widths, and **which net is assigned to which class** — they are separate
lists in that dialog and it is easy to fill in one and forget the other.

| Net class | Width | Assign these nets |
|---|---|---|
| Power-high | **1.0 mm** | `+LED_PWR`, `+SYS` |
| Power-med | 0.5 mm | `+BATT`, `+5V`, `+3V3` |
| Default | 0.25 mm | everything else |

⚠️ A net with **no** assignment silently falls to Default. That is the failure to
watch for: `+LED_PWR` and `+SYS` are the two nets carrying 1.8 A, and if they are
left unassigned they come out at 0.25 mm — a quarter of what they need — with no
warning at all.

At 1.8 A on 1 oz copper a 0.5 mm trace runs hot; 1.0 mm keeps the rise sensible.
`+5V`, `+3V3` and `+BATT` never carry more than ~0.5 A, so 1.0 mm on those just
makes them awkward to route into fine-pitch pads.

**Sanity check when you are done:** click any `+SYS` track and read the width in
the status bar. It should say 1.0 mm.

### 2. Add a ground pour on the front

**There is currently no `GND` zone on F.Cu at all** — the small one deleted with
the two dead `+3V3` zones was the only one, and it was a 3 × 4 mm scrap. The back
has a board-wide pour; the front has nothing.

Draw one to match: *Place → Add Filled Zone*, layer **F.Cu**, net **GND**, outline
the whole board just inside `Edge.Cuts`, priority 0.

This is what connects 47 currently-isolated ground pads — every front LED
`D21`–`D30`, all four buttons, the four front decoupling caps, and the new power
section. Without it you would be drawing dozens of ground traces by hand.

### How to draw a trace (read once)

1. Press <kbd>X</kbd>, or click **Route Tracks** in the right-hand toolbar.
2. **Click once on the starting pad.** The trace snaps to the pad centre and the
   width comes from the net class automatically — you never type a width.
3. Move the mouse. Click to drop a corner. KiCad keeps 45° angles by default;
   <kbd>D</kbd> cycles that if you need a free angle.
4. **Double-click the destination pad** to finish, or press <kbd>Esc</kbd> to stop
   where you are.
5. <kbd>Backspace</kbd> undoes the last segment while still routing.
6. <kbd>V</kbd> drops a via and switches layer mid-route — needed only in action 8.

Helpers worth knowing:

- The thin white lines are the **ratsnest** — every one is a connection still
  missing. They vanish as you route. That is your progress bar.
- **Hover a pad and the status bar names its net.** That is the fastest way to
  confirm you grabbed the right one.
- KiCad names pads by **number**, not by function — `Q1` has no pad called
  "source". The tables below give pad numbers, nets and exact coordinates, so
  you can always check the status bar against them.
- If a trace refuses to attach, you are probably on the wrong layer. Front parts
  route on **F.Cu**, back parts on **B.Cu**; the layer dropdown is in the top
  toolbar.

---

### 3. Route the power path

Five sub-steps. **3.1 is the only one carrying 1.8 A** — draw it at the
Power-high width from action 1 and keep it short.

**You cannot start a 1.0 mm trace on a `U3` pad — and you are not meant to.**

`U3` is a QFN-20 on 0.5 mm pitch. Each pad is **0.25 mm wide** with a 0.25 mm gap
to its neighbour. A 1.0 mm trace is four times the pad width, so the moment you
start one it laps over the pads either side and DRC complains. That is the error
you hit, and it is telling the truth.

The fix is the standard QFN **fan-out**:

1. Leave the pad **straight outward**, perpendicular to that edge of the package,
   at **0.25 mm** (Default width). Every pad's long axis already points outward,
   so this never runs between two pads — it runs along its own lane. Clearance to
   the neighbouring pad works out at 0.25 mm, comfortably over the 0.2 mm rule.
2. Carry on for about **1 mm**, until you are clear of the package body.
3. **Then widen to 1.0 mm** for the rest of the run.

To change width mid-route, use the track-width dropdown in the top toolbar. If it
only offers one size, add the sizes first in *Board Setup → Design Rules →
Pre-defined Sizes*. Easiest alternative if that feels fiddly: draw the 0.25 mm
escape, press <kbd>Esc</kbd>, then start a **new** trace at 1.0 mm from the end of
the stub. Two traces meeting end to end are one connection.

The wide trace only has to be wide where it is long. A 1 mm stub at 0.25 mm adds
about 4 mΩ — irrelevant next to the 400 mΩ the battery and charger already put in
the path.

**What pads 4, 9 and 17 actually are.**

They are **not** extra output pins and they do **not** share the load. They are
configuration inputs that the design ties to `+SYS`:

| Pad | Pin | What it does | Current |
|---|---|---|---|
| 1, 20 | OUT | the charger's real output | **the full 1.8 A** |
| 17 | CE | chip enable — high means charging allowed | ~0 |
| 9 | TE | timer enable — sets the safety-timer behaviour | ~0 |
| 4 | PROG2 | selects the input current limit | ~0 |

So pads 4, 9 and 17 each need **a thin 0.25 mm trace to any `+SYS` copper** —
nothing more. They are on three different edges (4 on top, 17 on the right, 9 on
the left), so do not try to gather them into one place near the package; that is
what blocks everything. Route each one outward from its own edge and join the
nearest `+SYS` you can reach.

**If the right-hand edge gets too congested**, that is expected — pads 20, 19,
18, 17 and 16 all want to escape rightward and they are four different nets. Two
ways out, either is fine:

- Fan all five straight out in parallel at 0.25 mm (they stay on 0.5 mm pitch, so
  clearance holds), then diverge once past the courtyard.
- Or drop `U3` pad 17 through a via onto B.Cu right after its escape, run it a few
  millimetres clear, and come back up. The `GND` pour there clears around it
  automatically.

⚠️ **`U3` has five pads on `+SYS`, and they are not interchangeable.** Only pads
**1 and 20** are the charger's actual output. Pads 4, 9 and 17 are configuration
straps (PROG2, TE, CE) that just need to sit on the same net — thin traces, no
current. Putting the load path through a strap pin would run 1.8 A through a
signal pin.

| `U3` pad | Function | Role |
|---|---|---|
| 20 at (122.94, 52.00) | OUT | **load path** |
| 1 at (122.00, 51.06) | OUT | **load path** |
| 17 at (122.94, 53.50) | CE | strap, thin |
| 4 at (120.50, 51.06) | PROG2 | strap, thin |
| 9 at (119.06, 53.50) | TE | strap, thin |

**3.1 — the LED power path (Power-high, 1.0 mm)**

| From | To | Note |
|---|---|---|
| `U3` pad 20 (122.94, 52.00) | `Q1` pad 2 (132.06, 51.95) | the main run, ~9 mm, almost straight across |
| `U3` pad 1 (122.00, 51.06) | onto that run | second output pin in parallel — start on the pad and finish anywhere on the trace you just drew |
| `C4` pad 1 (126.96, 48.00) | onto that run | `+SYS` bypass, tap straight down |
| `Q1` pad 3 (133.94, 51.00) | the via at (136.13, 43.42) | this is `+LED_PWR` leaving for the rings |
| `C20` pad 1 (136.46, 52.50) | onto the `+LED_PWR` run | 22 µF bulk |
| `R17` pad 1 (136.50, 49.50) | onto the `+LED_PWR` run | rail pulldown, thin is fine |

Then the three straps, Default width: `U3` pad 17 → pad 20 (they are 1.5 mm
apart on the same edge), and pads 4 and 9 across to the nearest `+SYS` copper.

**3.2 — battery (Power-med, 0.5 mm)**

`J1` is on the **back**, `U3` and `C7` on the front, so this one needs a via.

| From | To |
|---|---|
| `U3` pad 15 (122.00, 54.94) | `C7` pad 1 (126.96, 54.00) |
| `U3` pad 14 (121.50, 54.94) | onto that run |
| `U3` pad 16 (122.94, 54.00) | onto that run |
| `C7` pad 1 | a via, then across B.Cu to `J1` pad 2 (139.24, 56.62) |

**3.3 — USB input (Power-med, 0.5 mm)**

| From | To |
|---|---|
| `U3` pad 19 (122.94, 52.50) | `C6` pad 1 (126.96, 51.00) |
| `U3` pad 18 (122.94, 53.00) | onto that run |
| `C6` pad 1 | the existing `+5V` copper — its nearest free end is (133.36, 46.59) |
| `U3` pad 2 (121.50, 51.06) | onto the `+5V` run — VPCC, thin |

**3.4 — the 3.3 V regulator (Power-med, 0.5 mm)**

| From | To |
|---|---|
| `U4` pad 1 (122.86, 62.55) | the `+SYS` run from 3.1 |
| `U4` pad 5 (125.14, 62.55) | `C5` pad 1 (117.96, 63.50) — this is `+3V3` |
| `C5` pad 1 | onward to the existing `+3V3` copper on the right of the board |
| `U4` pad 3 (122.86, 64.45) | the `+LED_PWR` run — this is the enable pin, thin |

`U4` pad 3 being on `+LED_PWR` is deliberate: the regulator switches off with the
LED rail. It is an input, so Default width is fine.

**3.5 — check before moving on**

`+SYS` and `+BATT` must be separate copper — never let them touch. Shorting them
silently recreates the original load-sharing bug: the board looks fine and
charges wrongly.

### 4. Outer ring — power (check only)

Already connected. All twenty `D1`–`D20` pad 1s are in one group with `Q1` pad 3,
`C20`, `R17` and `U4` pad 3, on r = 30.5 mm.

Just confirm it: click any outer LED's pad 1 and check the highlight runs the
whole way round. If it does, move on — nothing to draw.

### 5. Outer ring — data

**19 hops, back side, `D1` → `D20`.** From each LED's **pad 2** (DOUT) to the next
LED's **pad 4** (DIN). Default 0.25 mm, all on B.Cu, no vias.

Every hop is **5.6 mm** and they are all the same shape, so once you have drawn
the first one the rest are repetition. `/large_ring` already feeds `D1` pad 4, so
start at `D1` pad 2 and work round to `D20`.

### 6. Inner ring — power, then data

Front side, `D21` → `D30`.

- **Power: 9 hops.** Pad 1 to pad 1, Power-high width, on r = 18.8 mm. `D21` is
  already tied to the rest of `+LED_PWR`, so start there and work round to `D30`.
- **Data: 9 hops.** Pad 2 (DOUT) to pad 4 (DIN), Default width, ~6.0 mm each.
  `/small_ring` already feeds `D21` pad 4.

All on F.Cu, no vias.

### 7. Connect the twelve decoupling capacitors

Each cap's **pad 1** taps `+LED_PWR` at the nearest LED pad 1, on the same side as
the cap — the back eight to the outer ring, the front four to the inner ring.
They sit 0.42–1.21 mm from their neighbours, so these are very short runs.

**Pad 2 needs no trace** — it is `GND` and the pour picks it up.

### 8. Route what is left

Five sub-steps, 17 traces. **8.1 to 8.3 are all short front-side runs** — start
there, they are the easy half. 8.4 and 8.5 are the two long hauls and the only
places you need a via.

Widths come from the net class, so you never type one: everything here is
Default 0.25 mm except `+BATT` and `+5V` (Power-med 0.5 mm) and the one `+SYS`
trace in 8.3 (Power-high 1.0 mm).

**8.1 — `U3`'s three configuration resistors (F.Cu, Default)**

All three land on parts sitting just below `U3`. Nothing is routed on these
nets yet, so each is one clean run.

| From | To | Note |
|---|---|---|
| `U3` pad 13 (121.00, 54.94) | `R2` pad 1 (123.00, 57.20) | PROG1, ~3.0 mm |
| `U3` pad 12 (120.50, 54.94) | `R15` pad 1 (123.00, 59.70) | PROG3, ~5.3 mm |
| `U3` pad 5 (120.00, 51.06) | `R14` pad 1 (118.50, 57.20) | THERM — pad 5 is on the *top* edge, so escape upward and come round `U3`'s left side |

**8.2 — the P-FET gate (F.Cu, Default)**

`SW1` switches the LED rail by pulling `Q1`'s gate; `R13` is the pull-up that
holds it off. `Q1` pad 1 sits between the other two, so route through it.

| From | To | Note |
|---|---|---|
| `SW1` pad 3 (134.77, 42.29) | `Q1` pad 1 (132.06, 50.05) | ~8.2 mm |
| `Q1` pad 1 (132.06, 50.05) | `R13` pad 1 (132.00, 55.00) | ~5.0 mm, straight down |

**8.3 — `R13`'s other end to `+SYS` (F.Cu, Power-high 1.0 mm)**

| From | To | Note |
|---|---|---|
| `R13` pad 2 (134.00, 55.00) | `Q1` pad 2 (132.06, 51.95) | ~3.6 mm |

This is a pull-up carrying microamps, not a load path — it only comes out at
1.0 mm because `+SYS` is in Power-high. `R13`'s pad is 1.2 mm wide so the class
width does fit; let it. You are landing on the same `Q1` pad 2 that 3.1 already
took the full 1.8 A to, which is exactly right: `R13` has to reference `+SYS` to
hold the gate off.

**8.4 — the two status LEDs (F.Cu → B.Cu, Default)**

The awkward one. `U3` is on the front at the upper left; `R5`, `R16`, `D33` and
`D35` are all on the **back**, clustered at the top of the board. The two `STAT`
nets have to cross about 40 mm and change layer on the way.

| From | To | Layer | Note |
|---|---|---|---|
| `U3` pad 8 (119.06, 53.00) | `R5` pad 1 (158.15, 43.61) | F.Cu → via → B.Cu | STAT1, ~40 mm |
| `U3` pad 7 (119.06, 52.50) | `R16` pad 1 (162.11, 44.62) | F.Cu → via → B.Cu | STAT2, ~44 mm |
| `R5` pad 2 (157.73, 41.65) | `D33` pad 1 (159.68, 40.08) | B.Cu | ~2.5 mm |
| `R16` pad 2 (161.49, 42.72) | `D35` pad 1 (163.30, 41.03) | B.Cu | ~2.5 mm |
| `D33` pad 2 (159.22, 38.08) | `D35` pad 2 (162.66, 39.09) | B.Cu | joins both anodes, ~3.6 mm |
| that run | `J3` pad A4 (152.40, 42.08) | via → F.Cu | `+5V`, Power-med 0.5 mm |

That is **three vias**, not four: `R5` and `R16` are already on B.Cu, so the
`STAT` nets change layer once, near `U3`, and stay down. Joining the two anodes
first means they share one via up to `+5V`. Give each anode its own via if you
prefer — four is equally correct, just more work.

Two things make this easier than it looks:

- **The back of the board is nearly empty out here.** Between `U3` and `R5`
  there is a clean annulus, measured from the board centre (150, 80): the outer
  LED ring's copper stops at r = 34.4 mm, and the next thing outward is `J3`'s
  shield holes at r = 38.0 mm. Run the pair as an arc at **r ≈ 35.5–37.5 mm**
  and nothing is in the way the whole distance. `R5` and `R16` sit at r = 36.5,
  so the arc lands straight on them.
- `J3`'s VBUS pad A4 is the nearest `+5V` copper to the LEDs by a wide margin —
  7.9 mm, against 15.9 mm to the nearest free end of the existing `+5V` run at
  (147.69, 49.08). ⚠️ But `J3`'s shield holes at (154.27, 36.98) and
  (154.27, 41.16) sit between it and the LEDs, with a 3.2 mm lane between them.
  Aim for y ≈ 39 as you pass x = 154.3. Put the via on the LED side of those
  holes, so the F.Cu leg is the short one.

⚠️ **Stagger the two `U3` vias.** Pads 7 and 8 are 0.5 mm apart, but a 0.8 mm
via needs 1.0 mm centre to centre. Fan the two escapes apart on F.Cu first, then
drop. There is a `+SYS` strap already running left out of pad 9 at y = 53.50, so
leave that lane alone.

**8.5 — the battery-sense divider (F.Cu, Default unless noted)**

`R18`/`R19` halve `+BATT` and feed `U1`'s ADC; `C21` is the buffer cap. Four of
the five runs are millimetres apart on the right-hand edge. The fifth is not.

| From | To | Note |
|---|---|---|
| `R18` pad 2 (177.80, 59.40) | `R19` pad 1 (175.80, 61.80) | the divider tap |
| `R19` pad 1 (175.80, 61.80) | `C21` pad 1 (178.56, 67.80) | ~6.6 mm |
| `C21` pad 1 (178.56, 67.80) | `U1` pad 3 (181.34, 70.27) | `IO4`, the ADC input |
| `R19` pad 2 (177.80, 61.80) | `GND` at (181.25, 59.89) | see below |
| `R18` pad 1 (175.80, 59.40) | `+BATT` at `J1` pad 2 (139.24, 56.62) | Power-med 0.5 mm, ~37 mm |

**`R19` pad 2 will not pick up the pour on its own.** It sits 0.5 mm from the
fill edge — exactly the zone clearance — so refilling in action 9 will not
close it. Draw the short stub to the `GND` copper at (181.25, 59.89).

**The `+BATT` run is long and that is fine.** The divider is 470k/470k, so it
draws about 4.5 µA; ~37 mm of 0.5 mm trace is electrically nothing. There is no
`+BATT` copper anywhere nearer — the whole charging section is on the opposite
side of the board. Two things help:

- Keep it on **F.Cu**. The outer LED ring is entirely back-side, so a front-side
  trace crosses straight under it without meeting anything.
- A chord at y ≈ 56–59 clears the inner ring comfortably (closest approach
  ~24 mm against its 18.8 mm radius). Watch for `R13` at (133.00, 55.00) as you
  come in near `J1`, and drop through a via at the end since `J1` pad 2 is on
  the back.

### 9. Refill and check

Press <kbd>B</kbd> to refill both pours. Then confirm three things:

- **`+SYS` and `+BATT` are separate copper.** They must never touch. Shorting
  them silently recreates the original load-sharing bug — the board would appear
  to work and charge wrongly. This is the single most important thing in the
  layout.
- **`U3`'s exposed pad reaches ground.** Its thermal vias are in the footprint;
  just make sure the pour reaches them.
- **No unconnected items left.** DRC in Phase 6 is the reliable answer, but the
  ratsnest going quiet is the quick check.

⚠️ **Refill before you believe any connectivity result.** A pour that has not
been refilled since you last drew copper still carries its *old* geometry, and
every check — the ratsnest, this file's `nets`, your own eyes — reads that stale
fill as if it were real. It will happily show a net connected through copper the
filler is about to take away. Press <kbd>B</kbd>, save, and only then judge.

That is not hypothetical here: after action 8 the pour was a whole action out of
date, `nets` reported `GND` fully connected, and the refill then stranded eleven
ground pads that had looked fine.

Refill again after any later routing change.

### 9a. Make the pour actually reach every ground pad

After the refill, run `python tools/pcb_check.py nets`. If `GND` comes back as
more than one group, the usual cause is **thermal spokes, not routing**.

Read the numbers before you touch anything. A stranded pad sitting **0.50 mm**
from a *large* island is not a pad the pour failed to reach — 0.50 mm is the
zone's own `thermal_gap`. The copper is wrapped right around it; the filler
simply could not place the two spokes `min_resolved_spokes` insists on, because
the ring traces block the spoke directions, and KiCad's answer to "I can only
manage one spoke" is to connect nothing at all. A pad **1–2 mm** out is the
different problem: there the pour really has retreated and the pad needs a stub.

For the spoke case:

1. Open the `GND` zone properties (select the pour, <kbd>E</kbd>).
2. Set **Pad connection: Solid**. On an SMT-assembled board this is the normal
   choice anyway — reflow does not care, and it lowers ground impedance.
3. That breaks hand-soldering on the through-hole parts, so put those back:
   for `J1`, `J4` and `J3`'s shield pads, open Pad Properties (<kbd>E</kbd> on
   the pad) and set **Connection to copper zones: Thermal relief**.
4. Refill (<kbd>B</kbd>), save, and re-run `nets`.

For the retreated case, draw a short stub from the pad to the nearest `GND`
copper, exactly as `U3`'s ground pins already do.

⚠️ Do not "fix" this by lowering `min_resolved_spokes` to 1 unless you have
looked at what it does to the pads that *are* connected — a single spoke is a
single point of failure on a ground return.

### 9b. Sweep for junk copper

Three kinds of stray copper survive a clean DRC run, so check for them here:

```bash
python tools/pcb_check.py strays
```

- **Copper near the board edge** — measured against 0.30 mm. KiCad's own
  `copper_edge_clearance` rule defaults to 0.0, so it never fires; Phase 6 tells
  you to set it, and this catches it beforehand.
- **Dangling stubs** — a trace with a free end touching no pad, no other track
  and no pour. Usually a mis-started route. Harmless electrically, but it is
  copper you did not mean to ship, and it eats clearance from things you did.
- **Zero-length segments** — nanometre-long artifacts the interactive router
  leaves behind. *Tools → Cleanup Tracks & Vias* (the **Tools** menu, not Edit),
  tick **Delete tracks with zero length**, then Update. Do not hunt them by
  hand: at zero length there is nothing on screen to click. If your KiCad has
  no such dialog, leave them — they are electrically nothing and DRC ignores
  them; just re-run `strays` after any cleanup to see the count drop.

---

### 10. Charger status to the ESP32 — the rework

This is a deliberate change of scope, taken after the Economic re-quote. It
routes the MCP73871's three status outputs to spare ESP32 pins and **deletes the
two status LEDs**, so charger state becomes readable in firmware and sendable to
the app, and the back side drops to the outer ring alone.

⚠️ **Deleting the LEDs is not optional — it is what makes the GPIO connection
safe.** `STAT1`, `STAT2` and `PG` are **open-drain**. Today `D33`/`D35` pull
`STAT1`/`STAT2` up to **`+5V`**, so whenever the charger releases the output the
net sits at 5 V. Wiring that to an ESP32-C3 pin rated 3.6 V max would damage it.
With the LEDs gone the nets have no pull-up at all, the open-drain outputs never
source voltage, and the ESP32's internal pull-ups to 3.3 V are the only thing
holding them high. Do not keep the LEDs and add the GPIO.

**Pin mapping** (all confirmed free — `IO5`, `IO6`, `IO10` are not ESP32-C3
strapping pins; those are `IO2`, `IO8`, `IO9`):

| Signal | `U3` pad | at (mm) | → | `U1` pad | at (mm) |
|---|---|---|---|---|---|
| `STAT1`/`LBO` | 8 | (122.94, 53.00) | `IO5` | 4 | (183.04, 87.73) |
| `STAT2` | 7 | (122.94, 53.50) | `IO6` | 5 | (184.54, 87.73) |
| `PG` | 6 | (122.94, 54.00) | `IO10` | 10 | (190.54, 70.23) |

`IO5` is `ADC2_CH0`, but it is being used as a digital input so the ADC2/WiFi
restriction does not apply.

- [x] **10a. Schematic first — this is the authoritative change** — done, ready for ERC

  Applied: `D33`, `D35`, `R5`, `R16` and their two `+5V` power symbols deleted
  along with the eight wires of both LED chains; the `no_connect` flags cleared
  from `U3` pin 6 and `U1` pins 4, 5 and 10; and six labels placed directly on
  those pins — `STAT1` on `U3`.8 + `U1`.4, `STAT2` on `U3`.7 + `U1`.5, `PG` on
  `U3`.6 + `U1`.10. Nets come out as `/STAT1`, `/STAT2`, `/PG`.
  165 symbols → 159, structure verified balanced.

  1. Delete the symbols `D33`, `D35`, `R5`, `R16`.
  2. Wire `U3` pin 8 → `U1` pin 4, pin 7 → pin 5, pin 6 → pin 10. Use **net
     labels**, not long wires — name them `/STAT1`, `/STAT2`, `/PG` so they read
     clearly in the netlist and in `pcb_check.py net`.
  3. Remove the *unconnected* flag on `U3` pin 6 if one is placed, or ERC will
     complain that a no-connect pin is driven.
  4. Re-run ERC. Expect **0 errors**; the 42 pre-existing warnings from Phase 1
     stay.

- [ ] **10b. Push it into the board**

  *Tools → Update PCB from Schematic* (<kbd>F8</kbd>). Tick **Delete footprints
  with no symbol** so `D33`/`D35`/`R5`/`R16` actually come off the board — without
  it they stay as orphans with live copper attached.

- [ ] **10c-1. Delete the orphaned copper**

  F8 removed the four footprints but left behind the copper that fed them. None
  of it connects anything — `pcb_check nets` shows all six pads stranded:

  | Net | Left behind | What it was |
  |---|---|---|
  | `/STAT1` | 22 items, 1 via | the old `U3` → `R5` run |
  | `/STAT2` | 13 items, 1 via | the old `U3` → `R16` run |
  | `+5V` | 8 items, 1 via | the old `D33`/`D35` anode feeds |

  It all sits between `U3` (lower left) and the top centre of the board where
  `D33`/`D35` used to be. In the board editor hover any segment, press
  <kbd>U</kbd> twice to select the whole connected run, then <kbd>Delete</kbd>.

  Check it is gone:

  ```bash
  cd Hardware/PCB/tools
  python pcb_check.py strays
  ```

  The `+5V`, `/STAT1` and `/STAT2` entries under *dangling copper* must all
  disappear. Nothing else in that report should change.

- [ ] **10c-2. Set the width**

  **0.25 mm** — what every other signal net on this board uses.

- [ ] **10c-3. Route the three nets**

  Click the source pad, route, click the destination pad. One net at a time:

  | # | Net | From | To |
  |---|---|---|---|
  | 1 | `/STAT1` | `U3` pad **8** | `U1` pad **4** |
  | 2 | `/STAT2` | `U3` pad **7** | `U1` pad **5** |
  | 3 | `/PG` | `U3` pad **6** | `U1` pad **10** |

  Do `/STAT1` and `/STAT2` as a pair — they start on adjacent `U3` pins and end
  on adjacent `U1` pins 1.5 mm apart, so they run together the whole way.

  **The corridor:** via down to **B.Cu** near `U3`, sweep round to `U1`, via back
  up. Stay in the band **r ≈ 35–42 mm** from the board centre (150, 80) — outside
  the outer LED ring at r = 32, inside the 45 mm edge. That band is exactly what
  deleting `D33`/`D35` (r = 42) and `R5`/`R16` (r = 38) freed up. B.Cu carries a
  `GND` pour; it will part around the new tracks when you refill.

  Take any path that fits. These signals change about once a second, so there is
  no length, impedance or timing constraint whatsoever.

- [ ] **10c-4. Refill the zones** — <kbd>B</kbd> refills all of them.

- [ ] **10d. Verify before moving on**

  ```bash
  cd Hardware/PCB/tools
  python pcb_check.py net "/STAT1"
  python pcb_check.py net "/STAT2"
  python pcb_check.py net "/PG"
  python pcb_check.py nets
  python pcb_check.py strays
  ```

  Each of the three must come back as **one connected group with exactly two
  pads**. `nets` must show no new split nets or stranded pads — deleting four
  footprints is a classic way to strand the copper that fed them.

**What this changes downstream:**

- The BOM stays at **20 lines / 47 parts**. `D33`/`D35` shared the `C2297` line
  with `D34`, and `R5`/`R16` shared `C17513` with `R8`/`R9`/`R12`; both lines
  survive on their front-side members, and all four deleted parts were already
  `(dnp yes)`. **The quote does not move.**
- Back-side placeable parts drop **32 → 28**, and stage-1 hand-soldering drops
  from five parts to **one** — `J1`, the through-hole battery connector.
- Firmware gains three inputs on `INPUT_PULLUP`. Decode per the datasheet's
  **Table 5-1** (DS20002090F p.21); a pull-up makes High-Z read HIGH:

  | `PG` | `STAT1` | `STAT2` | State |
  |---|---|---|---|
  | L | L | H | **Charging** — preconditioning, constant current or constant voltage |
  | L | H | L | **Charge complete** / standby |
  | L | L | L | **Fault** — temperature (the safety timer is strapped off, below) |
  | L | H | H | No battery present, or `CE` low |
  | H | L | H | **Low battery** — running on battery, cell under 3.1 V (LBO) |
  | H | H | H | No input power — running on battery, level OK |

  **`PG` is what makes this decodable.** "Charging" and "low battery" share the
  same `STAT1`/`STAT2` pair and differ *only* in `PG`. Two pins would be
  ambiguous; three are not. That is why `PG` is worth the extra track.

  ⚠️ **`PG` is *pseudo* open-drain.** §5.2.4: it "must not be pulled up higher
  than V_IN because there is a diode path back to V_IN". With USB unplugged
  V_IN is 0 V, so the ESP32's ~45 kΩ internal pull-up back-feeds about
  (3.3 − 0.7)/45k ≈ 58 µA into `+5V` and floats that rail to ~2.6 V. Harmless
  here: 2.6 V is far below `U3`'s UVLO so the charger stays in battery-powered
  mode, `PG` still reads HIGH correctly, and `SW1` cuts ESP32 power entirely
  when off so nothing drains in storage. Worth knowing, not worth a part.
  `STAT1`/`STAT2` are true open-drain and carry no such restriction.

- **How `U3` is strapped**, verified against the datasheet — this is what the
  decode table assumes:

  | Pin | | Net | Meaning |
  |---|---|---|---|
  | 3 | `SEL` | `GND` | USB-port mode |
  | 4 | `PROG2` | `+SYS` | 500 mA USB input limit |
  | 13 | `PROG1` | `R2` 2k | I_REG = 1000/2 = **500 mA** charge current |
  | 12 | `PROG3` | `R15` 20k | I_TERM = 1000/20 = **50 mA** termination |
  | 5 | `THERM` | `R14` 10k to `GND` | thermistor monitoring **disabled** |
  | 9 | `TE` | `+SYS` | safety timer **disabled** |
  | 17 | `CE` | `+SYS` | charger enabled |
  | 2 | `VPCC` | `+5V` (= IN) | VPCC feature **disabled** |

  `TE` high means a timer fault can never occur, so both-status-low is
  unambiguously a temperature fault. Note `R15` sets the *termination current*,
  not the timer — the timer is factory-set (6 h on the `-2CC`) and gated by
  `TE`.
- The BLE contract needs a coaster→app path, which it does not have today. See
  the battery-level TODO in CLAUDE.md — do both in one package type, and land
  firmware and app together.

---

## Phase 6 — DRC

- [ ] **Set manufacturing limits** — *File → Board Setup → Design Rules →
      Constraints*. Safe values for JLCPCB's standard (cheapest) process:

      - Minimum track width: **0.15 mm**
      - Minimum clearance: **0.15 mm**
      - **Copper to board edge: 0.30 mm** — see below
      - Minimum via: 0.6 mm diameter / 0.3 mm drill
      - Minimum annular ring: 0.13 mm
      - Minimum hole-to-hole: 0.5 mm
      - **Minimum through hole: 0.20 mm**, not 0.30 — see below

      ⚠️ **"Minimum through hole" hits vias, not just component holes.** 0.30 mm
      is the right floor for a hole you push a lead through, but KiCad applies
      the same rule to the stitching vias that live inside footprints — `U3`'s
      QFN thermal vias are 0.20 mm and `U1`'s are 0.25 mm. Leave it at 0.30 and
      DRC throws 16 errors at two vendor land patterns that are perfectly
      manufacturable. There is no component hole on this board below 0.60 mm,
      so 0.20 costs you no protection.

      ⚠️ **Copper-to-edge ships as 0.0 and therefore never fails.** That is the
      one constraint in this list KiCad will not nag you about, because the
      default rule permits copper flush with the outline — and the routing bit
      then eats it. This board has already had a 1.0 mm `+SYS` trace sitting
      0.022 mm from the edge with DRC perfectly happy. Set it to 0.30 mm
      before you run anything.

- [ ] **Run DRC** — *Inspect → Design Rules Checker → Run DRC*, with
      "Check footprint courtyard overlap" and "Test for parity between PCB and
      schematic" both ticked.

- [ ] **Get to zero errors.** Unconnected-item warnings are the ones that bite —
      each means a net you forgot to route. Warnings about silkscreen overlapping
      pads are cosmetic and can be left.

- [ ] **Visual check in 3D** — *View → 3D Viewer*. Look for parts overlapping,
      parts hanging off the board edge, and the QFN sitting where you meant it.

---

## Phase 7 — Generate production files

- [ ] **Install the Fabrication Toolkit plugin** — in the main KiCad window,
      *Plugin and Content Manager* → Plugins → search "Fabrication Toolkit" →
      Install.

      If it is not available or errors on KiCad 10, use the manual fallback in
      the appendix below.

- [ ] **Run it** — in the PCB editor toolbar there is a new button (a small
      fabrication icon). Click it. It writes gerbers, drill files, BOM and
      position files into `production/` and zips them.

- [ ] **Sanity-check the output** — open `production/bom.csv` and confirm all
      your new parts are listed with LCSC numbers, and no line has an empty
      part number that you meant to fill.

- [ ] **Commit the production files**

  ```bash
  git add Hardware/PCB
  git commit -m "Regenerate production files for reworked power path"
  git push
  ```

---

## Phase 8 — Order at JLCPCB

- [ ] **Upload the gerbers** at <https://jlcpcb.com> → *Order now* → drag in
      `production/LED_Coaster.zip`. Confirm the preview shows a 90 mm circle.

- [ ] **PCB options:**

      | Option | Choose | Why |
      |---|---|---|
      | Layers | 2 | |
      | Quantity | 5 | minimum |
      | Surface finish | **ENIG** | costs more, but gives a flat finish. The QFN-20 has 0.5 mm pitch pads and HASL's uneven surface makes those harder to solder reliably |
      | Thickness | 1.6 mm | unless you deliberately want thinner |
      | Everything else | default | |

- [ ] **Turn on assembly** — set *PCB Assembly* to ON.
      - Assembly side: **top side only** — the 32 back-side parts are `(dnp yes)`
        and hand-soldered. Economic is single-side only regardless
      - PCBA type: **Economic** — possible because `C55109525` is offered on it.
        Verify the cart accepts it; one Standard-only line forces the whole
        order to Standard
      - Tooling holes: **added by JLCPCB**
      - Quantity: **5** — all of them. Decided deliberately: the ~$50 setup fee
        is paid once regardless, so boards 3–5 add only ~$20 of parts between
        them. Assembling fewer saves much less than it appears to.

      **Actual quote**, 5 boards, against the Standard double-sided quote that
      started the re-scope:

      | | Standard, 2-side (first quote) | **Economic, front only (ordered)** |
      |---|---|---|
      | Setup | $51.12 | **$8.18** |
      | Stencil | $16.42 | **$1.53** |
      | Feeder loading | $35.19 (23 lines × $1.53) | **$21.49** (7 extended × $3.07) |
      | Components | $66.23 | **$49.79** |
      | SMT assembly | $3.00 | $1.47 |
      | Nitrogen reflow | — | $0.90 |
      | **PCBA** | **$172.46** | **$83.36** |
      | Bare PCB | quoted separately | **$2.00** (sub-100 mm special offer) |
      | Shipping | — | $12.60 |
      | **Total** | | **$97.96** |

      About **$19.60 per coaster**, a 55% cut. Three things made it:

      1. **Economic instead of Standard.** Standard bills the loading fee on
         every line including Basic ones; Economic bills it only on extended
         parts. Worth $13.70 here, and the setup and stencil another $57.83.
      2. **Front side only**, which halves setup and stencil.
      3. **20 BOM lines instead of 23** after merging the caps.

      The $2.00 board price is the sub-100 mm promo tier and implies **HASL, not
      ENIG** — which is the right call anyway for this board. Monthly PCBA
      coupons did not materialise on this account; the PCB promo replaced them.

      At ~€85 the order is **under the €150 import threshold**, so it clears via
      IOSS without the customs handling fee. Budget ~25% Swedish VAT on top.

- [ ] **Upload BOM and CPL** — `production/bom.csv` as the BOM,
      `production/positions.csv` as the CPL (pick-and-place).

      Before uploading, open `bom.csv` and **count the lines**. Every part you
      expect to be fitted must be there. Anything marked `(dnp yes)` in the
      schematic will be missing without warning — that is the single easiest
      way to get a board back that does not work. Check the other direction
      too: a part that is *not* DNP but has no `MPN` comes out as a line with
      an empty `LCSC Part #`, which JLCPCB cannot source. Neither state is
      announced; both are silent.

      Expect **20 lines covering 47 parts**, 7 of them extended — that is what
      the schematic holds today, grouped by value + footprint + `MPN`. Confirm
      `C55109525` appears with a quantity of **10** (the inner ring only) and
      `C2944070` with **1**. A quantity of 30 on the LED line means the
      back-side DNP flags did not take.

- [ ] **Review part matching.** JLCPCB shows every line with the part it matched.
      Check each one, especially:
      - `U3` MCP73871 — confirm the `-2` variant
      - `U4` TLV75533PDBVR
      - `Q1` AO3401A
      - any line showing "out of stock" — substitute before continuing

- [ ] **Review the placement preview — this is the step that catches expensive
      mistakes.** JLCPCB renders every part on the board. Rotation errors are
      the most common assembly failure. Pay attention to:
      - `U3` — pin 1 orientation on the QFN
      - `U4`, `Q1` — SOT-23 packages
      - `D33`, `D35` — LED polarity
      - **`D1`–`D30` rotation — the biggest risk in this order, but it has been
        narrowed down to one number.** See the section below before you look.

- [ ] **The WS2812B rotation check — do this one properly.**

      **What was verified.** The footprint is project-local
      (`Library:LED_WS2812_5050_handsolder`), and the Fabrication Toolkit's
      correction database (`plugins/transformations.csv`) has **no entry
      matching it** — it covers `^SOT-23`, `^QFN-`, `^SOIC-` and similar, so
      `U3`/`U4`/`Q1` are handled, but the LEDs get no correction at all.

      That sounds alarming, but the placements were checked and they are clean:

      | | |
      |---|---|
      | Inner ring `D21`–`D30`, F.Cu | every LED at 90° to its own radius, spread 0.14° |
      | Outer ring `D1`–`D20`, B.Cu | every LED at 90° to its own radius, spread 0.07° |
      | `+LED_PWR` pads | one clean circle per ring — r = 18.8 mm front, r = 30.5 mm back |
      | Data chain | two clean chains, `D21→D30` and `D1→D20`, no breaks, uniform 6.0 mm and 5.6 mm hops |

      A revision of this file in between claimed the outer ring fanned through
      five orientations with a 324° spread. That was wrong. It came from
      mirroring back-side pad coordinates in the X axis when deriving the
      geometry — KiCad does not do that — and every conclusion drawn from it
      (irregular hops, scattered power radius, a forced zig-zag) was an artifact.
      Both rings are uniform.

      The toolkit applies `rotation = 180 - rotation` to bottom-side parts, which
      is the correct JLCPCB convention, and the numbers confirm it lands the
      bottom ring in agreement with the top. A pin-1 offset error would still hit
      all 30 LEDs equally, so the preview check below is the right check.

      **What this means:** there is no possibility of a per-LED error. Either
      all 30 are right, or all 30 are wrong by the *same* constant. So the
      preview check is quick:

      1. Look at any **one** LED on the top ring and any **one** on the bottom.
      2. Check DOUT of each faces DIN of the next one round the ring
         (pin 1 = VDD, 2 = DOUT, 3 = VSS, 4 = DIN).
      3. If they are right, all 30 are right. If one is rotated by 90/180/270,
         every LED needs that same offset.

      **If a correction is needed**, it does *not* mean hand-editing the CPL.
      The toolkit reads a per-part field called **`FT Rotation Offset`**
      (counter-clockwise degrees), so the fix lives in the schematic, survives
      regeneration, and gets committed. Ask Claude to apply it — one command
      sets it on all 30 — then re-run F8 and regenerate Phase 7.

      Do not pay until this reads correctly. Getting it wrong ruins all 150 LEDs.

      **Separately, about that footprint:** its pads are the hand-solder
      variant, extended past the LED's actual contacts and slightly asymmetric
      (pads 1/2 at x = −2.925 mm, pads 3/4 at +2.65 mm). More paste than a
      machine footprint would use, and the asymmetry pulls each part ~0.14 mm
      off centre. For a 5050 part with 0.9 mm pads that is tolerable — it was
      considered and deliberately left alone, since swapping the footprint
      would have disturbed the routing to all 30 LEDs on a crowded ring. Expect
      slightly off-centre parts; expect them to work.

- [ ] **Place the order.**

- [ ] **Note what you ordered** — add the order number and date at the bottom of
      this file so there is a record.

---

## Appendix

### Why not just VS Code extensions?

The KiCad extensions for VS Code are viewers and syntax helpers. They can render
a `.kicad_pcb` and highlight the s-expression syntax, but they cannot place
footprints, route traces, run DRC, fill zones, or export gerbers. Everything in
Phases 2–7 above requires the real application.

For what it is worth, the schematic changes so far were made by editing the file
format directly, which is why the netlist could be built and verified without
KiCad installed. That approach does not extend to layout — routing needs to see
the board.

Install the desktop app. Keep VS Code for the firmware and the app.

### Manual fallback if the Fabrication Toolkit plugin will not run

1. **Gerbers** — *File → Plot*. Format Gerber, output `Gerber/`. Layers:
   F.Cu, B.Cu, F.Paste, B.Paste, F.Silkscreen, B.Silkscreen, F.Mask, B.Mask,
   Edge.Cuts. Tick "Use extended X2 format". Click *Plot*, then
   *Generate Drill Files* (Excellon, separate PTH/NPTH file **off**, mm).
2. **Zip** everything in `Gerber/` — the `.gbr` files and the `.drl` files
   together, no enclosing folder.
3. **BOM** — schematic editor → *Tools → Generate BOM*. Needs columns
   `Comment` (value), `Designator`, `Footprint`, `LCSC Part #`.
4. **CPL** — PCB editor → *File → Fabrication Outputs → Component Placement*.
   CSV, mm, "use drill/place file origin" off. Needs columns
   `Designator`, `Mid X`, `Mid Y`, `Rotation`, `Layer`.

The last successful run's files are in `production/` — use them as a reference
for what the columns should look like.

### Things to watch on this specific board

- **`+SYS` and `+BATT` must stay separate.** Repeated because it is the failure
  mode that looks fine and is not.
- **`C20` DC bias.** A 22µF 0805 MLCC delivers noticeably less than 22µF at
  3.7 V — that is normal for ceramics, and already accounted for. If you see LED
  flicker on a low battery, add a second one in parallel.
- **Charger thermals.** At 500 mA charge current the MCP73871 dissipates around
  0.6 W worst case. The thermal vias under its exposed pad are what keep it
  cool — do not remove them, and let the ground pour reach them.
- **System load vs the charger's rating.** The MCP73871's internal BAT→SYS path
  is ~200 mΩ and the datasheet recommends keeping system load under 1 A. All 30
  LEDs at full white exceeds that. Limiting global brightness in firmware is the
  practical fix.

### Order history

| Date | Order # | Qty PCB | Qty assembled | Notes |
|---|---|---|---|---|
| 2026-09 | | 5 | 5, front only | Economic, 20 lines / 47 parts. $97.96 all in. `C26167850` LEDs. Outer ring + `C8`-`C15` + `J1` hand-soldered |
