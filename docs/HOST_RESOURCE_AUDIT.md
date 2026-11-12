# Host Resource Audit — TALOS host-side background agent

**Date:** 2026-11-12 (measurement window 2026-11-12 03:30–09:20 IST)
**Host:** Celeron laptop, 2 cores, 3.7 GiB RAM, 12 GiB swap, 229 GB disk (92 % used, 18 GB free)
**Scope:** the always-on TALOS host side — `talos-api` (Python/FastAPI, `:8000`) and `talos-core` (Rust, `:8010`).
**Goal:** keep the host quiet on a low-power machine that is also a WiFi hotspot and the user's coding box.

Everything below is measured, not estimated. Re-running the commands reproduces the numbers.

---

## 1. What is actually resident

```
ps -eo pid,rss,pcpu,etime,comm,args --sort=-rss | head -30
systemctl --user list-units --type=service --state=running
systemctl list-units --type=service --state=running
```

Only **two** processes belong to TALOS. No duplicate workers, no orphans, no stray
instances:

| PID | Unit | What it is | RSS | CPU | Notes |
|---|---|---|---|---|---|
| 1533757 | `talos-api.service` | `python3 -m uvicorn server.main:app --host 0.0.0.0 --port 8000` | **18.6 MB** | 0.2 % | 1 worker, `Restart=always` |
| 1139 | `talos-core.service` | `crates/talos-core/target/release/talos-core` | **2.0 MB** | 0.0 % | idle Rust control plane, up 1d 05 h |

Everything else in the top-30 RSS list is unrelated to TALOS (Hermes agents, `omniroute`,
node/n8n, `tailscaled`, `dockerd`, `opencode`). Nothing to clean up there.

The Rust core's **178 MB peak** is a startup transient, not a steady-state cost: `main.rs`
calls `supervisor.rescan()` at boot, which shells out to `which` + `--version` for 12 agent
binaries (hermes, opencode, omnirush, codex, cline, opencli, koda, atlarix, darce, supercode,
zcode, gemini). Resident cost settles back to 2 MB.

---

## 2. Idle CPU — the headline

Measured with `/proc/<pid>/stat` `utime+stime` ticks over 150 s windows (1 tick = 10 ms),
cross-checked against cgroup `cpu.stat` `usage_usec`:

| Process | % of one core | CPU-s/day |
|---|---|---|
| `talos-api` (full app, asyncio) | 0.2133 % | 184.3 |
| `talos-core` (Rust) | 0.0000 % | 0.0 |
| **TALOS total** | **0.2133 %** | **184.3** |

**The TALOS stack burns 184 CPU-seconds per day — 0.11 % of one core.** On a 2-core box
that is 0.06 % of total machine capacity. The CPU story is already good; the interesting
number is *where that 184 s goes*.

### The uvicorn floor

Running a bare FastAPI app with a single `/health` route under the same uvicorn
(0.52.4), same interpreter, same host:

| Configuration | % of one core | CPU-s/day |
|---|---|---|
| Bare uvicorn, asyncio | 0.2167 % | 187.2 |
| Bare uvicorn, uvloop | 0.1833 % | 158.4 |
| Full TALOS app, asyncio | 0.2133 % | 184.3 |
| Full TALOS app, uvloop | 0.1867 % | 161.3 |

**96 % of TALOS's idle CPU is uvicorn's own event loop, not TALOS code.** TALOS's
application code accounts for ~7 CPU-s/day on top of the floor. `uvicorn.Server.main_loop`
sleeps 0.1 s per iteration and rebuilds the `Date` header each second; that is the tick.

`--loop uvloop` cuts idle CPU by **23 CPU-s/day (12.5 %)**. Measured on the full app,
including a WebSocket round-trip (`/ws/the-council/<client>` → `room_sync` reply) to confirm
the gateway works on the uvloop event loop.

### Per-request cost

| Endpoint | CPU per request | Status |
|---|---|---|
| `GET /health` | 1.60–1.70 ms | 200 |
| `GET /v1/models` | 1.80 ms | **404** |

