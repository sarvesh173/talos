"""Real model execution for TALOS agents.

The orchestration layer used to synthesise replies from templates. That made
the UI honest about *shape* but dishonest about *content*: the thinking panel
showed canned strings and the token stream never touched a model. This module
replaces that surface with an actual OpenAI-compatible streaming call against
the local CommandCode proxy.

Two things matter for the UI contract:

* **reasoning** and **content** are separate streams. Providers emit chain of
  thought as ``reasoning_content`` (DeepSeek/Qwen/vLLM style) or as ``thinking``
  content blocks. Both are normalised into one ``thought`` channel so the client
  can render "what the agent is thinking right now" without provider-specific
  branching.
* **speed is measurable.** We report tokens/second from real elapsed time, so
  the orb tempo and the avatar breathing follow the model instead of a guess.

Credentials are read from the environment at call time and never logged.
"""

from __future__ import annotations

import json
import os
import time
from dataclasses import dataclass, field
from typing import AsyncIterator, Dict, List, Optional, Sequence

import httpx

#: Local CommandCode proxy. It exposes an OpenAI-compatible surface and an
#: Anthropic-compatible one; we use OpenAI because the delta shape carries
#: ``reasoning_content`` for every model behind it.
DEFAULT_BASE_URL = os.environ.get("TALOS_LLM_BASE_URL", "http://127.0.0.1:4123/v1")

#: Provider key for the proxy. Read lazily so a test can stub the environment.
DEFAULT_KEY_ENV = "COMMANDCODE_API_KEY"

#: A delegate that produces nothing for this long is treated as stalled.
DEFAULT_TIMEOUT = 180.0


@dataclass
class StreamEvent:
    """One normalised unit of model output."""

    kind: str  # "thought" | "token" | "done" | "error"
    text: str = ""
    model: str = ""
    tokens: int = 0
    elapsed: float = 0.0
    detail: str = ""


@dataclass
class TurnResult:
    """Everything one completed model turn produced."""

    content: str = ""
    thought: str = ""
    model: str = ""
    prompt_tokens: int = 0
    completion_tokens: int = 0
    reasoning_tokens: int = 0
    latency_ms: int = 0
    ttft_ms: Optional[int] = None
    tokens_per_second: float = 0.0
    error: Optional[str] = None

    @property
    def ok(self) -> bool:
        return self.error is None and bool(self.content.strip())


def protocol_for(model: str) -> str:
    """Pick the wire protocol for a model id.

    The ``stealth/*`` family is served only through the Anthropic surface on the
    CommandCode proxy. Everything else is OpenAI-compatible.
    """
    return "anthropic" if (model or "").strip().lower().startswith("stealth/") else "openai"


def _credential(env_name: str = DEFAULT_KEY_ENV) -> str:
    """Resolve a provider credential. Never logged, never persisted here.

    The key name follows the endpoint: the local proxy uses
    ``COMMANDCODE_API_KEY``, OpenRouter uses ``OPENROUTER_API_KEY``. Reading the
    wrong one silently produced "Missing Authentication header" 401s that look
    like a broken model instead of a misconfigured client.
    """
    value = os.environ.get(env_name, "").strip()
    if value:
        return value
    # Fall back to the shared Hermes env file so core and agent use one key.
    env_path = os.path.expanduser("~/.hermes/.env")
    try:
        with open(env_path, "r", encoding="utf-8") as handle:
            for line in handle:
                if line.startswith(f"{env_name}="):
                    return line.split("=", 1)[1].strip()
    except OSError:
        pass
    return ""


def key_env_for(base_url: str) -> str:
    """Which env var holds the credential for this endpoint."""
    host = (base_url or "").lower()
    if "openrouter.ai" in host:
        return "OPENROUTER_API_KEY"
    if "127.0.0.1" in host or "localhost" in host:
        return DEFAULT_KEY_ENV
    return DEFAULT_KEY_ENV


def _delta_text(chunk: Dict) -> tuple[str, str]:
    """Split one streaming chunk into ``(thought, content)``.

    Handles the three shapes seen in the wild:
    * ``choices[0].delta.reasoning_content`` — DeepSeek, Qwen, vLLM
    * ``choices[0].delta.reasoning`` — some gateways
    * ``choices[0].delta.content`` — the answer itself
    """
    choices = chunk.get("choices") or []
    if not choices:
        return "", ""
    delta = choices[0].get("delta") or {}
    thought = delta.get("reasoning_content") or delta.get("reasoning") or ""
    content = delta.get("content") or ""
    if isinstance(thought, list):
        thought = "".join(part.get("text", "") for part in thought if isinstance(part, dict))
    if isinstance(content, list):
        content = "".join(
            part.get("text", "") for part in content if isinstance(part, dict)
        )
    return thought, content


