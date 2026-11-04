"""Concurrent model comparison for TALOS (Msty-style fan-out).

Send ONE prompt to 2-4 models at once, open every stream concurrently, and
yield tagged events as they arrive so the UI can render answers side by
side. This is a fan-out over the existing streaming client — no new HTTP
client is invented here.

Design notes:

* Streams are opened with :class:`core.llm_client.ModelClient`, which
  already picks the wire protocol (``protocol_for``) and resolves
  credentials from the environment at call time (``key_env_for`` /
  ``_credential``). This module never reads, stores, logs, or emits an
  API key. Error strings are scrubbed of any known credential value
  before they reach an event.
* One model failing must not cancel the others: each model runs in its
  own task feeding a shared :class:`asyncio.Queue`; the generator drains
  the queue and yields events in arrival order. Failures are isolated
  per model and reported in place as ``Failed``.
* Token counts: the current ``ModelClient`` stream does not surface
  provider-reported ``usage``, so counts derived from it are estimates
  (~4 chars/token, mirroring ``ModelClient.complete``) and the metrics
  say so explicitly. When a stream *does* carry provider-reported usage
  (a ``usage`` mapping / explicit token fields on the ``done`` event),
  the metrics mark them as provider-reported instead.
* A hard per-model timeout (``timeout_s``) yields ``Failed(model,
  "timeout")`` for that model only and cancels its stream.
* ``cancel_all()`` cancels every in-flight comparison task. The module
  also exposes the task registry (``active_task_count``) so callers and
  tests can verify nothing is left running.
"""

from __future__ import annotations

import asyncio
import os
import re
import time
from dataclasses import dataclass, field
from typing import (
    Any,
    AsyncIterator,
    Callable,
    Dict,
    List,
    Optional,
    Sequence,
    Set,
    Union,
)

from core.llm_client import DEFAULT_BASE_URL, ModelClient

__all__ = [
    "CompareError",
    "CompareValidationError",
    "InvalidModelCountError",
    "ModelCompareError",
    "ModelRef",
    "CompareMetrics",
    "ModelMetrics",
    "CompareEvent",
    "Started",
    "Delta",
    "Finished",
    "Failed",
    "compare",
    "cancel_all",
    "active_task_count",
    "active_tasks",
    "MIN_MODELS",
    "MAX_MODELS",
    "DEFAULT_TIMEOUT_S",
    "DEFAULT_MAX_TOKENS",
]

MIN_MODELS = 2
MAX_MODELS = 4
DEFAULT_TIMEOUT_S = 120.0
DEFAULT_MAX_TOKENS = 512

# Env vars whose values must never appear in an event or error string.
_SECRET_ENV_HINTS = ("API_KEY", "SECRET", "TOKEN", "PASSWORD", "CREDENTIAL")


# ---------------------------------------------------------------------------
# Errors
# ---------------------------------------------------------------------------


class CompareError(ValueError):
    """Base typed error for model comparison."""


class ModelCompareError(CompareError):
    """Alias for :class:`CompareError`."""


class CompareValidationError(CompareError):
    """Raised when comparison arguments are invalid."""


class InvalidModelCountError(CompareValidationError):
    """Raised when the model count is outside 2..4."""


# ---------------------------------------------------------------------------
# Model reference
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class ModelRef:
    """One model to compare against.

    ``model`` is the provider model id (e.g. ``stealth/crouton-alpha``).
    ``base_url`` overrides the endpoint; when omitted the default
    ``TALOS_LLM_BASE_URL`` endpoint is used. ``provider_id`` optionally
    resolves ``base_url`` / ``model`` defaults from the provider catalog
    (:mod:`core.providers`) without handling any credential — the
    streaming client resolves credentials from the environment itself.

    No credential field exists on purpose: keys are never persisted here.
    """

    model: str
    base_url: Optional[str] = None
    provider_id: Optional[str] = None
    reasoning_effort: str = "high"

    @property
    def provider(self) -> Optional[str]:
        return self.provider_id

    def __str__(self) -> str:  # pragma: no cover - trivial
        return self.model


