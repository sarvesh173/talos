#!/usr/bin/env python3
"""
SVG -> Android VectorDrawable converter for the TALOS provider emblems.

The emblems are *authentic* brand artwork: the source SVGs in
`assets/agent-icons/svg/` are the official marks published by each vendor
(Anthropic, OpenAI, Google, Meta, Mistral, DeepSeek, Alibaba/Qwen, Nous
Research, OpenClaw, OmniRush, ...).

This converter preserves that authenticity instead of flattening everything to
a single monochrome silhouette:

  * every `<path>` keeps its own `android:fillColor`, so multi-colour artwork
    (Gemini's blue/green/red/yellow star, the OpenClaw lobster, the Meta AI
    swirl, the Nous Research wing) survives intact;
  * `stroke` maps to `android:strokeColor` / `android:strokeWidth`;
  * `linearGradient` / `radialGradient` fills map onto
    `<aapt:attr name="android:fillColor"><gradient>`, which Android's
    VectorDrawable supports from API 24 (this app's minSdk is 26);
  * `fill-opacity` / `opacity` become `android:fillAlpha`;
  * `transform` on groups *and* on individual paths is flattened into the
    emitted path data.

Because the artwork carries real colour, the Compose side must NOT apply a
solid `ColorFilter`. `AgentEmblemIcon` therefore keeps `tint = null` by
default: a null tint means Compose applies no colour filter at all and the
vector's own multi-colour artwork is what the user sees.

Usage:
    python3 assets/agent-icons/svg_to_vector.py --all
    python3 assets/agent-icons/svg_to_vector.py --only anthropic openai
"""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
DRAWABLE_DIR = os.path.join(REPO, "android", "app", "src", "main", "res", "drawable")
MANIFEST = os.path.join(HERE, "manifest.json")

ANDROID_NS = "http://schemas.android.com/apk/res/android"
AAPT_NS = "http://schemas.android.com/aapt"

ET.register_namespace("android", ANDROID_NS)
ET.register_namespace("aapt", AAPT_NS)

NUM_RE = re.compile(r"[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?")
CMD_RE = re.compile(r"[MmZzLlHhVvCcSsQqTtAa]")
IDENTITY = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)


# --------------------------------------------------------------------- colour
def parse_color(value: str | None, default: str = "#000000") -> str:
    """Normalise any SVG colour string to an #AARRGGBB literal."""
    if value is None:
        value = default
    value = value.strip()
    if not value or value.lower() == "none":
        return "#00000000"
    if value.lower() == "currentcolor":
        return "#FF000000"
    if value.startswith("#"):
        h = value[1:]
        if len(h) in (3, 4):
            h = "".join(c * 2 for c in h)
            return "#" + h.upper()
        if len(h) == 6:
            return "#FF" + h.upper()
        if len(h) == 8:
            return "#" + h.upper()
        raise ValueError(f"unsupported hex colour: {value}")
    named = {
        "black": "#FF000000", "white": "#FFFFFFFF", "red": "#FFFF0000",
        "green": "#FF008000", "blue": "#FF0000FF", "yellow": "#FFFFFF00",
        "cyan": "#FF00FFFF", "magenta": "#FFFF00FF", "gray": "#FF808080",
        "grey": "#FF808080", "orange": "#FFFFA500", "purple": "#FF800080",
        "pink": "#FFFFC0CB", "brown": "#FFA52A2A", "lime": "#FF00FF00",
        "navy": "#FF000080", "teal": "#FF008080", "silver": "#FFC0C0C0",
        "gold": "#FFFFD700", "transparent": "#00000000",
    }
    key = value.lower()
    if key in named:
        return named[key]
    if key.startswith("rgb("):
        nums = [float(n) for n in NUM_RE.findall(key[4:-1])]
        r, g, b = (int(max(0, min(255, round(n)))) for n in nums[:3])
        a = 255
        if len(nums) >= 4:
            a = int(max(0, min(255, round(nums[3] * 255))))
        return f"#{a:02X}{r:02X}{g:02X}{b:02X}"
    raise ValueError(f"unsupported colour: {value}")


