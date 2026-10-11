"""Fits batteryPercent() to a run-down log from the rundown firmware build.

Usage:
  python rundown_fit.py runs/2026-10-10.csv
  python rundown_fit.py runs/<new>.csv --compare runs/2026-10-10.csv [more.csv ...]
  options: --zero-mv 3300 (0% point) --load-ma 150 (assumed constant load)

At constant load, remaining charge is proportional to remaining time, so
percent(t) = (t_zero - t) / t_zero * 100, where t_zero is when the smoothed
voltage first reaches --zero-mv. Prints a summary and a drop-in C++ table for
batteryPercent(), and saves a plot next to the CSV. --compare overlays earlier
runs so a weakening cell shows up as a shorter, lower curve.
Needs numpy and matplotlib.
"""
import argparse
import csv
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

CUTOFF_FLAG = 0x80
LOW_BATTERY_STATE = 4
# The generic table batteryPercent() used before the first fit, for comparison.
GENERIC = [(4200, 100), (4150, 95), (4110, 90), (4080, 85), (4020, 80), (3980, 75),
           (3950, 70), (3910, 65), (3870, 60), (3850, 55), (3840, 50), (3820, 45),
           (3800, 40), (3790, 35), (3770, 30), (3750, 25), (3730, 20), (3710, 15),
           (3690, 10), (3610, 5), (3270, 0)]
TARGETS = [100, 95, 90, 85, 80, 75, 70, 65, 60, 55, 50, 45, 40, 35, 30, 25, 20,
           15, 10, 8, 6, 4, 2, 0]


def load_run(path, zero_mv):
    """Returns a dict with minutes, raw and smoothed mV, and run facts."""
    rows = [{k: int(v) for k, v in r.items()} for r in csv.DictReader(open(path))]
    # Anything after the cut-off sample was logged on a later power-up, not in this run.
    end = next((i for i, r in enumerate(rows) if r["flags"] & CUTOFF_FLAG), None)
    ignored = 0
    if end is not None:
        ignored = len(rows) - end - 1
        rows = rows[:end + 1]
    if len(rows) < 10:
        raise SystemExit(f"{path}: only {len(rows)} samples; nothing to fit.")

    # Continuous minutes across resets: a drop in uptime means the coaster rebooted.
    t, resets, offset = [], [], 0
    for i, r in enumerate(rows):
        if i and r["uptime_s"] < rows[i - 1]["uptime_s"]:
            resets.append(i)
            offset = t[-1] * 60 + 60 - r["uptime_s"]
        t.append((r["uptime_s"] + offset) / 60)
    t = np.array(t) - t[0]
    mv = np.array([r["mv"] for r in rows], float)
    smooth = np.array([np.median(mv[max(0, i - 2):i + 3]) for i in range(len(mv))])
    below = np.nonzero(smooth <= zero_mv)[0]
    lbo = next((i for i, r in enumerate(rows) if r["charger_state"] == LOW_BATTERY_STATE), None)
    return {
        "name": Path(path).stem, "t": t, "mv": mv, "smooth": smooth,
        "t_zero": t[below[0]] if len(below) else None, "cutoff": end is not None,
        "resets": resets, "ignored": ignored, "lbo": lbo,
    }


def fit_table(run, zero_mv):
    """Voltage at each target percent, read off the smoothed curve up to t_zero."""
    use = run["t"] <= run["t_zero"]
    pct = (run["t_zero"] - run["t"][use]) / run["t_zero"] * 100
    table = [(int(round(np.interp(p, pct[::-1], run["smooth"][use][::-1]))), p) for p in TARGETS]
    # 0% is the threshold by definition, not the first smoothed sample below it.
    table[-1] = (zero_mv, 0)
    # batteryPercent() needs strictly falling voltages.
    fixed = [table[0]]
    for v, p in table[1:]:
        fixed.append((min(v, fixed[-1][0] - 1), p))
    return fixed