def _coerce_ref(item: Any) -> ModelRef:
    if isinstance(item, ModelRef):
        return item
    if isinstance(item, str):
        return ModelRef(model=item)
    if isinstance(item, dict):
        return ModelRef(
            model=str(item.get("model") or item.get("id") or ""),
            base_url=item.get("base_url"),
            provider_id=item.get("provider_id") or item.get("provider"),
            reasoning_effort=str(item.get("reasoning_effort") or "high"),
        )
    # Duck-typed object with a .model attribute.
    model = getattr(item, "model", None)
    if isinstance(model, str) and model:
        return ModelRef(
            model=model,
            base_url=getattr(item, "base_url", None),
            provider_id=getattr(item, "provider_id", None)
            or getattr(item, "provider", None),
            reasoning_effort=str(getattr(item, "reasoning_effort", None) or "high"),
        )
    raise CompareValidationError(f"invalid model reference: {item!r:.80}")


def _resolve_ref(ref: ModelRef) -> ModelRef:
    """Fill defaults from the provider catalog when a provider is named."""
    provider_id = (ref.provider_id or "").strip()
    if not provider_id:
        return ref
    try:
        from core.providers import get_spec

        spec = get_spec(provider_id)
    except Exception:  # pragma: no cover - catalog lookup is best-effort
        spec = None
    if spec is None:
        return ref
    return ModelRef(
        model=ref.model or spec.default_model,
        base_url=ref.base_url or spec.base_url,
        provider_id=provider_id,
        reasoning_effort=ref.reasoning_effort,
    )


# ---------------------------------------------------------------------------
# Metrics
# ---------------------------------------------------------------------------


@dataclass
class CompareMetrics:
    """Honest per-model numbers for one finished comparison answer.

    ``token_source`` is ``"provider"`` only when the stream carried
    provider-reported usage; otherwise it is ``"estimated"`` and
    ``note`` explains the estimation. Latency fields are always measured
    locally from real elapsed time.
    """

    model: str = ""
    latency_ms: int = 0
    ttft_ms: Optional[int] = None
    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0
    tokens_per_second: float = 0.0
    token_source: str = "estimated"
    estimated: bool = True
    provider_reported: bool = False
    note: str = ""

    # -- aliases so callers can use whichever name they expect ----------
    @property
    def duration_ms(self) -> int:
        return self.latency_ms

    @property
    def first_token_latency_ms(self) -> Optional[int]:
        return self.ttft_ms

    @property
    def counts_estimated(self) -> bool:
        return self.estimated

    @property
    def is_estimated(self) -> bool:
        return self.estimated

    @property
    def token_counts(self) -> Dict[str, int]:
        return {
            "prompt": self.prompt_tokens,
            "completion": self.completion_tokens,
            "total": self.total_tokens,
        }

    def to_message_metrics(self):  # pragma: no cover - trivial adapter
        from core.models import MessageMetrics

        return MessageMetrics(
            latency_ms=self.latency_ms,
            ttft_ms=self.ttft_ms,
            prompt_tokens=self.prompt_tokens,
            completion_tokens=self.completion_tokens,
            tokens_per_second=self.tokens_per_second,
            model=self.model,
        )

    def to_dict(self) -> Dict[str, Any]:
        return {
            "model": self.model,
            "latency_ms": self.latency_ms,
            "duration_ms": self.latency_ms,
            "ttft_ms": self.ttft_ms,
            "first_token_latency_ms": self.ttft_ms,
            "prompt_tokens": self.prompt_tokens,
            "completion_tokens": self.completion_tokens,
            "total_tokens": self.total_tokens,
            "tokens_per_second": self.tokens_per_second,
            "token_source": self.token_source,
            "estimated": self.estimated,
            "counts_estimated": self.estimated,
            "provider_reported": self.provider_reported,
            "note": self.note,
        }


#: Backwards/forwards compatible alias.
ModelMetrics = CompareMetrics

_ESTIMATE_NOTE = (
    "Token counts are estimated from characters (~4 chars/token); "
    "the provider did not report usage."
)
_PROVIDER_NOTE = "Token counts are provider-reported usage."


