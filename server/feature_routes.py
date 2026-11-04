"""HTTP surface for the features that had a module but no route.

Five capabilities shipped as pure-Python modules with passing tests but were
never reachable from the app, because nothing mounted a router for them:

* ``room_fork``          — conversation branching
* ``conversation_search``— full-text search across messages
* ``prompt_library``     — reusable prompt templates with ``{{vars}}``
* ``model_compare``      — fan one prompt across 2-4 models (SSE)
* ``diff_review``        — what files an agent changed, line by line

The modules stay pure; every FastAPI concern (auth, request parsing, error
translation, streaming) lives here. That split is why the logic is testable on
the JVM-free side and why this file can be read as a plain list of endpoints.

Conventions match ``server/main.py``:
* ``require_auth`` on every route (Bearer token, 401 otherwise);
* money-and-tokens numbers are never invented — comparison metrics carry an
  explicit ``estimated`` flag from ``model_compare``;
* a missing entity is a real 404, never an empty 200 that looks like
  "nothing changed".
"""

from __future__ import annotations

import dataclasses
import json
import sqlite3
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Depends, Header, HTTPException, Query, status
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from server.auth import verify_token
from server.db import _get_connection

router = APIRouter(tags=["features"])


async def require_auth(authorization: Optional[str] = Header(None)) -> str:
    """Verify the Bearer token. Mirrors ``server.main.require_auth``."""
    if not authorization or not verify_token(authorization):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing TALOS API Token",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return authorization.replace("Bearer ", "").strip()


def _conn() -> sqlite3.Connection:
    """One shared, WAL-mode connection (see server/db.py)."""
    return _get_connection()


def _payload(value: Any) -> Any:
    """Serialize a module dataclass without inventing a to_dict on it.

    The feature modules are deliberately free of HTTP concerns, so they return
    plain dataclasses. ``dataclasses.asdict`` keeps every field (including the
    ones a hand-written ``to_dict`` would forget) and recurses into nested
    dataclasses such as search hits.
    """
    if dataclasses.is_dataclass(value) and not isinstance(value, type):
        return dataclasses.asdict(value)
    return value


# ---------------------------------------------------------------------------
# Conversation branching
# ---------------------------------------------------------------------------


class ForkRequest(BaseModel):
    message_id: str = Field(..., description="Message to branch at (inclusive)")
    title: Optional[str] = Field(None, description="Title for the forked room")
    new_room_id: Optional[str] = Field(
        None, description="Explicit id; re-posting the same id returns the same fork"
    )


@router.post("/api/rooms/{room_id}/fork")
async def fork_room_endpoint(
    room_id: str, req: ForkRequest, token: str = Depends(require_auth)
):
    """Branch a conversation at one message into a new room.

    The new room holds every message up to and including ``message_id``, so the
    branch point is exactly what the user saw when they chose to fork.
    """
    from server.room_fork import fork_room

    conn = _conn()
    try:
        result = fork_room(
            conn,
            room_id=room_id,
            message_id=req.message_id,
            title=req.title,
            new_room_id=req.new_room_id,
        )
    finally:
        conn.close()

    if not result.ok:
        # The module returns typed error codes; map them onto HTTP honestly
        # instead of collapsing everything into one 400.
        code = getattr(result, "error_code", "") or ""
        if code == "room_not_found":
            raise HTTPException(status_code=404, detail=result.error)
        if code in ("message_not_found", "message_not_in_room"):
            raise HTTPException(status_code=404, detail=result.error)
        raise HTTPException(status_code=400, detail=result.error)
    return _payload(result)


@router.get("/api/rooms/{room_id}/lineage")
async def room_lineage_endpoint(room_id: str, token: str = Depends(require_auth)):
    """Forks of this room, oldest first. Empty list for an unforked room."""
    from server.room_fork import fork_lineage

    conn = _conn()
    try:
        refs = fork_lineage(conn, room_id)
    finally:
        conn.close()
    return [_payload(ref) for ref in refs]


# ---------------------------------------------------------------------------
# Conversation search
# ---------------------------------------------------------------------------


@router.get("/api/search")
async def search_endpoint(
    q: str = Query("", description="Search terms"),
    room_id: Optional[str] = Query(None, description="Limit to one room"),
    limit: int = Query(20, ge=0, le=100),
    offset: int = Query(0, ge=0),
    token: str = Depends(require_auth),
):
    """Full-text search across messages.

    Returns hits with a snippet and the character offsets of every match inside
    that snippet, so the client can highlight without re-searching the text.
    """
    from server.conversation_search import search

    conn = _conn()
    try:
        response = search(conn, q, room_id=room_id, limit=limit, offset=offset)
    finally:
        conn.close()
    return _payload(response)


@router.get("/api/search/status")
async def search_status_endpoint(token: str = Depends(require_auth)):
    """Whether the FTS index exists, how many messages it covers, and its lag.

    A client that shows a "search" affordance should not offer it when the
    index is absent — better to say so than to return zero hits that look like
    "no matches".
    """
    from server.conversation_search import index_status

    conn = _conn()
    try:
        return index_status(conn)
    finally:
        conn.close()


@router.post("/api/search/reindex")
async def search_reindex_endpoint(token: str = Depends(require_auth)):
    """Build or rebuild the FTS index. Idempotent; safe to call on boot."""
    from server.conversation_search import ensure_index

    conn = _conn()
    try:
        result = ensure_index(conn)
        conn.commit()
    finally:
        conn.close()
    return result


# ---------------------------------------------------------------------------
# Prompt library
# ---------------------------------------------------------------------------


class PromptCreateRequest(BaseModel):
    title: str
    body: str
    category: Optional[str] = None
    variables: Optional[List[str]] = None


