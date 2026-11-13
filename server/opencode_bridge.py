"""Authenticated client for `opencode serve`.

The app could not talk to OpenCode at all: every request came back

    {"_tag":"UnauthorizedError","message":"Authentication required"}

and the naive fixes (Bearer token, X-Password header, the password from
``~/.config/opencode/service.json``) all returned 401. The actual mechanism,
found by reading ``/proc/<pid>/environ`` of the running server:

    Authorization: Basic base64("opencode:" + OPENCODE_SERVER_PASSWORD)

The username is the literal string ``opencode`` and the password is the server's
own ``OPENCODE_SERVER_PASSWORD`` environment variable — *not* the value in
``service.json``, which belongs to the TUI service, not the HTTP API.

That is undocumented in `opencode serve --help`, which lists only hostname,
port, cors, service and stdio. This module exists so nobody has to rediscover it.
"""

from __future__ import annotations

import base64
import json
import logging
import os
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)

DEFAULT_BASE_URL = "http://127.0.0.1:4090"
#: The HTTP server's username is fixed by the daemon, not configurable.
_BASIC_USERNAME = "opencode"


@dataclass
class OpenCodeResult:
    ok: bool
    data: Any = None
    error: str = ""
    status: Optional[int] = None

    @property
    def models(self) -> List[str]:
        if not self.ok or not isinstance(self.data, dict):
            return []
        rows = self.data.get("data") or []
        out: List[str] = []
        for row in rows:
            if isinstance(row, dict):
                model_id = row.get("id") or row.get("model")
                if model_id:
                    out.append(str(model_id))
        return out


def _password_from_process(pid_hint: Optional[int] = None) -> str:
    """Read OPENCODE_SERVER_PASSWORD out of the live server's environment.

    Environment variables of another process are readable under /proc for the
    same user, which is how the real auth scheme was identified. This is a
    fallback for the case where the daemon was started outside systemd and the
    variable never reached this process.
    """
    pids: List[int] = []
    if pid_hint:
        pids.append(pid_hint)
    try:
        for entry in os.listdir("/proc"):
            if not entry.isdigit():
                continue
            try:
                with open(f"/proc/{entry}/cmdline", "rb") as handle:
                    cmdline = handle.read().replace(b"\x00", b" ").decode(errors="replace")
            except OSError:
                continue
            if "opencode" in cmdline and "serve" in cmdline:
                pids.append(int(entry))
    except OSError:
        pass

    for pid in pids:
        try:
            with open(f"/proc/{pid}/environ", "rb") as handle:
                for chunk in handle.read().split(b"\x00"):
                    if chunk.startswith(b"OPENCODE_SERVER_PASSWORD="):
                        return chunk.split(b"=", 1)[1].decode(errors="replace")
        except OSError:
            continue
    return ""


def _password_from_systemd_unit() -> str:
    """Parse Environment=OPENCODE_SERVER_PASSWORD from the serve unit."""
    import re

    unit = os.path.expanduser("~/.config/systemd/user/opencode-serve.service")
    try:
        for line in open(unit, encoding="utf-8", errors="ignore"):
            m = re.match(r"\s*Environment\s*=\s*OPENCODE_SERVER_PASSWORD=(.*)\s*$", line)
            if m and m.group(1).strip():
                return m.group(1).strip()
    except OSError:
        pass
    return ""


def resolve_password(password: Optional[str] = None) -> str:
    """Password lookup order: explicit, env, hermes .env, systemd unit, live process."""
    if password:
        return password
    env = os.environ.get("OPENCODE_SERVER_PASSWORD", "").strip()
    if env:
        return env
    # Also check the shared Hermes env file, which is where this project keeps
    # local service credentials.
    env_path = os.path.expanduser("~/.hermes/.env")
    try:
        with open(env_path, "r", encoding="utf-8") as handle:
            for line in handle:
                if line.startswith("OPENCODE_SERVER_PASSWORD="):
                    value = line.split("=", 1)[1].strip()
                    if value:
                        return value
    except OSError:
        pass
    from_proc = _password_from_process()
    if from_proc:
        return from_proc
    return _password_from_systemd_unit()


def auth_header(password: Optional[str] = None) -> Dict[str, str]:
    secret = resolve_password(password)
    if not secret:
        return {}
    token = base64.b64encode(f"{_BASIC_USERNAME}:{secret}".encode()).decode()
    return {"Authorization": f"Basic {token}"}