def apply_opacity(argb: str, opacity: float) -> str:
    if opacity >= 0.999:
        return argb
    a = int(argb[1:3], 16)
    return f"#{int(round(a * opacity)):02X}{argb[3:]}"


# ------------------------------------------------------------------ path data
def _nums(s: str):
    return [float(x) for x in NUM_RE.findall(s)]


def normalize_path(d: str) -> str:
    """Rewrite a path 'd' string into an explicit, Android-safe form.

    Android's VectorDrawable path parser is stricter than a browser's: it
    rejects a few shorthand forms and does not accept scientific notation. We
    therefore expand every command into its full, explicit argument list and
    normalise all numbers to plain decimals.
    """
    tokens: list[str] = []
    for chunk in re.split(r"([MmZzLlHhVvCcSsQqTtAa])", d or ""):
        chunk = chunk.strip()
        if chunk:
            tokens.append(chunk)

    out: list[str] = []
    i = 0
    cmd = None
    cx = cy = 0.0
    sx = sy = 0.0
    last_cx = last_cy = 0.0
    last_qx = last_qy = 0.0

    def fmt(v: float) -> str:
        s = f"{v:.4f}".rstrip("0").rstrip(".")
        return "0" if s in ("", "-0") else s

    def emit(letter: str, vals):
        out.append(letter + " " + " ".join(fmt(v) for v in vals))

    while i < len(tokens):
        tok = tokens[i]
        if CMD_RE.fullmatch(tok):
            cmd = tok
            i += 1
            if cmd in "Zz":
                cx, cy = sx, sy
                out.append("Z")
                continue
        if cmd is None:
            i += 1
            continue

        rel = cmd.islower()
        upper = cmd.upper()

        def take(n):
            """Consume n numbers starting at i, skipping over command tokens."""
            nonlocal i
            vals = []
            while i < len(tokens) and len(vals) < n:
                if CMD_RE.fullmatch(tokens[i]):
                    break
                vals += _nums(tokens[i])
                i += 1
            return vals[:n]

        if upper == "M":
            vals = take(2)
            if len(vals) < 2:
                break
            x, y = vals
            if rel:
                x, y = cx + x, cy + y
            cx, cy = x, y
            sx, sy = x, y
            emit("M", [x, y])
            cmd = "L"
            rest = take(1000)
            for k in range(0, len(rest) - 1, 2):
                px, py = rest[k], rest[k + 1]
                if rel:
                    px, py = cx + px, cy + py
                cx, cy = px, py
                emit("L", [px, py])
        elif upper == "L":
            vals = take(2)
            if len(vals) < 2:
                break
            x, y = vals
            if rel:
                x, y = cx + x, cy + y
            cx, cy = x, y
            emit("L", [x, y])
        elif upper == "H":
            vals = take(1)
            if not vals:
                break
            x = vals[0]
            if rel:
                x = cx + x
            cx = x
            emit("L", [x, cy])
        elif upper == "V":
            vals = take(1)
            if not vals:
                break
            y = vals[0]
            if rel:
                y = cy + y
            cy = y
            emit("L", [cx, y])
        elif upper in ("C", "S", "Q", "T"):
            count = {"C": 6, "S": 4, "Q": 4, "T": 2}[upper]
            vals = take(count)
            if len(vals) < count:
                break
            if rel:
                vals = [(vals[k] + cx if k % 2 == 0 else vals[k] + cy) for k in range(count)]
            if upper == "C":
                c1 = (vals[0], vals[1])
                c2 = (vals[2], vals[3])
                end = (vals[4], vals[5])
                emit("C", [c1[0], c1[1], c2[0], c2[1], end[0], end[1]])
                last_cx, last_cy = c2
                cx, cy = end
            elif upper == "S":
                c2 = (vals[0], vals[1])
                end = (vals[2], vals[3])
                c1 = (2 * cx - last_cx, 2 * cy - last_cy)
                emit("C", [c1[0], c1[1], c2[0], c2[1], end[0], end[1]])
                last_cx, last_cy = c2
                cx, cy = end
            elif upper == "Q":
                q = (vals[0], vals[1])
                end = (vals[2], vals[3])
                emit("Q", [q[0], q[1], end[0], end[1]])
                last_qx, last_qy = q
                cx, cy = end
            else:  # T
                end = (vals[0], vals[1])
                q = (2 * cx - last_qx, 2 * cy - last_qy)
                emit("Q", [q[0], q[1], end[0], end[1]])
                last_qx, last_qy = q
                cx, cy = end
        elif upper == "A":
            vals = take(7)
            if len(vals) < 7:
                break
            rx, ry, rot, large, sweep, x, y = vals
            if rel:
                x, y = cx + x, cy + y
            emit("A", [rx, ry, rot, large, sweep, x, y])
            cx, cy = x, y
        else:
            i += 1

    return " ".join(out)