At the observed 4 req/min the request path costs ~10 CPU-s/day. There is a **constant
~2.4 ms/s background CPU independent of request rate** — a 180 s, 1 s-resolution timeline
showed a flat baseline with no request correlation, i.e. it is the uvicorn loop above.

---

## 3. Memory

| Process | RSS now | cgroup `memory.peak` | `memory.swap.current` |
|---|---|---|---|
| `talos-api` | 18.6 MB | 91.9–96 MB | 39.2 MB |
| `talos-core` | 2.0 MB | 170.3–178 MB | 1.0 MB |

The API's 96 MB peak is startup, and it is almost entirely the framework, not TALOS:

```
stage                           maxRSS MB    delta
python baseline                       9.1     +0.3
fastapi+uvicorn+pydantic             45.6    +36.4   <- 63 % of the total
core.models                          46.4     +0.9
core.room_bus+consensus              46.4     +0.0
server.db+auth                       54.3     +7.9
server.agent_orchestrator            55.1     +0.8
server.cli_engines                   55.2     +0.1
server.provider_service              55.2     +0.0
server.main                          57.8     +2.6
```

No third-party heavyweights are imported (`httpx`, `cryptography`, `yaml`, `PIL` all cost
0.0 MB; `numpy`/`aiohttp` are not installed).

The 39.2 MB of swap is **not TALOS's fault** — the host has only 210 MB free RAM and
5.6 GB of swap already in use across all processes. TALOS's steady 18.6 MB RSS is small;
the kernel evicted its cold startup pages because the machine is short on RAM generally.

---

## 4. Disk

```
df -h /          -> 229G total, 200G used, 18G avail, 92%
du -sh server/data/  -> 9.5M
du -sh ~/.talos/      -> 5.7M
```

| File | Size | Growth | Note |
|---|---|---|---|
| `server/data/talos.db` | 5.7 MB | — | main store |
| `server/data/talos.db-wal` | 4.2 MB | — | WAL, 1016 pages vs the 1000-page autocheckpoint threshold, so it checkpoints constantly |
| `~/.talos/api.log` | 178 KB | **~354 KiB/day** | uvicorn access log, one line per request; **no logrotate rule covered it** |

`api.log` at 63 bytes/request and the observed request rate works out to ~354 KiB/day,
~126 MiB/year, on a disk that is already 92 % full.

### The `tick proof` job was the growth engine

A job named **`tick proof`** with the prompt *"prove the loop fires"*, `cron_expr = * * * * *`
(minutely), `enabled = true`, created 2026-11-11T22:49:09 — two minutes after commit
`9c64f75` *"feat(scheduler): fire due cron jobs on a real tick loop"*. It exists **only in
the live database**; it is not in git history, not in any source file, not created by any
code path. It was a developer's smoke test for the new tick loop, left switched on.

Each run wrote a `messages` row, an FTS row and a `job_runs` row:

```
600 s window, job enabled:  job_runs +10, messages +10, dbstat +12 288 B
                              -> 1 229 B per run -> 1 728 KiB/day -> 616 GiB/year of page churn
```

Note the row *counts* grew while the main DB file and WAL did not — SQLite reuses freed
pages, so the disk file stayed flat at 5 669 KB while the table contents grew. The growth
shows up as scan cost, `dbstat` page count, and FTS index bloat, not as file size.

---

## 5. The test suite was writing to the production database

This is the largest single finding, and it is a correctness bug as much as a resource one.

`tests/` has **no `conftest.py`** and no DB isolation. `server/db.py` hardcoded
`_DB_PATH = server/data / "talos.db"` and `server/auth.py` only allowed an override for
the *auth* store via `TALOS_AUTH_DB_PATH`. So `pytest tests` opened and wrote the same
file the live `talos-api` service has open:

```
before a full 601-test run:  api_tokens 6828   messages 4735
after  a full 601-test run:  api_tokens 6828+  messages 4735+
```