# ---------------------------------------------------------------------------
# Events
# ---------------------------------------------------------------------------


@dataclass
class Started:
    """A model's stream has been opened and is running."""

    model: str
    ref: Optional[ModelRef] = None
    kind: str = "started"

    @property
    def model_id(self) -> str:
        return self.model


@dataclass
class Delta:
    """One text delta from a model, tagged by model id."""

    model: str
    text: str = ""
    ref: Optional[ModelRef] = None
    kind: str = "delta"

    @property
    def model_id(self) -> str:
        return self.model


@dataclass
class Finished:
    """A model completed; ``metrics`` carries measured/estimated numbers."""

    model: str
    metrics: Optional[CompareMetrics] = None
    content: str = ""
    ref: Optional[ModelRef] = None
    kind: str = "finished"

    @property
    def model_id(self) -> str:
        return self.model


@dataclass
class Failed:
    """A model failed in place; other models are unaffected."""

    model: str
    reason: str = ""
    ref: Optional[ModelRef] = None
    kind: str = "failed"

    @property
    def model_id(self) -> str:
        return self.model


#: Union of every event ``compare`` can yield.
CompareEvent = Union[Started, Delta, Finished, Failed]


# ---------------------------------------------------------------------------
# Credential hygiene
# ---------------------------------------------------------------------------

_BEARER_RE = re.compile(r"Bearer\s+\S+", re.IGNORECASE)
_SK_RE = re.compile(r"sk-[A-Za-z0-9_\-]{8,}")


def _secret_values() -> List[str]:
    values: List[str] = []
    for name, value in os.environ.items():
        upper = name.upper()
        if any(hint in upper for hint in _SECRET_ENV_HINTS):
            value = (value or "").strip()
            if len(value) >= 8:
                values.append(value)
    return sorted(values, key=len, reverse=True)


def _scrub(text: Any) -> str:
    """Remove credential material from a string destined for an event."""
    out = "" if text is None else str(text)
    for secret in _secret_values():
        if secret and secret in out:
            out = out.replace(secret, "[redacted]")
    out = _BEARER_RE.sub("Bearer [redacted]", out)
    out = _SK_RE.sub("[redacted]", out)
    return out


# ---------------------------------------------------------------------------
# Task registry
# ---------------------------------------------------------------------------

_ACTIVE_TASKS: Set["asyncio.Task[Any]"] = set()


def active_tasks() -> List["asyncio.Task[Any]"]:
    """Snapshot of in-flight per-model comparison tasks."""
    return [t for t in _ACTIVE_TASKS if not t.done()]


def active_task_count() -> int:
    return len(active_tasks())


def cancel_all() -> int:
    """Cancel every in-flight comparison task. Returns how many were cancelled.

    Safe to call from synchronous code (e.g. a REST endpoint): it cancels
    the tasks; the owning ``compare`` generator observes the cancellation
    and terminates without leaving tasks behind.
    """
    tasks = active_tasks()
    for task in tasks:
        task.cancel()
    return len(tasks)


def _register(task: "asyncio.Task[Any]") -> "asyncio.Task[Any]":
    _ACTIVE_TASKS.add(task)
    task.add_done_callback(_ACTIVE_TASKS.discard)
    return task


# ---------------------------------------------------------------------------
# Stream consumption
# ---------------------------------------------------------------------------


def _default_client_factory(ref: ModelRef, timeout_s: float):
    base_url = (ref.base_url or DEFAULT_BASE_URL).rstrip("/")
    try:
        return ModelClient(
            base_url=base_url,
            model=ref.model,
            timeout=timeout_s,
            reasoning_effort=ref.reasoning_effort,
        )
    except TypeError:
        # A substituted client may accept fewer keyword arguments.
        return ModelClient(base_url=base_url, model=ref.model)  # type: ignore[call-arg]


def _make_client(ref: ModelRef, timeout_s: float, factory: Any):
    if factory is None:
        return _default_client_factory(ref, timeout_s)
    try:
        return factory(ref)
    except TypeError:
        # Tolerate factories shaped like the orchestrator's:
        # (base_url, model, reasoning_effort) or (model,).
        try:
            return factory(
                base_url=(ref.base_url or DEFAULT_BASE_URL).rstrip("/"),
                model=ref.model,
                reasoning_effort=ref.reasoning_effort,
            )
        except TypeError:
            return factory(ref.model)


