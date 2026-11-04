"""
Python reference implementation of SvgPathParser.kt.

Mirrors the Kotlin parser exactly (same algorithm, same constants):
  * SVG path commands: M m L l H h V v C c S s Q q T t A a Z z
  * transform on <path>/<g>/<svg>: translate, scale, rotate([cx cy]),
    matrix(a b c d e f), skewX, skewY — nested groups compose outer*inner
  * adaptive flattening (recursive midpoint-deviation subdivision),
    bounded by MAX_POINTS with a `truncated` flag
  * viewBox normalisation (subtract viewBox minX/minY)
  * fill-rule="evenodd" preserved; stroke-only (fill="none") paths ignored
  * malformed input -> SvgParseResult(error=...), never raises

Coords: y-down, viewport coords — same convention AgentLogoBaker expects.
"""
from __future__ import annotations

import math
import re
from dataclasses import dataclass, field

MAX_POINTS = 5000
MAX_DEPTH = 12
DEFAULT_TOLERANCE = 0.25

COMMANDS = set("MmLlHhVvCcSsQqTtAaZz")
_NUMBER_RE = re.compile(r"[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?")
_TAG_RE = re.compile(r"<[^>]*>", re.DOTALL)
_ATTR_RE = re.compile(r"([A-Za-z_:][\w:.-]*)\s*=\s*(?:\"([^\"]*)\"|'([^']*)')")
_TRANSFORM_RE = re.compile(r"([A-Za-z]+)\s*\(([^)]*)\)")


@dataclass
class SvgParseResult:
    contours: list = field(default_factory=list)   # list[list[(x, y)]]
    fill_type: str = "nonZero"                     # "nonZero" | "evenOdd"
    truncated: bool = False
    error: str | None = None

    @property
    def ok(self) -> bool:
        return self.error is None


class _ParseError(Exception):
    pass


class _Truncated(Exception):
    def __init__(self, partial=None):
        super().__init__("point budget exceeded")
        self.partial = partial or []


# --- 2D affine: (a, b, c, d, e, f): x' = a*x + c*y + e, y' = b*x + d*y + f
IDENTITY = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)


def mat_mul(outer, inner):
    a1, b1, c1, d1, e1, f1 = outer
    a2, b2, c2, d2, e2, f2 = inner
    return (
        a1 * a2 + c1 * b2,
        b1 * a2 + d1 * b2,
        a1 * c2 + c1 * d2,
        b1 * c2 + d1 * d2,
        a1 * e2 + c1 * f2 + e1,
        b1 * e2 + d1 * f2 + f1,
    )


def mat_apply(m, x, y):
    a, b, c, d, e, f = m
    return (a * x + c * y + e, b * x + d * y + f)


def _nums(s: str) -> list:
    return [float(tok) for tok in _NUMBER_RE.findall(s)]


def parse_transform(attr: str):
    """Parse an SVG transform attribute into a single matrix."""
    result = IDENTITY
    pos = 0
    for match in _TRANSFORM_RE.finditer(attr):
        if attr[pos:match.start()].strip(" \t\r\n,"):
            raise _ParseError(f"bad transform: {attr!r}")
        name, args = match.group(1), _nums(match.group(2))
        if name == "translate":
            if len(args) < 1:
                raise _ParseError("translate needs tx")
            tx, ty = args[0], (args[1] if len(args) > 1 else 0.0)
            m = (1.0, 0.0, 0.0, 1.0, tx, ty)
        elif name == "scale":
            if len(args) < 1:
                raise _ParseError("scale needs sx")
            sx, sy = args[0], (args[1] if len(args) > 1 else args[0])
            m = (sx, 0.0, 0.0, sy, 0.0, 0.0)
        elif name == "rotate":
            if len(args) < 1:
                raise _ParseError("rotate needs angle")
            deg, cx, cy = args[0], (args[1] if len(args) > 1 else 0.0), (
                args[2] if len(args) > 2 else 0.0)
            r = math.radians(deg)
            cos, sin = math.cos(r), math.sin(r)
            rot = (cos, sin, -sin, cos, 0.0, 0.0)
            m = mat_mul((1.0, 0.0, 0.0, 1.0, cx, cy),
                        mat_mul(rot, (1.0, 0.0, 0.0, 1.0, -cx, -cy)))
        elif name == "matrix":
            if len(args) != 6:
                raise _ParseError("matrix needs 6 numbers")
            m = tuple(args)
        elif name == "skewX":
            if len(args) < 1:
                raise _ParseError("skewX needs angle")
            m = (1.0, 0.0, math.tan(math.radians(args[0])), 1.0, 0.0, 0.0)
        elif name == "skewY":
            if len(args) < 1:
                raise _ParseError("skewY needs angle")
            m = (1.0, math.tan(math.radians(args[0])), 0.0, 1.0, 0.0, 0.0)
        else:
            raise _ParseError(f"unsupported transform: {name}")
        result = mat_mul(result, m)
        pos = match.end()
    if attr[pos:].strip(" \t\r\n,"):
        raise _ParseError(f"bad transform: {attr!r}")
    return result