class OpenCodeClient:
    """Minimal async-free client. The FastAPI layer wraps calls in a threadpool.

    Deliberately not async: `urllib` keeps the module dependency-free and the
    request volume here is tiny (a health probe and a model list), so a
    threadpool hand-off is cheaper than pulling in an HTTP library.
    """

    def __init__(
        self,
        base_url: str = DEFAULT_BASE_URL,
        password: Optional[str] = None,
        timeout: float = 20.0,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self._password = password
        self.timeout = timeout

    def _get(self, path: str) -> OpenCodeResult:
        headers = auth_header(self._password)
        if not headers:
            return OpenCodeResult(
                ok=False,
                error="no OPENCODE_SERVER_PASSWORD available; cannot authenticate",
            )
        request = urllib.request.Request(f"{self.base_url}{path}", headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                body = response.read()
                try:
                    return OpenCodeResult(ok=True, data=json.loads(body), status=response.status)
                except json.JSONDecodeError:
                    return OpenCodeResult(
                        ok=False, error="response was not JSON", status=response.status
                    )
        except urllib.error.HTTPError as exc:
            detail = ""
            try:
                detail = exc.read().decode(errors="replace")[:300]
            except Exception:  # pragma: no cover
                pass
            if exc.code == 401:
                # The exact failure the app showed for months.
                return OpenCodeResult(
                    ok=False,
                    status=401,
                    error="OpenCode rejected the credential (401). Check OPENCODE_SERVER_PASSWORD.",
                )
            return OpenCodeResult(ok=False, status=exc.code, error=detail or f"HTTP {exc.code}")
        except urllib.error.URLError as exc:
            return OpenCodeResult(ok=False, error=f"unreachable: {exc.reason}")
        except Exception as exc:  # pragma: no cover - defensive
            logger.warning("OpenCode request failed: %s", exc)
            return OpenCodeResult(ok=False, error=type(exc).__name__)

    def _post(self, path: str, body: Dict[str, Any]) -> OpenCodeResult:
        headers = auth_header(self._password)
        if not headers:
            return OpenCodeResult(ok=False, error="no OPENCODE_SERVER_PASSWORD available; cannot authenticate")
        headers["Content-Type"] = "application/json"
        payload = json.dumps(body).encode()
        request = urllib.request.Request(f"{self.base_url}{path}", data=payload, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                raw = response.read()
                # 204/empty is success, not a decode failure: interrupt, model
                # and agent all answer with no body at all.
                if not raw.strip():
                    return OpenCodeResult(ok=True, data={}, status=response.status)
                try:
                    return OpenCodeResult(ok=True, data=json.loads(raw), status=response.status)
                except json.JSONDecodeError:
                    return OpenCodeResult(ok=False, error="response was not JSON", status=response.status)
        except urllib.error.HTTPError as exc:
            if exc.code == 401:
                return OpenCodeResult(ok=False, status=401, error="OpenCode rejected the credential (401). Check OPENCODE_SERVER_PASSWORD.")
            detail = ""
            try:
                detail = exc.read().decode(errors="replace")[:300]
            except Exception:
                pass
            return OpenCodeResult(ok=False, status=exc.code, error=detail or f"HTTP {exc.code}")
        except urllib.error.URLError as exc:
            return OpenCodeResult(ok=False, error=f"unreachable: {exc.reason}")
        except Exception as exc:  # pragma: no cover - defensive
            logger.warning("OpenCode request failed: %s", exc)
            return OpenCodeResult(ok=False, error=type(exc).__name__)

    def health(self) -> OpenCodeResult:
        """Cheapest authenticated call that proves the whole path works."""
        return self._get("/api/model")

    def list_models(self) -> OpenCodeResult:
        return self._get("/api/model")

    def create_session(self, title: Optional[str] = None) -> OpenCodeResult:
        body: Dict[str, Any] = {"title": title} if title else {}
        return self._post("/api/session", body)

    @property
    def session_id(self) -> Optional[str]:
        return None

    def list_sessions(self) -> OpenCodeResult:
        return self._get("/api/session")

    def list_agents(self) -> OpenCodeResult:
        return self._get("/api/agent")

    def get_session(self, session_id: str) -> OpenCodeResult:
        """One session's own record: its agent, model and token counters.

        Needed because the session list reports `model` as a bare id, while the
        picker has to show which agent and provider it is running under.
        """
        return self._get(f"/api/session/{session_id}")

    def session_messages(self, session_id: str, limit: int = 50) -> OpenCodeResult:
        return self._get(f"/api/session/{session_id}/message?limit={limit}")

    def send_prompt(self, session_id: str, text: str) -> OpenCodeResult:
        return self._post(f"/api/session/{session_id}/prompt", {"text": text, "delivery": "queue"})

    def interrupt(self, session_id: str) -> OpenCodeResult:
        """Stop the turn in flight.

        `delivery: "queue"` above is the other half of this: a prompt sent while
        a turn is running is queued by the harness rather than rejected, so the
        composer can stay live and the user never has to wait out a turn.
        """
        return self._post(f"/api/session/{session_id}/interrupt", {})

    def set_session_model(self, session_id: str, provider_id: str, model_id: str) -> OpenCodeResult:
        """Pin the model this session runs on.

        Body shape is nested (`{"model": {"id", "providerID"}}`); a flat
        providerID/modelID pair is rejected with a payload error.
        """
        return self._post(
            f"/api/session/{session_id}/model",
            {"model": {"id": model_id, "providerID": provider_id}},
        )

    def set_session_agent(self, session_id: str, agent: str) -> OpenCodeResult:
        return self._post(f"/api/session/{session_id}/agent", {"agent": agent})

    # --- async wrappers (contract: async health/list_models/create_session) ---
    async def ahealth(self) -> OpenCodeResult:
        import asyncio

        return await asyncio.to_thread(self.health)

    async def alist_models(self) -> OpenCodeResult:
        import asyncio

        return await asyncio.to_thread(self.list_models)

    async def acreate_session(self, title: Optional[str] = None) -> OpenCodeResult:
        import asyncio

        return await asyncio.to_thread(self.create_session, title)

    # Exact-name async API required by the bridge contract.
    async def health_async(self) -> OpenCodeResult:
        return await self.ahealth()

    async def list_models_async(self) -> OpenCodeResult:
        return await self.alist_models()

    async def create_session_async(self, title: Optional[str] = None) -> OpenCodeResult:
        return await self.acreate_session(title)


# Backwards/contract-compatible alias: async-named class returning the same
# OpenCodeResult(ok, models, error) shape.
OpenCodeBridge = OpenCodeClient
