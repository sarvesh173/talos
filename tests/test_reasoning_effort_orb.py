"""Tests for ReasoningEffortOrb.kt — mirrors the Kotlin maths in Python.

Values are not invented: tier-bundle literals are parsed from
ReasoningEffortOrb.kt and throughput anchors (min/max intensity, floor,
ceiling, knee) are parsed from AdaptiveOrbSpeed.kt. Checks:
- >=6 tiers, strictly monotonic (rgbSplit non-decreasing, zero below max)
- sub-linear: ultra/low intensity ratio below the documented cap (2.0),
  and intensity increments shrink toward the top
- lerp converges within MAX_CONVERGENCE_FRAMES_60FPS (60) frames at 60fps,
  never overshoots, scale 0 jumps to target
- composition with the real orbIntensity() anchors: effective = throughput
  * multiplier, off multiplier is 1.0 so effort adds nothing when disabled
- off disables the effect (aura 0, rgbSplit 0, dot count = 0.5x base)
Run: python3 -m pytest tests/test_reasoning_effort_orb.py -q
"""
import math
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ORB = ROOT / "android/app/src/main/java/com/sarvesh173/talos/ui/orb/ReasoningEffortOrb.kt"
SPEED = ROOT / "android/app/src/main/java/com/sarvesh173/talos/ui/orb/AdaptiveOrbSpeed.kt"

TIER_RE = re.compile(
    r"ReasoningEffortTier\.(\w+) to EffortBundle\("
    r"([\d.]+)f, ([\d.]+)f, ([\d.]+)f, ([\d.]+)f, ([\d.]+)f, (\d+), Family\.(\w+), (\d+)\)"
)
TIERS = ["Off", "Low", "Medium", "High", "Max", "Ultra"]
FIELDS = ["intensity", "point_mult", "radius", "split", "rim", "aura", "family", "dwell"]


def _parse_tiers():
    text = ORB.read_text()
    found = TIER_RE.findall(text)
    assert len(found) == 6, f"expected 6 tier literals in ReasoningEffortOrb.kt, got {len(found)}"
    rows = []
    for name, im, pm, rs, rgb, rim, aura, fam, dwell in found:
        rows.append({
            "tier": name, "intensity": float(im), "point_mult": float(pm),
            "radius": float(rs), "split": float(rgb), "rim": float(rim),
            "aura": int(aura), "family": fam, "dwell": int(dwell),
        })
    assert [r["tier"] for r in rows] == TIERS, "tier order must be Off..Ultra"
    return rows


ROWS = _parse_tiers()
ORB_TEXT = ORB.read_text()
SPEED_TEXT = SPEED.read_text()


def _const(name, text):
    m = re.search(rf"{name} = ([\d._]+)f", text)
    assert m, f"{name} not found"
    return float(m.group(1).replace("_", ""))


def _iconst(name, text):
    m = re.search(rf"{name} = ([\d_]+)", text)
    assert m, f"{name} not found"
    return int(m.group(1).replace("_", ""))


MIN_I = _const("DEFAULT_MIN_INTENSITY", SPEED_TEXT)
MAX_I = _const("DEFAULT_MAX_INTENSITY", SPEED_TEXT)
FLOOR = _const("DEFAULT_FLOOR_TPS", SPEED_TEXT)
CEILING = _const("DEFAULT_CEILING_TPS", SPEED_TEXT)
KNEE_F = _const("DEFAULT_KNEE_FRACTION", SPEED_TEXT)
BASE_DOTS = _iconst("BASE_DOT_COUNT", ORB_TEXT)
CAP = _const("ULTRA_TO_LOW_INTENSITY_CAP", ORB_TEXT)
MAX_FRAMES = _iconst("MAX_CONVERGENCE_FRAMES_60FPS", ORB_TEXT)
RATES = {
    "intensity": _const("INTENSITY_RATE_PER_SECOND", ORB_TEXT),
    "point_mult": _const("POINT_RATE_PER_SECOND", ORB_TEXT),
    "radius": _const("RADIUS_RATE_PER_SECOND", ORB_TEXT),
    "split": _const("SPLIT_RATE_PER_SECOND", ORB_TEXT),
    "rim": _const("RIM_RATE_PER_SECOND", ORB_TEXT),
    "aura": _const("AURA_RATE_PER_SECOND", ORB_TEXT),
    "dwell": _const("DWELL_RATE_PER_SECOND", ORB_TEXT),
}


def orb_intensity(tps):
    """Mirror of orbIntensity() in AdaptiveOrbSpeed.kt."""
    if not math.isfinite(tps) or tps <= 0:
        return MIN_I
    rate = min(max(tps, FLOOR), 1_000_000)
    octaves = math.log2(rate / FLOOR)
    body = math.log2(CEILING / FLOOR)
    norm = octaves / body
    if norm <= 0:
        return MIN_I
    knee = MIN_I + (MAX_I - MIN_I) * KNEE_F
    if norm <= 1:
        return min(max(MIN_I + (knee - MIN_I) * norm, MIN_I), MAX_I)
    return min(max(knee + (MAX_I - knee) * (1 - math.exp(-(norm - 1))), MIN_I), MAX_I)


def lerp(cur, tgt, dt, scale=1.0):
    if scale <= 0:
        return dict(tgt)
    step = dt / scale
    out = dict(cur)
    for k, rate in RATES.items():
        if k in ("aura", "dwell"):
            ms = int(round(rate * step))
            d = tgt[k] - cur[k]
            out[k] = cur[k] if ms <= 0 else (tgt[k] if abs(d) <= ms else cur[k] + max(-ms, min(ms, d)))
        else:
            ms = rate * step
            d = tgt[k] - cur[k]
            out[k] = tgt[k] if abs(d) <= ms else cur[k] + max(-ms, min(ms, d))
    return out


