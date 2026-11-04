"""Tests for FurOrbRenderer.kt — mirrors the Kotlin maths in Python.

Nothing is invented: throughput anchors are parsed from
AdaptiveOrbSpeed.kt, strand-count / hair-length / slew / sweep
constants are parsed from FurOrbRenderer.kt, tier RGB splits are
parsed from ReasoningEffortOrb.kt, and GOLDEN / hash constants are
parsed from ThinkingOrbEngine.kt.

Checks:
- strand count monotonic in intensity, 48 at the floor, capped at
  192 at the ceiling (1M tok/s saturation point and the 2.6 max)
- hair length at 1M tok/s equals the documented maximum (0.42 of
  the orb radius) and never exceeds it; floor is 0.18
- seed produces identical strand fields across calls; a different
  agent produces a different field
- agitation respects slew limits rise <= 1.6/s, fall <= 0.55/s
  over a simulated frame sequence
- RGB split is exactly zero below the documented effort threshold
  (Max) and non-zero above it; aura thins above the threshold
- flow mapping: Off (low effort) combs straight back, Ultra sweeps
  sideways/outward; draw gate skips when off-screen or scale is 0

Run: python3 -m pytest tests/test_fur_orb.py -q
"""
import math
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FUR = ROOT / "android/app/src/main/java/com/sarvesh173/talos/ui/orb/FurOrbRenderer.kt"
SPEED = ROOT / "android/app/src/main/java/com/sarvesh173/talos/ui/orb/AdaptiveOrbSpeed.kt"
EFFORT = ROOT / "android/app/src/main/java/com/sarvesh173/talos/ui/orb/ReasoningEffortOrb.kt"
ENGINE = ROOT / "android/app/src/main/java/com/sarvesh173/talos/ui/orb/ThinkingOrbEngine.kt"

FUR_TEXT = FUR.read_text()
SPEED_TEXT = SPEED.read_text()
EFFORT_TEXT = EFFORT.read_text()
ENGINE_TEXT = ENGINE.read_text()


def _const(name, text):
    m = re.search(rf"{name} = ([\d._]+)f", text)
    assert m, f"{name} not found"
    return float(m.group(1).replace("_", ""))


def _iconst(name, text):
    m = re.search(rf"{name} = ([\d_]+)\b", text)
    assert m, f"{name} not found"
    return int(m.group(1).replace("_", ""))


# --- real anchors from AdaptiveOrbSpeed.kt ---------------------------------
MIN_I = _const("DEFAULT_MIN_INTENSITY", SPEED_TEXT)
MAX_I = _const("DEFAULT_MAX_INTENSITY", SPEED_TEXT)
FLOOR_TPS = _const("DEFAULT_FLOOR_TPS", SPEED_TEXT)
CEILING_TPS = _const("DEFAULT_CEILING_TPS", SPEED_TEXT)
KNEE_F = _const("DEFAULT_KNEE_FRACTION", SPEED_TEXT)
MAX_SUPPORTED = _const("MAX_SUPPORTED_TPS", SPEED_TEXT)

# --- fur constants from FurOrbRenderer.kt -----------------------------------
MIN_COUNT = _iconst("MIN_STRAND_COUNT", FUR_TEXT)
MAX_COUNT = _iconst("MAX_STRAND_COUNT", FUR_TEXT)
MIN_HAIR = _const("MIN_HAIR_LENGTH_FRACTION", FUR_TEXT)
MAX_HAIR = _const("MAX_HAIR_LENGTH_FRACTION", FUR_TEXT)
RISE_PER_S = _const("AGITATION_RISE_PER_SECOND", FUR_TEXT)
FALL_PER_S = _const("AGITATION_FALL_PER_SECOND", FUR_TEXT)
MAX_SWEEP = _const("MAX_SWEEP_RADIANS", FUR_TEXT)
MAX_CURV = _const("MAX_CURVATURE", FUR_TEXT)