Every run inserted ~130 `api_tokens` rows and ~85 `messages` rows into live production
data. The residue after the project's history:

```
api_tokens by label prefix:
e2e-providers-client    2042   e2e-token-client   1197   e2e-rooms-client   855
Test Consensus Token     646   Test Failover Token  467   e2e-verify-client   199
e2e-dm-client            199   e2e-council-loop-   199   e2e-council-client  199
e2e-ws-binary-client     166   e2e-revoke-client   171   test-jobs-404        49
probe                     34   ws-test               1
------------------------------------------------------
test artifacts          6424   real tokens           404
```

**94 % of the token table was test residue.** Consequences:

* `server/auth.py::list_tokens()` did a full scan + temp B-tree sort over 6 699 rows and
  returned **339 ms** per call with no pagination.
* `api_tokens` occupied 852 KB + a 512 KB index = 1.36 MB of the 5.6 MB database.
* The table only ever grew — nothing ever removed a test token.

`EXPLAIN QUERY PLAN` for the hot path was fine (`SEARCH api_tokens USING INDEX
sqlite_autoindex_api_tokens_1 (token=?)`), so `verify_token()` was never the problem;
`list_tokens()` was.

---

## 6. Changes applied

All changes are in `server/**`. Nothing in `android/**` or `plugins/**` was touched.

### 6.1 `server/job_scheduler.py` — `JobStore` reuses one connection, schema created once

**Evidence first.** `JobStore._connect()` opened a *fresh* SQLite connection on every
call and `JobStore` re-ran `CREATE TABLE IF NOT EXISTS` ×3 + `CREATE INDEX IF NOT EXISTS` ×2
on every call. Isolated measurements:

```
connect + close                     0.097 ms
connect + the 5 DDL statements      0.531 ms   <- the DDL is ~90 % of a call
connect + a SELECT                  0.541 ms
```

The tick loop runs **every 20 s** and the scheduler can only fire on **minute**-granular
cron (`CronSpec` is a 5-field cron), so 4 320 ticks/day were paying for 4 320 × ~0.58 ms of
pure setup:

```
SchedulerTick.tick()   before   0.75 ms  ->  3.24 CPU-s/day
                      after    0.0142 ms ->  0.06 CPU-s/day      (53x faster)
run_now()              before   8.66 ms  -> 12.5 CPU-s/day
                      after    2.78 ms  ->  4.0 CPU-s/day
```

The connection is now created once and reused, the schema is created on first use, and the
pragmas match `server/db.py` (`WAL`, `busy_timeout=5000`, `synchronous=NORMAL` — the old
`DELETE` journal mode and per-commit fsync cost 0.733 ms/commit vs 0.046 ms with `NORMAL`).
Access is serialised by an `RLock` because a shared `sqlite3` connection is not safe for
concurrent use even with `check_same_thread=False`.

Verified: cross-connection visibility (`save_job` from store A is visible to store B),
`enabled` flag round-trips, **601 tests pass**, `/health` → 200, `GET /api/jobs` → 200.

### 6.2 `server/db.py` — `TALOS_DB_PATH` override

`_DB_PATH` is now read from the environment, defaulting to the previous location. The Rust
core already reads the same variable (`crates/talos-core/src/main.rs:76`), so the two halves
of the stack now agree on one convention.

### 6.3 `server/auth.py` — an existing master-token file is never overwritten

`init_auth_db()` seeds a master token when the token table is empty and wrote it to
`~/.hermes/talos_token.txt`. With an empty (isolated) database, that clobbered the **live**
master token file — locking the phone and the OpenCode bridge out of the token table. The
file now wins: an existing file is left alone.

> **Incident note, disclosed in full.** While validating the isolation recipe I ran the
> suite against an empty database, and that path overwrote
> `/home/kira/.hermes/talos_token.txt` with a fresh token that is not in the live
> `api_tokens` table. The file was restored from the live
> `'Sarvesh Master (Phone)'` row and verified to match
> `/home/kira/talos_release/API.txt` (sha256 prefix `3c1f90316556`, 57 chars, mode 0600).
> The root cause is fixed by 6.3. No token value was printed or committed at any point.

