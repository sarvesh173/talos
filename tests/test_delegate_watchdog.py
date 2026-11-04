"""Delegate watchdog: milestone ladder, elapsed formatting, stall and transition detection.

All tests use an injected FakeClock — no real sleeping.
"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server.agent_orchestrator import AgentOrchestrator
from server.delegate_watchdog import (
    DelegateWatchdog,
    compact_elapsed,
    crossed_milestones,
    format_elapsed,
    is_stalled,
    milestone_seconds,
    new_milestone,
    next_milestone_in,
)


class FakeClock:
    def __init__(self, start=1_000_000.0):
        self.now = float(start)

    def __call__(self):
        return self.now

    def advance(self, seconds):
        self.now += seconds


class MilestoneLadderTest(unittest.TestCase):
    def test_base_rungs_10_20_30_50(self):
        self.assertEqual(milestone_seconds(0), 10 * 60)
        self.assertEqual(milestone_seconds(1), 20 * 60)
        self.assertEqual(milestone_seconds(2), 30 * 60)
        self.assertEqual(milestone_seconds(3), 50 * 60)

    def test_doubling_after_50(self):
        self.assertEqual(milestone_seconds(4), 100 * 60)
        self.assertEqual(milestone_seconds(5), 200 * 60)
        self.assertEqual(milestone_seconds(6), 400 * 60)

    def test_crossed_counts_rungs_not_seconds(self):
        self.assertEqual(crossed_milestones(0), 0)
        self.assertEqual(crossed_milestones(599), 0)
        self.assertEqual(crossed_milestones(600), 1)
        self.assertEqual(crossed_milestones(1199), 1)
        self.assertEqual(crossed_milestones(1200), 2)
        self.assertEqual(crossed_milestones(3000), 4)
        self.assertEqual(crossed_milestones(6000), 5)

    def test_new_milestone_only_on_crossing(self):
        self.assertIsNone(new_milestone(599, 0))
        self.assertEqual(new_milestone(600, 0), 600)
        self.assertIsNone(new_milestone(601, 1))
        self.assertEqual(new_milestone(1200, 1), 1200)

    def test_multiple_rungs_in_one_jump_collapse_to_one(self):
        # Backgrounded app jumps from 0 to 50 min: one update, not four.
        self.assertEqual(new_milestone(3000, 0), 3000)

    def test_next_milestone_in(self):
        self.assertEqual(next_milestone_in(0), 600)
        self.assertEqual(next_milestone_in(600), 600)  # next is 1200
        self.assertEqual(next_milestone_in(700), 500)

    def test_arbitrarily_large_elapsed_no_overflow(self):
        huge = 10**15
        crossed = crossed_milestones(huge)
        self.assertGreater(crossed, 10)
        # next milestone is still computed and in the future
        self.assertGreaterEqual(next_milestone_in(huge), 0)


class ElapsedFormatTest(unittest.TestCase):
    def test_seconds_to_hours(self):
        self.assertEqual(format_elapsed(45), "45s")
        self.assertEqual(format_elapsed(63), "1:03")
        self.assertEqual(format_elapsed(3605), "1:00:05")

    def test_days_months_years_centuries(self):
        self.assertTrue(format_elapsed(86_400).startswith("1d"))
        self.assertTrue(format_elapsed(2_592_000).startswith("1mo"))
        self.assertTrue(format_elapsed(31_536_000).startswith("1y"))
        self.assertIn("c", format_elapsed(3_153_600_000))

    def test_negative_clamped(self):
        self.assertEqual(format_elapsed(-45), "45s")

    def test_compact(self):
        self.assertEqual(compact_elapsed(72), "1m 12s")
        self.assertEqual(compact_elapsed(7500), "2h 5m")
        self.assertEqual(compact_elapsed(273_600), "3d 4h")


class StallDetectionTest(unittest.TestCase):
    def test_is_stalled_threshold(self):
        self.assertFalse(is_stalled(now=1000, last_progress_at=900, stall_threshold_seconds=300))
        self.assertFalse(is_stalled(now=1200, last_progress_at=900, stall_threshold_seconds=300))
        self.assertTrue(is_stalled(now=1201, last_progress_at=900, stall_threshold_seconds=300))

    def test_watchdog_emits_stall_once(self):
        clock = FakeClock()
        events, notes = [], []
        wd = DelegateWatchdog(
            emit=lambda r, t, p: events.append((r, t, p)),
            notify=lambda m: notes.append(m),
            stall_threshold_seconds=300,
            clock=clock,
        )
        registry = {
            "d1": {"id": "d1", "name": "Worker", "status": "running",
                    "started_at": clock.now, "last_progress_at": clock.now,
                    "progress": 0.0, "tokens_generated": 0, "room_id": "r1"}
        }
        clock.advance(301)
        transitions = wd.poll_once(registry)
        self.assertEqual([t["type"] for t in transitions], ["stalled"])
        self.assertEqual(events[0][1], "delegate_stalled")
        self.assertEqual(len(notes), 1)
        # Second poll: no duplicate stall event.
        self.assertEqual(wd.poll_once(registry), [])
        self.assertEqual(len(events), 1)

    def test_progress_resets_stall_and_emits_resumed(self):
        clock = FakeClock()
        events = []
        wd = DelegateWatchdog(emit=lambda r, t, p: events.append(t),
                              stall_threshold_seconds=100, clock=clock)
        registry = {
            "d1": {"id": "d1", "name": "W", "status": "running",
                    "started_at": clock.now, "last_progress_at": clock.now,
                    "progress": 0.0, "tokens_generated": 0, "room_id": "r"}
        }
        clock.advance(150)
        wd.poll_once(registry)
        # Progress arrives.
        registry["d1"]["progress"] = 0.5
        registry["d1"]["last_progress_at"] = clock.now
        transitions = wd.poll_once(registry)
        self.assertEqual([t["type"] for t in transitions], ["resumed"])


class OrchestratorWatchdogTest(unittest.TestCase):
    def setUp(self):
        self.clock = FakeClock()
        self.orch = AgentOrchestrator(persist=False, clock=self.clock, stall_threshold_seconds=300)
        self.events = []
        self.orch._emit = lambda room, etype, payload: self.events.append((room, etype, payload))

    def test_milestone_notification_only_on_rungs_not_per_second(self):
        self.orch._watchdog.stall_threshold_seconds = 10**9
        self.orch.register_delegate("d1", "Worker", "task", ttl_seconds=10**9, room_id="r1")
        # Poll every second for 9 minutes: zero milestone events.
        for _ in range(540):
            self.clock.advance(1)
            self.assertEqual(self.orch.watchdog_poll(), [])
        # Cross 10 min.
        self.clock.advance(60)
        transitions = self.orch.watchdog_poll()
        self.assertEqual([t["type"] for t in transitions], ["milestone"])
        self.assertEqual(transitions[0]["milestone_seconds"], 600)
        # Immediate re-poll: no duplicate.
        self.assertEqual(self.orch.watchdog_poll(), [])

    def test_completion_and_failure_emitted_once(self):
        self.orch.register_delegate("d1", "Worker", "task", ttl_seconds=10**9, room_id="r1")
        self.orch.watchdog_poll()  # baseline running state
        self.orch.complete_delegate("d1")
        transitions = self.orch.watchdog_poll()
        self.assertEqual([t["type"] for t in transitions], ["completed"])
        self.assertEqual(self.orch.watchdog_poll(), [])

        self.orch.register_delegate("d2", "Worker2", "task", ttl_seconds=10**9, room_id="r1")
        self.orch.watchdog_poll()
        self.orch.fail_delegate("d2", error="boom")
        transitions = self.orch.watchdog_poll()
        self.assertEqual([t["type"] for t in transitions], ["failed"])

    def test_list_delegates_elapsed_label_with_fake_clock(self):
        self.orch.register_delegate("d1", "Worker", "task", ttl_seconds=10**9, room_id="r1")
        self.clock.advance(3661)
        delegates = self.orch.list_delegates()
        self.assertEqual(delegates[0]["elapsed_seconds"], 3661)
        self.assertEqual(delegates[0]["elapsed_label"], "1:01:01")
        self.assertEqual(delegates[0]["milestones_crossed"], 4)  # 10,20,30,50 -> 50min=3000 <3661


if __name__ == "__main__":
    unittest.main()