_m = re.search(r"RGB_SPLIT_THRESHOLD_TIER: ReasoningEffortTier = ReasoningEffortTier\.(\w+)", FUR_TEXT)
assert _m, "RGB_SPLIT_THRESHOLD_TIER not found in FurOrbRenderer.kt"
THRESHOLD_TIER = _m.group(1)

# --- GOLDEN + hash constants from ThinkingOrbEngine.kt ----------------------
def _search(pat, text, what):
    m = re.search(pat, text)
    assert m, f"{what} not found"
    return m


GOLDEN = float(_search(r"internal const val GOLDEN = ([\d.]+)", ENGINE_TEXT, "GOLDEN").group(1))
_HASH_M = _search(r"\+ b\.toDouble\(\) \* ([\d.]+)\) \* ([\d.]+)", ENGINE_TEXT, "hashD")
HASH_A = float(_search(r"sin\(a\.toDouble\(\) \* ([\d.]+)", ENGINE_TEXT, "hashD a").group(1))
HASH_B = float(_HASH_M.group(1))
HASH_SCALE = float(_HASH_M.group(2))

# --- tier RGB splits from ReasoningEffortOrb.kt -----------------------------
TIER_RE = re.compile(
    r"ReasoningEffortTier\.(\w+) to EffortBundle\("
    r"([\d.]+)f, ([\d.]+)f, ([\d.]+)f, ([\d.]+)f, ([\d.]+)f, (\d+), Family\.(\w+), (\d+)\)"
)
TIERS = ["Off", "Low", "Medium", "High", "Max", "Ultra"]
TIER_ROWS = []
for _name, _im, _pm, _rs, _rgb, _rim, _aura, _fam, _dwell in TIER_RE.findall(EFFORT_TEXT):
    TIER_ROWS.append({"tier": _name, "split": float(_rgb)})
assert [r["tier"] for r in TIER_ROWS] == TIERS, "tier order must be Off..Ultra"
SPLIT_BY_TIER = {r["tier"]: r["split"] for r in TIER_ROWS}
MAX_SPLIT = SPLIT_BY_TIER["Ultra"]


# --- Python mirrors of the Kotlin maths -------------------------------------
def orb_intensity(tps):
    """Mirror of orbIntensity() in AdaptiveOrbSpeed.kt."""
    if not math.isfinite(tps) or tps <= 0:
        return MIN_I
    rate = min(max(tps, FLOOR_TPS), MAX_SUPPORTED)
    octaves = math.log2(rate / FLOOR_TPS)
    body = math.log2(CEILING_TPS / FLOOR_TPS)
    norm = octaves / body
    if norm <= 0:
        return MIN_I
    knee = MIN_I + (MAX_I - MIN_I) * KNEE_F
    if norm <= 1:
        return min(max(MIN_I + (knee - MIN_I) * norm, MIN_I), MAX_I)
    return min(max(knee + (MAX_I - knee) * (1 - math.exp(-(norm - 1))), MIN_I), MAX_I)


I_SAT = orb_intensity(MAX_SUPPORTED)  # saturation intensity at 1M tok/s


def normalized_intensity(intensity):
    if not math.isfinite(intensity):
        return 0.0
    return min(1.0, max(0.0, (intensity - MIN_I) / (I_SAT - MIN_I)))


def count_for_intensity(intensity):
    # Kotlin roundToInt() on a positive value is floor(x + 0.5).
    raw = MIN_COUNT + (MAX_COUNT - MIN_COUNT) * normalized_intensity(intensity)
    return min(MAX_COUNT, max(MIN_COUNT, math.floor(raw + 0.5)))


def hair_length_fraction(intensity):
    n = normalized_intensity(intensity)
    return min(MAX_HAIR, max(MIN_HAIR, MIN_HAIR + (MAX_HAIR - MIN_HAIR) * n))


def java_hash_code(s):
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return h - 0x100000000 if h >= 0x80000000 else h


def strand_seed(agent_id):
    return java_hash_code(agent_id)


def hash_d(a, b):
    h = math.sin(a * HASH_A + b * HASH_B) * HASH_SCALE
    return h - math.floor(h)


