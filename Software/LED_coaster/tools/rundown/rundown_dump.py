"""Pulls the run-down log off the coaster over USB serial and saves it as CSV.

Usage: python rundown_dump.py [COM6] [runs/<today>.csv]
Needs the rundown build flashed, any serial monitor closed, and pyserial
(PlatformIO's own Python has it: ~/.platformio/penv/Scripts/python.exe).
"""
import datetime
import sys
import time
from pathlib import Path

import serial

port = sys.argv[1] if len(sys.argv) > 1 else "COM6"
default_out = Path(__file__).parent / "runs" / f"{datetime.date.today()}.csv"
out = Path(sys.argv[2]) if len(sys.argv) > 2 else default_out

with serial.Serial(port, 115200, timeout=1) as s:
    time.sleep(0.5)
    s.reset_input_buffer()
    s.write(b"dump\n")
    lines, started, deadline = [], False, time.time() + 30
    while time.time() < deadline:
        line = s.readline().decode(errors="replace").strip()
        if not line:
            continue
        if line.startswith("uptime_s,"):
            started = True
        if not started:
            continue
        if line == "end":
            break
        lines.append(line)
    else:
        sys.exit("No 'end' marker within 30 s; is the rundown build flashed?")

out.parent.mkdir(parents=True, exist_ok=True)
out.write_text("\n".join(lines) + "\n")
print(f"{len(lines) - 1} samples saved to {out}")
