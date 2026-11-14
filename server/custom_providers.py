"""Custom model providers, the endpoints the app already calls.

`ProviderVaultScreen` has offered "add a provider" since it was written: it
POSTs to /api/providers/custom, lists them, fetches their model catalogue and
deletes them. None of those four endpoints existed, so every action in that
screen failed and the vault couldn't be filled from the app at all.

Keys are stored in the same `~/.hermes/.env` the provider prober already reads
(core.providers.read_dotenv), under a per-provider variable, so a provider
added here is immediately visible to /api/providers without a second config
path. The key is never returned by the API — only whether one is set.
"""

from __future__ import annotations

import ipaddress
import json
import os
import re
import socket
import time
import uuid
from urllib.parse import urlparse
from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Depends, Header, HTTPException
from pydantic import BaseModel, Field

from server.auth import verify_token

router = APIRouter()


async def require_auth(authorization: Optional[str] = Header(None)) -> str:
    """Verify the Bearer token. Mirrors ``server.main.require_auth``."""
    if not authorization or not verify_token(authorization):
        raise HTTPException(
            status_code=401,
            detail="Invalid or missing TALOS API Token",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return authorization.replace("Bearer ", "").strip()


def _public_base_url_or_400(base: str) -> str:
    """Reject base_urls that resolve to an address no model endpoint uses.

    A provider's base_url is fetched server-side, so a link-local address would
    turn /fetch-models into a proxy for the cloud metadata service. Loopback and
    RFC1918 stay allowed — they are where local model gateways actually live, and
    reaching these routes already requires a bearer token.
    """
    parsed = urlparse(base)
    host = parsed.hostname
    if not host:
        raise HTTPException(status_code=400, detail="base_url has no host")

    candidates = [host]
    try:
        candidates.extend(
            str(info[4][0]) for info in socket.getaddrinfo(host, None, proto=socket.IPPROTO_TCP)
        )
    except OSError:
        # Unresolvable here means unreachable, not hostile. /fetch-models will
        # say so; refusing to save would break adding a provider while offline.
        return base

    for candidate in candidates:
        try:
            addr = ipaddress.ip_address(candidate)
        except ValueError:
            continue
        # Loopback and RFC1918 are allowed: the vault is where local model
        # gateways live, and every route here now needs a bearer token, so the
        # caller is an authorised device rather than an anonymous prober.
        # What is never a model endpoint is the cloud metadata service.
        if addr.is_link_local or addr.is_multicast or addr.is_unspecified:
            raise HTTPException(
                status_code=400,
                detail="base_url points at an address that is not a usable endpoint "
                       "(link-local, multicast or unspecified)",
            )
    return base

#: Where custom providers live. Next to the app's own DB so one workspace.
STORE_PATH = Path(__file__).resolve().parent / "data" / "custom_providers.json"

#: Model list endpoint every OpenAI-compatible provider serves.
MODELS_PATH = "/models"


class CreateProviderRequest(BaseModel):
    name: str = Field(..., min_length=1, max_length=80)
    base_url: str = Field(..., min_length=1, max_length=500)
    api_key: str = Field("", max_length=500)
    target_agents: List[str] = Field(default_factory=list)


class CustomProviderView(BaseModel):
    id: str
    name: str
    base_url: str
    api_key: str  # masked, never the real key
    enabled: bool = True
    target_agents: List[str] = Field(default_factory=list)
    created_at: str = ""
    key_set: bool = False


def _mask(key: str) -> str:
    if not key:
        return ""
    if len(key) <= 8:
        return "•" * len(key)
    return key[:4] + "•" * 8 + key[-4:]


def _env_var_for(provider_id: str) -> str:
    """A stable env var name for a custom provider id."""
    slug = re.sub(r"[^A-Za-z0-9]+", "_", provider_id).strip("_").upper()
    return f"TALOS_CUSTOM_{slug}_API_KEY"


def _load() -> List[Dict[str, Any]]:
    if not STORE_PATH.exists():
        return []
    try:
        data = json.loads(STORE_PATH.read_text())
    except (ValueError, OSError):
        return []
    return data if isinstance(data, list) else []


def _save(rows: List[Dict[str, Any]]) -> None:
    STORE_PATH.parent.mkdir(parents=True, exist_ok=True)
    tmp = STORE_PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(rows, indent=2))
    tmp.replace(STORE_PATH)