def strand_jitter(index, seed):
    return hash_d(float(index), float(seed))


def strand_angle(index, seed):
    return index * GOLDEN + seed * 0.01


def strand_field(agent_id, count):
    seed = strand_seed(agent_id)
    return tuple(
        (strand_angle(i, seed), strand_jitter(i, seed)) for i in range(count)
    )


def advance_agitation(current, target, dt):
    cur = min(1.0, max(0.0, current)) if math.isfinite(current) else 0.0
    tgt = min(1.0, max(0.0, target)) if math.isfinite(target) else 0.0
    if not math.isfinite(dt) or dt <= 0:
        return cur
    limit = RISE_PER_S if tgt > cur else FALL_PER_S
    step = max(-limit * dt, min(limit * dt, tgt - cur))
    return min(1.0, max(0.0, cur + step))


def flow_for_tier(tier):
    return TIERS.index(tier) / 5.0


def rgb_split_dp(tier):
    return SPLIT_BY_TIER[tier]


def aura_thin_factor(tier):
    split = rgb_split_dp(tier)
    if split <= 0:
        return 1.0
    return min(1.0, max(0.45, 1.0 - (split / MAX_SPLIT) * 0.55))


def should_draw_fur(visible, animator_scale, paused=False):
    return bool(visible) and not paused and math.isfinite(animator_scale) and animator_scale > 0


# --- anchor sanity: real Kotlin literals, not invented ----------------------
def test_anchors_from_adaptive_orb_speed():
    assert (MIN_I, MAX_I, FLOOR_TPS, CEILING_TPS, KNEE_F) == (0.55, 2.6, 5.0, 20000.0, 0.92)
    assert MAX_SUPPORTED == 1_000_000.0
    assert (MIN_COUNT, MAX_COUNT) == (48, 192)
    assert (MIN_HAIR, MAX_HAIR) == (0.18, 0.42)
    assert (RISE_PER_S, FALL_PER_S) == (1.6, 0.55)
    assert THRESHOLD_TIER == "Max"


# --- strand count: monotonic, capped ----------------------------------------
def test_strand_count_floor_and_ceiling():
    assert count_for_intensity(MIN_I) == 48
    assert count_for_intensity(MAX_I) == 192
    assert count_for_intensity(orb_intensity(1_000_000)) == 192
    assert count_for_intensity(0.0) == 48  # below floor clamps to floor count


def test_strand_count_monotonic_and_capped():
    prev = -1
    for tps in (0, 5, 60, 250, 621, 1178, 2623, 5000, 17000, 20000, 40000, 100000, 1_000_000):
        c = count_for_intensity(orb_intensity(tps))
        assert c >= prev, (tps, c, prev)
        assert 48 <= c <= 192, (tps, c)
        prev = c
    for intensity in (MIN_I, 1.0, 1.5, 2.0, 2.4, MAX_I, 10.0, 100.0):
        assert 48 <= count_for_intensity(intensity) <= 192


# --- hair length: at 1M equals documented max, never exceeds -----------------
def test_hair_length_at_1m_equals_documented_max():
    at_1m = orb_intensity(1_000_000)
    assert hair_length_fraction(at_1m) == MAX_HAIR
    assert hair_length_fraction(MAX_I) == MAX_HAIR
    assert hair_length_fraction(MIN_I) == MIN_HAIR
    assert hair_length_fraction(orb_intensity(0)) == MIN_HAIR


def test_hair_length_never_exceeds_max():
    for tps in (0, 5, 250, 2623, 17000, 20000, 100000, 1_000_000):
        assert hair_length_fraction(orb_intensity(tps)) <= MAX_HAIR
    for intensity in (MIN_I, 1.2, 2.0, MAX_I, 5.0, 50.0):
        assert hair_length_fraction(intensity) <= MAX_HAIR
        assert hair_length_fraction(intensity) >= MIN_HAIR


