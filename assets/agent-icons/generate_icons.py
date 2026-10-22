#!/usr/bin/env python3
"""
TALOS agent emblem generator.

Single source of truth for the CLI-agent emblems used by the landing page.
Emits, for every agent:
  * assets/agent-icons/<slug>.svg          - standalone colored vector (web / docs)
  * android/app/src/main/res/drawable/ic_<slug>.xml
                                        - Android VectorDrawable (white, tintable)
  * assets/agent-icons/manifest.json    - slug -> accent color mapping

Everything is pure vector: no emoji, no raster, no fonts.

Run:  python3 assets/agent-icons/generate_icons.py
"""

import json
import math
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID_RES = os.path.abspath(os.path.join(HERE, "..", "..", "android", "app", "src", "main", "res", "drawable"))

VIEWBOX = 24.0


# ---------------------------------------------------------------- helpers
def fmt(v):
    """Compact, deterministic number formatting."""
    s = f"{v:.3f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def poly(points, close=True):
    d = "M" + " L".join(f"{fmt(x)} {fmt(y)}" for x, y in points)
    return d + (" Z" if close else "")


def mirror_x(points):
    return [(VIEWBOX - x, y) for x, y in points]


def mirror_path(d):
    """Reflect a path's x-coordinates about the vertical center of the 24x24 box.

    Works on M/L/C style data (the only commands these emblems use): every
    command is followed by an even number of coordinates in x,y pairs.
    """
    tokens = re.findall(r"[A-Za-z]|-?\d*\.?\d+", d)
    out = []
    i = 0
    cmd = None
    while i < len(tokens):
        tok = tokens[i]
        if re.match(r"[A-Za-z]", tok):
            cmd = tok
            out.append(tok)
            i += 1
            continue
        # command with implicit repeated coordinates
        count = {"M": 2, "L": 2, "C": 6, "Z": 0, "H": 1, "V": 1}.get(str(cmd), 2)
        if cmd == "H":
            out.append(fmt(VIEWBOX - float(tok)))
            i += 1
            continue
        if cmd == "V":
            out.append(tok)
            i += 1
            continue
        if cmd == "Z":
            i += 1
            continue
        vals = [float(t) for t in tokens[i:i + count]]
        for j in range(0, count, 2):
            out.append(fmt(VIEWBOX - vals[j]))
            out.append(fmt(vals[j + 1]))
        i += count
    return " ".join(out)


def spiral_points(cx, cy, r_start, r_end, turns, steps=48, rot=0.0):
    """Archimedean spiral sampled into a point list."""
    pts = []
    total = turns * 2 * math.pi
    for i in range(steps + 1):
        t = total * i / steps
        r = r_start + (r_end - r_start) * (i / steps)
        a = t + rot
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def bezier(p0, p1, p2, p3, steps=24):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        x = u**3 * p0[0] + 3 * u**2 * t * p1[0] + 3 * u * t**2 * p2[0] + t**3 * p3[0]
        y = u**3 * p0[1] + 3 * u**2 * t * p1[1] + 3 * u * t**2 * p2[1] + t**3 * p3[1]
        pts.append((x, y))
    return pts


def tapered(points, w_start, w_end):
    """Build a closed tapered ribbon around a polyline (constant-ish thickness).

    Used for wings and talons: the shape is a filled polygon rather than a
    stroked line, so it can taper to a sharp point.
    """
    n = len(points)
    left, right = [], []
    for i, (x, y) in enumerate(points):
        if i == 0:
            dx, dy = points[1][0] - x, points[1][1] - y
        elif i == n - 1:
            dx, dy = x - points[-2][0], y - points[-2][1]
        else:
            dx, dy = points[i + 1][0] - points[i - 1][0], points[i + 1][1] - points[i - 1][1]
        ln = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / ln, dx / ln
        t = i / (n - 1)
        w = (w_start + (w_end - w_start) * t) / 2
        left.append((x + nx * w, y + ny * w))
        right.append((x - nx * w, y - ny * w))
    return poly(left + list(reversed(right)))


# ---------------------------------------------------------------- emblems
# Each emblem is a list of shapes:
#   {"d": path_data, "fill": True}              -> filled path
#   {"d": path_data, "fill": False, "w": 1.7}  -> stroked path (round cap/join)


def hermes():
    """Hermes Agent - winged H monogram (Nous Research emblem)."""
    shapes = [
        # H monogram (centered, x 9..15)
        {"d": "M9.0 5.2 L11.0 5.2 L11.0 11.0 L13.0 11.0 L13.0 5.2 L15.0 5.2 "
              "L15.0 18.8 L13.0 18.8 L13.0 13.4 L11.0 13.4 L11.0 18.8 L9.0 18.8 Z", "fill": True},
    ]
    # solid wing per side: smooth leading edge, three scalloped feather tips
    wing = (
        "M9.0 6.2 "
        "C6.2 4.3 3.4 3.0 0.8 2.6 "
        "C1.5 4.3 2.5 5.5 3.8 6.7 "
        "C2.5 7.1 1.5 7.5 0.5 7.8 "
        "C1.8 9.1 2.9 10.1 4.1 10.9 "
        "C3.3 11.5 2.5 11.9 1.7 12.2 "
        "C3.1 13.1 4.5 13.8 5.9 14.1 "
        "C7.1 14.3 8.2 14.0 9.0 13.5 "
        "Z"
    )
    shapes.append({"d": wing, "fill": True})
    shapes.append({"d": mirror_path(wing), "fill": True})
    return shapes


