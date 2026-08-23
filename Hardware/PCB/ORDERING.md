# LED Coaster — PCB layout & JLCPCB ordering checklist

Working checklist for taking the reworked schematic (branch
`fix/charging-and-power-path`) through layout to a JLCPCB order.

**How to use this:** work top to bottom. Tick boxes as you go. The next unticked
box is the next thing to do. Notes and gotchas live under each step — read them
before doing the step, not after.

**Current status:** Phase 1 — ERC clean (0 errors). Next: fill in the 4 missing LCSC part numbers, then F8.

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
| Last order's assembly | **single-sided, front only, 27 parts** |

**Important:** although parts sit on both sides, the previous order was
*single-sided* assembly. JLCPCB fitted 27 front-side parts; the 30 WS2812Bs,
the ESP32 module `U1`, the battery connector `J1` and the UART header `J4` were
hand-soldered afterwards. You can tell because none of them have an `MPN` field,
and only parts with an `MPN` reach the generated BOM.

Keep that split if you can — it is much cheaper. The one thing that has changed
is that the new charger `U3` is a QFN-20 with a thermal pad underneath, which
**cannot realistically be hand-soldered**. It must be machine-placed, and it is
on the front, so single-sided assembly still works.

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

- [ ] **Commit the format upgrade once it happens**

  After your first save in either editor:

  ```bash
  git add Hardware/PCB
  git commit -m "Upgrade KiCad project files to v10 format"
  ```

  Doing this as a separate commit keeps the real design changes readable in
  history later. The diff will look enormous — that is expected, KiCad rewrites
  the whole file.

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

- [ ] **Fill in the missing LCSC part numbers**

  **There is no BOM file to edit.** `production/bom.csv` is *output* from the
  last order and gets overwritten in Phase 7. The part number lives on the
  symbol in the schematic, in a field called **`MPN`**, and the BOM is generated
  from that.

  **This matters more than it looks:** a part with an empty `MPN` is *silently
  left out of the generated BOM entirely* — it does not appear as a blank line,
  it just vanishes, and JLCPCB never fits it. That is how the WS2812Bs and the
  ESP32 module were excluded last time (deliberately — they were hand-soldered).
  If you leave these six blank, the board comes back missing them.

  | Ref | Value | Package | Purpose if missing |
  |---|---|---|---|
  | `R13` | 100k | 0805 | FET gate pull-up — LEDs stuck on |
  | `R17` | 100k | 0805 | rail pulldown — LDO may not shut down |
  | `R15` | 20k | 0805 | charge termination — charging never ends properly |
  | `R18`, `R19` | 470k | 0805 | battery sense divider |
  | `C20` | 100µF | 1210 | LED bulk cap |

  `R13`/`R17` are the same part, and `R18`/`R19` are the same part, so it is
  four distinct components to find.

  **How to search.** Go to <https://jlcpcb.com/parts>, pick the category, then
  use the *filters* down the left rather than the text box — text search on
  values is unreliable. For the resistors: category *Resistors → Chip Resistor -
  Surface Mount*, then set **Package = 0805**, **Resistance =** the value you
  want, **Tolerance = ±1%**, and tick **Basic Part** and **In Stock**.

  Prefer **Basic** parts — Extended parts carry a one-off feeder fee of a few
  dollars each, per part type.

  Starting points worth verifying rather than trusting (stock and part numbers
  move, and I have not confirmed these against a live listing):

  - 100k 0805 1% — `0805W8F1003T5E`, listed under both `C149504` and `C17407`
  - 100µF 1210 X5R 6.3 V — `1210X5R107M6R3NT`, `C49326798`
  - 20k and 470k — search the filters as above

  Sanity check: this board's existing 0805 resistors are all UNI-ROYAL
  `0805W8F####T5E` (10k = `C17414`, 1k = `C17513`, 2k = `C17604`). Staying in
  that family keeps the BOM consistent.

  Then in KiCad: double-click the symbol → find the `MPN` field → paste the
  `Cxxxxx` code. Save.

- [ ] **Confirm these are still in stock** (they were when specified, stock moves)

  - `C637761` — MCP73871-2CCI/ML charger
  - `C404027` — TLV75533PDBVR regulator
  - `C15127` — AO3401A MOSFET (Basic part)
  - `C14663` — 100nF 0603 (Basic part, ×12)

  If the MCP73871 is gone, **any `MCP73871-2xxx` in the same QFN-20 package is
  pin-compatible** — the `-2` prefix is what matters (4.20 V cell).

---

## Phase 2 — Push the schematic into the PCB

- [ ] **Open the PCB editor** and run *Tools → Update PCB from Schematic* (<kbd>F8</kbd>)

  In the dialog, tick:
  - ☑ Delete footprints with no symbols  ← removes the old `D31`/`D32` diodes
  - ☑ Replace footprints with those specified in schematic  ← swaps `U3` and `U4`
  - ☐ Re-link footprints... *(leave off)*

  Click **Update PCB**. New footprints appear in a loose cluster near the board —
  that is normal.

- [ ] **Read the report** — it should list 19 added, 2 removed, 2 changed
  footprints. Anything unexpected, stop here.

---

## Phase 3 — Clear out the old power routing

The schematic changed the nets, but existing copper keeps its old net
assignment, so the whole power area is now wrong and DRC will scream. Easiest
approach is to delete it and re-route rather than trying to patch.

- [ ] **Delete the old power-section traces.** The area is roughly x 110–150,
      y 38–62 (upper-left of the board, around where the old charger sat).
      Select traces and press <kbd>Delete</kbd>. Use *Edit → Find* to jump to a
      reference if you get lost.