class ModelClient:
    """Streaming client for one model endpoint.

    Two wire protocols reach the same proxy:

    * OpenAI ``/chat/completions`` — every non-stealth model behind CommandCode.
    * Anthropic ``/messages`` — the ``stealth/*`` family (crouton-alpha, glyph)
      answer *only* here. Sent through the OpenAI path they return HTTP 200 with
      an empty ``content`` and zero completion tokens, which looks like a dead
      model instead of a wrong protocol. :func:`protocol_for` picks the right one
      from the model id so callers never have to know.
    """

    def __init__(
        self,
        base_url: str = DEFAULT_BASE_URL,
        model: str = "stealth/crouton-alpha",
        timeout: float = DEFAULT_TIMEOUT,
        reasoning_effort: str = "high",
        api_style: Optional[str] = None,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.timeout = timeout
        self.reasoning_effort = reasoning_effort
        self.api_style = api_style or protocol_for(model)

    async def stream(
        self,
        messages: Sequence[Dict[str, str]],
        max_tokens: int = 2048,
        temperature: float = 0.7,
    ) -> AsyncIterator[StreamEvent]:
        """Yield normalised events; always terminates with done or error."""
        if self.api_style == "anthropic":
            async for event in self._stream_anthropic(messages, max_tokens, temperature):
                yield event
        else:
            async for event in self._stream_openai(messages, max_tokens, temperature):
                yield event

    def _headers(self, style: str) -> Dict[str, str]:
        key = _credential(key_env_for(self.base_url))
        headers = {"Content-Type": "application/json"}
        if not key:
            return headers
        if style == "anthropic":
            headers["x-api-key"] = key
            headers["anthropic-version"] = "2023-06-01"
        else:
            headers["Authorization"] = f"Bearer {key}"
        return headers

    async def _stream_anthropic(
        self,
        messages: Sequence[Dict[str, str]],
        max_tokens: int,
        temperature: float,
    ) -> AsyncIterator[StreamEvent]:
        """`/v1/messages` SSE: thinking blocks and text deltas.

        The CommandCode proxy silently drops the whole turn when a ``system``
        field is present for the stealth models — HTTP 200, ``output_tokens: 0``,
        empty content, and ``input_tokens`` collapsing to the system length. So
        the persona is folded into the first user message instead. It costs a
        few tokens and keeps every model working through one code path.
        """
        system_parts = [m["content"] for m in messages if m.get("role") == "system"]
        turns = [
            {"role": m["role"], "content": m["content"]}
            for m in messages
            if m.get("role") in ("user", "assistant")
        ]
        if system_parts:
            persona = "\n\n".join(system_parts).strip()
            first_user = next((t for t in turns if t["role"] == "user"), None)
            if first_user is not None:
                first_user["content"] = f"{persona}\n\n{first_user['content']}"
            else:
                turns.insert(0, {"role": "user", "content": persona})

        payload: Dict = {
            "model": self.model,
            "messages": turns,
            "max_tokens": max_tokens,
            "temperature": temperature,
            "stream": True,
        }

        started = time.perf_counter()
        first_token_at: Optional[float] = None
        emitted = 0
        try:
            timeout = httpx.Timeout(self.timeout, connect=10.0)
            async with httpx.AsyncClient(timeout=timeout) as client:
                async with client.stream(
                    "POST",
                    f"{self.base_url}/messages",
                    headers=self._headers("anthropic"),
                    json=payload,
                ) as response:
                    if response.status_code >= 400:
                        body = (await response.aread()).decode("utf-8", "replace")
                        yield StreamEvent(
                            kind="error",
                            model=self.model,
                            detail=f"HTTP {response.status_code}: {body[:300]}",
                        )
                        return

                    async for line in response.aiter_lines():
                        if not line or not line.startswith("data:"):
                            continue
                        data = line[5:].strip()
                        if not data or data == "[DONE]":
                            continue
                        try:
                            event = json.loads(data)
                        except json.JSONDecodeError:
                            continue
                        kind = event.get("type")
                        if kind == "content_block_delta":
                            delta = event.get("delta") or {}
                            # "thinking_delta" carries chain of thought,
                            # "text_delta" the answer itself.
                            if delta.get("type") == "thinking_delta" and delta.get("thinking"):
                                yield StreamEvent(
                                    kind="thought", text=delta["thinking"], model=self.model
                                )
                            elif delta.get("text"):
                                if first_token_at is None:
                                    first_token_at = time.perf_counter()
                                emitted += 1
                                yield StreamEvent(
                                    kind="token", text=delta["text"], model=self.model
                                )
                        elif kind == "error":
                            detail = (event.get("error") or {}).get("message", "stream error")
                            yield StreamEvent(kind="error", model=self.model, detail=detail)
                            return
        except httpx.TimeoutException:
            yield StreamEvent(
                kind="error", model=self.model, detail="model timed out with no output"
            )
            return
        except Exception as exc:  # pragma: no cover - transport level
            yield StreamEvent(
                kind="error", model=self.model, detail=f"{type(exc).__name__}: {exc}"
            )
            return

        elapsed = time.perf_counter() - started
        yield StreamEvent(
            kind="done",
            model=self.model,
            tokens=emitted,
            elapsed=elapsed,
            detail=str(int((first_token_at - started) * 1000) if first_token_at else -1),
        )

    async def _stream_openai(
        self,
        messages: Sequence[Dict[str, str]],
        max_tokens: int,
        temperature: float,
    ) -> AsyncIterator[StreamEvent]:
        """OpenAI-compatible SSE: reasoning_content and content deltas."""
        headers = self._headers("openai")

        payload: Dict = {
            "model": self.model,
            "messages": list(messages),
            "max_tokens": max_tokens,
            "temperature": temperature,
            "stream": True,
        }
        # Only models that honour it will use it; the rest ignore the field.
        if self.reasoning_effort and self.reasoning_effort != "off":
            payload["reasoning_effort"] = self.reasoning_effort

        started = time.perf_counter()
        first_token_at: Optional[float] = None
        emitted = 0

        try:
            timeout = httpx.Timeout(self.timeout, connect=10.0)
            async with httpx.AsyncClient(timeout=timeout) as client:
                async with client.stream(
                    "POST",
                    f"{self.base_url}/chat/completions",
                    headers=headers,
                    json=payload,
                ) as response:
                    if response.status_code >= 400:
                        body = (await response.aread()).decode("utf-8", "replace")
                        yield StreamEvent(
                            kind="error",
                            model=self.model,
                            detail=f"HTTP {response.status_code}: {body[:300]}",
                        )
                        return

                    async for line in response.aiter_lines():
                        if not line or not line.startswith("data:"):
                            continue
                        data = line[5:].strip()
                        if data == "[DONE]":
                            break
                        try:
                            chunk = json.loads(data)
                        except json.JSONDecodeError:
                            continue

                        thought, content = _delta_text(chunk)
                        if thought:
                            yield StreamEvent(kind="thought", text=thought, model=self.model)
                        if content:
                            if first_token_at is None:
                                first_token_at = time.perf_counter()
                            emitted += 1
                            yield StreamEvent(kind="token", text=content, model=self.model)
        except httpx.TimeoutException:
            yield StreamEvent(
                kind="error", model=self.model, detail="model timed out with no output"
            )
            return
        except Exception as exc:  # pragma: no cover - transport level
            yield StreamEvent(
                kind="error", model=self.model, detail=f"{type(exc).__name__}: {exc}"
            )
            return

        elapsed = time.perf_counter() - started
        yield StreamEvent(
            kind="done",
            model=self.model,
            tokens=emitted,
            elapsed=elapsed,
            detail=str(int((first_token_at - started) * 1000) if first_token_at else -1),
        )

    async def complete(
        self,
        messages: Sequence[Dict[str, str]],
        max_tokens: int = 2048,
        temperature: float = 0.7,
    ) -> TurnResult:
        """Consume :meth:`stream` and return the assembled turn."""
        result = TurnResult(model=self.model)
        started = time.perf_counter()
        thought: List[str] = []
        content: List[str] = []
        token_events = 0

        async for event in self.stream(messages, max_tokens, temperature):
            if event.kind == "thought":
                thought.append(event.text)
            elif event.kind == "token":
                content.append(event.text)
                token_events += 1
                if result.ttft_ms is None:
                    result.ttft_ms = int((time.perf_counter() - started) * 1000)
            elif event.kind == "error":
                result.error = event.detail
            elif event.kind == "done":
                result.tokens_per_second = (
                    event.tokens / event.elapsed if event.elapsed > 0 else 0.0
                )
                reported_ttft = int(event.detail) if event.detail.lstrip("-").isdigit() else -1
                if reported_ttft >= 0:
                    result.ttft_ms = reported_ttft

        result.thought = "".join(thought).strip()
        result.content = "".join(content).strip()
        result.latency_ms = int((time.perf_counter() - started) * 1000)
        # Rough split so the UI has a number before a provider reports usage.
        result.prompt_tokens = sum(len(m.get("content", "")) for m in messages) // 4
        # ~4 chars/token, not the SSE event count: fast providers batch many
        # tokens into a single delta, so counting events reported 5 tok/s for a
        # stream genuinely running at 300.
        result.completion_tokens = max(len(result.content) // 4, token_events)
        if result.error:
            result.content = ""
        return result


async def probe(base_url: str = DEFAULT_BASE_URL) -> Dict:
    """Cheap liveness check used by the service gate."""
    key = _credential(key_env_for(base_url))
    headers = {"Authorization": f"Bearer {key}"} if key else {}
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(f"{base_url.rstrip('/')}/models", headers=headers)
            payload = response.json()
            return {
                "ok": response.status_code == 200,
                "status": response.status_code,
                "models": len(payload.get("data", []) or []),
            }
    except Exception as exc:
        return {"ok": False, "status": 0, "models": 0, "detail": str(exc)}
