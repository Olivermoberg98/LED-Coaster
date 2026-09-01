# LED Coaster — PCB layout & JLCPCB ordering checklist

Working checklist for taking the reworked schematic (branch
`fix/charging-and-power-path`) through layout to a JLCPCB order.

**How to use this:** work top to bottom. Tick boxes as you go. The next unticked
box is the next thing to do. Notes and gotchas live under each step — read them
before doing the step, not after.

**Current status:** Phase 3 complete and verified — the 29 orphaned copper
items are gone, taking total copper from 413 to 384, and nothing else moved:
`+LED_PWR` still holds the 104-item ring distribution, `GND` 92, the ring
inputs and USB intact, 88 footprints, antenna keepout still in place. No
netless copper remains on the board.
Next: Phase 4, and read its note about the power section being off the board
before you start moving parts.

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

      That distribution is still correct and worth keeping — only its *source*
      end changes, from the battery to `Q1`'s drain. Deleting it would throw
      away the entire ring power distribution and force you to re-route 30
      LEDs by hand for no reason.

- [x] **Leave everything else alone** — USB, buttons and the ESP32 are
      untouched by this rework.

---

## Phase 4 — Placement

Place with <kbd>M</kbd> (move) and <kbd>R</kbd> (rotate). Press <kbd>F</kbd> to
flip a part to the other side of the board.

**The whole power section is off the board, not just the new parts.** Because
the section was redrawn in the schematic, F8 dropped every part in it into the
loose cluster — including ones that already had positions. 28 footprints now
sit outside the 90 mm outline: `R2`, `R5`, `R13`–`R19`, `C4`–`C15`, `C19`,
`C20`, `C21`, `Q1`, `U4`, `SW1`, `J1`. `U3`, `D33`, `D35` and `C16`–`C18` are
technically inside the circle but sit in the empty lower-left area, so they
need placing too.

Old positions worth reusing, from the pre-F8 board: `SW1` (136.3, 38.9),
`J1` (139.2, 59.1), `D33` (150.3, 79.8), `U4` (122.3, 56.6), `C4` (115.8, 55.7),
`C5` (115.8, 58.3), `C6` (140.2, 45.9), `C7` (145.4, 50.6), `R2` (137.8, 50.5),
`R5` (147.1, 79.8).

- [ ] **`J1` (battery connector)** — place this first, since `U3` and `C7` are
      positioned relative to it. Old spot was (139.2, 59.1).

- [ ] **`U3` (MCP73871, QFN-20 4×4 mm)** — put it where the two `SS34` diodes
      used to be, around (131, 48). Removing them freed exactly this pocket.
      Keep it close to `J1` (battery connector) and `C7`.

- [ ] **`U4` (TLV75533, SOT-23-5)** — where the old AMS1117 was, around
      (122, 57). It is much smaller now, so there is spare room.

- [ ] **`Q1` (AO3401A, SOT-23)** — between `U3`'s SYS output and where the LED
      rail leaves for the rings. This part carries the full LED current, so keep
      its path short.

- [ ] **`SW1`** — back to roughly (136, 39) on the board edge, where it was
      before F8 moved it. It only carries microamps now, so its traces are thin.

- [ ] **Charger support parts, all close to `U3`:**
      `R2` (PROG1), `R15` (PROG3), `R14` (THERM), `C4` (SYS bypass), `C7` (BAT
      bypass), `C6` (IN bypass). Short traces matter more for `C4`/`C6`/`C7`.

- [ ] **`R13`, `R17`** — near `Q1` (gate pull-up and rail pulldown).

- [ ] **`D33`, `R16` + `D35`** — the two charge-status LEDs. `D33` needs
      placing as well (old spot (150.3, 79.8)); put the pair side by side so
      both are visible.

- [ ] **`C20` (22µF bulk, 0805)** — right where `+LED_PWR` leaves `Q1` for the rings.

- [ ] **Battery sense `R18`, `R19`, `C21`** — `C21` should be close to the
      **ESP32's GPIO4 pin**, not to the divider. The divider itself can sit near
      the battery connector.

- [ ] **The 12 decoupling caps `C8–C19` — now put 8 of them on the BACK.**

      ⚠️ **This reverses earlier advice in this file.** The original plan kept
      all 12 on the front, specifically to avoid triggering double-sided
      assembly and roughly doubling the cost. That constraint no longer exists —
      the order is double-sided regardless, so putting caps on the back is free.

      Place them beside their LEDs rather than opposite them:

      - **8 caps** on the **back**, spread around radius ≈32 mm, directly next
        to `D1`–`D20`
      - **4 caps** on the **front**, among `D21`–`D30`

      Beside beats opposite: it removes two vias and the 1.6 mm return path from
      every decoupling loop. Press <kbd>F</kbd> to flip a part to the other side.

      Each cap goes between `+LED_PWR` and `GND`. Proximity is the whole point —
      a cap 20 mm from its LED does nothing useful.

---

## Phase 5 — Routing

⚠️ **Read this scope note first — the job is bigger than this file originally
implied.** The post-F8 board was analysed net by net. **38 nets need routing:**

| Group | Count | Notes |
|---|---|---|
| **LED data links** | **28** | every `DOUT → DIN` hop, both rings |
| Power path | 2 | `+BATT`, `+SYS` |
| Charger support | 7 | `PROG1`, `PROG3`, `THERM`, `STAT1`, `STAT2`, `Q1` gate, `D35` cathode |
| Battery sense | 1 | `/BAT_SENSE` |

The 28 LED data links are the surprise. **They were never routed in this file** —
not something F8 broke. Verified by analysing the previous commit: the data nets
had zero copper before F8 as well, and total board copper is 413 items in both.
Whatever was fabricated previously must have come from a different file state
than the one committed as "latest and greatest".

What *is* already routed and should be left alone: `GND` (92 items), the ring
power distribution now on `+LED_PWR` (104), `+3V3`, `+5V`, USB `D+`/`D-`, the
buttons, and both ring *inputs* (`/small_ring`, `/large_ring` — ESP32 out to the
first LED of each chain). 23 nets carry copper today.

Budget real time for the 28 data hops. They are short, adjacent-LED links around
two rings, so each one is quick — but there are 28 of them and they gate whether
the board works at all.

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

- [ ] **Route the 28 LED data links.** Each is a short hop from one LED's pad 2
      (DOUT) to the next LED's pad 4 (DIN), following the chains
      `D1 → D20` on the back and `D21 → D30` on the front. Default 0.25 mm is
      fine — these carry no current. Keep them on the same side as their LEDs so
      no vias are needed.

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

      That sounds alarming, but the placements were checked and they are clean:

      | | |
      |---|---|
      | Top ring `D21`–`D30` | all 10 sit at **270°** relative to their seat on the ring |
      | Bottom ring `D1`–`D20` | all 20 sit at **270°** relative to their seat, in the mirrored frame |
      | Spread within each ring | 0.1° (rounding) |
      | Data chain | two clean chains, `D21→D30` and `D1→D20`, no breaks |

      The toolkit applies `rotation = 180 − rotation` to bottom-side parts,
      which is the correct JLCPCB convention, and the numbers confirm it lands
      the bottom ring in agreement with the top.

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