def _write_env(var: str, value: str) -> Path:
    """Write KEY=value into ~/.hermes/.env, replacing an existing line.

    The provider prober reads this file, so writing here is what makes a
    provider added through the app actually probe-able. Never logged.
    """
    env_path = Path.home() / ".hermes" / ".env"
    lines: List[str] = []
    if env_path.exists():
        lines = env_path.read_text().splitlines()
    prefix = f"{var}="
    replaced = False
    for i, line in enumerate(lines):
        if line.startswith(prefix):
            lines[i] = f"{prefix}{value}"
            replaced = True
            break
    if not replaced:
        lines.append(f"{prefix}{value}")
    env_path.parent.mkdir(parents=True, exist_ok=True)
    env_path.write_text("\n".join(lines) + "\n")
    try:
        env_path.chmod(0o600)
    except OSError:
        pass
    return env_path


def _view(row: Dict[str, Any]) -> CustomProviderView:
    var = row.get("env_var") or _env_var_for(row["id"])
    key_set = bool(os.environ.get(var))
    return CustomProviderView(
        id=row["id"],
        name=row.get("name", ""),
        base_url=row.get("base_url", ""),
        api_key=_mask("x" * 40) if key_set else "",
        enabled=bool(row.get("enabled", True)),
        target_agents=list(row.get("target_agents") or []),
        created_at=row.get("created_at", ""),
        key_set=key_set,
    )


@router.get("/api/providers/custom")
async def list_custom_providers(token: str = Depends(require_auth)) -> List[CustomProviderView]:
    """Every provider added through the app. Keys are masked, never returned."""
    return [_view(row) for row in _load()]


@router.post("/api/providers/custom", status_code=201)
async def create_custom_provider(req: CreateProviderRequest, token: str = Depends(require_auth)) -> CustomProviderView:
    """Register an OpenAI-compatible provider and store its key.

    The key goes into ~/.hermes/.env, which is where the prober looks, so the
    provider shows real health on /api/providers instead of sitting inert in a
    side file that nothing reads.
    """
    base = _public_base_url_or_400(req.base_url.strip().rstrip("/"))
    if not base.startswith(("http://", "https://")):
        raise HTTPException(status_code=400, detail="base_url must start with http:// or https://")

    rows = _load()
    if any(r.get("base_url") == base for r in rows):
        raise HTTPException(status_code=409, detail="a provider with that base_url already exists")

    provider_id = f"c_{uuid.uuid4().hex[:12]}"
    env_var = _env_var_for(provider_id)
    row = {
        "id": provider_id,
        "name": req.name.strip(),
        "base_url": base,
        "env_var": env_var,
        "enabled": True,
        "target_agents": list(req.target_agents),
        "created_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
    }
    rows.append(row)
    _save(rows)
    if req.api_key.strip():
        _write_env(env_var, req.api_key.strip())
        os.environ[env_var] = req.api_key.strip()
    return _view(row)


@router.post("/api/providers/custom/{provider_id}/fetch-models")
async def fetch_custom_provider_models(provider_id: str, token: str = Depends(require_auth)) -> List[Dict[str, Any]]:
    """Ask the provider for its model list. Honest empty list when unreachable."""
    rows = _load()
    row = next((r for r in rows if r["id"] == provider_id), None)
    if row is None:
        raise HTTPException(status_code=404, detail="no such provider")

    key = os.environ.get(row.get("env_var") or _env_var_for(provider_id), "")
    url = row["base_url"].rstrip("/") + MODELS_PATH
    headers = {"Authorization": f"Bearer {key}"} if key else {}

    try:
        import httpx

        async with httpx.AsyncClient(timeout=20.0) as client:
            resp = await client.get(url, headers=headers)
        if resp.status_code != 200:
            return []
        payload = resp.json()
    except Exception:
        return []

    raw = payload.get("data") if isinstance(payload, dict) else payload
    if not isinstance(raw, list):
        return []
    out: List[Dict[str, Any]] = []
    for item in raw:
        if not isinstance(item, dict):
            continue
        model_id = item.get("id") or item.get("name")
        if not model_id:
            continue
        context = item.get("context_length") or item.get("context_window") or 0
        out.append({"id": str(model_id), "name": str(model_id), "context": int(context or 0)})
    return out


@router.delete("/api/providers/custom/{provider_id}")
async def delete_custom_provider(provider_id: str, token: str = Depends(require_auth)) -> Dict[str, Any]:
    """Remove a provider. Reports whether it actually existed."""
    rows = _load()
    remaining = [r for r in rows if r["id"] != provider_id]
    if len(remaining) == len(rows):
        raise HTTPException(status_code=404, detail="no such provider")
    _save(remaining)
    return {"deleted": True, "id": provider_id}
