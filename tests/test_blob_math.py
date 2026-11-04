"""Blob deformation core contract.

Python mirror of BlobMath.kt (ui/avatar/BlobMath.kt): the radial blend,
the 12 motion archetypes and the preset catalogue must satisfy the same
invariants — positive finite radius on [0,100)s for every preset, C1
continuity (bounded finite differences), archetypes that are pairwise
distinguishable, and a catalogue of >= 200 unique parameter tuples.
"""

import math
import unittest
from itertools import permutations

ARCHETYPES = [
    "BREATHE", "PULSE", "SLOSH", "RIPPLE", "CHURN", "DRIFT",
    "KNEAD", "BOUNCE", "DRIP", "BLOOM", "TIGHTEN", "SHEAR",
]
OMEGA = {
    "BREATHE": 1.5708, "PULSE": 3.1416, "SLOSH": 2.0944, "RIPPLE": 6.2832,
    "CHURN": 2.618, "DRIFT": 0.5236, "KNEAD": 1.2566, "BOUNCE": 4.1888,
    "DRIP": 1.0472, "BLOOM": 0.7854, "TIGHTEN": 1.0472, "SHEAR": 1.5708,
}
SYMMETRIES = ["SYMMETRIC", "ASYMMETRIC", "TRAVELLING"]
SPEEDS = {"SLOW": 0.4, "SLOW_MEDIUM": 0.7, "MEDIUM": 1.0,
          "MEDIUM_FAST": 1.5, "FAST": 2.2}
LOBE_COUNTS = list(range(4, 13))  # 4..12


def lobe_phase(lobe, seed):
    x = math.sin(lobe * 127.1 + seed * 311.7) * 43758.5453
    return (x - math.floor(x)) * 2 * math.pi


def lobe_weight(lobe, lobe_count):
    return 1.0 / (1.0 + 0.35 * (lobe - 1))


def _impulse_bump(frac, sharpness):
    u = 2 * math.pi * frac
    return math.exp(-sharpness * (1 - math.cos(u)))


def _smooth_pulse(frac):
    s = max(-1.0, min(1.0, math.sin(2 * math.pi * frac)))
    u = 0.5 * (s + 1)
    return u * u * (3 - 2 * u)


def envelope_and_phase(archetype, t, speed):
    w = OMEGA[archetype] * speed
    if archetype == "BREATHE":
        return 0.5 + 0.5 * math.sin(w * t), 0.0
    if archetype == "PULSE":
        s = 0.5 + 0.5 * math.sin(w * t)
        return s * s * (0.25 + 0.75 * s), 0.0
    if archetype == "SLOSH":
        return 0.5 + 0.5 * math.sin(w * t), 1.6
    if archetype == "RIPPLE":
        return 0.8 + 0.2 * math.sin(w * t), -2.2
    if archetype == "CHURN":
        return 0.6 + 0.4 * math.sin(w * t + 1.3 * math.sin(0.7 * w * t)), 0.9
    if archetype == "DRIFT":
        return 0.5 + 0.5 * math.sin(w * t + 2 * math.sin(0.31 * w * t)), 0.25
    if archetype == "KNEAD":
        frac = (w * t / (2 * math.pi)) % 1.0
        return _smooth_pulse(frac), 0.7
    if archetype == "BOUNCE":
        frac = (w * t / (2 * math.pi)) % 1.0
        return _impulse_bump(frac, 2.5), 2.8
    if archetype == "DRIP":
        frac = (w * t / (2 * math.pi)) % 1.0
        return 1 - 0.6 * _smooth_pulse(frac), 0.5
    if archetype == "BLOOM":
        return 0.5 + 0.5 * math.sin(w * t), 0.35
    if archetype == "TIGHTEN":
        s = 0.5 + 0.5 * math.sin(w * t)
        return 1 - s * s, 0.0
    if archetype == "SHEAR":
        return 0.5 + 0.5 * math.sin(w * t), 1.1
    raise ValueError(archetype)


def default_params(archetype="BREATHE", lobe_count=6, speed="MEDIUM",
                   symmetry="ASYMMETRIC", seed=0.0):
    return {"archetype": archetype, "lobe_count": lobe_count,
            "speed": speed, "symmetry": symmetry,
            "base": 1.0, "amplitude": 0.22, "seed": seed}


def blob_radius(theta, t, p):
    if not math.isfinite(t) or not math.isfinite(theta):
        return p["base"]
    tt = max(t, 0.0)
    env, phase_rate = envelope_and_phase(p["archetype"], tt, SPEEDS[p["speed"]])
    total = 0.0
    wtot = 0.0
    for lobe in range(1, p["lobe_count"] + 1):
        if p["symmetry"] == "SYMMETRIC" and lobe % 2 != 0:
            continue
        wgt = lobe_weight(lobe, p["lobe_count"])
        base_phase = lobe_phase(lobe, p["seed"])
        phase = base_phase + phase_rate * lobe * tt
        if p["symmetry"] == "TRAVELLING":
            phase = base_phase - 2.2 * lobe * OMEGA[p["archetype"]] * SPEEDS[p["speed"]] * tt
        elif p["symmetry"] == "SYMMETRIC":
            phase = lobe * 0.5 + phase_rate * (lobe / 2) * tt
        if p["archetype"] == "SHEAR":
            phase += 1.8 * math.sin(theta) * env
        w2 = wgt
        if p["archetype"] == "BLOOM":
            w2 *= 0.4 + 0.6 * env * (lobe / p["lobe_count"])
        total += w2 * math.cos(lobe * theta + phase)
        wtot += wgt
    s = total / wtot if wtot > 0 else 0.0
    deform = max(-0.55, min(0.55, p["amplitude"] * env * s))
    r = p["base"] * (1 + deform)
    return r if math.isfinite(r) and r > 1e-4 else p["base"]