# ------------------------------------------------------------------- matrix
def mul(a, b):
    """Multiply two 2x3 affine matrices; `a` is applied after `b`."""
    return (
        a[0] * b[0] + a[2] * b[1],
        a[1] * b[0] + a[3] * b[1],
        a[0] * b[2] + a[2] * b[3],
        a[1] * b[2] + a[3] * b[3],
        a[0] * b[4] + a[2] * b[5] + a[4],
        a[1] * b[4] + a[3] * b[5] + a[5],
    )


def parse_transform(value: str):
    """Parse an SVG transform attribute into a 2x3 affine matrix."""
    m = IDENTITY
    for name, args in re.findall(r"([a-zA-Z]+)\s*\(([^)]*)\)", value or ""):
        n = _nums(args)
        if name == "matrix" and len(n) == 6:
            t = tuple(n)
        elif name == "translate" and n:
            t = (1, 0, 0, 1, n[0], n[1] if len(n) > 1 else 0)
        elif name == "scale" and n:
            sy = n[1] if len(n) > 1 else n[0]
            t = (n[0], 0, 0, sy, 0, 0)
        elif name == "rotate" and n:
            ang = math.radians(n[0])
            cos, sin = math.cos(ang), math.sin(ang)
            t = (cos, sin, -sin, cos, 0, 0)
            if len(n) >= 3:
                cx, cy = n[1], n[2]
                t = mul((1, 0, 0, 1, cx, cy), mul(t, (1, 0, 0, 1, -cx, -cy)))
        elif name == "skewX" and n:
            t = (1, 0, math.tan(math.radians(n[0])), 1, 0, 0)
        elif name == "skewY" and n:
            t = (1, math.tan(math.radians(n[0])), 0, 1, 0, 0)
        else:
            continue
        m = mul(m, t)
    return m


def apply_matrix(d: str, m) -> str:
    """Apply an affine matrix to already-normalised path data."""
    if m == IDENTITY:
        return d
    a, b, c, dd, e, f = m
    tokens = d.split()
    out: list[str] = []
    i = 0
    cmd = None
    while i < len(tokens):
        t = tokens[i]
        if CMD_RE.fullmatch(t):
            cmd = t
            i += 1
            if cmd == "Z":
                out.append("Z")
                continue
        if cmd is None:
            i += 1
            continue
        n = {"M": 2, "L": 2, "C": 6, "Q": 4, "A": 7}[cmd.upper()]
        vals = [float(x) for x in tokens[i:i + n]]
        if len(vals) < n:
            break
        i += n
        if cmd.upper() == "A":
            out.append(cmd)
            out.extend(f"{v:g}" for v in vals)
            continue
        out.append(cmd)
        for k in range(0, n, 2):
            x, y = vals[k], vals[k + 1]
            nx = a * x + c * y + e
            ny = b * x + dd * y + f
            out.append(f"{nx:.4f}".rstrip("0").rstrip(".") or "0")
            out.append(f"{ny:.4f}".rstrip("0").rstrip(".") or "0")
    return " ".join(out)