class _Budget:
    def __init__(self):
        self.used = 0


def _dist(x1, y1, x2, y2):
    return math.hypot(x2 - x1, y2 - y1)


def _cubic_at(p0, c1, c2, p1, t):
    u = 1.0 - t
    return (
        u * u * u * p0[0] + 3 * u * u * t * c1[0] + 3 * u * t * t * c2[0] + t * t * t * p1[0],
        u * u * u * p0[1] + 3 * u * u * t * c1[1] + 3 * u * t * t * c2[1] + t * t * t * p1[1],
    )


def _quad_at(p0, q, p1, t):
    u = 1.0 - t
    return (
        u * u * p0[0] + 2 * u * t * q[0] + t * t * p1[0],
        u * u * p0[1] + 2 * u * t * q[1] + t * t * p1[1],
    )


def _emit(out, budget, pt):
    if budget.used >= MAX_POINTS:
        raise _Truncated()
    budget.used += 1
    out.append(pt)


def _flat_cubic(p0, c1, c2, p1, out, budget, tol, depth):
    if depth >= MAX_DEPTH:
        _emit(out, budget, p1)
        return
    mid = _cubic_at(p0, c1, c2, p1, 0.5)
    cm = ((p0[0] + p1[0]) / 2.0, (p0[1] + p1[1]) / 2.0)
    if _dist(mid[0], mid[1], cm[0], cm[1]) <= tol:
        _emit(out, budget, p1)
        return
    m01 = ((p0[0] + c1[0]) / 2.0, (p0[1] + c1[1]) / 2.0)
    m12 = ((c1[0] + c2[0]) / 2.0, (c1[1] + c2[1]) / 2.0)
    m23 = ((c2[0] + p1[0]) / 2.0, (c2[1] + p1[1]) / 2.0)
    m012 = ((m01[0] + m12[0]) / 2.0, (m01[1] + m12[1]) / 2.0)
    m123 = ((m12[0] + m23[0]) / 2.0, (m12[1] + m23[1]) / 2.0)
    m = ((m012[0] + m123[0]) / 2.0, (m012[1] + m123[1]) / 2.0)
    _flat_cubic(p0, m01, m012, m, out, budget, tol, depth + 1)
    _flat_cubic(m, m123, m23, p1, out, budget, tol, depth + 1)


def _flat_quad(p0, q, p1, out, budget, tol, depth):
    if depth >= MAX_DEPTH:
        _emit(out, budget, p1)
        return
    mid = _quad_at(p0, q, p1, 0.5)
    cm = ((p0[0] + p1[0]) / 2.0, (p0[1] + p1[1]) / 2.0)
    if _dist(mid[0], mid[1], cm[0], cm[1]) <= tol:
        _emit(out, budget, p1)
        return
    m01 = ((p0[0] + q[0]) / 2.0, (p0[1] + q[1]) / 2.0)
    m12 = ((q[0] + p1[0]) / 2.0, (q[1] + p1[1]) / 2.0)
    m = ((m01[0] + m12[0]) / 2.0, (m01[1] + m12[1]) / 2.0)
    _flat_quad(p0, m01, m, out, budget, tol, depth + 1)
    _flat_quad(m, m12, p1, out, budget, tol, depth + 1)


