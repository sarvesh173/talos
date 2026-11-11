"""The cron tick loop actually fires jobs.

`due_jobs()` existed with tests and no caller: a job created through the API
rendered a schedule in the app and only ever ran when someone pressed Run.
This pins the loop that fixes it, including the two ways a naive loop goes
wrong — running a job that is already running, and running it every tick.
"""

import sys
import tempfile
import time
import unittest
from datetime import datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server.job_scheduler import (
    Job,
    JobStore,
    RunRecord,
    SchedulerTick,
    due_jobs,
)


def _job(**overrides) -> Job:
    payload = dict(
        name="nightly digest",
        prompt="summarise the day",
        cron_expr="* * * * *",
        deliver="local",
        profile="maintainer",
        enabled=True,
        created_at=datetime.now() - timedelta(hours=2),
    )
    payload.update(overrides)
    return Job(**payload)


class RecordingRunner:
    """Stands in for run_now and records what was actually started."""

    def __init__(self, delay: float = 0.0, fail: bool = False):
        self.calls = []
        self.delay = delay
        self.fail = fail

    def __call__(self, job, store):
        self.calls.append(job.id)
        if self.delay:
            time.sleep(self.delay)
        if self.fail:
            raise RuntimeError("boom")
        record = RunRecord(job_id=job.id, started_at=datetime.now(), status="success")
        record.finished_at = datetime.now()
        return record


class TickTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = JobStore(db_path=str(Path(self.tmp.name) / "jobs.db"))

    def tearDown(self):
        self.tmp.cleanup()

    def _tick(self, runner, now=None, **kwargs):
        return SchedulerTick(
            store=self.store,
            runner=runner,
            now=(lambda: now) if now else None,
            **kwargs,
        )

    def test_due_job_is_started_exactly_once(self):
        job = self.store.save_job(_job())
        runner = RecordingRunner()
        tick = self._tick(runner)

        started = tick.tick()

        self.assertEqual(started, [job.id])
        # The runner is on a thread; give it a moment to record the call.
        for _ in range(50):
            if runner.calls:
                break
            time.sleep(0.01)
        self.assertEqual(runner.calls, [job.id])

    def test_disabled_job_never_starts(self):
        self.store.save_job(_job(enabled=False))
        runner = RecordingRunner()
        self.assertEqual(self._tick(runner).tick(), [])
        self.assertEqual(runner.calls, [])

    def test_nothing_due_means_no_call(self):
        # A job that just ran cannot be due again until its next slot.
        self.store.save_job(_job(last_run_at=datetime.now()))
        runner = RecordingRunner()
        self.assertEqual(self._tick(runner).tick(), [])
        time.sleep(0.05)
        self.assertEqual(runner.calls, [])

    def test_same_job_is_not_started_twice_while_running(self):
        """A job slower than its cron interval must not pile up executions."""
        job = self.store.save_job(_job())
        runner = RecordingRunner(delay=0.4)
        tick = self._tick(runner, min_rerun_seconds=0.0)

        first = tick.tick()
        second = tick.tick()  # the thread is still inside the runner

        self.assertEqual(first, [job.id])
        self.assertEqual(second, [], "in-flight job was started a second time")
        time.sleep(0.5)

    def test_rerun_is_throttled_within_the_window(self):
        job = self.store.save_job(_job())
        runner = RecordingRunner()
        tick = self._tick(runner, min_rerun_seconds=3600.0)

        self.assertEqual(tick.tick(), [job.id])
        time.sleep(0.05)
        self.assertEqual(tick.tick(), [], "throttle window was ignored")

    def test_throttle_expires(self):
        job = self.store.save_job(_job())
        runner = RecordingRunner()
        tick = self._tick(runner, min_rerun_seconds=0.0)

        self.assertEqual(tick.tick(), [job.id])
        time.sleep(0.1)
        self.assertEqual(tick.tick(), [job.id], "job never became eligible again")
        time.sleep(0.1)

    def test_a_crashing_runner_does_not_kill_the_loop(self):
        self.store.save_job(_job())
        runner = RecordingRunner(fail=True)
        tick = self._tick(runner, min_rerun_seconds=0.0)

        self.assertEqual(len(tick.tick()), 1)
        time.sleep(0.2)
        # The job is released from in-flight even though the runner raised.
        self.assertEqual(tick._in_flight, set())

    def test_status_reports_what_the_loop_did(self):
        job = self.store.save_job(_job())
        tick = self._tick(RecordingRunner())
        tick.tick()
        time.sleep(0.05)

        status = tick.status()
        self.assertEqual(status["ticks"], 1)
        self.assertEqual(status["runs_started"], 1)
        self.assertFalse(status["running"])
        self.assertEqual(status["in_flight"], [])
        self.assertEqual(status["tick_seconds"], 20.0)


class DueJobContractTest(unittest.TestCase):
    """due_jobs is the selector the loop depends on; keep its contract pinned."""

    def test_only_enabled_and_due_jobs_are_selected(self):
        now = datetime.now()
        due = _job(created_at=now - timedelta(hours=3))
        future = _job(created_at=now, cron_expr="0 4 * * *", last_run_at=now)
        off = _job(enabled=False, created_at=now - timedelta(days=9))
        selected = due_jobs([due, future, off], now)
        self.assertIn(due, selected)
        self.assertNotIn(future, selected)
        self.assertNotIn(off, selected)


class SchedulerRouteTest(unittest.TestCase):
    def test_status_route_is_reachable(self):
        from server.main import app

        self.assertIn("/api/scheduler/status", app.openapi()["paths"])


if __name__ == "__main__":
    unittest.main()
