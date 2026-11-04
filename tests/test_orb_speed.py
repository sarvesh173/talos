"""Orb intensity curve contract.

The orb is a tachometer, so its maths has to satisfy three things at once:

* **monotonic** — a faster model never animates slower;
* **bounded** — nothing escapes the visual maximum, even at 1,000,000 tok/s;
* **legible** — every real rate from a throttled proxy to hardwired silicon
  lands on a distinguishable rung.

The 20k ceiling is calibration, not a maximum. Past it the curve keeps rising
asymptotically, so 40k / 100k / 1M stay ordered instead of collapsing onto one
value. These tests pin that behaviour, including the boundary rates the operator
asked for explicitly.
"""

import math
import unittest

DEFAULT_CEILING = 20_000.0
DEFAULT_FLOOR = 5.0
MIN_INTENSITY = 0.55
MAX_INTENSITY = 2.6
KNEE_FRACTION = 0.92
MAX_SUPPORTED = 1_000_000.0


def knee_intensity(min_i=MIN_INTENSITY, max_i=MAX_INTENSITY, knee=KNEE_FRACTION):
    return min_i + (max_i - min_i) * knee


def body_octaves(floor=DEFAULT_FLOOR, ceiling=DEFAULT_CEILING):
    return max(math.log(ceiling / floor, 2), 1e-3)


def orb_intensity(
    rate,
    ceiling=DEFAULT_CEILING,
    floor=DEFAULT_FLOOR,
    min_i=MIN_INTENSITY,
    max_i=MAX_INTENSITY,
    knee=KNEE_FRACTION,
):
    """Python mirror of orbIntensity() in AdaptiveOrbSpeed.kt."""
    if rate is None or not math.isfinite(rate) or rate <= 0:
        return min_i
    rate = min(max(rate, floor), MAX_SUPPORTED)
    octaves = math.log(rate / floor, 2)
    normalised = octaves / body_octaves(floor, ceiling)
    if normalised <= 0:
        return min_i
    knee_v = knee_intensity(min_i, max_i, knee)
    if normalised <= 1:
        return min(max(min_i, min_i + (knee_v - min_i) * normalised), max_i)
    overshoot = normalised - 1
    reached = 1 - math.exp(-overshoot)
    return min(max(min_i, knee_v + (max_i - knee_v) * reached), max_i)


def exceeds(rate, ceiling=DEFAULT_CEILING):
    return rate is not None and math.isfinite(rate) and rate > ceiling


class BoundaryRateTest(unittest.TestCase):
    """The exact rates the operator called out."""

    def test_zero_is_idle(self):
        self.assertAlmostEqual(orb_intensity(0), MIN_INTENSITY, places=4)

    def test_negative_and_non_finite_are_idle_not_crash(self):
        self.assertAlmostEqual(orb_intensity(-1000), MIN_INTENSITY, places=4)
        self.assertAlmostEqual(orb_intensity(float("nan")), MIN_INTENSITY, places=4)
        self.assertAlmostEqual(orb_intensity(float("inf")), MIN_INTENSITY, places=4)

    def test_two_thousand_six_hundred_twenty_three(self):
        """Cerebras Llama 4 Scout — the fastest mainstream model."""
        value = orb_intensity(2623)
        # Under a 20k default ceiling, 2623 sits at ~87% of the log body, which
        # is where the fastest widely-available model should sit: clearly fast,
        # with visible room left for the silicon-class hardware above it.
        self.assertGreater(value, 1.9, "Cerebras should read clearly faster than a proxy")
        self.assertLess(value, MAX_INTENSITY, "Cerebras must not saturate the visual")

    def test_five_thousand(self):
        self.assertGreater(orb_intensity(5000), orb_intensity(2623))

    def test_seventeen_thousand(self):
        """Taalas HC1 — 8B model hardwired into silicon."""
        value = orb_intensity(17000)
        self.assertLess(value, MAX_INTENSITY, "Taalas must fit under the visible cap")
        self.assertGreater(value, orb_intensity(5000))

    def test_twenty_thousand_is_the_calibration_knee(self):
        value = orb_intensity(20000)
        self.assertAlmostEqual(value, knee_intensity(), places=3)
        self.assertLess(value, MAX_INTENSITY, "the ceiling is a knee, not a wall")

    def test_forty_thousand(self):
        value = orb_intensity(40000)
        self.assertGreater(value, orb_intensity(20000), "2x the ceiling must read faster")
        self.assertLess(value, MAX_INTENSITY)

    def test_one_hundred_thousand(self):
        value = orb_intensity(100000)
        self.assertGreater(value, orb_intensity(40000), "100k must read faster than 40k")
        self.assertLess(value, MAX_INTENSITY)

    def test_one_million(self):
        value = orb_intensity(1_000_000)
        self.assertGreater(value, orb_intensity(100000), "1M must read faster than 100k")
        self.assertLessEqual(value, MAX_INTENSITY, "even 1M cannot break the visual ceiling")
        self.assertGreater(value, MAX_INTENSITY - 0.15)

    def test_above_supported_range_is_clamped_not_exploded(self):
        for rate in (2_000_000, 1e12, 1e18):
            value = orb_intensity(rate)
            self.assertTrue(math.isfinite(value))
            self.assertLessEqual(value, MAX_INTENSITY)


class MonotonicityTest(unittest.TestCase):
    RATES = (0, 1, 5, 25, 60, 140, 248, 402, 621, 1178, 1581, 2623,
             5000, 17000, 20000, 40000, 100000, 500000, 1000000)

    def test_never_decreases(self):
        previous = -1.0
        for rate in self.RATES:
            value = orb_intensity(rate)
            self.assertGreaterEqual(value, previous, f"rate={rate} went backwards")
            previous = value

    def test_strictly_increasing_across_the_tail(self):
        tail = (20000, 40000, 100000, 500000, 1000000)
        values = [orb_intensity(r) for r in tail]
        for i in range(1, len(values)):
            self.assertGreater(
                values[i], values[i - 1],
                f"{tail[i]} must be strictly faster than {tail[i-1]}",
            )


