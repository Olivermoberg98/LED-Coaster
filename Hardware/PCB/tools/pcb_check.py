#!/usr/bin/env python3
"""Static analysis for LED_Coaster.kicad_pcb - connectivity, placement, clearance.

Reads the board file directly; no KiCad install or Python packages needed.
KiCad's own DRC is the authority on manufacturability. This exists to answer
questions DRC does not, and to answer them without a GUI:

  - which nets are still electrically split, and which pads are stranded
  - whether a placement matches an intended table of positions
  - whether foreign copper is sitting on a pad
  - what track widths each net actually got

Usage:  python pcb_check.py <subcommand> [board.kicad_pcb]

Subcommands:
  nets       per-net connected components; lists split nets and isolated pads
  net NAME   detail for one net: every group, and every copper item
  shorts     foreign copper overlapping a pad (true rotated-rectangle test)
  widths     track width histogram per net
  zones      copper zones: net, layers, priority, fill state
  placement  footprints: position, angle, side, off-board, courtyard overlaps
  rings      LED ring checks: chain continuity, orientation uniformity, pad radii
  all        nets + shorts + zones summary
"""

import math
import os
import re
import sys
import collections

DEFAULT_BOARD = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                             os.pardir, "LED_Coaster.kicad_pcb")

# Board geometry. Update if the outline ever changes.
CX, CY, R_BOARD = 150.0, 80.0, 45.0

# ---------------------------------------------------------------------------
# Gotchas this file exists to encode. Every one of these was learned the hard
# way; changing them without evidence will produce confident wrong answers.
#
# 1. Back-side pads are NOT mirrored in X. A footprint on B.Cu uses the same
#    local->board transform as one on F.Cu: rotate by -rot, then translate.
#    Mirroring X swaps pads left-for-right, which silently turns "pad 4" into
#    "pad 3" and invents shorts that do not exist.
# 2. A track end within a via's PAD radius is connected, not merely near it.
#    Vias here are 0.8 mm diameter, so a 0.2 mm gap to the centre is contact.
# 3. Zone fills carry a per-polygon (layer ...) tag. A single zone can span
#    F.Cu and B.Cu; you must match the polygon's layer, not the zone's first.
# 4. Two same-net zones whose fills touch are one island. Priority decides who
#    wins an overlap, so a higher-priority zone can orphan a lower one.
# 5. Pads sharing a number on one footprint (switch terminals, connector
#    shields) are internally connected. Treat them as one node.
# 6. Fine-pitch rectangular pads must not be approximated by their circum-
#    radius. On a 0.5 mm pitch QFN that over-reports clearance failures wildly.
# 7. Nor may a round pad be approximated by its bounding square: the corners
#    overstate it by a factor of sqrt(2). On an 8.6 mm M4 mounting-hole pad
#    that is 1.8 mm of copper that is not there, and it invents shorts.
#    Every pad here carries its corner radius and is traced as an outline.
# 8. A pad's (at x y ang) angle is ABSOLUTE - KiCad has already folded the
#    footprint's rotation into it. Adding the footprint rotation again turns
#    every pad on a rotated part by an extra rot; on this board that is 71 of
#    88 footprints, and it silently rotates fine-pitch pads across their
#    neighbours. Omitted means the pad matches the footprint's own rotation.
# ---------------------------------------------------------------------------


def _blocks(src, kw):
    """Yield balanced s-expressions '(kw ...)' from src."""
    out, i, pat = [], 0, "(" + kw
    while True:
        j = src.find(pat, i)
        if j < 0:
            return out
        if src[j + len(pat)] not in " \n\t":
            i = j + 1
            continue
        depth, k = 0, j
        while True:
            c = src[k]
            if c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0:
                    break
            elif c == '"':
                k += 1
                while src[k] != '"':
                    k += 1
            k += 1
        out.append(src[j:k + 1])
        i = k + 1


def _xf(x, y, rot, layer):
    """Local footprint coords -> board coords. No X mirror on B.Cu (gotcha 1)."""
    a = math.radians(-rot)
    ca, sa = math.cos(a), math.sin(a)

    def f(px, py):
        return (x + px * ca - py * sa, y + px * sa + py * ca)

    return f