def _usage_from_done(event: Any) -> Optional[Dict[str, int]]:
    """Extract provider-reported usage from a ``done`` event, if any."""
    usage = getattr(event, "usage", None)
    if isinstance(usage, dict) and usage:
        return {
            "prompt": int(usage.get("prompt_tokens") or usage.get("input_tokens") or 0),
            "completion": int(
                usage.get("completion_tokens") or usage.get("output_tokens") or 0
            ),
            "total": int(
                usage.get("total_tokens")
                or (usage.get("prompt_tokens") or 0) + (usage.get("completion_tokens") or 0)
            ),
        }
    prompt = getattr(event, "prompt_tokens", None)
    completion = getattr(event, "completion_tokens", None)
    reported = getattr(event, "tokens_reported", None)
    if reported or (prompt is not None and completion is not None):
        p, c = int(prompt or 0), int(completion or 0)
        total = getattr(event, "total_tokens", None)
        return {"prompt": p, "completion": c, "total": int(total) if total else p + c}
    return None


# ---------------------------------------------------------------------------
# One-terminal-event-per-model claim
# ---------------------------------------------------------------------------

# A run can reach a terminal state through two independent paths:
# _consume_one reads the stream to a natural end (Finished/Failed), and
# _run_one catches the wait_for deadline (Failed). Both can fire in the
# same tick, which produced duplicate terminal events. The claim makes the
# first to emit win and the second a no-op. The set is keyed by an id that
# includes the run number, so models compared twice are distinct.
_TERMINAL_CLAIMS: set = set()
_RUN_COUNTER = 0


def _claim_terminal(model_id: str, queue: "asyncio.Queue[CompareEvent]") -> bool:
    """True if this is the first terminal event for *model_id* in this run."""
    key = (id(queue), model_id)
    if key in _TERMINAL_CLAIMS:
        return False
    _TERMINAL_CLAIMS.add(key)
    return True


def _has_terminal(model_id: str, queue: "asyncio.Queue[CompareEvent]") -> bool:
    """True when a terminal event was already claimed for *model_id*."""
    return (id(queue), model_id) in _TERMINAL_CLAIMS


def _reset_terminal_claims(queue: "asyncio.Queue[CompareEvent]") -> None:
    for key in list(_TERMINAL_CLAIMS):
        if key[0] == id(queue):
            _TERMINAL_CLAIMS.discard(key)