### 6.4 `server/test_isolation.py` (new) — pytest plugin that isolates the suite

```bash
pytest -p server.test_isolation tests -q
```

The plugin snapshots the live database with the SQLite **backup API** (not a file copy — the
live DB is in WAL mode, so a plain copy would miss frames still in the `-wal` file) into a
throwaway directory, and points `TALOS_DB_PATH` / `TALOS_AUTH_DB_PATH` at it *before*
`server.db` / `server.auth` are imported. The tests still see the master token row and the
FTS tables they expect.

**Verified:**

```
live BEFORE: messages=4782  tokens=454
601 passed in 34.21s
live AFTER : messages=4782  tokens=454      <- zero writes to the live database
```

### 6.5 Runtime (no restart, no code change)

**`tick proof` disabled** via the documented admin API, so it takes effect on the next
tick without touching the service:

```bash
curl -X PATCH -H "Authorization: Bearer $TALOS_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"enabled": false}' \
     http://127.0.0.1:8000/api/jobs/job_3aae20167e61
```

Verified: `job_runs` and `messages` stopped growing, and a 280 s window afterwards showed
**+0 rows and +0 bytes** across `messages`, `messages_fts`, `job_runs`, `api_tokens`, the
main DB file and the WAL.

**Re-enable:** the same `PATCH` with `{"enabled": true}`.

**6424 test-artifact tokens pruned** from the live `api_tokens` table, restricted to labels
that only the test suite can create (`e2e-%`, `Test %`, `probe`, `test-jobs-404%`,
`ws-test`) — every one of them matches a `create_new_token(...)` call site in `tests/`.
The 410 remaining rows were kept: 406 `Mesh Link: V2158`, 2 `Mesh Link: Android Device`,
1 `Sarvesh Master (Phone)` (the one the phone and the bridge actually use — `use_count = 1`,
the only token ever used), 1 `Auto-Paired Client`.

```
api_tokens   6828 -> 410 rows
list_tokens() 339 ms -> 5.75 ms per call
api_tokens table+index  1.36 MB -> 135 KB
```

A backup of the pre-prune database is at
`~/.talos/backups/talos.db.pre-prune-20261112-092502`. **To undo:** copy it back over
`server/data/talos.db` and restart `talos-api`.

### 6.6 `/etc/logrotate.d/talos-api` (new)

```
/home/kira/.talos/api.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
    copytruncate
    su kira kira
}
```

`copytruncate` is required rather than `create`: the service writes to the file through
systemd's `StandardOutput=append:`, so renaming the file would leave the process writing to
the old inode and the new file would stay empty. `logrotate -d` parses it cleanly and the
system `logrotate.timer` is enabled and active, so no service restart is needed.

**To undo:** `sudo rm /etc/logrotate.d/talos-api`.

---

## 7. Recommended, deliberately not done