def _rect(cx, cy, w, h, ang):
    a = math.radians(-ang)
    ca, sa = math.cos(a), math.sin(a)
    out = []
    for dx, dy in ((-w / 2, -h / 2), (w / 2, -h / 2), (w / 2, h / 2), (-w / 2, h / 2)):
        out.append((cx + dx * ca - dy * sa, cy + dx * sa + dy * ca))
    return out


# Points per 90 deg of corner arc when tracing a rounded pad outline.
_ARC = 4


def _pad_outline(cx, cy, w, h, ang, r):
    """Outline of a pad as a polygon, corners rounded by radius r (gotcha 7).

    r = 0 gives the plain rectangle; r = min(w, h) / 2 gives a circle or a
    stadium. Corners are traced with _ARC points each, so the polygon sits
    just inside the true arc - clearance comes out marginally pessimistic,
    never optimistic.
    """
    r = max(0.0, min(r, min(w, h) / 2.0))
    if r <= 1e-9:
        return _rect(cx, cy, w, h, ang)
    a = math.radians(-ang)
    ca, sa = math.cos(a), math.sin(a)
    ix, iy = w / 2.0 - r, h / 2.0 - r
    out = []
    for qx, qy, base in ((ix, iy, 0.0), (-ix, iy, 90.0),
                         (-ix, -iy, 180.0), (ix, -iy, 270.0)):
        for k in range(_ARC + 1):
            t = math.radians(base + 90.0 * k / _ARC)
            dx, dy = qx + r * math.cos(t), qy + r * math.sin(t)
            out.append((cx + dx * ca - dy * sa, cy + dx * sa + dy * ca))
    return out


