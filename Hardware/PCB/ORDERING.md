# LED Coaster — PCB layout & JLCPCB ordering checklist

Working checklist for taking the reworked schematic (branch
`fix/charging-and-power-path`) through layout to a JLCPCB order.

**How to use this:** work top to bottom. Tick boxes as you go. The next unticked
box is the next thing to do. Notes and gotchas live under each step — read them
before doing the step, not after.

**Current status:** Phase 4 complete. All 34 parts placed and verified against
the board file — positions, angles, sides, courtyard clearance on both faces,
board outline, and pads against foreign-net tracks. The 13 stale copper items
and the 3 dead zones are gone; copper is 371 items and 15 zones.
Next: Phase 5. Read its routing note and the corrected Phase 8 note on the outer
ring before you start.

---

## Board facts (for reference)

| | |
|---|---|
| Outline | 90 mm circle, centre (150, 80) in PCB coordinates |
| Layers | 2 (F.Cu / B.Cu) |
| Components | both sides physically |
| Front (F.Cu) | 46 parts: ESP32 module, USB-C, power section, small ring `D21–D30` |
| Back (B.Cu) | 21 parts: large ring `D1–D20`, battery connector `J1` |
| Peak LED current | ~1.8 A theoretical (30 × WS2812B, full white) |
| Last order's assembly | single-sided, front only, 27 parts |
| **This order's assembly** | **double-sided, 83 parts — decided deliberately, see below** |

**Important — this order changes the assembly model.** The previous order was
*single-sided*: JLCPCB fitted 27 front-side parts, and the 30 WS2812Bs, the
ESP32 module `U1`, the battery connector `J1` and the UART header `J4` were
hand-soldered afterwards. Two mechanisms kept them out: they carry `(dnp yes)`
in the schematic, and they have no `MPN` field. Only parts that are *both*
populated and carry an `MPN` reach the generated BOM — a part failing either
test vanishes silently rather than appearing as a blank line.

For this order that was reversed for `D1–D30` and `U1`, so JLCPCB places the
whole LED ring and the ESP32 module. The reasoning: hand-soldering 150 WS2812Bs
across 5 boards is the highest-risk work in the project — the packages melt
before the solder flows, and a single damaged LED kills every LED downstream of
it in the data chain with no easy way to identify which one. The charger `U3`
is also now a QFN-20 with a thermal pad underneath, which cannot realistically
be hand-soldered at all.

Still hand-soldered, deliberately:

| Ref | Why |
|---|---|
| `J1` JST EH 2-pin | through-hole — not SMT-placeable |
| `J4` 1×04 header | through-hole, and a debug header you may not fit at all |
| `H1`/`H3`/`H4` | mounting holes, not parts |

To revert to single-sided, set `(dnp yes)` and clear `MPN` on `D1–D30` and `U1`.

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
  | `D1`–`D30` | WS2812B | `C2761795` | Worldsemi WS2812B-B/T, SMD5050-4P. **Standard assembly only**, MSL 5a, JLCPCB rates it "high" assembly difficulty |
  | `U1` | ESP32-C3-WROOM-02-H4 | `C2944070` | Espressif, SMD 20×18 mm. Economic *or* Standard |

  Two consequences of `C2761795` being **Standard-only**:

  1. The whole order moves from Economic to Standard PCBA. Double-sided
     assembly would have forced that anyway — Economic is single-side only —
     so the two decisions cost the same setup fee once.
  2. The parts are moisture-sensitive (MSL 5a) and get baked before placement.
     That is JLCPCB's problem, not yours, but it is why the part is flagged.

  There is a JLCPCB house-brand alternative, `C9900143998`, which *is* available
  for Economic assembly. It is a "new arrivals" part with no published datasheet
  link, so `C2761795` — the genuine Worldsemi part, 261k in stock — is the safer
  choice, and the sides decision means Economic is off the table regardless.

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

**What still needs connecting.** Measured by building the connected components of
every net — tracks, vias and zone fills together — with all 34 parts placed:

| Net | Pads | Separate groups | Connections needed |
|---|---|---|---|
| `+LED_PWR` | 46 | 41 | ~40 |
| `GND` | 110 | 48 | 47 isolated pads — mostly fixed by action 2 |
| `+SYS` | 9 | 9 | 8 |
| `+BATT` | 6 | 6 | 5 |
| `+5V` | 10 | 8 | pour handles most, see action 2 |
| `+3V3` | 10 | 3 | 2 — `U4` output into the existing group |
| `/BAT_SENSE` | 4 | 4 | 3 |
| LED data | — | — | 28 `DOUT → DIN` hops |

**The ring power was never a distribution.** An early revision of this file said
the 104 `+LED_PWR` copper items were a working ring feed worth keeping. They are
not — they are per-LED stubs a few millimetres long that were never joined up.
Nothing broke them; the committed file was simply never fully routed, exactly as
the data links never were. Keep the stubs as anchors, but expect to draw the ring.

**There is no constant power radius on the back.** On the front, all ten inner
LED `+LED_PWR` pads sit at exactly r = 18.8 mm, so that ring is a clean circle.
On the back the LED ring fans (see Phase 8), scattering its `+LED_PWR` pads from
r = 28.7 to 35.3 mm. The outer power ring has to zig-zag about 6.6 mm in and out
however you draw it. That is set by the LEDs, not by anything placed in Phase 4.

**The capacitors are clear of the ring.** All 19 outer-ring `+LED_PWR` hops pass
clear of every decoupling capacitor's GND pad, so no hop has to detour.

**Already routed — leave alone.** `+3V3` on the right of the board (8 pads in one
group), USB `D+`/`D-`, the buttons, and both ring *inputs* `/small_ring` and
`/large_ring` (ESP32 out to the first LED of each chain).

---

### 1. Set the trace widths

*File → Board Setup → Design Rules → Net Classes.*

| Net class | Nets | Width |
|---|---|---|
| Power-high | `+LED_PWR`, `+SYS` | **1.0 mm** |
| Power-med | `+BATT`, `+5V`, `+3V3` | 0.5 mm |
| Default | everything else | 0.25 mm |

At 1.8 A on 1 oz copper a 0.5 mm trace runs hot; 1.0 mm keeps the rise sensible.
Doing this first means you almost never touch the width dropdown afterwards.

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

### 4. Route the outer ring — power

19 hops, back side, `D1` → `D20`. Hop `+LED_PWR` from each LED's **pad 1** to the
next LED's **pad 1**. Power-high width. Widen the old 0.5 mm stubs as you join
them up. Keep it all on B.Cu — no vias needed.

### 5. Route the outer ring — data

19 hops, back side. From each LED's **pad 2** (DOUT) to the next LED's **pad 4**
(DIN), following `D1 → D20`. Default 0.25 mm. Same side as the LEDs.

These hops run 4–16 mm because of the ring fan — longer than they look like they
should be. That is expected.

### 6. Route the inner ring — power, then data

9 power hops and 9 data hops, front side, `D21` → `D30`, same pad rules as
actions 4 and 5. This ring is uniform, so every hop is a tidy ~6 mm.

### 7. Connect the twelve decoupling capacitors

Each cap's **pad 1** taps `+LED_PWR` at the nearest LED pad 1 — 2.3 to 6.9 mm
away, same side as the cap. **Pad 2 needs no trace**: it is `GND` and the pour
from action 2 (front) or the existing back pour picks it up.

### 8. Route what is left

`PROG1`, `PROG3`, `THERM`, `Q1` gate, `STAT1`/`STAT2` to `R5`/`R16` and on to
`D33`/`D35`, and `/BAT_SENSE` from the divider to `U1` pin 3. All Default width.

`D33`/`D35` are on the back and their anodes need `+5V`, which lives on the
front — so this step needs **4 vias**: both anodes up to `+5V`, and both `STAT`
nets through to `U3`.

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

Refill again after any later routing change.

---

