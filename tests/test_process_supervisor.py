"""The process supervisor: list running rounds, cancel exactly one.

FEATURE.md asked for a live agent process list with an elapsed timer and a
working kill action. The orchestrator only exposed `active_turns: 3` — no ids,
no elapsed time, no way to stop one. `orchestrator.stop()` exists but kills
every turn in the fleet, which is not a kill switch.

These tests drive a real orchestrator with a slow turn and assert that
cancelling one round leaves the other rounds alone.
"""

import asyncio
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server.agent_orchestrator import AgentOrchestrator


class _SlowOrchestrator(AgentOrchestrator):
    """An orchestrator whose turns block until released, so a round is observably running."""

    def __init__(self, **kwargs):
        super().__init__(persist=False, use_live_models=False, **kwargs)
        self.release = asyncio.Event()
        self.started = asyncio.Event()

    async def _run_turn(self, agent, round_, index, context):
        self.started.set()
        await self.release.wait()
        return f"{agent.id} reply"


def _run(coro):
    return asyncio.get_event_loop_policy().new_event_loop().run_until_complete(coro)


class ProcessListTest(unittest.TestCase):
    def test_no_processes_when_idle(self):
        orch = AgentOrchestrator(persist=False, use_live_models=False)
        self.assertEqual(orch.processes(), [])

    def test_running_round_appears_with_live_fields(self):
        async def scenario():
            orch = _SlowOrchestrator()
            orch._schedule_turns(list(orch.agent_order)[:2], "council", "hello")
            await orch.started.wait()
            rows = orch.processes()
            orch.release.set()
            await asyncio.sleep(0.05)
            return rows

        rows = _run(scenario())
        self.assertEqual(len(rows), 1)
        row = rows[0]
        self.assertEqual(row["kind"], "round")
        self.assertEqual(row["room_id"], "council")
        self.assertEqual(row["prompt"], "hello")
        self.assertTrue(row["agent_id"])
        self.assertTrue(row["agent_name"])
        self.assertGreaterEqual(row["total_turns"], 1)
        self.assertIsInstance(row["started_at"], float)
        self.assertGreaterEqual(row["elapsed_seconds"], 0.0)
        self.assertTrue(row["cancellable"])

    def test_finished_round_leaves_the_list(self):
        async def scenario():
            orch = _SlowOrchestrator()
            orch.release.set()
            orch._schedule_turns(list(orch.agent_order)[:1], "council", "quick")
            await asyncio.sleep(0.15)
            return orch.processes()

        self.assertEqual(_run(scenario()), [])


class CancelTest(unittest.TestCase):
    def test_cancel_stops_the_round_and_reports_true(self):
        async def scenario():
            orch = _SlowOrchestrator()
            orch._schedule_turns(list(orch.agent_order)[:1], "council", "long")
            await orch.started.wait()
            round_id = orch.processes()[0]["id"]

            result = orch.cancel_round(round_id)
            await asyncio.sleep(0.1)
            return result, orch.processes()

        result, remaining = _run(scenario())
        self.assertTrue(result["cancelled"])
        self.assertEqual(result["room_id"], "council")
        self.assertIn("elapsed_seconds", result)
        self.assertEqual(remaining, [], "a cancelled round must leave the process list")

    def test_cancel_unknown_round_is_reported_not_faked(self):
        orch = AgentOrchestrator(persist=False, use_live_models=False)
        result = orch.cancel_round("round-does-not-exist")
        self.assertFalse(result["cancelled"])
        self.assertIn("no running round", result["reason"])

    def test_cancel_one_round_leaves_a_sibling_running(self):
        """The whole point of a per-process kill: the fleet keeps working."""

        async def scenario():
            orch = _SlowOrchestrator()
            orch._schedule_turns(list(orch.agent_order)[:1], "alpha", "first")
            await orch.started.wait()
            orch._schedule_turns(list(orch.agent_order)[:1], "beta", "second")
            await asyncio.sleep(0.05)

            rows = orch.processes()
            self.assertGreaterEqual(len(rows), 1)
            victim = next(r for r in rows if r["room_id"] == "alpha")
            summary = orch.cancel_round(victim["id"])
            await asyncio.sleep(0.1)
            return summary, orch.processes()

        summary, remaining = _run(scenario())
        self.assertTrue(summary["cancelled"])
        self.assertEqual(
            [r["room_id"] for r in remaining],
            ["beta"],
            "cancelling alpha must not touch beta",
        )


class ProcessRoutesTest(unittest.TestCase):
    def test_routes_are_reachable_by_clients(self):
        from server.main import app

        paths = app.openapi()["paths"]
        self.assertIn("/api/processes", paths)
        self.assertIn("/api/processes/{round_id}/kill", paths)
        self.assertIn("get", paths["/api/processes"])
        self.assertIn("post", paths["/api/processes/{round_id}/kill"])


if __name__ == "__main__":
    unittest.main()
