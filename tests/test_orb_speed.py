"""Orb speed curve contract.

The curve is what makes the thinking orb a tachometer instead of decoration.
These tests pin the properties that matter:

* the real measured per-user band (5 → 2623 tok/s) stays legible, i.e. distinct
  models produce visibly different animation speeds;
* nothing exceeds maxSpeed, no matter how absurd the input;
* the 5000 tok/s ceiling is a hard boundary and crossing it is detectable, so the
  UI can offer the "raise the ceiling" report link.
"""

import math
import unittest

MAX_CALIBRATED_RATE = 5000.0
MIN_SPEED = 0.55
MAX_SPEED = 2.6


def curve_slope(max_speed=MAX_SPEED, floor=5.0, ceiling=MAX_CALIBRATED_RATE):
    octaves = math.log(ceiling / floor) / math.log(2)
    return (max_speed - 1.0) / octaves


def orb_speed_target(rate, min_speed=MIN_SPEED, max_speed=MAX_SPEED, floor=5.0):
    if rate <= 0:
        return min_speed
    clamped = max(rate, floor)
    octaves = math.log(clamped / floor) / math.log(2)
    target = 1.0 + octaves * curve_slope(max_speed, floor)
    return max(min_speed, min(max_speed, target))


def exceeds_ceiling(rate, ceiling=MAX_CALIBRATED_RATE):
    return rate > ceiling


class MonotonicityTest(unittest.TestCase):
    def test_speed_never_decreases_as_rate_increases(self):
        previous = -1.0
        for rate in (5, 10, 25, 60, 140, 248, 402, 621, 1178, 1581, 2623, 5000):
            current = orb_speed_target(rate)
            self.assertGreaterEqual(current, previous, f"rate={rate} went backwards")
            previous = current

    def test_zero_and_negative_are_idle_speed(self):
        self.assertEqual(orb_speed_target(0), MIN_SPEED)
        self.assertEqual(orb_speed_target(-100), MIN_SPEED)

    def test_below_floor_is_clamped_to_baseline(self):
        # A trickle should look like normal work, not like the orb is stopped.
        self.assertAlmostEqual(orb_speed_target(1), 1.0, places=2)
        self.assertAlmostEqual(orb_speed_target(5), 1.0, places=2)


class CeilingTest(unittest.TestCase):
    def test_ceiling_maps_to_max_speed(self):
        self.assertAlmostEqual(orb_speed_target(5000), MAX_SPEED, places=2)

    def test_absurd_rate_is_still_capped(self):
        for rate in (6000, 10_000, 100_000, 1_000_000):
            self.assertEqual(
                orb_speed_target(rate), MAX_SPEED, f"{rate} tok/s escaped the cap"
            )

    def test_ceiling_detection_is_strict(self):
        self.assertFalse(exceeds_ceiling(4999))
        self.assertFalse(exceeds_ceiling(5000), "exactly at the ceiling is not a breach")
        self.assertTrue(exceeds_ceiling(5001))
        self.assertTrue(exceeds_ceiling(6200))


class LegibilityTest(unittest.TestCase):
    """Every real model must land on a visibly different rung."""

    ANCHORS = (
        (140, "GLM-5.3-Flash @ Lambda C1"),
        (248, "DeepSeek V4.1 Flash"),
        (402, "Gemini 3.5 Flash Lite"),
        (621, "Llama 4 Scout @ Groq"),
        (1178, "Mercury 2.5"),
        (1581, "Celeris-1"),
        (2623, "Llama 4 Scout @ Cerebras"),
    )

    def test_consecutive_anchors_are_distinguishable(self):
        speeds = [orb_speed_target(rate) for rate, _ in self.ANCHORS]
        for i in range(1, len(speeds)):
            self.assertGreater(
                speeds[i] - speeds[i - 1],
                0.04,
                f"{self.ANCHORS[i][1]} and {self.ANCHORS[i-1][1]} animate "
                f"almost identically ({speeds[i-1]:.2f} vs {speeds[i]:.2f})",
            )

    def test_fastest_measured_model_has_headroom_below_the_cap(self):
        """Cerebras 2623 sits under 5000 so the ceiling is not already in use."""
        speed = orb_speed_target(2623)
        self.assertLess(speed, MAX_SPEED, "the fastest real model already saturates")
        self.assertGreater(speed, 2.3, "the fastest real model should still read as fast")

    def test_a_local_proxy_and_a_cloud_flash_model_differ_a_lot(self):
        slow = orb_speed_target(6)
        fast = orb_speed_target(248)
        self.assertGreater(fast - slow, 0.8)


if __name__ == "__main__":
    unittest.main()