def _arc_params(x1, y1, rx, ry, rot_deg, large, sweep, x2, y2):
    """Endpoint -> centre arc conversion (SVG 1.1 F.6.5). Returns
    (cx, cy, rx, ry, phi, theta1, dtheta) or None if degenerate."""
    rx, ry = abs(rx), abs(ry)
    if rx < 1e-9 or ry < 1e-9:
        return None
    phi = math.radians(rot_deg)
    cos, sin = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x2) / 2.0, (y1 - y2) / 2.0
    x1p = cos * dx + sin * dy
    y1p = -sin * dx + cos * dy
    lam = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
    if lam > 1.0:
        s = math.sqrt(lam)
        rx, ry = rx * s, ry * s
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    co = math.sqrt(max(0.0, num / den)) if den > 0 else 0.0
    if large == sweep:
        co = -co
    cxp = co * rx * y1p / ry
    cyp = -co * ry * x1p / rx
    cx = cos * cxp - sin * cyp + (x1 + x2) / 2.0
    cy = sin * cxp + cos * cyp + (y1 + y2) / 2.0

    def ang(ux, uy):
        return math.atan2(uy, ux)

    theta1 = ang((x1p - cxp) / rx, (y1p - cyp) / ry)
    dtheta = ang((-x1p - cxp) / rx, (-y1p - cyp) / ry) - theta1
    if sweep == 0 and dtheta > 0:
        dtheta -= 2 * math.pi
    if sweep == 1 and dtheta < 0:
        dtheta += 2 * math.pi
    return (cx, cy, rx, ry, phi, theta1, dtheta)


def _arc_point(ap, theta):
    cx, cy, rx, ry, phi, _, _ = ap
    cos, sin = math.cos(phi), math.sin(phi)
    return (
        cx + rx * math.cos(theta) * cos - ry * math.sin(theta) * sin,
        cy + rx * math.cos(theta) * sin + ry * math.sin(theta) * cos,
    )


def _flat_arc(ap, t1, t2, out, budget, tol, depth):
    p1 = _arc_point(ap, t1)
    p2 = _arc_point(ap, t2)
    if depth >= MAX_DEPTH:
        _emit(out, budget, p2)
        return
    mid = _arc_point(ap, (t1 + t2) / 2.0)
    cm = ((p1[0] + p2[0]) / 2.0, (p1[1] + p2[1]) / 2.0)
    if _dist(mid[0], mid[1], cm[0], cm[1]) <= tol:
        _emit(out, budget, p2)
        return
    tm = (t1 + t2) / 2.0
    _flat_arc(ap, t1, tm, out, budget, tol, depth + 1)
    _flat_arc(ap, tm, t2, out, budget, tol, depth + 1)


# --- path-data tokenizer/parser -------------------------------------------

class _Tokens:
    def __init__(self, s: str):
        self.s = s
        self.i = 0

    def skip_sep(self):
        while self.i < len(self.s) and self.s[self.i] in " \t\r\n,":
            self.i += 1

    def peek(self):
        self.skip_sep()
        return self.s[self.i] if self.i < len(self.s) else ""

    def number(self):
        self.skip_sep()
        m = _NUMBER_RE.match(self.s, self.i)
        if not m:
            raise _ParseError(f"expected number at offset {self.i} in {self.s!r}")
        self.i = m.end()
        return float(m.group(0))

    def flag(self):
        self.skip_sep()
        if self.i < len(self.s) and self.s[self.i] in "01":
            v = self.s[self.i] == "1"
            self.i += 1
            return v
        raise _ParseError(f"expected arc flag at offset {self.i} in {self.s!r}")


