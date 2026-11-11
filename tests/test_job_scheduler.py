"""Scheduler tests: cron maths, due/pause semantics, persistence, 404."""

import sys
import tempfile
import unittest
from datetime import datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server.job_scheduler import (
    CronError,
    Job,
    JobStore,
    RunRecord,
    due_jobs,
    next_run,
    parse_cron,
    record_run,
)


def _job(**kw):
    kw.setdefault("name", "nightly")
    kw.setdefault("prompt", "run pytest -q")
    kw.setdefault("cron_expr", "0 6 * * *")
    return Job(**kw)


class CronTest(unittest.TestCase):
    def test_daily_0600_from_midnight(self):
        spec = parse_cron("0 6 * * *")
        got = next_run(spec, datetime(2026, 1, 1, 0, 0))
        self.assertEqual(got, datetime(2026, 1, 1, 6, 0))

    def test_quarter_hour(self):
        spec = parse_cron("*/15 * * * *")
        self.assertEqual(
            next_run(spec, datetime(2026, 1, 1, 0, 7)),
            datetime(2026, 1, 1, 0, 15),
        )
        self.assertEqual(
            next_run(spec, datetime(2026, 1, 1, 0, 15)),
            datetime(2026, 1, 1, 0, 30),
        )

    def test_month_day_rollover(self):
        spec = parse_cron("0 0 1 * *")
        got = next_run(spec, datetime(2026, 1, 31, 12, 0))
        self.assertEqual(got, datetime(2026, 2, 1, 0, 0))

    def test_dow_zero_and_seven_are_sunday(self):
        # 2026-01-04 is a Sunday.
        for expr in ("0 0 * * 0", "0 0 * * 7"):
            got = next_run(parse_cron(expr), datetime(2026, 1, 1, 0, 0))
            self.assertEqual(got.weekday(), 6, expr)  # Sunday
            self.assertEqual(got, datetime(2026, 1, 4, 0, 0), expr)

    def test_invalid_expression_names_field(self):
        with self.assertRaises(CronError) as ctx:
            parse_cron("99 * * * *")
        self.assertEqual(ctx.exception.field, "minute")
        self.assertIn("minute", str(ctx.exception))
        with self.assertRaises(CronError) as ctx2:
            parse_cron("* * * *")
        self.assertEqual(ctx2.exception.field, "expression")


class DueTest(unittest.TestCase):
    def test_past_due_future_not_due(self):
        now = datetime(2026, 1, 1, 12, 0)
        past = _job(name="past", last_run_at=now - timedelta(days=2))
        future_anchor = _job(name="future", last_run_at=now + timedelta(days=2))
        due = due_jobs([past, future_anchor], now)
        self.assertEqual([j.name for j in due], ["past"])

    def test_pause_stops_due(self):
        now = datetime(2026, 1, 1, 12, 0)
        paused = _job(enabled=False, last_run_at=now - timedelta(days=2))
        self.assertEqual(due_jobs([paused], now), [])


class PersistenceTest(unittest.TestCase):
    def test_record_run_appends_and_persists(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = JobStore(str(Path(tmp) / "t.db"))
            job = _job(name="persist-me")
            store.save_job(job)
            rec = RunRecord(
                job_id=job.id, started_at=datetime(2026, 1, 1, 6, 0), status="success"
            )
            record_run(job, rec, store=store)
            runs = store.list_runs(job.id)
            self.assertEqual(len(runs), 1)
            self.assertEqual(runs[0].status, "success")
            reloaded = store.get_job(job.id)
            self.assertEqual(reloaded.last_run_at, datetime(2026, 1, 1, 6, 0))

    def test_run_now_writes_exactly_one_completed_record(self):
        """Regression: run_now used to append a 'running' row at the start and a
        second finished row at the end, leaving a permanent phantom 'running'
        entry per run. One execution must produce exactly one record, and that
        record must be terminal."""
        from server.job_scheduler import run_now

        with tempfile.TemporaryDirectory() as tmp:
            store = JobStore(str(Path(tmp) / "t.db"))
            job = _job(name="single-record")
            store.save_job(job)

            record = run_now(job, store=store)

            runs = store.list_runs(job.id)
            self.assertEqual(
                len(runs), 1, f"expected 1 run record, got {len(runs)}: {[r.status for r in runs]}"
            )
            self.assertIn(record.status, ("success", "failed"))
            self.assertNotEqual(runs[0].status, "running")
            self.assertIsNotNone(runs[0].finished_at)


class Endpoint404Test(unittest.IsolatedAsyncioTestCase):
    async def test_unknown_job_returns_404(self):
        import httpx
        from fastapi import FastAPI
        from server.auth import create_new_token, init_auth_db
        from server.job_scheduler import router

        init_auth_db()
        token = create_new_token("test-jobs-404")
        app = FastAPI()
        app.include_router(router)
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://t"
        ) as client:
            headers = {"Authorization": f"Bearer {token}"}
            for method, path in (
                ("POST", "/api/jobs/nope/run"),
                ("POST", "/api/jobs/nope/pause"),
                ("GET", "/api/jobs/nope/runs"),
            ):
                resp = await client.request(method, path, headers=headers)
                self.assertEqual(resp.status_code, 404, (method, path, resp.text))


if __name__ == "__main__":
    unittest.main()