class PromptUpdateRequest(BaseModel):
    title: Optional[str] = None
    body: Optional[str] = None
    category: Optional[str] = None
    variables: Optional[List[str]] = None


class PromptRenderRequest(BaseModel):
    values: Dict[str, str] = Field(default_factory=dict)


@router.get("/api/prompts")
async def list_prompts_endpoint(
    category: Optional[str] = Query(None),
    q: Optional[str] = Query(None, description="Filter by title/body text"),
    token: str = Depends(require_auth),
):
    """Every saved prompt, optionally filtered by category or a text query."""
    from server.prompt_library import list_prompts

    conn = _conn()
    try:
        prompts = list_prompts(conn, category=category, query=q)
    finally:
        conn.close()
    return [_payload(p) for p in prompts]


@router.post("/api/prompts", status_code=201)
async def create_prompt_endpoint(req: PromptCreateRequest, token: str = Depends(require_auth)):
    """Save a new prompt. A malformed ``{{placeholder}}`` is a 400, not a crash."""
    from server.prompt_library import MalformedPlaceholderError, create_prompt

    conn = _conn()
    try:
        prompt = create_prompt(
            conn,
            title=req.title,
            body=req.body,
            category=req.category,
            variables=req.variables,
        )
        conn.commit()
    except MalformedPlaceholderError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    finally:
        conn.close()
    return _payload(prompt)


@router.get("/api/prompts/{prompt_id}")
async def get_prompt_endpoint(prompt_id: str, token: str = Depends(require_auth)):
    from server.prompt_library import get_prompt

    conn = _conn()
    try:
        prompt = get_prompt(conn, prompt_id=prompt_id)
    finally:
        conn.close()
    if prompt is None:
        raise HTTPException(status_code=404, detail=f"prompt {prompt_id!r} not found")
    return _payload(prompt)


@router.patch("/api/prompts/{prompt_id}")
async def update_prompt_endpoint(
    prompt_id: str, req: PromptUpdateRequest, token: str = Depends(require_auth)
):
    from server.prompt_library import MalformedPlaceholderError, update_prompt

    conn = _conn()
    try:
        prompt = update_prompt(
            conn,
            prompt_id=prompt_id,
            title=req.title,
            body=req.body,
            category=req.category,
            variables=req.variables,
        )
        conn.commit()
    except MalformedPlaceholderError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    finally:
        conn.close()
    if prompt is None:
        raise HTTPException(status_code=404, detail=f"prompt {prompt_id!r} not found")
    return _payload(prompt)


@router.delete("/api/prompts/{prompt_id}", status_code=204)
async def delete_prompt_endpoint(prompt_id: str, token: str = Depends(require_auth)):
    from server.prompt_library import delete_prompt

    conn = _conn()
    try:
        deleted = delete_prompt(conn, prompt_id=prompt_id)
        conn.commit()
    finally:
        conn.close()
    if not deleted:
        raise HTTPException(status_code=404, detail=f"prompt {prompt_id!r} not found")
    return None


@router.post("/api/prompts/{prompt_id}/render")
async def render_prompt_endpoint(
    prompt_id: str, req: PromptRenderRequest, token: str = Depends(require_auth)
):
    """Fill a prompt's placeholders.

    Unsupplied variables are reported in ``missing`` and left intact in the
    text — never guessed, never silently blanked.
    """
    from server.prompt_library import get_prompt, render_prompt

    conn = _conn()
    try:
        prompt = get_prompt(conn, prompt_id=prompt_id)
        if prompt is None:
            raise HTTPException(status_code=404, detail=f"prompt {prompt_id!r} not found")
        result = render_prompt(conn=conn, prompt=prompt, values=req.values)
        conn.commit()
    finally:
        conn.close()
    return _payload(result)


# ---------------------------------------------------------------------------
# Model comparison (SSE)
# ---------------------------------------------------------------------------


class CompareRequest(BaseModel):
    prompt: str
    models: List[str] = Field(..., min_length=2, max_length=4)
    max_tokens: int = 512
    timeout_s: float = 60.0


@router.post("/api/compare")
async def compare_endpoint(req: CompareRequest, token: str = Depends(require_auth)):
    """Stream 2-4 models answering the same prompt, as Server-Sent Events.

    Events mirror ``model_compare``: ``started``, ``delta``, ``finished``,
    ``failed``. A failing model emits ``failed`` for itself and never cancels
    the others. ``finished`` metrics carry ``estimated`` so the client can mark
    estimated token counts instead of presenting them as provider-reported.
    """
    from server.model_compare import CompareError, compare

    async def event_stream():
        try:
            async for event in compare(
                req.prompt, req.models, max_tokens=req.max_tokens, timeout_s=req.timeout_s
            ):
                payload = _payload(event)
                payload["type"] = type(event).__name__.lower()
                yield f"data: {json.dumps(payload, default=str)}\n\n"
        except CompareError as exc:
            yield f"data: {json.dumps({'type': 'error', 'detail': str(exc)})}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


# ---------------------------------------------------------------------------
# Diff review
# ---------------------------------------------------------------------------


@router.get("/api/agents/{agent_id}/review")
async def review_endpoint(
    agent_id: str,
    since_ms: Optional[float] = Query(None, description="Only changes after this epoch ms"),
    token: str = Depends(require_auth),
):
    """What files an agent changed, as parsed unified-diff hunks.

    Reads the same tool ledger the UI shows, so the review and the activity feed
    can never disagree about what ran. An unknown agent is a 404, not an empty
    review that looks like "no changes".
    """
    from server.diff_review import UnknownAgentError, build_review
    from server.tool_ledger import ledger as tool_ledger

    events = tool_ledger.recent(room_id=None, limit=2000)
    try:
        return build_review(agent_id, events, since_ms=since_ms)
    except UnknownAgentError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