def parse_path_data(d: str, tol: float = DEFAULT_TOLERANCE, budget=None) -> list:
    """Flatten one `d` attribute into contours (list of point lists)."""
    tok = _Tokens(d)
    contours = []
    current = []
    if budget is None:
        budget = _Budget()
    cur = start = (0.0, 0.0)
    prev_c = prev_q = None
    cmd = None
    try:
        while True:
            tok.skip_sep()
            if tok.i >= len(d):
                break
            ch = d[tok.i]
            if ch.isalpha():
                if ch not in COMMANDS:
                    raise _ParseError(f"unknown path command {ch!r} in {d!r}")
                cmd = ch
                tok.i += 1
                if cmd in "Zz":
                    if current:
                        _emit(current, budget, start)
                    cur = start
                    prev_c = prev_q = None
                    cmd = None
                    continue
            if cmd is None:
                raise _ParseError(f"path data without command: {d!r}")
            rel = cmd.islower()
            up = cmd.upper()
            if up == "M":
                x, y = tok.number(), tok.number()
                cur = (cur[0] + x, cur[1] + y) if rel else (x, y)
                start = cur
                if current:
                    contours.append(current)
                current = []
                _emit(current, budget, cur)
                prev_c = prev_q = None
                cmd = "l" if rel else "L"  # implicit lineto
            elif up == "L":
                x, y = tok.number(), tok.number()
                cur = (cur[0] + x, cur[1] + y) if rel else (x, y)
                _emit(current, budget, cur)
                prev_c = prev_q = None
            elif up == "H":
                x = tok.number()
                cur = (cur[0] + x, cur[1]) if rel else (x, cur[1])
                _emit(current, budget, cur)
                prev_c = prev_q = None
            elif up == "V":
                y = tok.number()
                cur = (cur[0], cur[1] + y) if rel else (cur[0], y)
                _emit(current, budget, cur)
                prev_c = prev_q = None
            elif up == "C":
                vals = [tok.number() for _ in range(6)]

                def adj(vx, vy):
                    return (cur[0] + vx, cur[1] + vy) if rel else (vx, vy)

                c1, c2, end = adj(vals[0], vals[1]), adj(vals[2], vals[3]), adj(vals[4], vals[5])
                _flat_cubic(cur, c1, c2, end, current, budget, tol, 0)
                prev_c, prev_q = c2, None
                cur = end
            elif up == "S":
                vals = [tok.number() for _ in range(4)]

                def adj(vx, vy):
                    return (cur[0] + vx, cur[1] + vy) if rel else (vx, vy)

                c1 = (2 * cur[0] - prev_c[0], 2 * cur[1] - prev_c[1]) if prev_c else cur
                c2, end = adj(vals[0], vals[1]), adj(vals[2], vals[3])
                _flat_cubic(cur, c1, c2, end, current, budget, tol, 0)
                prev_c, prev_q = c2, None
                cur = end
            elif up == "Q":
                vals = [tok.number() for _ in range(4)]

                def adj(vx, vy):
                    return (cur[0] + vx, cur[1] + vy) if rel else (vx, vy)

                q, end = adj(vals[0], vals[1]), adj(vals[2], vals[3])
                _flat_quad(cur, q, end, current, budget, tol, 0)
                prev_q, prev_c = q, None
                cur = end
            elif up == "T":
                vals = [tok.number() for _ in range(2)]

                def adj(vx, vy):
                    return (cur[0] + vx, cur[1] + vy) if rel else (vx, vy)

                q = (2 * cur[0] - prev_q[0], 2 * cur[1] - prev_q[1]) if prev_q else cur
                end = adj(vals[0], vals[1])
                _flat_quad(cur, q, end, current, budget, tol, 0)
                prev_q, prev_c = q, None
                cur = end
            elif up == "A":
                rx, ry, rot = tok.number(), tok.number(), tok.number()
                large, sweep = tok.flag(), tok.flag()
                x, y = tok.number(), tok.number()
                end = (cur[0] + x, cur[1] + y) if rel else (x, y)
                ap = _arc_params(cur[0], cur[1], rx, ry, rot, large, sweep,
                                 end[0], end[1])
                if ap is None:
                    _emit(current, budget, end)
                else:
                    _flat_arc(ap, ap[5], ap[5] + ap[6], current, budget, tol, 0)
                prev_c = prev_q = None
                cur = end
            else:  # pragma: no cover - guarded by COMMANDS check
                raise _ParseError(f"unknown path command {cmd!r}")
    except _Truncated as t:
        if current:
            t.partial.append(current)
        raise
    if current:
        contours.append(current)
    return contours


# --- SVG document scanning -------------------------------------------------

def _is_fill_none(attrs: dict) -> bool:
    fill = attrs.get("fill", "")
    style = attrs.get("style", "")
    return fill.strip().lower() == "none" or "fill:none" in style.replace(" ", "")