- [ ] **Delete the old `+BATT` traces that fed the LED rings.** These are the
      0.5 mm traces running out to `D1–D30` pin 1. That net is now `+LED_PWR`
      and comes from the FET instead.

  Tip: in the *Appearance* panel on the right, the **Nets** tab lets you
  highlight one net at a time. Highlight `+BATT` to see exactly what is left.

- [ ] **Leave everything else alone** — the LED data chain, USB, buttons and the
      ESP32 are untouched by this rework.

---

## Phase 4 — Placement

Place with <kbd>M</kbd> (move) and <kbd>R</kbd> (rotate). Press <kbd>F</kbd> to
flip a part to the other side of the board.

- [ ] **`U3` (MCP73871, QFN-20 4×4 mm)** — put it where the two `SS34` diodes
      used to be, around (131, 48). Removing them freed exactly this pocket.
      Keep it close to `J1` (battery connector) and `C7`.

- [ ] **`U4` (TLV75533, SOT-23-5)** — where the old AMS1117 was, around
      (122, 57). It is much smaller now, so there is spare room.

- [ ] **`Q1` (AO3401A, SOT-23)** — between `U3`'s SYS output and where the LED
      rail leaves for the rings. This part carries the full LED current, so keep
      its path short.

- [ ] **`SW1`** — leave it where it is. It only carries microamps now.

- [ ] **Charger support parts, all close to `U3`:**
      `R2` (PROG1), `R15` (PROG3), `R14` (THERM), `C4` (SYS bypass), `C7` (BAT
      bypass), `C6` (IN bypass). Short traces matter more for `C4`/`C6`/`C7`.

- [ ] **`R13`, `R17`** — near `Q1` (gate pull-up and rail pulldown).

- [ ] **`R16` + `D35`** — the second status LED. Put it next to the existing
      `D33` so the two charge-status LEDs sit together and are visible.

- [ ] **`C20` (100µF bulk)** — right where `+LED_PWR` leaves `Q1` for the rings.

- [ ] **Battery sense `R18`, `R19`, `C21`** — `C21` should be close to the
      **ESP32's GPIO4 pin**, not to the divider. The divider itself can sit near
      the battery connector.

- [ ] **The 12 decoupling caps `C8–C19` — keep all of them on the FRONT.**

      The obvious move is to put 8 on the back next to the large ring. Don't:
      that would force double-sided assembly and roughly double the assembly
      cost, for no real electrical gain.

      Instead place them on the front **directly opposite** their LED. The large
      ring sits at radius 32 mm on the back, and most of that circle is clear on
      the front — only the electronics cluster occupies part of it. A cap on the
      opposite face, right underneath its LED, is electrically about as good as
      one beside it: the return loop is two vias through 1.6 mm of board.

      - **8 caps** spread around radius ≈32 mm, opposite `D1–D20`, skipping the
        sector where the ESP32 and power section sit
      - **4 caps** among `D21–D30` on the front, normally

      Each cap goes between `+LED_PWR` and `GND`. Proximity is the whole point —
      a cap 20 mm from its LED does nothing useful.

---

## Phase 5 — Routing

Set trace width before drawing: the dropdown in the top toolbar, or
*File → Board Setup → Design Rules → Net Classes*.

- [ ] **Set up net classes** (saves a lot of manual width switching)

  | Net class | Nets | Width |
  |---|---|---|
  | Power-high | `+LED_PWR`, `+SYS` | **1.0 mm** |
  | Power-med | `+BATT`, `+5V`, `+3V3` | 0.5 mm |
  | Default | everything else | 0.25 mm |

  **Why 1.0 mm:** at 1.8 A on standard 1 oz copper, a 0.5 mm trace runs hot.
  1.0 mm keeps the temperature rise sensible. The existing `+BATT` traces were
  0.5 mm and were already marginal for this load.

- [ ] **Route the power path in this order** (most critical first):
      1. `U3` SYS → `Q1` source → `Q1` drain → `+LED_PWR` out to the rings
      2. `U3` BAT ↔ `J1` and `C7`
      3. `U3` IN ← `+5V` from USB
      4. `+SYS` → `U4` input, `U4` output → `+3V3`
      5. Everything thin: PROG/THERM resistors, gate, status LEDs, battery sense

- [ ] **Keep `+SYS` and `+BATT` as separate copper.** They must never touch.
      Shorting them silently recreates the original load-sharing bug — the board
      would appear to work and charge incorrectly. This is the single most
      important thing to get right in this layout.

- [ ] **Ground under the charger.** `U3`'s exposed pad must connect to ground —
      the footprint already includes thermal vias, so just make sure the pad's
      copper reaches the ground pour.

- [ ] **Refill zones** — press <kbd>B</kbd>. Do this after any routing change.

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
      - Assembly side: **top side only** — matches last order. The parts on
        the back (`D1–D20`, `J1`) have no `MPN`, so they are not in the BOM and
        you hand-solder them as before
      - Tooling holes: **added by JLCPCB**
      - Quantity: 2 to start. Assembly is where the money goes, and a first
        revision of a reworked power path is worth proving before committing to five.

- [ ] **Upload BOM and CPL** — `production/bom.csv` as the BOM,
      `production/positions.csv` as the CPL (pick-and-place).

      Before uploading, open `bom.csv` and **count the lines**. Every part you
      expect to be fitted must be there. Anything with a blank `MPN` in the
      schematic will be missing without warning — that is the single easiest way
      to get a board back that does not work.

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
      - the WS2812B rotations should be unchanged from your last order; if they
        look different from what worked before, something moved that shouldn't have

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
- **`C20` DC bias.** A 100µF 1210 MLCC delivers noticeably less than 100µF at
  3.7 V — that is normal for ceramics. If you see LED flicker on a low battery,
  add a second one in parallel.
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