def _pt_seg(p, a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    L = dx * dx + dy * dy
    t = 0.0 if L == 0 else max(0.0, min(1.0, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / L))
    return math.hypot(p[0] - (a[0] + dx * t), p[1] - (a[1] + dy * t))


def _seg_seg(p1, p2, p3, p4):
    d = (p2[0] - p1[0]) * (p4[1] - p3[1]) - (p2[1] - p1[1]) * (p4[0] - p3[0])
    if abs(d) > 1e-12:
        t = ((p3[0] - p1[0]) * (p4[1] - p3[1]) - (p3[1] - p1[1]) * (p4[0] - p3[0])) / d
        u = ((p3[0] - p1[0]) * (p2[1] - p1[1]) - (p3[1] - p1[1]) * (p2[0] - p1[0])) / d
        if 0 <= t <= 1 and 0 <= u <= 1:
            return 0.0
    return min(_pt_seg(p1, p3, p4), _pt_seg(p2, p3, p4),
               _pt_seg(p3, p1, p2), _pt_seg(p4, p1, p2))


def _in_poly(pt, poly):
    x, y = pt
    inside, j = False, len(poly) - 1
    for i in range(len(poly)):
        xi, yi = poly[i]
        xj, yj = poly[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi:
            inside = not inside
        j = i
    return inside


def _poly_seg_dist(poly, a, b):
    """0 if the segment touches the polygon, else the closest approach."""
    if _in_poly(a, poly) or _in_poly(b, poly):
        return 0.0
    best = 1e9
    for i in range(len(poly)):
        best = min(best, _seg_seg(poly[i], poly[(i + 1) % len(poly)], a, b))
    return best


def _pad_in_zone(pad, plat, zone):
    """Is a pad connected to a zone fill?

    Not a centre test: a thermally-relieved pad sits in a hole in the fill and
    is joined by narrow spokes that overlap the pad edge. Sample across the pad
    so a spoke landing anywhere on it counts. The outline supplies the edge
    points; the grid runs over the pad's core rect, which is inside the shape
    whatever the corner radius (gotcha 7).
    """
    core = pad["core"]
    for zl, poly in zone["fills"]:
        if not pad["th"] and zl != plat:
            continue
        for c in pad["poly"]:
            if _in_poly(c, poly):
                return True
        for i in range(5):
            for j in range(5):
                u, v = i / 4.0, j / 4.0
                ax = core[0][0] + (core[1][0] - core[0][0]) * u
                ay = core[0][1] + (core[1][1] - core[0][1]) * u
                bx = core[3][0] + (core[2][0] - core[3][0]) * u
                by = core[3][1] + (core[2][1] - core[3][1]) * u
                if _in_poly((ax + (bx - ax) * v, ay + (by - ay) * v), poly):
                    return True
    return False


class Board(object):
    def __init__(self, path):
        self.path = path
        self.src = open(path, encoding="utf-8").read()
        self._footprints()
        self._tracks()
        self._zones()

    # -- parsing ----------------------------------------------------------
    def _footprints(self):
        self.fps = {}
        for b in self.src.split("\n\t(footprint ")[1:]:
            ref = re.search(r'\(property "Reference" "([^"]+)"', b).group(1)
            m = re.search(r'\n\t\t\(at ([-0-9.]+) ([-0-9.]+)(?: ([-0-9.]+))?\)', b)
            x, y = float(m.group(1)), float(m.group(2))
            rot = float(m.group(3) or 0)
            layer = re.search(r'\n\t\t\(layer "([^"]+)"', b).group(1)
            val = re.search(r'\(property "Value" "([^"]*)"', b)
            lib = re.match(r'"([^"]+)"', b)
            f = _xf(x, y, rot, layer)
            pads, crt = [], []
            for seg in re.findall(r"\(fp_(?:line|poly|rect)\b(.*?)\n\t\t\)", b, re.S):
                if "CrtYd" not in seg:
                    continue
                for a, c in re.findall(r"\((?:start|end|xy) ([-0-9.]+) ([-0-9.]+)\)", seg):
                    crt.append((float(a), float(c)))
            for p in _blocks(b, "pad"):
                head = re.match(r'\(pad "([^"]*)" +(\w+) +(\w+)', p)
                num = head.group(1)
                if not num:
                    continue
                shape = head.group(3)
                pm = re.search(r"\(at ([-0-9.]+) ([-0-9.]+)(?: ([-0-9.]+))?\)", p)
                sz = re.search(r"\(size ([-0-9.]+) ([-0-9.]+)\)", p)
                rr = re.search(r"\(roundrect_rratio ([-0-9.]+)\)", p)
                net = re.search(r'\(net "([^"]*)"\)', p)
                fn = re.search(r'\(pinfunction "([^"]*)"', p)
                px, py = float(pm.group(1)), float(pm.group(2))
                # A pad's stored angle is absolute, not relative to the
                # footprint (gotcha 8). Omitted means "same as the footprint".
                ang = float(pm.group(3)) if pm.group(3) else rot
                w = float(sz.group(1)) if sz else 0.5
                h = float(sz.group(2)) if sz else 0.5
                if shape in ("circle", "oval"):
                    r = min(w, h) / 2.0
                elif shape == "roundrect":
                    r = float(rr.group(1)) * min(w, h) if rr else 0.0
                else:
                    r = 0.0
                bx, by = f(px, py)
                pads.append(dict(num=num, net=net.group(1) if net else "",
                                 fn=fn.group(1) if fn else "", x=bx, y=by,
                                 w=w, h=h, ang=ang, shape=shape, r=r,
                                 th="thru_hole" in p.split("\n")[0],
                                 core=_rect(bx, by, max(w - 2 * r, 0.0),
                                            max(h - 2 * r, 0.0), ang),
                                 poly=_pad_outline(bx, by, w, h, ang, r)))
            if crt:
                xs = [c[0] for c in crt]
                ys = [c[1] for c in crt]
                loc = (min(xs), min(ys), max(xs), max(ys))
            else:
                loc = None
            self.fps[ref] = dict(ref=ref, x=x, y=y, rot=rot, layer=layer,
                                 value=val.group(1) if val else "",
                                 lib=lib.group(1) if lib else "",
                                 pads=pads, loc=loc,
                                 dnp=bool(re.search(r"\(attr [^)]*dnp", b)))

    def _tracks(self):
        self.tracks = []
        cur, depth = None, 0
        for ln in self.src.split("\n"):
            t = ln.strip()
            if cur is None:
                if t in ("(segment", "(via", "(arc"):
                    cur = dict(kind=t[1:], net="", lay=[], pts=[], w=0.25)
                    depth = 0
                else:
                    continue
            depth += ln.count("(") - ln.count(")")
            m = re.match(r'\(net "([^"]*)"\)', t)
            if m:
                cur["net"] = m.group(1)
            m = re.match(r"\(width ([0-9.]+)\)", t)
            if m:
                cur["w"] = float(m.group(1))
            m = re.match(r"\(size ([0-9.]+)\)", t)
            if m and cur["kind"] == "via":
                cur["w"] = float(m.group(1))
            m = re.match(r"\(layers? (.*)\)$", t)
            if m:
                cur["lay"] = re.findall(r'"([^"]+)"', m.group(1))
            m = re.match(r'\(layer "([^"]+)"\)$', t)
            if m:
                cur["lay"] = [m.group(1)]
            for a, c in re.findall(r"\((?:start|end|at) ([-0-9.]+) ([-0-9.]+)\)", t):
                cur["pts"].append((float(a), float(c)))
            if depth <= 0:
                self.tracks.append(cur)
                cur = None

    def _zones(self):
        self.zones = []
        for z in _blocks(self.src, "zone"):
            if "keepout" in z:
                continue
            net = re.search(r'\(net "([^"]*)"\)', z)
            lay = re.search(r"\(layers? ([^\n]*)\)", z)
            pri = re.search(r"\(priority (\d+)\)", z)
            fills = []
            for fp in re.findall(r"\(filled_polygon(.*?)\n\t\t\)", z, re.S):
                lm = re.search(r'\(layer "([^"]+)"', fp)
                pts = [(float(a), float(b))
                       for a, b in re.findall(r"\(xy ([-0-9.]+) ([-0-9.]+)\)", fp)]
                if len(pts) > 2:
                    fills.append((lm.group(1) if lm else "?", pts))
            self.zones.append(dict(net=net.group(1) if net else "",
                                   layers=re.findall(r'"([^"]+)"', lay.group(1)) if lay else [],
                                   priority=int(pri.group(1)) if pri else 0,
                                   fills=fills))

    # -- helpers ----------------------------------------------------------
    def on_board(self, ref):
        f = self.fps[ref]
        return math.hypot(f["x"] - CX, f["y"] - CY) <= R_BOARD

    def net_pads(self, net):
        out = []
        for ref, f in self.fps.items():
            if not self.on_board(ref):
                continue
            for p in f["pads"]:
                if p["net"] == net:
                    out.append((ref, p, f["layer"]))
        return out

    def connectivity(self, net):
        """Connected components of one net. Returns list of pad-name lists."""
        segs = [t for t in self.tracks if t["net"] == net]
        pads = self.net_pads(net)
        zs = [z for z in self.zones if z["net"] == net]
        n = len(segs) + len(pads) + len(zs)
        par = list(range(n))

        def find(a):
            while par[a] != a:
                par[a] = par[par[a]]
                a = par[a]
            return a

        def uni(a, b):
            a, b = find(a), find(b)
            if a != b:
                par[a] = b

        S = lambda i: i
        P = lambda i: len(segs) + i
        Z = lambda i: len(segs) + len(pads) + i
        lay = lambda t: set(l.split(".")[0] for l in t["lay"])

        for i in range(len(segs)):
            for j in range(i + 1, len(segs)):
                if not (lay(segs[i]) & lay(segs[j])):
                    continue
                # gotcha 2: a via's pad radius counts as contact
                tol = 0.15
                if len(segs[i]["pts"]) == 1 or len(segs[j]["pts"]) == 1:
                    tol = max(segs[i]["w"], segs[j]["w"]) / 2 + 0.05
                if any(math.hypot(p[0] - q[0], p[1] - q[1]) < tol
                       for p in segs[i]["pts"] for q in segs[j]["pts"]):
                    uni(S(i), S(j))

        for pi, (ref, p, plat) in enumerate(pads):
            for si, t in enumerate(segs):
                if not p["th"] and plat.split(".")[0] not in lay(t):
                    continue
                pts = t["pts"]
                a, b = (pts[0], pts[1]) if len(pts) >= 2 else (pts[0], pts[0])
                # copper edge to copper edge: subtract the track/via radius
                d = _poly_seg_dist(p["poly"], a, b) - t["w"] / 2
                if d <= 0.01:
                    uni(P(pi), S(si))
            for zi, z in enumerate(zs):
                if _pad_in_zone(p, plat, z):
                    uni(P(pi), Z(zi))
            # gotcha 5: same pad number on one footprint = internally joined
            for pj in range(pi):
                if pads[pj][0] == ref and pads[pj][1]["num"] == p["num"]:
                    uni(P(pi), P(pj))

        for si, t in enumerate(segs):
            for zi, z in enumerate(zs):
                for zl, poly in z["fills"]:
                    if zl.split(".")[0] not in lay(t):
                        continue
                    if any(_in_poly(p, poly) for p in t["pts"]):
                        uni(S(si), Z(zi))
                        break

        # gotcha 4: same-net zones whose fills touch are one island
        for i in range(len(zs)):
            for j in range(i + 1, len(zs)):
                touch = False
                for l1, p1 in zs[i]["fills"]:
                    for l2, p2 in zs[j]["fills"]:
                        if l1 != l2:
                            continue
                        if any(math.hypot(a[0] - b[0], a[1] - b[1]) < 0.05
                               for a in p1 for b in p2):
                            touch = True
                            break
                    if touch:
                        break
                if touch:
                    uni(Z(i), Z(j))

        groups = collections.defaultdict(list)
        for pi, (ref, p, _) in enumerate(pads):
            groups[find(P(pi))].append("%s.%s" % (ref, p["num"]))
        return sorted((sorted(set(g)) for g in groups.values()), key=len, reverse=True)

    def all_nets(self):
        s = set()
        for ref, f in self.fps.items():
            if not self.on_board(ref):
                continue
            for p in f["pads"]:
                if p["net"] and not p["net"].startswith("unconnected-"):
                    s.add(p["net"])
        return sorted(s)


# ---------------------------------------------------------------------------
# subcommands
# ---------------------------------------------------------------------------

def cmd_nets(bd, args):
    split = []
    for net in bd.all_nets():
        pads = bd.net_pads(net)
        if len(pads) < 2:
            continue
        gr = bd.connectivity(net)
        if len(gr) > 1:
            split.append((net, len(pads), gr))
    if not split:
        print("every net with more than one pad is fully connected")
        return
    print("%-30s %5s %7s  %s" % ("NET", "PADS", "GROUPS", "STRANDED PADS"))
    for net, np_, gr in sorted(split, key=lambda z: -len(z[2])):
        iso = [g[0] for g in gr if len(g) == 1]
        print("%-30s %5d %7d  %s" % (net, np_, len(gr),
                                     ", ".join(iso)[:70] if iso else "(no singletons)"))


def cmd_net(bd, args):
    if not args:
        print("usage: net <NAME>")
        return
    net = args[0]
    gr = bd.connectivity(net)
    print("%s: %d pads in %d group(s)" % (net, len(bd.net_pads(net)), len(gr)))
    for g in gr:
        print("   [%2d] %s" % (len(g), ", ".join(g)))
    print()
    print("copper:")
    for t in bd.tracks:
        if t["net"] != net:
            continue
        pts = " -> ".join("(%.2f,%.2f)" % p for p in t["pts"])
        print("   %-6s %-46s w=%.2f  %s" % (t["kind"], pts, t["w"], ",".join(t["lay"])))


def cmd_shorts(bd, args):
    """Foreign copper overlapping a pad. True rotated-rect test (gotcha 6)."""
    hits = []
    for ref, f in bd.fps.items():
        if not bd.on_board(ref):
            continue
        for p in f["pads"]:
            for t in bd.tracks:
                if len(t["pts"]) < 2:
                    continue
                if not p["th"] and f["layer"] not in t["lay"]:
                    continue
                if t["net"] == p["net"]:
                    continue
                d = _poly_seg_dist(p["poly"], t["pts"][0], t["pts"][1]) - t["w"] / 2
                if d < 0:
                    hits.append((ref, p["num"], p["net"], t["net"], -d, t["pts"]))
    if not hits:
        print("no foreign copper overlapping any pad")
        return
    print("%d overlap(s):" % len(hits))
    for ref, num, pnet, tnet, ov, pts in sorted(hits, key=lambda z: -z[4]):
        print("   %-5s pad %-3s [%-20s] vs %-12s  %.3f mm  %s"
              % (ref, num, pnet, tnet or "NO-NET", ov,
                 " -> ".join("(%.2f,%.2f)" % q for q in pts)))


def cmd_widths(bd, args):
    per = collections.defaultdict(collections.Counter)
    for t in bd.tracks:
        if len(t["pts"]) < 2:
            continue
        per[t["net"]][t["w"]] += 1
    print("%-26s %s" % ("NET", "width: count"))
    for net in sorted(per, key=lambda n: -sum(per[n].values())):
        w = ", ".join("%.2f:%d" % (k, v) for k, v in sorted(per[net].items()))
        print("%-26s %s" % (net or "(no net)", w))


def cmd_zones(bd, args):
    print("%-10s %-16s %4s %6s  %s" % ("NET", "LAYERS", "PRI", "FILLS", "FILL BY LAYER"))
    for z in bd.zones:
        by = collections.Counter(l for l, _ in z["fills"])
        print("%-10s %-16s %4d %6d  %s"
              % (z["net"], ",".join(z["layers"]), z["priority"],
                 len(z["fills"]), dict(by) or "UNFILLED - press B"))


def cmd_placement(bd, args):
    off = [r for r in bd.fps if math.hypot(bd.fps[r]["x"] - CX, bd.fps[r]["y"] - CY) > R_BOARD]
    print("footprints: %d   off-board: %s" % (len(bd.fps), ", ".join(sorted(off)) or "none"))
    print()
    print("%-6s %-6s %9s %9s %7s  %s" % ("REF", "SIDE", "X", "Y", "ROT", "VALUE"))
    for ref in sorted(bd.fps):
        f = bd.fps[ref]
        print("%-6s %-6s %9.2f %9.2f %7.1f  %s"
              % (ref, f["layer"], f["x"], f["y"], f["rot"], f["value"][:28]))


def cmd_rings(bd, args):
    """LED ring sanity: chain continuity, orientation uniformity, pad radii."""
    def pad(ref, num):
        for p in bd.fps[ref]["pads"]:
            if p["num"] == num:
                return p
        return None

    for name, rng in (("outer D1-D20", range(1, 21)), ("inner D21-D30", range(21, 31))):
        refs = ["D%d" % n for n in rng if "D%d" % n in bd.fps]
        if not refs:
            continue
        ang, rad = [], []
        for ref in refs:
            f = bd.fps[ref]
            b = math.degrees(math.atan2(f["y"] - CY, f["x"] - CX))
            ang.append((((-f["rot"]) - b + 180) % 360) - 180)
            p1 = pad(ref, "1")
            if p1:
                rad.append(math.hypot(p1["x"] - CX, p1["y"] - CY))
        print("%s: %d LEDs" % (name, len(refs)))
        print("   orientation vs radius: %.1f to %.1f deg  (spread %.2f)"
              % (min(ang), max(ang), max(ang) - min(ang)))
        print("   +LED_PWR pad radius  : %.2f to %.2f mm" % (min(rad), max(rad)))
        openh = []
        for i in range(len(refs) - 1):
            p2 = pad(refs[i], "2")
            if not p2:
                continue
            if len(bd.connectivity(p2["net"])) > 1:
                openh.append("%s->%s" % (refs[i], refs[i + 1]))
        print("   data chain           : %d of %d hops connected%s"
              % (len(refs) - 1 - len(openh), len(refs) - 1,
                 "" if not openh else "  OPEN: " + ", ".join(openh)))
        print()


def cmd_all(bd, args):
    cmd_nets(bd, args)
    print()
    cmd_zones(bd, args)
    print()
    cmd_shorts(bd, args)


CMDS = dict(nets=cmd_nets, net=cmd_net, shorts=cmd_shorts, widths=cmd_widths,
            zones=cmd_zones, placement=cmd_placement, rings=cmd_rings, all=cmd_all)


def main():
    argv = sys.argv[1:]
    if not argv or argv[0] in ("-h", "--help", "help"):
        print(__doc__)
        return 0
    cmd = argv[0]
    if cmd not in CMDS:
        print("unknown subcommand %r; try --help" % cmd)
        return 2
    rest = argv[1:]
    board = DEFAULT_BOARD
    if rest and rest[-1].endswith(".kicad_pcb"):
        board = rest[-1]
        rest = rest[:-1]
    if not os.path.exists(board):
        print("board not found: %s" % board)
        return 2
    bd = Board(board)
    CMDS[cmd](bd, rest)
    return 0


if __name__ == "__main__":
    sys.exit(main())