# ---------------------------------------------------------------- gradients
def gradient_to_android(grad_el, viewport):
    """Build an Android `<gradient>` element from an SVG gradient element.

    Android's VectorDrawable gradients are always expressed in viewport
    coordinates, which matches SVG's `userSpaceOnUse`. Gradient stops carry
    their own alpha, so a `stop-opacity` is folded into the item colour.
    """
    kind = "radial" if grad_el.tag.endswith("radialGradient") else "linear"
    units = grad_el.get("gradientUnits", "objectBoundingBox")

    stops = []
    for child in grad_el:
        if not child.tag.endswith("stop"):
            continue
        offset = child.get("offset", "0")
        offset = float(offset.rstrip("%")) / 100.0 if offset.endswith("%") else float(offset)
        color = parse_color(child.get("stop-color", "#000000"))
        stop_op = child.get("stop-opacity")
        if stop_op is not None:
            color = apply_opacity(color, float(stop_op))
        stops.append((offset, color))
    if not stops:
        return None
    stops.sort(key=lambda s: s[0])

    vx0, vy0, vx1, vy1 = viewport

    def coord(name, default):
        raw = grad_el.get(name)
        if raw is None:
            return default
        if raw.endswith("%"):
            pct = float(raw.rstrip("%")) / 100.0
            # objectBoundingBox percentages are relative to the viewport box.
            return vx0 + pct * (vx1 - vx0) if kind == "linear" else vy0 + pct * (vy1 - vy0)
        return float(raw)

    grad = ET.Element(f"{{{AAPT_NS}}}gradient")
    ET.SubElement(grad, f"{{{ANDROID_NS}}}type").text = kind
    if kind == "linear":
        ET.SubElement(grad, f"{{{ANDROID_NS}}}startX").text = f"{coord('x1', vx0):g}"
        ET.SubElement(grad, f"{{{ANDROID_NS}}}startY").text = f"{coord('y1', vy0):g}"
        ET.SubElement(grad, f"{{{ANDROID_NS}}}endX").text = f"{coord('x2', vx1):g}"
        ET.SubElement(grad, f"{{{ANDROID_NS}}}endY").text = f"{coord('y2', vy0):g}"
    else:
        ET.SubElement(grad, f"{{{ANDROID_NS}}}centerX").text = f"{coord('cx', (vx0 + vx1) / 2):g}"
        ET.SubElement(grad, f"{{{ANDROID_NS}}}centerY").text = f"{coord('cy', (vy0 + vy1) / 2):g}"
        ET.SubElement(grad, f"{{{ANDROID_NS}}}gradientRadius").text = f"{coord('r', (vx1 - vx0) / 2):g}"

    for offset, color in stops:
        item = ET.SubElement(grad, f"{{{ANDROID_NS}}}item")
        item.set(f"{{{ANDROID_NS}}}offset", f"{offset:g}")
        item.set(f"{{{ANDROID_NS}}}color", color)
    return grad


# ------------------------------------------------------------------ shapes
def shape_to_path(tag: str, el):
    def f(name, default=0.0):
        raw = el.get(name)
        return float(raw) if raw is not None else default

    if tag == "path":
        return el.get("d")
    if tag == "rect":
        x, y, w, h = f("x"), f("y"), f("width"), f("height")
        rx, ry = f("rx"), f("ry")
        if not rx and el.get("ry"):
            rx = float(el.get("ry"))
        if not ry and el.get("rx"):
            ry = float(el.get("rx"))
        if rx and ry:
            return (
                f"M{x + rx},{y} H{x + w - rx} A{rx},{ry} 0 0 1 {x + w},{y + ry} "
                f"V{y + h - ry} A{rx},{ry} 0 0 1 {x + w - rx},{y + h} "
                f"H{x + rx} A{rx},{ry} 0 0 1 {x},{y + h - ry} "
                f"V{y + ry} A{rx},{ry} 0 0 1 {x + rx},{y} Z"
            )
        return f"M{x},{y} H{x + w} V{y + h} H{x} Z"
    if tag == "circle":
        cx, cy, r = f("cx"), f("cy"), f("r")
        return f"M{cx - r},{cy} A{r},{r} 0 1 0 {cx + r},{cy} A{r},{r} 0 1 0 {cx - r},{cy} Z"
    if tag == "ellipse":
        cx, cy, rx, ry = f("cx"), f("cy"), f("rx"), f("ry")
        return f"M{cx - rx},{cy} A{rx},{ry} 0 1 0 {cx + rx},{cy} A{rx},{ry} 0 1 0 {cx - rx},{cy} Z"
    if tag in ("polygon", "polyline"):
        nums = _nums(el.get("points", ""))
        if len(nums) < 4:
            return None
        pts = [(nums[i], nums[i + 1]) for i in range(0, len(nums) - 1, 2)]
        d = "M" + " L".join(f"{x:g},{y:g}" for x, y in pts)
        return d + (" Z" if tag == "polygon" else "")
    if tag == "line":
        return f"M{f('x1')},{f('y1')} L{f('x2')},{f('y2')}"
    return None