def converged(a, b):
    return all(a[k] == b[k] for k in RATES)


# --- anchor sanity: real Kotlin literals, not invented -------------------
def test_anchors_from_adaptive_orb_speed():
    assert (MIN_I, MAX_I, FLOOR, CEILING, KNEE_F) == (0.55, 2.6, 5.0, 20000.0, 0.92)
    assert BASE_DOTS == 220 and CAP == 2.0 and MAX_FRAMES == 60


def test_throughput_anchors():
    assert orb_intensity(0) == MIN_I
    assert orb_intensity(5) == MIN_I
    assert orb_intensity(250) > orb_intensity(5)
    assert orb_intensity(2600) > orb_intensity(250)
    assert orb_intensity(17000) > orb_intensity(2600)
    assert orb_intensity(1_000_000) <= MAX_I
    assert orb_intensity(20000) < orb_intensity(1_000_000)  # tail still visible


# --- monotonic + sub-linear ----------------------------------------------
def test_strictly_monotonic():
    for field in ("intensity", "point_mult", "radius", "rim", "aura", "dwell"):
        vals = [r[field] for r in ROWS]
        assert all(b > a for a, b in zip(vals, vals[1:])), (field, vals)
    splits = [r["split"] for r in ROWS]
    assert all(b >= a for a, b in zip(splits, splits[1:])), splits
    # rgb split only at max/ultra
    assert splits[:4] == [0.0] * 4 and splits[4] > 0 and splits[5] > splits[4]


def test_sublinear_ultra_low_ratio_below_cap():
    ratio = ROWS[-1]["intensity"] / ROWS[1]["intensity"]
    assert ratio < CAP, ratio
    assert ratio < 2.0
    # diminishing increments toward the top (sub-linear, not linear)
    steps = [b["intensity"] - a["intensity"] for a, b in zip(ROWS, ROWS[1:])]
    assert steps[1:] == sorted(steps[1:], reverse=True), steps


def test_dot_counts():
    counts = [round(r["point_mult"] * BASE_DOTS) for r in ROWS]
    assert counts == [110, 165, 220, 297, 396, 484], counts
    assert counts == sorted(counts) and len(set(counts)) == 6


def test_expression_bias_calm_to_celebrating():
    assert ROWS[0]["family"] == "Calm" and ROWS[-1]["family"] == "Celebrating"
    assert [r["family"] for r in ROWS] == ["Calm", "Calm", "Focused", "Curious", "Alert", "Celebrating"]


# --- composition with the real throughput curve ---------------------------
def test_composition_after_throughput():
    for tps in (0, 5, 250, 2600, 17000, 20000, 100000, 1000000):
        vals = [orb_intensity(tps) * r["intensity"] for r in ROWS]
        assert vals == sorted(vals), (tps, vals)  # effort order preserved at any speed
        assert all(v <= MAX_I * ROWS[-1]["intensity"] for v in vals)
    # off is neutral: effort adds nothing, orb still follows throughput
    assert ROWS[0]["intensity"] == 1.0
    assert orb_intensity(250) * ROWS[0]["intensity"] == orb_intensity(250)
    # ultra ultra-slow still escalates relative to low at same throughput,
    # and a fast low run can out-score a slow ultra run (separate axes)
    assert orb_intensity(17000) * ROWS[1]["intensity"] > orb_intensity(5) * ROWS[-1]["intensity"]


def test_off_disables_effect_not_frozen():
    off = ROWS[0]
    assert off["aura"] == 0 and off["split"] == 0.0 and off["point_mult"] == 0.5
    # transitioning ultra -> off lands on the off bundle, not on ultra values
    cur = lerp(dict(ROWS[-1]), off, 10.0)
    assert converged(cur, off)


# --- lerp: bounded convergence, no overshoot, scale 0 jump ----------------
def test_lerp_converges_within_bound_no_overshoot():
    for src in ROWS:
        for tgt in ROWS:
            cur = dict(src)
            frames = 0
            for _ in range(10000):
                if converged(cur, tgt):
                    break
                nxt = lerp(cur, tgt, 1 / 60)
                frames += 1
                for k in RATES:
                    lo, hi = min(src[k], tgt[k]), max(src[k], tgt[k])
                    assert lo - 1e-9 <= nxt[k] <= hi + 1e-9, (k, src, tgt, nxt)
                    # never moves away from target
                    assert abs(nxt[k] - tgt[k]) <= abs(cur[k] - tgt[k]) + 1e-9
                cur = nxt
            assert converged(cur, tgt), (src, tgt)
            assert frames <= MAX_FRAMES, (src["tier"], tgt["tier"], frames)


def test_lerp_scale_zero_jumps_and_slow_scale_stretches():
    assert lerp(dict(ROWS[0]), ROWS[-1], 1 / 60, scale=0.0) == ROWS[-1]
    cur = dict(ROWS[0])
    n = 0
    while not converged(cur, ROWS[-1]):
        cur = lerp(cur, ROWS[-1], 1 / 60, scale=2.0)
        n += 1
        assert n < 10000
    assert n > MAX_FRAMES  # slower system scale = proportionally calmer glide


def test_lerp_partial_step_is_strictly_between():
    nxt = lerp(dict(ROWS[0]), ROWS[-1], 1 / 60)
    assert ROWS[0]["intensity"] < nxt["intensity"] < ROWS[-1]["intensity"]
    assert ROWS[0]["aura"] <= nxt["aura"] <= ROWS[-1]["aura"]