## Phase 6 — DRC

- [ ] **Set manufacturing limits** — *File → Board Setup → Design Rules →
      Constraints*. Safe values for JLCPCB's standard (cheapest) process:

      - Minimum track width: **0.15 mm**
      - Minimum clearance: **0.15 mm**
      - Minimum via: 0.6 mm diameter / 0.3 mm drill
      - Minimum annular ring: 0.13 mm
      - Minimum hole-to-hole: 0.5 mm

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
      - Assembly side: **both sides** — changed from last order. `D1–D20` are on
        the back and are now in the BOM
      - PCBA type: **Standard** — forced, `C2761795` is not offered on Economic
      - Tooling holes: **added by JLCPCB**
      - Quantity: **5** — all of them. Decided deliberately: the ~$50 setup fee
        is paid once regardless, so boards 3–5 add only ~$20 of parts between
        them. Assembling fewer saves much less than it appears to.

      **Cost expectation.** Rough figures for 5 boards, from JLCPCB's published
      rates — confirm against the live quote, they change:

      | | |
      |---|---|
      | Setup, double-sided Standard | ~$50 (vs ~$25 single side, ~$8 Economic) |
      | ~795 extra joints @ ~$0.0017 | ~$1.35 |
      | 150 + attrition WS2812B @ $0.0762 | ~$13 |
      | 5 + attrition ESP32 modules @ $3.27 | ~$20 |
      | Extended-part loading, Standard | ~$1.50 per part type |

      That is roughly **$80 more than the old single-sided order**, of which
      ~$33 is parts you would have bought anyway. So the real assembly premium
      is ~$45 for 5 boards — about $9/board to not hand-solder 150 MSL-5a LEDs
      and 5 ESP32 modules. Note the joint count barely matters; the setup fee
      and the part cost are the whole story.

- [ ] **Upload BOM and CPL** — `production/bom.csv` as the BOM,
      `production/positions.csv` as the CPL (pick-and-place).

      Before uploading, open `bom.csv` and **count the lines**. Every part you
      expect to be fitted must be there. Anything with a blank `MPN` *or* marked
      `(dnp yes)` in the schematic will be missing without warning — that is the
      single easiest way to get a board back that does not work.

      Expect **26 lines covering 83 parts** — that is what the schematic holds
      today, grouped by value + footprint + `MPN`. Confirm `C2761795` appears with a
      quantity of **30** and `C2944070` with a quantity of **1**. If the
      WS2812B line is absent, the DNP flag did not clear.

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

      That sounds alarming, and the earlier reassurance here was wrong, so
      read this carefully.

      | | |
      |---|---|
      | Inner ring `D21`–`D30`, on F.Cu | genuinely uniform — every LED holds the same angle to its seat, `+LED_PWR` pads all land at exactly r = 18.8 mm |
      | Outer ring `D1`–`D20`, on B.Cu | **not uniform.** It carries front-side rotations while the parts sit on the back, so it fans through 5 orientations 36° apart, repeating every 5 LEDs |
      | Data chain | two clean chains, `D21→D30` and `D1→D20`, no breaks |

      An earlier revision claimed both rings were uniform to 0.1°. That used
      `rot + bearing`, which is the right invariant on the front and the wrong
      one on a mirrored layer. Measured with `rot - bearing`, the outer ring
      spreads **324°**.

      This is pre-existing — the previously fabricated board has the identical
      convention — and it is **not worth fixing**. Re-rotating all 20 to a
      consistent ring would give lovely uniform 5.5 mm data hops instead of
      today's 4–16 mm, but it collides with `H1`/`H3`/`H4`, `J1`, `C10` and
      `C14`. The mounting holes are fixed and validated against a built
      assembly, so the ring stays as it is.

      What this does **not** change: the toolkit applies
      `rotation = 180 - rotation` to bottom-side parts, which is the correct
      JLCPCB convention, and a global pin-1 offset error would still hit all 30
      LEDs equally. The preview check below is still the right check.

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
| | | | | |