def wobble_energy(p, t):
    if not math.isfinite(t):
        return 0.0
    tt = max(t, 0.0)
    env, _ = envelope_and_phase(p["archetype"], tt, SPEEDS[p["speed"]])
    acc = 0.0
    steps = 8
    for i in range(steps):
        th = (i / steps) * 2 * math.pi
        d = blob_radius(th, tt, p) / p["base"] - 1
        acc += d * d
    mean_sq = acc / steps
    e = env * (0.35 + 0.65 * math.sqrt(max(0.0, min(1.0, mean_sq / 0.3))))
    return max(0.0, min(1.0, e))


def catalogue():
    out = []
    for a in ARCHETYPES:
        for lobes in LOBE_COUNTS:
            for band in SPEEDS:
                for sym in SYMMETRIES:
                    out.append(default_params(a, lobes, band, sym))
    return out


class CatalogueTest(unittest.TestCase):
    def test_at_least_200_presets(self):
        self.assertGreaterEqual(len(catalogue()), 200)

    def test_exact_dimension_product(self):
        self.assertEqual(len(catalogue()), 12 * 9 * 5 * 3)  # 1620

    def test_no_duplicate_parameter_tuples(self):
        keys = [(p["archetype"], p["lobe_count"], p["speed"], p["symmetry"])
                for p in catalogue()]
        self.assertEqual(len(set(keys)), len(keys))


class RadiusInvariantTest(unittest.TestCase):
    def test_positive_finite_across_t_and_all_presets(self):
        thetas = [i * math.pi / 4 for i in range(8)]
        for p in catalogue():
            for t in (0.0, 0.5, 3.7, 17.0, 99.9):
                for th in thetas:
                    r = blob_radius(th, t, p)
                    self.assertTrue(math.isfinite(r) and r > 0,
                                    f"{p} t={t} th={th} r={r}")

    def test_positive_finite_full_time_sweep(self):
        # Representative subset: every archetype at MEDIUM/ASYMMETRIC.
        for a in ARCHETYPES:
            p = default_params(a)
            t = 0.0
            while t < 100.0:
                for th in (0.0, 1.1, 3.3, 5.0):
                    r = blob_radius(th, t, p)
                    self.assertTrue(math.isfinite(r) and r > 0)
                t += 0.25

    def test_non_finite_inputs_fall_back(self):
        p = default_params()
        self.assertAlmostEqual(blob_radius(0.0, float("nan"), p), p["base"])
        self.assertAlmostEqual(blob_radius(float("inf"), 1.0, p), p["base"])
        self.assertEqual(wobble_energy(p, float("nan")), 0.0)


class ContinuityTest(unittest.TestCase):
    def test_c1_bounded_finite_differences(self):
        h = 1e-3
        for a in ARCHETYPES:
            p = default_params(a)
            for t in (0.0, 0.7, 2.3, 9.1):
                for th in (0.0, 0.8, 2.9, 5.5):
                    d_theta = abs(blob_radius(th + h, t, p)
                                  - blob_radius(th - h, t, p)) / (2 * h)
                    d_time = abs(blob_radius(th, t + h, p)
                                 - blob_radius(th, max(0.0, t - h), p)) / (2 * h)
                    self.assertLess(d_theta, 12.0, f"dtheta {a} t={t} th={th}")
                    self.assertLess(d_time, 12.0, f"dt {a} t={t} th={th}")
                    # Curvature bounded: no kinks / slope jumps.
                    curv = abs(blob_radius(th + h, t, p) - 2 * blob_radius(th, t, p)
                               + blob_radius(th - h, t, p)) / (h * h)
                    self.assertLess(curv, 2000.0, f"curv {a} t={t} th={th}")

    def test_periodicity_in_theta(self):
        p = default_params("CHURN")
        for th in (0.3, 2.0, 4.7):
            self.assertAlmostEqual(blob_radius(th + 2 * math.pi, 1.3, p),
                                   blob_radius(th, 1.3, p), places=5)


class ArchetypeDistinctnessTest(unittest.TestCase):
    def _signal(self, archetype):
        p = default_params(archetype, seed=3.0)
        vals = []
        for i in range(160):
            t = i * 0.125  # 20 s window
            for j in range(8):
                th = j * math.pi / 4
                vals.append(blob_radius(th, t, p))
        return vals

    @staticmethod
    def _corr(x, y):
        n = len(x)
        mx = sum(x) / n
        my = sum(y) / n
        cov = sum((a - mx) * (b - my) for a, b in zip(x, y))
        vx = sum((a - mx) ** 2 for a in x)
        vy = sum((b - my) ** 2 for b in y)
        return cov / math.sqrt(vx * vy)

    def test_pairwise_correlation_below_threshold(self):
        signals = {a: self._signal(a) for a in ARCHETYPES}
        worst = 0.0
        worst_pair = None
        for a, b in permutations(ARCHETYPES, 2):
            c = abs(self._corr(signals[a], signals[b]))
            if c > worst:
                worst, worst_pair = c, (a, b)
        self.assertLess(worst, 0.90, f"worst pair {worst_pair} corr={worst}")


class EnergyTest(unittest.TestCase):
    def test_wobble_energy_bounded(self):
        for a in ARCHETYPES:
            p = default_params(a)
            for i in range(50):
                e = wobble_energy(p, i * 0.7)
                self.assertTrue(math.isfinite(e) and 0.0 <= e <= 1.0)


if __name__ == "__main__":
    unittest.main()