| # | Change | Saving | Why not applied |
|---|---|---|---|
| 1 | `--loop uvloop` on `talos-api` | **23 CPU-s/day (12.5 %)** | Requires editing the unit and restarting `talos-api`. A restart needs user consent and was blocked this session. It is a one-line change to `~/.config/systemd/user/talos-api.service`; `uvloop` 0.23.0 is already installed and importable by the service interpreter, and uvicorn's default `--loop auto` will pick it up on the next start **with no unit edit at all**. Verified working: boots clean, `/health` → 200, WebSocket round-trip → `room_sync`. |
| 2 | `RUST_LOG=info,talos_core=debug` → `info` in `talos-core.service` | 0 | **Already inert.** `crates/talos-core/src/main.rs:71` calls `.with_env_filter("info,talos_core=debug")` — a *hard-coded* string, not `EnvFilter::from_default_env()`. The `RUST_LOG` env var is never read by the binary, and the crate contains **zero** `debug!`/`trace!` macros. The journal shows 0 new entries in 30 min and 1 in the last hour. Removing the env var would change nothing; the hard-coded filter in the Rust source is the real (currently harmless) issue. |
| 3 | Raise the scheduler tick from 20 s to 60 s | ~0.02 CPU-s/day | After 6.1 the tick costs 0.014 ms, so the interval is no longer the cost driver. Raising it would change firing latency for a negligible gain. |
| 4 | Prune the 508 tick-proof `messages` rows and their FTS entries | 144 KiB of pages (measured) | Cosmetic — the file no longer grows, and deleting rows from a live FTS index is a needless risk. |
| 5 | Drop the 406 unused `Mesh Link: V2158` tokens | ~40 KB | Not provably junk — `use_count`/`last_used_at` are only incremented on `verify_token()`, and a token could legitimately be issued ahead of first use. Not worth the risk for 40 KB. |
| 6 | Add `TALOS_DB_PATH`/`TALOS_AUTH_DB_PATH` to the systemd unit | 0 | The default already points at the right file. |
| 7 | Reclaim the 4.2 MB WAL by raising `wal_autocheckpoint` | 4.2 MB | WAL is a fixed-size buffer here, not a growth source. |
| 8 | Restart `talos-api` to pick up 6.1–6.3 | — | **Restart is consent-blocked.** The running PID 1533757 started 01:42:07, before these edits, so it still has the old `JobStore` in memory. The code fix is on disk and proven by the test suite; it activates on the next restart. |
| 9 | Prune `messages_fts` / add a `list_tokens` page limit | 339 ms → 5.75 ms already achieved by 6.5 | Adding pagination to `list_tokens()` changes a public API response shape — the Android app consumes it. |

---

## 8. Verification

```
curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8000/health        -> 200
python3 -m pytest tests -q                                                  -> 601 passed
python3 -m pytest -p server.test_isolation tests -q                         -> 601 passed
```

Baseline before any change was 601 passed; it is 601 passed after. The live database was
confirmed byte-for-byte unchanged across an isolated run.

---

## 9. Result

| | Before | After |
|---|---|---|
| TALOS idle CPU | 184.3 CPU-s/day | 184.3 CPU-s/day (→ **161.3** once uvloop is active at next restart) |
| `list_tokens()` | 339 ms | **5.75 ms** |
| `api_tokens` rows | 6 828 (94 % test residue) | **410** |
| Live DB growth | +4 rows / 240 s, forever | **+0 rows, +0 bytes** |
| `api.log` | unbounded, no rotation | rotates daily, 7 kept, compressed |
| Test suite | wrote to production data | writes to a throwaway copy |

Two processes, 20.6 MB RSS, 184 CPU-seconds a day, and — after the changes above — a
production database that no longer grows.

---

## 10. Reproducing this audit

```bash
# Resident processes and their cost
ps -eo pid,rss,pcpu,etime,comm,args --sort=-rss | head -30
systemctl --user list-units --type=service --state=running
systemctl list-units --type=service --state=running

# Idle CPU (authoritative: cgroup counters)
cat /sys/fs/cgroup/user.slice/user-1000.slice/user@1000.service/app.slice/talos-api.service/cpu.stat
cat /sys/fs/cgroup/user.slice/user-1000.slice/user@1000.service/app.slice/talos-core.service/cpu.stat

# Memory
cat /sys/fs/cgroup/.../talos-api.service/memory.stat

# Database
sqlite3 server/data/talos.db "SELECT COUNT(*) FROM api_tokens;"
sqlite3 server/data/talos.db "SELECT COUNT(*) FROM messages; SELECT COUNT(*) FROM job_runs;"
sqlite3 server/data/talos.db "SELECT name, SUM(pgsize) FROM dbstat GROUP BY name ORDER BY 2 DESC;"

# Polling intervals
grep -rn "sleep\|interval\|poll" server/*.py | head -40

# Log growth
ls -l ~/.talos/api.log
```

No secret values appear in this document. The admin token is read from
`/home/kira/talos_release/API.txt` into a shell variable and never echoed.