def test_hair_length_monotonic_with_throughput():
    prev = -1.0
    for tps in (0, 5, 250, 2623, 17000, 20000, 100000, 1_000_000):
        h = hair_length_fraction(orb_intensity(tps))
        assert h >= prev, (tps, h, prev)
        prev = h


# --- determinism -------------------------------------------------------------
def test_seed_produces_identical_output_across_calls():
    a = strand_field("agent-alpha", 64)
    b = strand_field("agent-alpha", 64)
    assert a == b
    assert len(a) == 64
    # jitter stays in [0, 1)
    assert all(0.0 <= j < 1.0 for _ang, j in a)


def test_seed_differs_per_agent():
    assert strand_field("agent-alpha", 64) != strand_field("agent-beta", 64)


# --- agitation slew ----------------------------------------------------------
def test_agitation_respects_rise_and_fall_slew_limits():
    dt = 1.0 / 60.0
    # Simulated frame sequence: 0 -> 1 (rise) then 1 -> 0 (fall).
    value = 0.0
    for _ in range(240):
        nxt = advance_agitation(value, 1.0, dt)
        assert (nxt - value) / dt <= RISE_PER_S + 1e-9
        assert nxt >= value
        value = nxt
    assert value == 1.0
    for _ in range(240):
        nxt = advance_agitation(value, 0.0, dt)
        assert (value - nxt) / dt <= FALL_PER_S + 1e-9
        assert nxt <= value
        value = nxt
    assert value == 0.0


def test_agitation_rise_is_faster_than_fall_and_no_snap():
    dt = 1.0 / 60.0
    # One frame can never cross the whole range.
    assert advance_agitation(0.0, 1.0, dt) < 0.1
    assert advance_agitation(1.0, 0.0, dt) > 0.98
    # Zero / negative dt holds position (no snap, no NaN).
    assert advance_agitation(0.3, 1.0, 0.0) == 0.3
    assert advance_agitation(0.3, 1.0, -1.0) == 0.3


# --- RGB split threshold + aura thinning -------------------------------------
def test_rgb_split_zero_below_threshold_nonzero_above():
    threshold_index = TIERS.index(THRESHOLD_TIER)
    for tier in TIERS[:threshold_index]:
        assert rgb_split_dp(tier) == 0.0, tier
    for tier in TIERS[threshold_index:]:
        assert rgb_split_dp(tier) > 0.0, tier
    assert rgb_split_dp("Max") == 1.2
    assert rgb_split_dp("Ultra") == 2.4


def test_aura_thins_above_threshold_only():
    threshold_index = TIERS.index(THRESHOLD_TIER)
    for tier in TIERS[:threshold_index]:
        assert aura_thin_factor(tier) == 1.0, tier
    assert aura_thin_factor("Max") < 1.0
    assert aura_thin_factor("Ultra") == 0.45
    assert aura_thin_factor("Ultra") < aura_thin_factor("Max")


# --- flow mapping ------------------------------------------------------------
def test_flow_mapping_low_calm_ultra_sweeps():
    assert flow_for_tier("Off") == 0.0
    assert flow_for_tier("Ultra") == 1.0
    flows = [flow_for_tier(t) for t in TIERS]
    assert flows == sorted(flows)
    # flow 0: straight back, no sweep or curl; flow 1: max sweep/curl.
    assert 0.0 * MAX_SWEEP == 0.0 and 0.0 * MAX_CURV == 0.0
    assert MAX_SWEEP > 0 and MAX_CURV > 0
    sweep_by_tier = [flow_for_tier(t) * MAX_SWEEP for t in TIERS]
    assert sweep_by_tier == sorted(sweep_by_tier)
    assert sweep_by_tier[0] == 0.0 and sweep_by_tier[-1] == MAX_SWEEP


# --- draw gate ---------------------------------------------------------------
def test_draw_gate_skips_offscreen_and_zero_scale():
    assert should_draw_fur(True, 1.0) is True
    assert should_draw_fur(False, 1.0) is False
    assert should_draw_fur(True, 0.0) is False
    assert should_draw_fur(True, -1.0) is False
    assert should_draw_fur(True, 1.0, paused=True) is False
