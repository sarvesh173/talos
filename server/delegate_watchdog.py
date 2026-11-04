"""Delegate watchdog: stall detection, completion/failure transitions, milestones.

Pure, clock-injectable logic. No asyncio, no network in this module — the
orchestrator owns the polling task and the bus emission, this module owns the
decision of *what changed* since the last poll. That separation is what lets
the tests run without ever sleeping for real.

Notification transport: TALOS has no server-side Telegram sender. The
existing mechanism is the room bus — ``AgentOrchestrator._emit`` broadcasts a
``BusEvent`` and the Android client (``TalosClient.handleInboundMessage``)
turns activity/progress events into local notifications via
``AgentNotifications.notifyActivity`` / ``notifyProgress``. The watchdog
therefore emits bus events through the injected ``emit`` callback and, when a
``notify`` callback is supplied, calls it with a human message — the
orchestrator wires ``notify`` to the same bus path, never a new transport.

Elapsed-time rules (mirroring Android ``AgentClock``):
  * In-app value is exact real seconds and renders correctly from seconds up
    to centuries (Python ints are unbounded, so no overflow branch needed).
  * Notifications fire only on rounded progressive milestones:
    10, 20, 30, 50 minutes, then doubling (100, 200, 400, ...). Never
    per-second / per-minute spam.
"""

from __future__ import annotations

from typing import Any, Callable, Dict, List, Optional

# ---------------------------------------------------------------------------
# Elapsed-time formatting (in-app, exact)
# ---------------------------------------------------------------------------

_SECONDS_PER_MINUTE = 60
_SECONDS_PER_HOUR = 3_600
_SECONDS_PER_DAY = 86_400
_SECONDS_PER_MONTH = 2_592_000  # 30 days, as in AgentClock
_SECONDS_PER_YEAR = 31_536_000  # 365 days


def format_elapsed(elapsed_seconds: float | int) -> str:
    """Exact live label: seconds early, then minutes, hours, days, months, years, centuries.

    Handles arbitrarily large values (Python ints do not overflow) and
    negative input (clamped via abs, matching AgentClock).
    """
    s = int(abs(elapsed_seconds))
    if s < _SECONDS_PER_MINUTE:
        return f"{s}s"
    if s < _SECONDS_PER_HOUR:
        return f"{s // 60}:{s % 60:02d}"
    if s < _SECONDS_PER_DAY:
        h = s // _SECONDS_PER_HOUR
        m = (s % _SECONDS_PER_HOUR) // 60
        sec = s % 60
        return f"{h}:{m:02d}:{sec:02d}"
    if s < _SECONDS_PER_MONTH:
        d = s // _SECONDS_PER_DAY
        h = (s % _SECONDS_PER_DAY) // _SECONDS_PER_HOUR
        m = (s % _SECONDS_PER_HOUR) // 60
        return f"{d}d {h}h {m}m"
    if s < _SECONDS_PER_YEAR:
        mo = s // _SECONDS_PER_MONTH
        d = (s % _SECONDS_PER_MONTH) // _SECONDS_PER_DAY
        return f"{mo}mo {d}d"
    y = s // _SECONDS_PER_YEAR
    mo = (s % _SECONDS_PER_YEAR) // _SECONDS_PER_MONTH
    if y < 100:
        return f"{y}y {mo}mo"
    return f"{y // 100}c {y % 100}y"


def compact_elapsed(elapsed_seconds: float | int) -> str:
    """Short badge label: ``45s``, ``1m 12s``, ``2h 5m``, ``3d 4h``."""
    s = int(abs(elapsed_seconds))
    if s < _SECONDS_PER_MINUTE:
        return f"{s}s"
    if s < _SECONDS_PER_HOUR:
        m, sec = s // 60, s % 60
        return f"{m}m" if sec == 0 else f"{m}m {sec}s"
    if s < _SECONDS_PER_DAY:
        h, m = s // _SECONDS_PER_HOUR, (s % _SECONDS_PER_HOUR) // 60
        return f"{h}h" if m == 0 else f"{h}h {m}m"
    d, h = s // _SECONDS_PER_DAY, (s % _SECONDS_PER_DAY) // _SECONDS_PER_HOUR
    return f"{d}d" if h == 0 else f"{d}d {h}h"