async def _consume_one(
    ref: ModelRef,
    prompt: str,
    queue: "asyncio.Queue[CompareEvent]",
    max_tokens: int,
    timeout_s: float,
    client_factory: Any,
    cancel_reason: Optional[List[str]] = None,
) -> None:
    """Stream one model, pushing events onto ``queue`` (never raises).

    ``cancel_reason`` is a one-element box owned by :func:`_run_one`. The
    runner writes ``"timeout"`` into it *before* cancelling this coroutine,
    so the cancellation handler can report the real cause. Without it a
    deadline looked identical to a user cancellation and the model was
    reported as ``"cancelled"``.
    """
    model_id = ref.model
    await queue.put(Started(model=model_id, ref=ref))
    started_at = time.perf_counter()
    first_token_at: Optional[float] = None
    content_parts: List[str] = []
    token_events = 0
    done_event: Any = None
    error_detail: Optional[str] = None

    client = _make_client(ref, timeout_s, client_factory)
    messages = [{"role": "user", "content": prompt}]
    stream = client.stream(messages, max_tokens=max_tokens)

    try:
        async for event in stream:
            kind = getattr(event, "kind", "")
            if kind == "token":
                if first_token_at is None:
                    first_token_at = time.perf_counter()
                text = getattr(event, "text", "") or ""
                content_parts.append(text)
                token_events += 1
                await queue.put(Delta(model=model_id, text=text, ref=ref))
            elif kind == "thought":
                # Reasoning is not part of the comparison answer; skip it
                # rather than corrupting the answer text.
                continue
            elif kind == "error":
                error_detail = _scrub(getattr(event, "detail", "") or "stream error")
                break
            elif kind == "done":
                done_event = event
                break
    except asyncio.CancelledError:
        reason = (cancel_reason[0] if cancel_reason else "cancelled") or "cancelled"
        if _claim_terminal(model_id, queue):
            await queue.put(Failed(model=model_id, reason=reason, ref=ref))
        raise
    except Exception as exc:  # isolated: only this model fails
        error_detail = _scrub(f"{type(exc).__name__}: {exc}")
    finally:
        # Close the async generator promptly so a timed-out / cancelled
        # stream does not linger.
        aclose = getattr(stream, "aclose", None)
        if aclose is not None:
            try:
                await aclose()
            except Exception:
                pass

    if error_detail is not None:
        if _claim_terminal(model_id, queue):
            await queue.put(Failed(model=model_id, reason=error_detail, ref=ref))
        return

    # Measured numbers (local elapsed time is always real).
    measured_elapsed = max(time.perf_counter() - started_at, 1e-6)
    ttft_ms: Optional[int] = None
    if done_event is not None:
        detail = str(getattr(done_event, "detail", "") or "")
        if detail.lstrip("-").isdigit() and int(detail) >= 0:
            ttft_ms = int(detail)
        elif getattr(done_event, "elapsed", None):
            pass
    if ttft_ms is None and first_token_at is not None:
        ttft_ms = int((first_token_at - started_at) * 1000)
    # A measurement that rounds to zero is still a measurement: an instant
    # run reports 0ms, which downstream code reads as "no measurement".
    # Round up to 1 rather than reporting 0 for a real (if sub-ms) run.
    latency_ms = max(int(measured_elapsed * 1000), 1)
    content = "".join(content_parts)

    # Token counts: provider-reported wins; otherwise honest estimates.
    usage = _usage_from_done(done_event) if done_event is not None else None
    if usage is not None:
        prompt_tokens = usage["prompt"]
        completion_tokens = usage["completion"]
        total_tokens = usage["total"] or (prompt_tokens + completion_tokens)
        token_source, estimated, reported, note = "provider", False, True, _PROVIDER_NOTE
    else:
        prompt_tokens = len(prompt) // 4
        completion_tokens = max(len(content) // 4, token_events)
        total_tokens = prompt_tokens + completion_tokens
        token_source, estimated, reported, note = "estimated", True, False, _ESTIMATE_NOTE

    generation_elapsed = measured_elapsed
    if done_event is not None and getattr(done_event, "elapsed", None):
        # done.elapsed covers the generation window in ModelClient.
        generation_elapsed = max(float(done_event.elapsed), 1e-6)
    tokens_per_second = (
        round(completion_tokens / generation_elapsed, 2) if generation_elapsed > 0 else 0.0
    )

    metrics = CompareMetrics(
        model=model_id,
        latency_ms=latency_ms,
        ttft_ms=ttft_ms,
        prompt_tokens=prompt_tokens,
        completion_tokens=completion_tokens,
        total_tokens=total_tokens,
        tokens_per_second=tokens_per_second,
        token_source=token_source,
        estimated=estimated,
        provider_reported=reported,
        note=note,
    )
    if _claim_terminal(model_id, queue):
        await queue.put(Finished(model=model_id, metrics=metrics, content=content, ref=ref))


async def _run_one(
    ref: ModelRef,
    prompt: str,
    queue: "asyncio.Queue[CompareEvent]",
    max_tokens: int,
    timeout_s: float,
    client_factory: Any,
) -> None:
    """Run one model under the per-model hard timeout (never raises).

    ``asyncio.wait_for`` is deliberately not used: it cancels the inner
    coroutine *before* raising ``TimeoutError``, so the inner cancellation
    handler ran first and reported the model as ``"cancelled"``. A deadline
    would then arrive at the caller labelled as a user cancellation, which
    is a lie about what happened. Waiting on a task and writing the reason
    into a shared box before cancelling keeps the label honest.
    """
    reason_box: List[str] = ["cancelled"]
    task = asyncio.create_task(
        _consume_one(ref, prompt, queue, max_tokens, timeout_s, client_factory, reason_box)
    )
    try:
        done, pending = await asyncio.wait(
            {task}, timeout=timeout_s if timeout_s and timeout_s > 0 else None
        )
        if pending:
            # Announce the cause, then cancel: the handler reads the box.
            reason_box[0] = "timeout"
            task.cancel()
            await asyncio.gather(task, return_exceptions=True)
        else:
            # Surface anything the coroutine itself raised (it should not).
            exc = task.exception()
            if exc is not None:
                raise exc
    except asyncio.CancelledError:
        # The comparison was cancelled from outside (cancel_all). The inner
        # handler has already reported it; re-raise so the task ends.
        task.cancel()
        raise
    except Exception as exc:  # pragma: no cover - defensive
        if _claim_terminal(ref.model, queue):
            await queue.put(
                Failed(model=ref.model, reason=_scrub(f"{type(exc).__name__}: {exc}"), ref=ref)
            )
    finally:
        # Guarantee exactly one terminal event per model, whatever path the
        # coroutine took (including being cancelled before its first await).
        if not _has_terminal(ref.model, queue):
            if _claim_terminal(ref.model, queue):
                await queue.put(Failed(model=ref.model, reason=reason_box[0], ref=ref))


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------


async def compare(
    prompt: str,
    models: Sequence[Any],
    room_id: Optional[str] = None,
    max_tokens: int = DEFAULT_MAX_TOKENS,
    timeout_s: float = DEFAULT_TIMEOUT_S,
    client_factory: Optional[Callable[..., Any]] = None,
) -> AsyncIterator[CompareEvent]:
    """Fan one prompt out to 2-4 models and yield events as they arrive.

    ``room_id`` is accepted for API compatibility with the room-scoped
    compare endpoint; streaming itself is room-agnostic and is not
    persisted here. ``client_factory`` is an injectable
    ``(ModelRef) -> client`` hook (tests use it to avoid the network;
    production leaves it ``None`` and uses :class:`ModelClient`).

    Validation errors are raised as :class:`CompareError` subclasses when
    iteration begins (this is an async generator). A per-model failure —
    including the hard ``timeout_s`` deadline — yields ``Failed`` for
    that model only and never cancels the others.
    """
    del room_id  # reserved for the endpoint layer; no persistence here

    if not isinstance(prompt, str) or not prompt.strip():
        raise CompareValidationError("prompt must be a non-empty string")
    refs_in = list(models or [])
    if not (MIN_MODELS <= len(refs_in) <= MAX_MODELS):
        raise InvalidModelCountError(
            f"model comparison requires {MIN_MODELS}..{MAX_MODELS} models, "
            f"got {len(refs_in)}"
        )
    if timeout_s is not None and timeout_s <= 0:
        raise CompareValidationError("timeout_s must be positive")
    refs = [_resolve_ref(_coerce_ref(item)) for item in refs_in]
    for ref in refs:
        if not ref.model or not str(ref.model).strip():
            raise CompareValidationError("each model reference needs a model id")

    queue: "asyncio.Queue[CompareEvent]" = asyncio.Queue()
    _reset_terminal_claims(queue)
    tasks = [
        _register(
            asyncio.create_task(
                _run_one(ref, prompt, queue, max_tokens, timeout_s, client_factory)
            )
        )
        for ref in refs
    ]

    try:
        while True:
            if all(t.done() for t in tasks):
                # Drain anything already queued, then stop.
                while True:
                    try:
                        event = queue.get_nowait()
                    except asyncio.QueueEmpty:
                        break
                    yield event
                break
            try:
                event = await asyncio.wait_for(queue.get(), timeout=0.05)
            except asyncio.TimeoutError:
                continue
            yield event
    finally:
        # No task is left running, however the generator exits
        # (completion, cancellation, or abandonment after close()).
        for task in tasks:
            if not task.done():
                task.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)
        for task in tasks:
            _ACTIVE_TASKS.discard(task)
