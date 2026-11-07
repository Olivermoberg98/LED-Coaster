# PCB

KiCad 10 project for the coaster board: a 90 mm round, two-layer board with an
ESP32-C3 module, a Li-Po charger with power path, and two rings of
WS2812-compatible LEDs, 10 on the front and 20 on the back.

| File | What it is |
|---|---|
| `LED_Coaster.kicad_pro` / `.kicad_sch` / `.kicad_pcb` | The project, schematic and board |
| `LED_Coaster.kicad_sym`, `Library.pretty/` | Project symbol and footprint libraries |
| `production/` | JLCPCB outputs from the Fabrication Toolkit plugin: gerber zip, BOM, placement, IPC netlist |
| `fabrication-toolkit-options.json` | The plugin's settings; `EXCLUDE DNP` must stay `true` |
| `tools/pcb_check.py` | Connectivity and stray-copper checker, see [below](#checking-the-board) |

## The board

| | |
|---|---|
| Outline | 90 mm circle, centre at (150, 80) mm in board coordinates |
| Layers | 2, with a `GND` pour on both |
| Front | ESP32-C3-WROOM-02 `U1`, USB-C `J3`, charger `U3`, LDO `U4`, LED-rail switch `Q1` and `SW1`, inner ring `D21`–`D30` with `C16`–`C19`, battery-sense divider, charge LED `D34` |
| Back | Outer ring `D1`–`D20` with decoupling `C8`–`C15`, battery connector `J1` |

**Power path.** USB-C 5 V feeds the MCP73871 charger `U3`, which powers the
system rail `+SYS` from USB or the cell and charges the cell at 500 mA.
`SW1` drives the gate of the P-FET `Q1`, which switches `+SYS` onto
`+LED_PWR` for the LEDs. The 3.3 V LDO `U4` takes its enable from
`+LED_PWR`, so switching `SW1` off powers down the ESP32 as well. Charging
still works with `SW1` off, because `U3` sits upstream of the switch.

**ESP32-C3 pins**

| GPIO | Net | Use |
|---|---|---|
| IO0 | `/small_ring` | Inner ring data, `D21` → `D30` |
| IO1 | `/large_ring` | Outer ring data, `D1` → `D20` |
| IO4 | `/BAT_SENSE` | Cell voltage through a 470k/470k divider and 100 nF |
| IO5, IO6, IO10 | `/STAT1`, `/STAT2`, `/PG` | Charger status, open-drain, read with internal pull-ups |
| IO7 | `D34` | Charge LED, through 1k to GND |
| IO18, IO19 | USB D−, D+ | Native USB: flashing and serial log over the USB-C port |
| IO20, IO21 | UART0 | `J4` header, not fitted |

**How `U3` is strapped.** USB-port mode (`SEL` low), 500 mA input limit
(`PROG2` high), 500 mA charge current (`R2` 2k), 50 mA termination (`R15`
20k), thermistor disabled (`R14` 10k to GND), and safety timer disabled
(`TE` high). The input limit (450 mA typical) is what governs, so the cell
charges at about 420 mA once the ESP32 has its share. With the timer off, both
status outputs low can only mean a temperature fault. The firmware decodes the states per datasheet Table 5-1;
the root [CLAUDE.md](../../CLAUDE.md) has the table.

## Rules for changing the design

- **`+SYS` and `+BATT` must never touch.** Shorting them defeats the power
  path: the board appears to work but charges wrongly.
- **Nothing may pull `STAT1`, `STAT2` or `PG` up to `+5V`.** They go straight
  to ESP32 pins rated 3.6 V. The board's original status LEDs did exactly
  that and were removed for this reason. `PG` is pseudo open-drain with a
  diode path to `IN`, so with USB unplugged the ESP32 pull-up back-feeds about
  58 µA into `+5V`. That is harmless.
- **The charger must be the `-2CC` variant** (`MCP73871-2CCI/ML`, LCSC
  `C5121473`), which regulates to 4.20 V and has LBO at 3.1 V. `C637761` is the
  `-4CA` variant: it charges to **4.40 V** and would overcharge a standard
  Li-Po.
- **Keep total system load near 1 A or below.** The charger's BAT→SYS path is
  about 200 mΩ. All 30 LEDs at full white draw about 1.08 A, enough to sag
  `+SYS` below the LDO's dropout on a part-discharged cell, so the firmware
  caps LED current.
- **Keep the thermal vias under `U3`, and keep the pour reaching them.** At
  about 420 mA into a low cell the charger dissipates up to about 0.8 W and
  gets hot.
- **Leave the netless zone on `B.Cu` at x 185–197, y 68–89.** It is the ESP32
  antenna keep-out.
- The battery-sense divider sits next to the ESP32, so the long trace is the
  low-impedance `+BATT` rather than the 235 kΩ divider node.
- `C20` is 22 µF. A bigger bulk capacitor does not help: droop on an LED step
  is set by the cell's resistance plus about 400 mΩ in the path, not by
  capacitance.

## Manufacturing

The first batch (September 2026) was 5 boards from JLCPCB with **Economic PCBA
on the front side only**: 20 BOM lines, 47 parts, about US$98 including
shipping. The back side is hand-soldered.

**LED part.** The LEDs are TUOZHAN **TZ-5050S2RGB-5V-I4-H1** (`C26167850`), a
WS2812B-compatible part. The BOM still calls them WS2812B. Every real WS2812B is
MSL 5a and rated for 240 °C peak, and JLCPCB refuses them for Economic
assembly, which reflows at 255 °C. Its part pages may list them as Economic, but
only the cart decides. The TZ part is MSL 3 and rated for 250 °C. It has the same
1 = VDD, 2 = DOUT, 3 = GND, 4 = DIN pinout, so it fits the footprint at 0°.

> **Do not substitute an SK6812 without a 180° rotation offset.** Its pinout
> is 1 = GND, 2 = DIN, 3 = VDD, 4 = DOUT. At 0° the supply is reversed and
> every LED on the board is destroyed at power-up.

**DNP.** The only thing that keeps a part out of the BOM is `(dnp yes)` on the
schematic symbol. A part without an `MPN` is not dropped; it appears as a line
with an empty LCSC number, which JLCPCB cannot source. The back-side parts,
`J1`, `J4`, `Btn1`–`Btn4` and the mounting holes are DNP. Set DNP in the
schematic, because *Update PCB from Schematic* overwrites a board-only change.

**Regenerating `production/`.** Run the Fabrication Toolkit plugin from the
board editor. Then check `bom.csv`: it should have 20 lines and 47 parts, the
LED line should have a quantity of **10** (30 means the DNP flags were lost),
and `U1` should appear once. In JLCPCB's placement preview, check one LED per
ring. All 30 share one orientation, so if one is right they all are. If one is
wrong, correct it with an `FT Rotation Offset` field on the symbols rather than
editing the placement file.

**DRC constraints** used for JLCPCB's standard process: track and clearance
0.15 mm, via 0.6 mm / 0.3 mm drill, annular ring 0.13 mm, hole-to-hole 0.5 mm,
**copper to edge 0.30 mm**, and **minimum through hole 0.20 mm**. KiCad's
default copper-to-edge of 0.0 mm never fires, and 0.30 mm minimum through hole
flags the 0.20 mm and 0.25 mm thermal vias inside the `U3` and `U1` footprints.

**Current DRC and ERC status.** DRC has 0 errors, 0 unconnected items and 0
schematic-parity issues. It reports 95 warnings, all cosmetic or library-related:
62 silkscreen over copper, 14 silkscreen overlaps, 2 silkscreen at the board
edge, 16 footprints that differ from the KiCad 10 library copies, and 1
missing library. ERC has 0 errors and 39 warnings, 35 of them off-grid wire
ends in the LED-ring wiring. The missing library is the ESP32-C3-WROOM-02's.
Its symbol and footprint are embedded in the schematic and board, so the design
is complete, but they are not in the project libraries yet.

## Hand-soldering

| Stage | Parts | What it adds |
|---|---|---|
| 1 | `J1` | Battery, so charging, BLE and the inner ring work |
| 2 | `D1`–`D20`, `C8`–`C15` (100 nF 0603) | The outer ring |

- `J1` is a JST-EH 2.5 mm connector: **pin 1 GND, pin 2 `+BATT`**. There is no
  reverse-polarity protection, so check a new battery lead with a meter first,
  and use a protected cell.
- TZ-5050S2RGB limits from its datasheet: iron at **≤ 315 °C, under 3 s per
  joint, once only**, and the tip must not touch the lens. To make that
  possible, the outer-ring LED and capacitor pads connect to the `GND` pour
  through thermal relief. Everywhere else the pour connects solid, for reflow.
- The outer ring is one data chain, `D1` → `D20`, fed from IO1 into `D1`
  pin 4. A dead or badly soldered LED leaves everything after it dark. If the
  ring lights up to `Dn`, look at `Dn`'s DOUT and `Dn+1`.
- The footprint is a hand-solder variant with extended, slightly asymmetric
  pads. Machine-placed LEDs sit about 0.14 mm off-centre, which is expected.
- `Btn1`–`Btn4` are not fitted. The firmware reads none of them, and `Btn3`
  (boot) and `Btn4` (enable) are not needed for flashing over native USB. A
  replacement must match the land pattern: two 1.80 × 1.10 mm pads plus two
  mechanical pads in an 8.6 × 9.3 mm envelope.

## Checking the board

`tools/pcb_check.py` reads the `.kicad_pcb` directly. It needs only the Python
standard library, no KiCad install. It answers what DRC does not: whether each
net is really one connected island, which pad is stranded, and whether stray
copper is left over. CI runs `all` on every push and fails on any problem.

```sh
cd Hardware/PCB/tools
python pcb_check.py all            # nets + zones + shorts + strays
python pcb_check.py net "+LED_PWR" # one net: groups and every copper item
python pcb_check.py rings          # LED chain continuity and ring uniformity
python pcb_check.py --help         # every subcommand
```

Refill the zones (<kbd>B</kbd>) and save before trusting any result, because
the checker reads the stored fill. The header of the script lists the parsing
traps it handles. The main one is that back-side pads are **not** mirrored in X.