def claude():
    """Claude Code - Anthropic sunburst spark."""
    cx = cy = 12.0
    shapes = []
    long_rays = [0, 90, 180, 270]
    short_rays = [45, 135, 225, 315]
    for ang in long_rays + short_rays:
        a = math.radians(ang)
        length = 11.2 if ang in long_rays else 7.6
        half_w = 1.55 if ang in long_rays else 1.35
        r_in = 3.0
        tip = (cx + length * math.cos(a), cy + length * math.sin(a))
        b1 = (cx + r_in * math.cos(a - half_w / length * 1.15), cy + r_in * math.sin(a - half_w / length * 1.15))
        b2 = (cx + r_in * math.cos(a + half_w / length * 1.15), cy + r_in * math.sin(a + half_w / length * 1.15))
        # tangent the base so the ray reads as a tapered spike
        t = a + math.pi / 2
        w = half_w
        b1 = (cx + r_in * math.cos(a) + w * math.cos(t) * 0.55, cy + r_in * math.sin(a) + w * math.sin(t) * 0.55)
        b2 = (cx + r_in * math.cos(a) - w * math.cos(t) * 0.55, cy + r_in * math.sin(a) - w * math.sin(t) * 0.55)
        shapes.append({"d": poly([b1, tip, b2]), "fill": True})
    return shapes


def openclaw():
    """OpenClaw - three tapered talons."""
    # three hooked talons, each a tapered ribbon curving down to a sharp point
    talons = [
        ((5.6, 2.6), (2.6, 7.4), (2.4, 12.6), (7.4, 18.4), 3.1, 0.5),
        ((12.0, 1.6), (9.0, 6.6), (9.0, 12.8), (13.6, 19.2), 3.3, 0.5),
        ((18.4, 2.6), (21.4, 7.4), (21.6, 12.6), (16.6, 18.4), 3.1, 0.5),
    ]
    return [{"d": tapered(bezier(p0, p1, p2, p3, 30), w0, w1), "fill": True}
            for p0, p1, p2, p3, w0, w1 in talons]


def omnirush():
    """OmniRush - speed bolt."""
    return [
        {"d": "M13.9 1.2 L4.6 13.4 L10.8 13.4 L9.6 22.8 L19.6 9.6 L13.0 9.6 Z", "fill": True},
    ]


def core():
    """Core Agent - nucleus rings (cyan)."""
    shapes = [
        {"d": poly([(12.0, 9.5), (14.5, 12.0), (12.0, 14.5), (9.5, 12.0)], close=True), "fill": False, "w": 1.7},
        {"d": poly([(12.0, 3.2), (15.4, 6.2), (18.6, 12.0), (15.4, 17.8), (12.0, 20.8),
                    (8.6, 17.8), (5.4, 12.0), (8.6, 6.2)], close=True), "fill": False, "w": 1.3},
    ]
    return shapes


EMBLEMS = [
    ("hermes", "Hermes Agent", "#22C55E", hermes),
    ("claude", "Claude Code", "#D97757", claude),
    ("openclaw", "OpenClaw", "#F97316", openclaw),
    ("omnirush", "OmniRush", "#A855F7", omnirush),
    ("core", "Core Agent", "#38BDF8", core),
]


# ---------------------------------------------------------------- emitters
def to_svg(shapes, color):
    body = "\n".join(
        f'  <path d="{s["d"]}" fill="none" stroke="{color}" stroke-width="{s["w"]}" '
        f'stroke-linecap="round" stroke-linejoin="round"/>'
        if not s.get("fill", True)
        else f'  <path d="{s["d"]}" fill="{color}"/>'
        for s in shapes
    )
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24" '
        f'fill="none" role="img">\n'
        f"  <title>{EMBLEMS[0][1]}</title>\n"
        f"{body}\n</svg>\n"
    )


def to_vector_xml(shapes, name):
    body = "\n".join(
        f'    <path\n        android:fillColor="#FFFFFFFF"\n        android:pathData="{s["d"]}" />'
        if s.get("fill", True)
        else f'    <path\n        android:fillColor="#00000000"\n        android:strokeColor="#FFFFFFFF"\n'
             f'        android:strokeWidth="{s["w"]}"\n        android:strokeLineCap="round"\n'
             f'        android:strokeLineJoin="round"\n        android:pathData="{s["d"]}" />'
        for s in shapes
    )
    return (
        f'<?xml version="1.0" encoding="utf-8"?>\n'
        f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="24dp"\n    android:height="24dp"\n'
        f'    android:viewportWidth="24"\n    android:viewportHeight="24">\n{body}\n</vector>\n'
    )


def main():
    os.makedirs(HERE, exist_ok=True)
    os.makedirs(ANDROID_RES, exist_ok=True)
    manifest = {}
    for slug, label, color, fn in EMBLEMS:
        shapes = fn()
        # fix the SVG title tag (per-emblem label)
        svg = to_svg(shapes, color).replace(f"<title>{EMBLEMS[0][1]}</title>", f"<title>{label}</title>")
        svg_path = os.path.join(HERE, f"{slug}.svg")
        with open(svg_path, "w") as f:
            f.write(svg)
        xml_path = os.path.join(ANDROID_RES, f"ic_{slug}.xml")
        with open(xml_path, "w") as f:
            f.write(to_vector_xml(shapes, slug))
        manifest[slug] = {"label": label, "accent": color, "svg": f"assets/agent-icons/{slug}.svg",
                          "drawable": f"res/drawable/ic_{slug}.xml"}
        print(f"wrote {svg_path}\nwrote {xml_path}")
    with open(os.path.join(HERE, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")
    print("wrote", os.path.join(HERE, "manifest.json"))


if __name__ == "__main__":
    main()
