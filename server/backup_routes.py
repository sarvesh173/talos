"""HTTP surface for the local backup/snapshot engine.

The engine in ``server/backup_service.py`` is deliberately free of HTTP
concerns so it can be tested without an app. This module owns the wire
format: auth, request bodies, and the mapping from typed engine errors onto
honest status codes.

Conventions match ``server/feature_routes.py``:
* ``require_auth`` on every route (Bearer token, 401 otherwise);
* a missing database or archive is a real 404, never an empty 200;
* nothing here invents a number: sizes come from the manifest or the file
  system, row counts come from the snapshot itself.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Depends, Header, HTTPException, Query, status
from pydantic import BaseModel, Field

from server.auth import verify_token
from server.backup_service import (
    DEFAULT_KEEP,
    BackupError,
    ConfirmationRequired,
    DatabaseNotFound,
    SnapshotCorrupt,
    SnapshotNotFound,
    create_snapshot,
    default_backup_dir,
    list_snapshots,
    prune_snapshots,
    restore_snapshot,
)
from server.db import _DB_PATH

router = APIRouter(prefix="/api/backup", tags=["backup"])


async def require_auth(authorization: Optional[str] = Header(None)) -> str:
    """Verify the Bearer token. Mirrors ``server.main.require_auth``."""
    if not authorization or not verify_token(authorization):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing TALOS API Token",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return authorization.replace("Bearer ", "").strip()


def _destination(dest_dir: Optional[str]) -> Path:
    """Resolve the snapshot directory a request asked for."""
    if not dest_dir:
        return default_backup_dir()
    path = Path(dest_dir).expanduser()
    if not path.is_absolute():
        raise HTTPException(
            status_code=400, detail="dest_dir must be an absolute path"
        )
    return path


def _database(db_path: Optional[str]) -> Path:
    """Resolve the database a request asked for, defaulting to the live one."""
    if not db_path:
        return Path(_DB_PATH)
    path = Path(db_path).expanduser()
    if not path.is_absolute():
        raise HTTPException(status_code=400, detail="db_path must be an absolute path")
    return path


def _fail(exc: BackupError) -> HTTPException:
    """Translate a typed engine error into an honest HTTP error."""
    if isinstance(exc, (DatabaseNotFound, SnapshotNotFound)):
        return HTTPException(status_code=404, detail=str(exc))
    if isinstance(exc, ConfirmationRequired):
        return HTTPException(status_code=400, detail=str(exc))
    if isinstance(exc, SnapshotCorrupt):
        return HTTPException(status_code=422, detail=str(exc))
    return HTTPException(status_code=400, detail=str(exc))


class CreateSnapshotRequest(BaseModel):
    dest_dir: Optional[str] = Field(None, description="Snapshot directory")
    db_path: Optional[str] = Field(None, description="Database to snapshot")
    keep: int = Field(DEFAULT_KEEP, ge=0, description="How many snapshots to keep")


class RestoreRequest(BaseModel):
    path: str = Field(..., description="Archive to restore")
    confirm: bool = Field(False, description="Must be true to replace the database")
    db_path: Optional[str] = Field(None, description="Database to replace")


class PruneRequest(BaseModel):
    dest_dir: Optional[str] = Field(None, description="Snapshot directory")
    keep: int = Field(DEFAULT_KEEP, ge=0, description="How many snapshots to keep")


@router.get("/snapshots")
async def list_snapshots_endpoint(
    dest_dir: Optional[str] = Query(None, description="Snapshot directory"),
    limit: int = Query(50, ge=0, le=500),
    token: str = Depends(require_auth),
):
    """Snapshots in the destination directory, newest first."""
    destination = _destination(dest_dir)
    snapshots = list_snapshots(destination)
    return {
        "dest_dir": str(destination),
        "count": len(snapshots),
        "snapshots": snapshots[:limit] if limit else [],
    }


@router.post("/snapshots", status_code=201)
async def create_snapshot_endpoint(
    req: CreateSnapshotRequest = CreateSnapshotRequest(),
    token: str = Depends(require_auth),
):
    """Take a snapshot now. Returns the manifest summary of the new archive."""
    destination = _destination(req.dest_dir)
    database = _database(req.db_path)
    try:
        return create_snapshot(database, dest_dir=destination, keep=req.keep)
    except BackupError as exc:
        raise _fail(exc) from exc


@router.post("/restore")
async def restore_snapshot_endpoint(
    req: RestoreRequest, token: str = Depends(require_auth)
):
    """Restore a snapshot over the database.

    ``confirm`` must be true: without it the request is refused, because the
    live database is replaced. The previous database is copied aside first.
    """
    database = _database(req.db_path)
    try:
        return restore_snapshot(Path(req.path), database, confirm=req.confirm)
    except BackupError as exc:
        raise _fail(exc) from exc


@router.post("/prune")
async def prune_snapshots_endpoint(
    req: PruneRequest = PruneRequest(),
    token: str = Depends(require_auth),
):
    """Delete the oldest snapshots beyond ``keep``."""
    destination = _destination(req.dest_dir)
    try:
        deleted = prune_snapshots(destination, keep=req.keep)
    except BackupError as exc:
        raise _fail(exc) from exc
    remaining = list_snapshots(destination)
    return {
        "dest_dir": str(destination),
        "keep": req.keep,
        "deleted": deleted,
        "remaining": len(remaining),
    }