# ---------------------------------------------------------------------------
# Milestone ladder (notifications only)
# ---------------------------------------------------------------------------

#: First rungs in minutes: 10, 20, 30, 50 — then each rung doubles the last.
_BASE_MILESTONE_MINUTES: tuple[int, ...] = (10, 20, 30, 50)


def milestone_seconds(step: int) -> int:
    """Elapsed seconds at milestone ``step`` (0-based). Unbounded, no overflow."""
    if step <= 0:
        return _BASE_MILESTONE_MINUTES[0] * 60
    if step < len(_BASE_MILESTONE_MINUTES):
        return _BASE_MILESTONE_MINUTES[step] * 60
    # step 4 -> 100m (50*2), step 5 -> 200m, step 6 -> 400m, ...
    minutes = _BASE_MILESTONE_MINUTES[-1] * (1 << (step - (len(_BASE_MILESTONE_MINUTES) - 1)))
    return int(minutes) * 60


def crossed_milestones(elapsed_seconds: float | int) -> int:
    """How many milestone rungs have elapsed. 0 before the first rung."""
    s = int(elapsed_seconds)
    if s < milestone_seconds(0):
        return 0
    steps = 0
    # Ladder grows exponentially, so this loop terminates in O(log elapsed)
    # even for arbitrarily large elapsed values.
    while s >= milestone_seconds(steps):
        steps += 1
        if steps > 10_000:  # defensive; unreachable for any real elapsed
            break
    return steps


def new_milestone(elapsed_seconds: float | int, already_crossed: int = 0) -> Optional[int]:
    """Seconds value of the newest milestone crossed since ``already_crossed``.

    Returns ``None`` when no new rung has been crossed — the caller fires a
    notification only when this is not ``None``. When several rungs were
    crossed in one interval (app was backgrounded), only the latest is
    returned so the user gets one update, not a burst.
    """
    crossed = crossed_milestones(elapsed_seconds)
    if crossed <= already_crossed:
        return None
    return milestone_seconds(crossed - 1)


def next_milestone_in(elapsed_seconds: float | int) -> Optional[int]:
    """Seconds until the next milestone, or ``None`` if already past it."""
    s = int(elapsed_seconds)
    crossed = crossed_milestones(s)
    nxt = milestone_seconds(crossed)
    return max(0, nxt - s)


def is_stalled(now: float, last_progress_at: float, stall_threshold_seconds: float) -> bool:
    """True when no progress has been observed for longer than the threshold."""
    return (now - last_progress_at) > stall_threshold_seconds


# ---------------------------------------------------------------------------
# Watchdog
# ---------------------------------------------------------------------------

_TERMINAL_STATUSES = frozenset({"completed", "failed", "cancelled", "expired", "cancelled_delegate"})


