#!/usr/bin/env python3
"""Watchdog for a running delegate batch.

The failure this exists to prevent is specific and has happened: a batch of
subagents was spawned, nobody watched the transcripts, the results arrived while
the orchestrator was busy with something else, and half the deliverables were
never integrated. The agents ran; the work was wasted.

This script polls each child's transcript, tracks the last line count and
timestamp, and prints one line per state change. It is deliberately dumb: no
model, no judgement, just "this child moved", "this child stopped moving",
"this child finished with X".

Usage:
    python3 tools/delegate_watchdog.py <delegation_dir> [--interval 20] [--stall 240]

Exit code is 0 when every child reached a terminal state, 2 if any child is
still running when the max wait elapses (so a CI job or a shell `&&` chain can
tell the difference between "all done" and "gave up watching").
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional

TERMINAL_RE = re.compile(r"\bfinal\s*\|.*status=(\w+)", re.IGNORECASE)


@dataclass
class ChildState:
    name: str
    path: Path
    last_size: int = 0
    last_change_at: float = field(default_factory=time.time)
    status: str = "running"
    stall_reported: bool = False

    @property
    def is_terminal(self) -> bool:
        return self.status not in ("running", "unknown")


def read_status(path: Path) -> str:
    """Terminal status from the transcript, or 'running' while it works."""
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return "unknown"
    match = TERMINAL_RE.search(text)
    if match:
        return match.group(1).lower()
    return "running"


def discover(directory: Path) -> List[ChildState]:
    files = sorted(directory.glob("task-*.log"))
    return [ChildState(name=f.stem, path=f) for f in files]


def summarize(path: Path, limit: int = 120) -> str:
    """Last meaningful line, trimmed for a one-line status."""
    try:
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return ""
    for line in reversed(lines):
        stripped = line.strip()
        if stripped and not stripped.startswith("─"):
            return stripped[:limit]
    return ""


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Watch delegate child transcripts.")
    parser.add_argument("directory", help="delegation live directory, e.g. ~/.hermes/cache/delegation/live/deleg_x")
    parser.add_argument("--interval", type=float, default=20.0, help="poll seconds")
    parser.add_argument("--stall", type=float, default=240.0, help="seconds of no growth before reporting a stall")
    parser.add_argument("--max-wait", type=float, default=1800.0, help="give up after this many seconds")
    parser.add_argument("--once", action="store_true", help="print the current state and exit")
    args = parser.parse_args(argv)

    directory = Path(os.path.expanduser(args.directory))
    if not directory.is_dir():
        print(f"no such directory: {directory}", file=sys.stderr)
        return 1

    children = discover(directory)
    if not children:
        print(f"no task-*.log transcripts in {directory}", file=sys.stderr)
        return 1

    started = time.time()
    print(f"watching {len(children)} children in {directory}")

    while True:
        for child in children:
            if child.is_terminal:
                continue
            try:
                size = child.path.stat().st_size
            except OSError:
                continue

            if size != child.last_size:
                child.last_size = size
                child.last_change_at = time.time()
                child.stall_reported = False
                status = read_status(child.path)
                if status != child.status:
                    child.status = status
                    tail = summarize(child.path)
                    print(f"  [{child.name}] -> {status} | {tail}")
                continue

            status = read_status(child.path)
            if status != child.status:
                child.status = status
                tail = summarize(child.path)
                print(f"  [{child.name}] -> {status} | {tail}")
                continue

            quiet_for = time.time() - child.last_change_at
            if quiet_for >= args.stall and not child.stall_reported:
                child.stall_reported = True
                print(
                    f"  [{child.name}] STALLED for {int(quiet_for)}s "
                    f"at {child.last_size} bytes | {summarize(child.path)}"
                )

        if args.once:
            break

        if all(c.is_terminal for c in children):
            print("\nall children finished:")
            for child in children:
                print(f"  {child.name}: {child.status}")
            return 0

        if time.time() - started > args.max_wait:
            pending = [c.name for c in children if not c.is_terminal]
            print(f"\ntimed out with {len(pending)} still running: {', '.join(pending)}")
            return 2

        time.sleep(args.interval)


if __name__ == "__main__":
    raise SystemExit(main())