def summary(run, zero_mv, load_ma):
    t, mv = run["t"], run["mv"]
    print(f"[{run['name']}] {len(t)} samples, {t[-1] / 60:.1f} h, {mv[0]:.0f} -> {mv[-1]:.0f} mV, "
          f"end: {'firmware cut-off' if run['cutoff'] else 'no cut-off sample (power lost or USB returned)'}")
    if run["ignored"]:
        print(f"  ignored {run['ignored']} sample(s) logged after the cut-off")
    print(f"  resets: {len(run['resets'])}" + (f" at samples {run['resets']}" if run["resets"] else ""))
    print(f"  LBO first seen: " + (f"{t[run['lbo']] / 60:.2f} h, {mv[run['lbo']]:.0f} mV" if run["lbo"] is not None else "never"))
    if run["t_zero"] is None:
        print(f"  never reached {zero_mv} mV (lowest {run['smooth'].min():.0f})")
    else:
        print(f"  {zero_mv} mV reached after {run['t_zero'] / 60:.2f} h; "
              f"usable capacity at ~{load_ma:.0f} mA: ~{load_ma * run['t_zero'] / 60:.0f} mAh")


ap = argparse.ArgumentParser()
ap.add_argument("csv")
ap.add_argument("--compare", nargs="*", default=[])
ap.add_argument("--zero-mv", type=int, default=3300)
ap.add_argument("--load-ma", type=float, default=150)
args = ap.parse_args()

run = load_run(args.csv, args.zero_mv)
others = [load_run(p, args.zero_mv) for p in args.compare]
for r in [run] + others:
    summary(r, args.zero_mv, args.load_ma)
if run["t_zero"] is None:
    raise SystemExit("The run did not get far enough to fit; lower --zero-mv or run longer.")

table = fit_table(run, args.zero_mv)
print("\nDrop-in table for batteryPercent():\n  static const uint16_t curve[][2] = {")
for i in range(0, len(table), 6):
    print("    " + ", ".join(f"{{{v}, {p}}}" for v, p in table[i:i + 6]) + ",")
print("  };")
by_pct = {p: v for v, p in table}
print(f"\nLOW_BATTERY_WARN_MV candidates: {by_pct[15]} mV (15%) or {by_pct[10]} mV (10%)")

fig, (a1, a2) = plt.subplots(1, 2, figsize=(12, 4.5))
for r in others:
    a1.plot(r["t"] / 60, r["smooth"], lw=1, color="0.6", label=f"{r['name']} (earlier)")
a1.plot(run["t"] / 60, run["mv"], ".", ms=2, color="0.8", label="raw")
a1.plot(run["t"] / 60, run["smooth"], lw=1.5, color="tab:blue", label=f"{run['name']}, median of 5")
a1.axhline(args.zero_mv, ls="--", color="tab:red", lw=1, label=f"0% = {args.zero_mv} mV")
for i in run["resets"]:
    a1.axvline(run["t"][i] / 60, color="tab:orange", lw=1)
a1.set(xlabel="hours on battery", ylabel="battery mV", title="Run-down under constant load")
a1.legend()
a2.plot([v for v, _ in GENERIC], [p for _, p in GENERIC], "o-", ms=3, color="0.6", label="generic Li-Po table")
for r in others:
    if r["t_zero"] is not None:
        t2 = fit_table(r, args.zero_mv)
        a2.plot([v for v, _ in t2], [p for _, p in t2], "-", lw=1, color="0.4", label=f"{r['name']} fit")
a2.plot([v for v, _ in table], [p for _, p in table], "o-", ms=3, color="tab:blue", label=f"{run['name']} fit")
a2.set(xlabel="battery mV", ylabel="percent", title="batteryPercent() table")
a2.invert_xaxis()
a2.legend()
fig.tight_layout()
out = Path(args.csv).with_suffix(".png")
fig.savefig(out, dpi=120)
print(f"Plot saved to {out}")