def style_of(el):
    """Resolve fill/stroke/opacity for an element, honouring `style`."""
    fill = el.get("fill")
    stroke = el.get("stroke")
    fill_opacity = el.get("fill-opacity")
    stroke_opacity = el.get("stroke-opacity")
    opacity = el.get("opacity")
    stroke_width = el.get("stroke-width")
    for decl in (el.get("style") or "").split(";"):
        if ":" not in decl:
            continue
        k, v = decl.split(":", 1)
        k, v = k.strip(), v.strip()
        if k == "fill":
            fill = v
        elif k == "stroke":
            stroke = v
        elif k == "fill-opacity":
            fill_opacity = v
        elif k == "stroke-opacity":
            stroke_opacity = v
        elif k == "opacity":
            opacity = v
        elif k == "stroke-width":
            stroke_width = v
    return fill, stroke, fill_opacity, stroke_opacity, opacity, stroke_width


# ------------------------------------------------------------------ convert
def convert(svg_path: str, out_path: str, override_fill: str | None = None,
            size_dp: float = 24.0) -> dict:
    root = ET.parse(svg_path).getroot()

    vb = root.get("viewBox")
    if vb:
        parts = [float(x) for x in NUM_RE.findall(vb)]
        viewport = (parts[0], parts[1], parts[0] + parts[2], parts[1] + parts[3])
    else:
        w = float(re.sub(r"[^\d.]", "", root.get("width", "24")) or 24)
        h = float(re.sub(r"[^\d.]", "", root.get("height", "24")) or 24)
        viewport = (0.0, 0.0, w, h)
    vw = viewport[2] - viewport[0]
    vh = viewport[3] - viewport[1]

    gradients = {}
    for el in root.iter():
        if (el.tag.endswith("linearGradient") or el.tag.endswith("radialGradient")) and el.get("id"):
            gradients[el.get("id")] = el

    vector = ET.Element(f"{{{ANDROID_NS}}}vector")
    vector.set(f"{{{ANDROID_NS}}}width", f"{size_dp:g}dp")
    vector.set(f"{{{ANDROID_NS}}}height", f"{size_dp * (vh / vw):g}dp")
    vector.set(f"{{{ANDROID_NS}}}viewportWidth", f"{vw:g}")
    vector.set(f"{{{ANDROID_NS}}}viewportHeight", f"{vh:g}")

    stats = {"paths": 0, "gradients": 0, "colors": set()}

    def resolve_fill(el, inherited):
        fill, _, _, _, _, _ = style_of(el)
        if fill is None:
            fill = inherited
        return fill

    def walk(el, matrix, inherited_fill, inherited_opacity):
        tag = el.tag.split("}")[-1]
        if tag in ("defs", "clipPath", "mask", "title", "desc", "metadata",
                   "style", "linearGradient", "radialGradient", "symbol", "text"):
            return

        if tag in ("g", "svg"):
            fill, _, _, _, op, _ = style_of(el)
            m = matrix
            if el.get("transform"):
                m = mul(matrix, parse_transform(el.get("transform")))
            group_opacity = inherited_opacity * (float(op) if op is not None else 1.0)
            child_fill = fill if fill is not None else inherited_fill
            for child in el:
                walk(child, m, child_fill, group_opacity)
            return

        fill, stroke, fop, sop, op, sw = style_of(el)
        if fill is None:
            fill = inherited_fill if inherited_fill is not None else "#000000"
        if fill.lower() == "none":
            return

        d = shape_to_path(tag, el)
        if not d:
            return
        d = normalize_path(d)
        if not d.strip():
            return

        m = matrix
        if el.get("transform"):
            m = mul(matrix, parse_transform(el.get("transform")))
        d = apply_matrix(d, m)

        alpha = inherited_opacity
        if op is not None:
            alpha *= float(op)
        if fop is not None:
            alpha *= float(fop)

        # A monochrome source (all paths share one flat colour) can be
        # re-tinted to the vendor's official brand colour.
        if override_fill and not fill.startswith("url("):
            fill = override_fill

        path_el = ET.SubElement(vector, f"{{{ANDROID_NS}}}path")
        path_el.set(f"{{{ANDROID_NS}}}pathData", d)

        if fill.startswith("url("):
            ref = fill[4:].split(")")[0].lstrip("#'\"")
            grad_el = gradients.get(ref)
            grad = gradient_to_android(grad_el, viewport) if grad_el is not None else None
            if grad is not None:
                attr = ET.SubElement(path_el, f"{{{AAPT_NS}}}attr", {"name": "android:fillColor"})
                attr.append(grad)
                stats["gradients"] += 1
                stats["colors"].add("gradient")
            else:
                path_el.set(f"{{{ANDROID_NS}}}fillColor", "#FF000000")
                stats["colors"].add("#FF000000")
        else:
            color = apply_opacity(parse_color(fill), alpha)
            path_el.set(f"{{{ANDROID_NS}}}fillColor", color)
            stats["colors"].add(color)

        if stroke and stroke.lower() != "none":
            sc = parse_color(stroke)
            if sop is not None:
                sc = apply_opacity(sc, float(sop) * alpha)
            path_el.set(f"{{{ANDROID_NS}}}strokeColor", sc)
            path_el.set(f"{{{ANDROID_NS}}}strokeWidth", sw or "1")
            path_el.set(f"{{{ANDROID_NS}}}strokeLineJoin", "round")
            path_el.set(f"{{{ANDROID_NS}}}strokeLineCap", "round")

        stats["paths"] += 1

    root_fill = root.get("fill")
    for child in root:
        walk(child, IDENTITY, root_fill, 1.0)

    ET.indent(vector, space="    ")
    out = '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(vector, encoding="unicode") + "\n"
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w") as f:
        f.write(out)
    stats["colors"] = sorted(stats["colors"])
    return stats


# --------------------------------------------------------------------- main
def load_manifest():
    with open(MANIFEST) as f:
        return json.load(f)


def main():
    ap = argparse.ArgumentParser(description="Convert vendor SVGs to Android VectorDrawables.")
    ap.add_argument("--only", nargs="*", help="only convert these slugs")
    ap.add_argument("--all", action="store_true", help="convert every entry in manifest.json")
    args = ap.parse_args()

    manifest = load_manifest()
    slugs = args.only or list(manifest.keys())
    for slug in slugs:
        entry = manifest.get(slug)
        if not entry:
            print(f"!! unknown slug {slug}", file=sys.stderr)
            continue
        svg_rel = entry["svg"].split("assets/agent-icons/")[-1]
        svg_path = os.path.join(HERE, svg_rel)
        if not os.path.exists(svg_path):
            print(f"!! missing svg for {slug}: {svg_path}", file=sys.stderr)
            continue
        out_path = os.path.join(DRAWABLE_DIR, f"ic_{slug}.xml")
        stats = convert(svg_path, out_path, override_fill=entry.get("fill"))
        print(f"ic_{slug}.xml  paths={stats['paths']:3d} gradients={stats['gradients']} colors={stats['colors']}")


if __name__ == "__main__":
    main()