class BoundsTest(unittest.TestCase):
    def test_never_below_min_or_above_max(self):
        for rate in (0, 1, 5, 2623, 20000, 100000, 1000000):
            value = orb_intensity(rate)
            self.assertGreaterEqual(value, MIN_INTENSITY)
            self.assertLessEqual(value, MAX_INTENSITY)

    def test_any_ceiling_still_respects_max(self):
        for ceiling in (1000, 20000, 100000):
            self.assertLessEqual(
                orb_intensity(1_000_000, ceiling=ceiling), MAX_INTENSITY,
                f"ceiling={ceiling} let the intensity escape",
            )


class LegibilityTest(unittest.TestCase):
    """Real models must land on distinguishable rungs of the curve."""

    ANCHORS = (
        (5, "local proxy floor"),
        (140, "GLM-5.3-Flash @ Lambda C1"),
        (248, "DeepSeek V4.1 Flash"),
        (621, "Llama 4 Scout @ Groq"),
        (1178, "Mercury 2.5"),
        (1581, "Celeris-1"),
        (2623, "Llama 4 Scout @ Cerebras"),
        (17000, "Taalas HC1"),
    )

    def test_each_anchor_is_visibly_faster_than_the_previous(self):
        values = [(orb_intensity(rate), label) for rate, label in self.ANCHORS]
        for i in range(1, len(values)):
            self.assertGreater(
                values[i][0] - values[i - 1][0], 0.02,
                f"{values[i][1]} and {values[i-1][1]} animate almost identically",
            )

    def test_proxy_and_cloud_flash_are_far_apart(self):
        self.assertGreater(orb_intensity(248) - orb_intensity(6), 0.5)

    def test_silicon_class_is_near_but_not_at_the_top(self):
        value = orb_intensity(17000)
        self.assertGreater(value, MAX_INTENSITY * 0.9)
        self.assertLess(value, MAX_INTENSITY)


class ConfigurableCeilingTest(unittest.TestCase):
    def test_raising_the_ceiling_recalibrates_the_body(self):
        """A 40k ceiling makes 20k a mid-body rate instead of the knee."""
        default_at_20k = orb_intensity(20000)
        raised_at_20k = orb_intensity(20000, ceiling=40000)
        self.assertLess(raised_at_20k, default_at_20k,
                        "20k should no longer sit at the knee under a 40k ceiling")
        self.assertAlmostEqual(orb_intensity(40000, ceiling=40000),
                               knee_intensity(), places=3)

    def test_ceiling_change_keeps_bounds(self):
        for ceiling in (100, 1000, 20000, 200000):
            for rate in (0, 1, 5000, 1_000_000):
                value = orb_intensity(rate, ceiling=ceiling)
                self.assertGreaterEqual(value, MIN_INTENSITY)
                self.assertLessEqual(value, MAX_INTENSITY)

    def test_exceeds_tracks_the_configured_ceiling(self):
        self.assertFalse(exceeds(17000))
        self.assertFalse(exceeds(20000))
        self.assertTrue(exceeds(20001))
        self.assertFalse(exceeds(17000, ceiling=40000),
                         "under a 40k ceiling, 17k is not a breach")
        self.assertTrue(exceeds(50000, ceiling=40000))


class SmoothStepTest(unittest.TestCase):
    """A sudden 200 → 20,000 tok/s jump must not snap the visual."""

    RISE_PER_SECOND = 1.6
    FALL_PER_SECOND = 0.55

    def _slew(self, start, target, seconds, dt=1 / 60):
        value = start
        elapsed = 0.0
        while elapsed < seconds:
            rising = target > value
            limit = (MAX_INTENSITY - MIN_INTENSITY) * (
                self.RISE_PER_SECOND if rising else self.FALL_PER_SECOND
            )
            step = max(-limit * dt, min(limit * dt, target - value))
            value += step
            elapsed += dt
        return value

    def test_rising_is_rate_limited(self):
        jumped = self._slew(MIN_INTENSITY, MAX_INTENSITY, seconds=0.1)
        self.assertLess(jumped, MIN_INTENSITY + 0.6,
                        "a spike must not cross most of the range in 100ms")

    def test_rising_reaches_target_given_enough_time(self):
        settled = self._slew(MIN_INTENSITY, MAX_INTENSITY, seconds=1.5)
        self.assertAlmostEqual(settled, MAX_INTENSITY, places=2)

    def test_falling_is_slower_than_rising(self):
        up = self._slew(MIN_INTENSITY, MAX_INTENSITY, seconds=0.3) - MIN_INTENSITY
        down = MAX_INTENSITY - self._slew(MAX_INTENSITY, MIN_INTENSITY, seconds=0.3)
        self.assertGreater(up, down, "decay should settle slower than a surge")

    def test_no_oscillation_at_the_boundary(self):
        """Sitting at the ceiling must not oscillate around it."""
        values = []
        value = MAX_INTENSITY
        for _ in range(120):
            rising = MAX_INTENSITY > value
            limit = (MAX_INTENSITY - MIN_INTENSITY) * (
                self.RISE_PER_SECOND if rising else self.FALL_PER_SECOND
            )
            step = max(-limit / 60, min(limit / 60, MAX_INTENSITY - value))
            value += step
            values.append(value)
        self.assertLessEqual(max(values) - min(values), 1e-6)


if __name__ == "__main__":
    unittest.main()