def parse_svg(source: str, tolerance: float = DEFAULT_TOLERANCE) -> SvgParseResult:
    """Parse full SVG, a bare <path .../>, or a raw `d` string."""
    src = (source or "").strip()
    if not src:
        return SvgParseResult(error="empty input")
    if "<" not in src:
        try:
            contours = parse_path_data(src, tolerance)
        except _ParseError as e:
            return SvgParseResult(error=str(e))
        except _Truncated as t:
            partial = [c for c in t.partial if len(c) >= 3]
            if not partial:
                return SvgParseResult(error="no contours (need >= 3 points)")
            return SvgParseResult(contours=partial, truncated=True)
        contours = [c for c in contours if len(c) >= 3]
        if not contours:
            return SvgParseResult(error="no contours (need >= 3 points)")
        return SvgParseResult(contours=contours)

    # XML-ish scan: only <svg>, <g>, <path> matter; everything else ignored.
    contours = []
    fill_type = "nonZero"
    seen_path = False
    viewbox_off = (0.0, 0.0)
    g_stack = []           # (matrix, fill_rule_or_None)
    truncated = False
    budget = _Budget()     # one point budget for the whole document
    try:
        for tag in _TAG_RE.findall(src):
            inner = tag[1:-1].strip()
            if not inner or inner.startswith("?") or inner.startswith("!"):
                continue
            closing = inner.startswith("/")
            if closing:
                inner = inner[1:].strip()
            self_closing = inner.endswith("/")
            if self_closing:
                inner = inner[:-1].strip()
            name = inner.split(None, 1)[0].split("/")[0] if inner else ""
            attr_src = inner[len(name):] if name else ""
            attrs = {}
            for m in _ATTR_RE.finditer(attr_src):
                attrs[m.group(1)] = m.group(2) if m.group(2) is not None else m.group(3)
            local = name.split(":")[-1]
            if local == "svg":
                if closing:
                    continue
                vb = attrs.get("viewBox", "")
                vals = _nums(vb)
                if len(vals) >= 4:
                    viewbox_off = (vals[0], vals[1])
                fr = attrs.get("fill-rule", "").strip().lower()
                if fr == "evenodd":
                    fill_type = "evenOdd"
                elif fr == "nonzero":
                    fill_type = "nonZero"
            elif local == "g":
                if closing:
                    if g_stack:
                        g_stack.pop()
                    continue
                mat = IDENTITY
                if attrs.get("transform"):
                    mat = parse_transform(attrs["transform"])
                fr = attrs.get("fill-rule", "").strip().lower()
                g_stack.append((mat, fr or None))
                if self_closing:
                    g_stack.pop()
            elif local == "path":
                if closing:
                    continue
                seen_path = True
                if _is_fill_none(attrs):
                    continue
                d = attrs.get("d", "")
                if not d.strip():
                    continue
                # inherited fill-rule: nearest ancestor wins, else own attr
                fr = attrs.get("fill-rule", "").strip().lower()
                if not fr:
                    for _, gfr in reversed(g_stack):
                        if gfr:
                            fr = gfr
                            break
                if fr == "evenodd":
                    fill_type = "evenOdd"
                elif fr == "nonzero":
                    fill_type = "nonZero"
                mat = IDENTITY
                for gmat, _ in g_stack:
                    mat = mat_mul(mat, gmat)
                if attrs.get("transform"):
                    mat = mat_mul(mat, parse_transform(attrs["transform"]))
                raw, hit_cap = _path_contours_capped(d, tolerance, budget)
                if hit_cap:
                    truncated = True
                for contour in raw:
                    ox, oy = viewbox_off
                    # transform chain first (user space), then viewBox
                    # normalisation (outermost viewport mapping)
                    contours.append([
                        (lambda q: (q[0] - ox, q[1] - oy))(mat_apply(mat, x, y))
                        for x, y in contour
                    ])
                if truncated:
                    break  # point budget spent; stop before parsing more paths
    except _ParseError as e:
        return SvgParseResult(error=str(e))
    except _Truncated:
        truncated = True
    if not seen_path:
        return SvgParseResult(error="no <path> elements found")
    contours = [c for c in contours if len(c) >= 3]
    if not contours:
        return SvgParseResult(fill_type=fill_type, truncated=truncated,
                              error="no fill contours (all paths stroke-only or empty)")
    return SvgParseResult(contours=contours, fill_type=fill_type, truncated=truncated)


def _path_contours_capped(d: str, tol: float, budget):
    """parse_path_data, but truncated input still yields its partial points."""
    try:
        return parse_path_data(d, tol, budget), False
    except _Truncated as t:
        return t.partial, True


def point_in_fill(x: float, y: float, contours: list, fill_type: str) -> bool:
    """Mirrors AgentLogoBaker.inside() — used to prove holes stay holes."""
    crossings = 0
    winding = 0
    for c in contours:
        m = len(c)
        for i in range(m):
            ax, ay = c[i]
            bx, by = c[(i + 1) % m]
            if (ay <= y) != (by <= y):
                xi = ax + (bx - ax) * (y - ay) / (by - ay)
                if xi > x:
                    crossings += 1
                    winding += 1 if by > ay else -1
    if fill_type.lower() == "nonzero":
        return winding != 0
    return crossings % 2 == 1