class DelegateWatchdog:
    """Polls a delegate registry and reports state transitions.

    ``emit(room_id, event_type, payload)`` is the bus emission callback
    (orchestrator wires it to ``_emit``). ``notify(message)`` is an optional
    human-readable notification callback — when supplied it receives exactly
    one call per transition/milestone, never per poll.

    The watchdog keeps per-delegate memory (last progress time, last known
    status, milestone count, whether a stall was already reported) so each
    transition is emitted exactly once.
    """

    def __init__(
        self,
        emit: Optional[Callable[[str, str, Dict[str, Any]], None]] = None,
        notify: Optional[Callable[[str], None]] = None,
        stall_threshold_seconds: float = 300.0,
        clock: Optional[Callable[[], float]] = None,
    ) -> None:
        self._emit = emit
        self._notify = notify
        self.stall_threshold_seconds = float(stall_threshold_seconds)
        self._clock = clock
        self._state: Dict[str, Dict[str, Any]] = {}

    # -- internal helpers --------------------------------------------------

    def _now(self, now: Optional[float]) -> float:
        if now is not None:
            return float(now)
        if self._clock is not None:
            return float(self._clock())
        import time

        return time.time()

    def _fire(self, room_id: str, event_type: str, payload: Dict[str, Any], message: str) -> None:
        if self._emit is not None:
            self._emit(room_id, event_type, payload)
        elif self._notify is not None:
            self._notify(message)
        # When both are supplied the emit path is the transport; notify is
        # an additional human-readable hook. Call it too so tests/UI that
        # only listen to notify still observe milestones.
        if self._emit is not None and self._notify is not None:
            self._notify(message)

    # -- polling -----------------------------------------------------------

    def poll_once(
        self,
        registry: Dict[str, Dict[str, Any]],
        now: Optional[float] = None,
    ) -> List[Dict[str, Any]]:
        """Inspect ``registry`` once; return (and emit) every transition found."""
        now = self._now(now)
        transitions: List[Dict[str, Any]] = []

        for delegate_id, rec in list(registry.items()):
            room_id = rec.get("room_id") or ""
            status = rec.get("status", "running")
            started_at = float(rec.get("started_at", now))
            elapsed = max(0.0, now - started_at)
            state = self._state.setdefault(
                delegate_id,
                {
                    "status": None,
                    "last_progress_at": float(rec.get("last_progress_at", started_at)),
                    "last_progress": None,
                    "milestones_crossed": 0,
                    "stalled": False,
                },
            )

            # Progress signal: explicit progress/tokens fields. Any change
            # resets the stall clock.
            progress_sig = (rec.get("progress"), rec.get("tokens_generated"))
            if progress_sig != state["last_progress"]:
                state["last_progress"] = progress_sig
                state["last_progress_at"] = float(rec.get("last_progress_at", now))
                if state["stalled"] and status == "running":
                    state["stalled"] = False
                    payload = {"id": delegate_id, "status": status, "elapsed_seconds": int(elapsed),
                               "elapsed_label": format_elapsed(elapsed)}
                    transitions.append({"type": "resumed", "id": delegate_id, **payload})
                    self._fire(room_id, "delegate_resumed", payload,
                               f"Delegate {rec.get('name', delegate_id)} resumed after {format_elapsed(elapsed)}")

            prev_status = state["status"]
            state["status"] = status

            # Terminal transitions: completion / failure (and expiry/cancel
            # surfaced through the same once-only path).
            if status in _TERMINAL_STATUSES and prev_status != status:
                kind = "completed" if status == "completed" else "failed" if status == "failed" else status
                payload = {"id": delegate_id, "status": status, "elapsed_seconds": int(elapsed),
                           "elapsed_label": format_elapsed(elapsed)}
                transitions.append({"type": kind, "id": delegate_id, **payload})
                self._fire(room_id, f"delegate_{kind}", payload,
                           f"Delegate {rec.get('name', delegate_id)} {kind} after {format_elapsed(elapsed)}")
                continue  # terminal: no stall/milestone after this

            if status != "running":
                continue

            # Stall detection.
            if not state["stalled"] and is_stalled(now, state["last_progress_at"], self.stall_threshold_seconds):
                state["stalled"] = True
                payload = {"id": delegate_id, "status": status, "elapsed_seconds": int(elapsed),
                           "elapsed_label": format_elapsed(elapsed),
                           "stall_threshold_seconds": int(self.stall_threshold_seconds)}
                transitions.append({"type": "stalled", "id": delegate_id, **payload})
                self._fire(room_id, "delegate_stalled", payload,
                           f"Delegate {rec.get('name', delegate_id)} stalled — no progress for {format_elapsed(now - state['last_progress_at'])}")

            # Milestones (only when not stalled? No — milestones still matter;
            # but a stalled delegate crossing a milestone still gets one
            # milestone update, which is the designed quiet cadence).
            milestone = new_milestone(elapsed, state["milestones_crossed"])
            if milestone is not None:
                state["milestones_crossed"] = crossed_milestones(elapsed)
                payload = {"id": delegate_id, "status": status, "elapsed_seconds": int(elapsed),
                           "elapsed_label": format_elapsed(elapsed), "milestone_seconds": int(milestone),
                           "milestone_label": format_elapsed(milestone),
                           "milestones_crossed": state["milestones_crossed"]}
                transitions.append({"type": "milestone", "id": delegate_id, **payload})
                self._fire(room_id, "delegate_milestone", payload,
                           f"Delegate {rec.get('name', delegate_id)} still working — {format_elapsed(elapsed)} elapsed")

        # Drop state for delegates removed from the registry.
        for gone in [k for k in self._state if k not in registry]:
            del self._state[gone]
        return transitions
