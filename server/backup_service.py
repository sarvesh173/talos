"""Local-first snapshot engine for TALOS.

FEATURE.md section 4 asks for Google Drive cloud backup. The cloud half needs
an OAuth handshake that an unattended host cannot complete, but the half that
actually protects data — a consistent, verifiable copy of the workspace — needs
no credential at all. This module is that local half, and it is the part a
future Drive sync would replicate.

Two properties shaped the design:

* The live database runs in WAL mode, so a plain file copy of ``talos.db`` can
  capture a torn state: committed pages may still live in the ``-wal`` sidecar.
  Snapshots therefore go through ``sqlite3.Connection.backup()``, which copies
  page by page under a read lock and yields the database as of one point in
  time.
* A backup nobody can verify is a guess. Every archive carries a manifest with
  the SHA-256 of the database copy and the byte size of every member, so a
  restore can refuse a payload whose checksum disagrees with the manifest
  instead of overwriting a working database with a damaged one.

The archive also carries the provider vault (``~/.talos/vault/providers.json``),
so it holds credentials: it is written 0600 inside a 0700 directory and must be
handled like a secret, never dropped into a public bucket.

The module is deliberately free of FastAPI so the logic can be tested without
an app; ``server/backup_routes.py`` owns the HTTP surface.
"""

from __future__ import annotations

import hashlib
import json
import os
import shutil
import sqlite3
import tarfile
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

#: How many snapshots are kept in the destination directory by default.
DEFAULT_KEEP = 10

#: Prefix of every archive this module writes.
SNAPSHOT_PREFIX = "talos-snapshot-"

#: Member names inside an archive.
MANIFEST_NAME = "manifest.json"
DB_MEMBER = "talos.db"
MESSAGES_MEMBER = "messages.jsonl"
VAULT_MEMBER = "vault/providers.json"

#: Bumped when the manifest layout changes incompatibly.
FORMAT_VERSION = 1


class BackupError(Exception):
    """Base class for every failure this module reports."""


class DatabaseNotFound(BackupError):
    """The database to back up does not exist."""


class SnapshotNotFound(BackupError):
    """The archive to restore does not exist."""


class SnapshotCorrupt(BackupError):
    """The archive is unreadable, incomplete, or fails its checksum."""


class ConfirmationRequired(BackupError):
    """A destructive operation was asked for without explicit confirmation."""


# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------


def default_backup_dir() -> Path:
    """Where snapshots live when the caller does not say otherwise."""
    return Path.home() / ".talos" / "backups"


def default_vault_path() -> Path:
    """The provider credential file that belongs in every snapshot."""
    return Path.home() / ".talos" / "vault" / "providers.json"


def ensure_backup_dir(dest_dir: Optional[Path] = None) -> Path:
    """Create the snapshot directory and keep it private.

    The default directory holds provider credentials, so it is 0700. A caller
    supplied directory is only tightened when this call creates it; changing the
    mode of a directory the user already owns would be rude.
    """
    dest = Path(dest_dir) if dest_dir is not None else default_backup_dir()
    if dest.exists():
        if not dest.is_dir():
            raise BackupError(f"{dest} exists and is not a directory")
        if dest == default_backup_dir():
            _chmod_quiet(dest, 0o700)
        return dest
    dest.mkdir(parents=True, mode=0o700, exist_ok=True)
    return dest


def _chmod_quiet(path: Path, mode: int) -> None:
    try:
        os.chmod(path, mode)
    except OSError:
        # A filesystem that refuses chmod is not a reason to fail a backup;
        # the caller already knows the path it asked for.
        pass


# ---------------------------------------------------------------------------
# Small helpers
# ---------------------------------------------------------------------------


def _stamp(moment: Optional[datetime] = None) -> str:
    """UTC timestamp used in archive names and pre-restore file names."""
    moment = moment or datetime.now(timezone.utc)
    return moment.strftime("%Y%m%dT%H%M%SZ")


def _sha256_file(path: Path) -> str:
    """SHA-256 of a file, read in chunks so a large database stays cheap."""
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _read_only_uri(path: Path) -> str:
    """A read-only SQLite URI for a path, percent-encoded by pathlib."""
    return path.resolve().as_uri() + "?mode=ro"


def _table_names(db_path: Path) -> List[str]:
    """User tables of a database, in schema order.

    ``PRAGMA table_list`` separates FTS shadow tables from real ones, so the
    manifest reports the tables a human would recognise instead of the
    implementation noise behind the search index.
    """
    conn = sqlite3.connect(_read_only_uri(db_path), uri=True)
    try:
        names: List[str] = []
        for row in conn.execute("PRAGMA table_list"):
            schema, name, kind = row[0], row[1], row[2]
            if schema != "main" or kind == "shadow":
                continue
            if name.startswith("sqlite_"):
                continue
            names.append(name)
        return names
    finally:
        conn.close()


def _column_names(db_path: Path, table: str) -> List[str]:
    conn = sqlite3.connect(_read_only_uri(db_path), uri=True)
    try:
        return [row[1] for row in conn.execute(f'PRAGMA table_info("{table}")')]
    except sqlite3.Error:
        return []
    finally:
        conn.close()


def _table_row_counts(db_path: Path) -> Dict[str, int]:
    """Row count per user table, read from the snapshot copy."""
    counts: Dict[str, int] = {}
    conn = sqlite3.connect(_read_only_uri(db_path), uri=True)
    try:
        for table in _table_names(db_path):
            try:
                row = conn.execute(f'SELECT COUNT(*) FROM "{table}"').fetchone()
                counts[table] = int(row[0]) if row else 0
            except sqlite3.Error:
                # A table we cannot count is reported as unknown, not as zero.
                counts[table] = -1
    finally:
        conn.close()
    return counts


def _decode_json(value: Any) -> Any:
    """Parse a JSON column, keeping the raw string when it is not JSON."""
    if value is None:
        return None
    if not isinstance(value, str):
        return value
    try:
        return json.loads(value)
    except (TypeError, ValueError):
        return value


# ---------------------------------------------------------------------------
# Snapshot creation
# ---------------------------------------------------------------------------


def _snapshot_database(src_db: Path, dst_path: Path) -> None:
    """Copy a live database into ``dst_path`` as one consistent file.

    ``sqlite3.Connection.backup`` is the only safe way to copy a WAL database
    that may be written to while the copy runs: it reads page by page under a
    read lock, so the result is the database at one point in time rather than a
    torn mixture of the main file and the write-ahead log.
    """
    if dst_path.exists():
        dst_path.unlink()
    src = sqlite3.connect(str(src_db))
    try:
        dst = sqlite3.connect(str(dst_path))
        try:
            src.backup(dst)
            dst.commit()
        finally:
            dst.close()
    finally:
        src.close()


def _export_messages(db_path: Path, out_path: Path) -> int:
    """Write the room message archive as JSONL, one JSON object per line.

    Columns that the database does not have are exported as ``null`` rather
    than omitted, and a room that no longer exists exports ``room_name`` as
    ``null`` instead of a made-up name.
    """
    tables = _table_names(db_path)
    if "messages" not in tables:
        out_path.write_text("", encoding="utf-8")
        return 0

    columns = _column_names(db_path, "messages")
    room_columns = _column_names(db_path, "rooms") if "rooms" in tables else []

    select_parts = [
        f'"{name}"' if name in columns else f'NULL AS "{name}"'
        for name in (
            "id",
            "room_id",
            "sender_id",
            "sender_name",
            "sender_role",
            "content",
            "thought",
            "tool_calls_json",
            "attachments_json",
            "reply_to_id",
            "created_at",
        )
    ]
    join = ""
    room_name = "NULL"
    if "rooms" in tables and "id" in room_columns and "name" in room_columns:
        join = 'LEFT JOIN "rooms" r ON r.id = "messages"."room_id"'
        room_name = 'r."name"'
    order = ""
    if "created_at" in columns and "id" in columns:
        order = 'ORDER BY "messages"."created_at" ASC, "messages"."id" ASC'

    sql = (
        f'SELECT {", ".join(select_parts)}, {room_name} AS "room_name" '
        f'FROM "messages" {join} {order}'
    )

    conn = sqlite3.connect(_read_only_uri(db_path), uri=True)
    conn.row_factory = sqlite3.Row
    written = 0
    try:
        with out_path.open("w", encoding="utf-8") as handle:
            for row in conn.execute(sql):
                record = {
                    "id": row["id"],
                    "room_id": row["room_id"],
                    "room_name": row["room_name"],
                    "sender_id": row["sender_id"],
                    "sender_name": row["sender_name"],
                    "sender_role": row["sender_role"],
                    "content": row["content"],
                    "thought": row["thought"],
                    "tool_calls": _decode_json(row["tool_calls_json"]),
                    "attachments": _decode_json(row["attachments_json"]),
                    "reply_to_id": row["reply_to_id"],
                    "created_at": row["created_at"],
                }
                handle.write(json.dumps(record, default=str) + "\n")
                written += 1
    finally:
        conn.close()
    return written


def _unique_archive_path(dest: Path, stamp: str) -> Path:
    """A free archive path. The suffix sorts after the plain name."""
    candidate = dest / f"{SNAPSHOT_PREFIX}{stamp}.tar.gz"
    seq = 0
    while candidate.exists():
        seq += 1
        candidate = dest / f"{SNAPSHOT_PREFIX}{stamp}_{seq:03d}.tar.gz"
    return candidate


def create_snapshot(
    db_path: Path,
    dest_dir: Optional[Path] = None,
    keep: int = DEFAULT_KEEP,
    vault_path: Optional[Path] = None,
) -> Dict[str, Any]:
    """Write one ``.tar.gz`` snapshot of the workspace.

    The archive holds the consistent database copy, the room message archive as
    JSONL, the provider vault when it exists, and a manifest describing all of
    it. ``keep`` prunes the destination afterwards so the newest N snapshots
    survive; the names of the pruned archives are reported back.
    """
    source_db = Path(db_path)
    if not source_db.is_file():
        raise DatabaseNotFound(f"no database at {source_db}")

    dest = ensure_backup_dir(dest_dir)
    vault = Path(vault_path) if vault_path is not None else default_vault_path()

    staging = Path(tempfile.mkdtemp(prefix=".talos-snapshot-", dir=str(dest)))
    try:
        snapshot_db = staging / DB_MEMBER
        _snapshot_database(source_db, snapshot_db)
        row_counts = _table_row_counts(snapshot_db)
        message_count = _export_messages(snapshot_db, staging / MESSAGES_MEMBER)

        files: List[Dict[str, Any]] = []
        for name in (DB_MEMBER, MESSAGES_MEMBER):
            member = staging / name
            files.append(
                {
                    "name": name,
                    "bytes": member.stat().st_size,
                    "sha256": _sha256_file(member),
                }
            )

        vault_included = False
        if vault.is_file():
            vault_target = staging / VAULT_MEMBER
            vault_target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(vault, vault_target)
            vault_included = True
            files.append(
                {
                    "name": VAULT_MEMBER,
                    "bytes": vault_target.stat().st_size,
                    "sha256": _sha256_file(vault_target),
                }
            )

        created = datetime.now(timezone.utc)
        manifest = {
            "format_version": FORMAT_VERSION,
            "created_at": created.isoformat(),
            "created_at_epoch": created.timestamp(),
            "source_db": str(source_db),
            "sqlite_sha256": _sha256_file(snapshot_db),
            "sqlite_bytes": snapshot_db.stat().st_size,
            "row_counts": row_counts,
            "message_count": message_count,
            "vault_included": vault_included,
            "files": files,
        }
        (staging / MANIFEST_NAME).write_text(
            json.dumps(manifest, indent=2, default=str), encoding="utf-8"
        )

        archive = _unique_archive_path(dest, _stamp(created))
        with tarfile.open(archive, "w:gz") as tar:
            tar.add(staging / MANIFEST_NAME, arcname=MANIFEST_NAME)
            tar.add(staging / DB_MEMBER, arcname=DB_MEMBER)
            tar.add(staging / MESSAGES_MEMBER, arcname=MESSAGES_MEMBER)
            if vault_included:
                tar.add(staging / VAULT_MEMBER, arcname=VAULT_MEMBER)
        _chmod_quiet(archive, 0o600)
    finally:
        shutil.rmtree(staging, ignore_errors=True)

    pruned: List[str] = []
    if keep is not None:
        pruned = prune_snapshots(dest, keep=keep)

    return {
        "archive": str(archive),
        "filename": archive.name,
        "created_at": manifest["created_at"],
        "created_at_epoch": manifest["created_at_epoch"],
        "bytes": archive.stat().st_size,
        "sqlite_sha256": manifest["sqlite_sha256"],
        "row_counts": manifest["row_counts"],
        "message_count": manifest["message_count"],
        "vault_included": manifest["vault_included"],
        "files": manifest["files"],
        "pruned": pruned,
    }


# ---------------------------------------------------------------------------
# Listing
# ---------------------------------------------------------------------------


def _read_manifest(archive: Path) -> Dict[str, Any]:
    """Read ``manifest.json`` out of an archive, or refuse the archive."""
    try:
        with tarfile.open(archive, "r:gz") as tar:
            member = None
            for candidate in tar.getmembers():
                if candidate.name == MANIFEST_NAME:
                    member = candidate
                    break
            if member is None:
                raise SnapshotCorrupt(f"{archive.name} has no {MANIFEST_NAME}")
            handle = tar.extractfile(member)
            if handle is None:
                raise SnapshotCorrupt(f"{archive.name} has an unreadable {MANIFEST_NAME}")
            raw = handle.read()
    except tarfile.TarError as exc:
        raise SnapshotCorrupt(f"{archive.name} is not a readable archive: {exc}") from exc
    try:
        manifest = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise SnapshotCorrupt(f"{archive.name} has an unreadable manifest: {exc}") from exc
    if not isinstance(manifest, dict):
        raise SnapshotCorrupt(f"{archive.name} has a malformed manifest")
    return manifest


def list_snapshots(dest_dir: Optional[Path] = None) -> List[Dict[str, Any]]:
    """Every snapshot in the destination directory, newest first.

    Sizes come from the manifest and from the file system — never from the
    archive name. An archive whose manifest cannot be read is reported as
    ``unreadable`` instead of being hidden, because a silently dropped backup
    looks exactly like a backup that was never taken.
    """
    dest = Path(dest_dir) if dest_dir is not None else default_backup_dir()
    if not dest.is_dir():
        return []

    entries: List[Dict[str, Any]] = []
    for archive in sorted(dest.glob(f"{SNAPSHOT_PREFIX}*.tar.gz")):
        if not archive.is_file():
            continue
        entry: Dict[str, Any] = {
            "filename": archive.name,
            "archive": str(archive),
            "bytes": archive.stat().st_size,
            "created_at": None,
            "created_at_epoch": None,
            "status": "unreadable",
            "error": None,
            "row_counts": None,
            "files": None,
            "sqlite_sha256": None,
            "message_count": None,
        }
        try:
            manifest = _read_manifest(archive)
        except SnapshotCorrupt as exc:
            entry["error"] = str(exc)
        else:
            entry.update(
                {
                    "status": "ok",
                    "created_at": manifest.get("created_at"),
                    "created_at_epoch": manifest.get("created_at_epoch"),
                    "row_counts": manifest.get("row_counts"),
                    "files": manifest.get("files"),
                    "sqlite_sha256": manifest.get("sqlite_sha256"),
                    "message_count": manifest.get("message_count"),
                    "vault_included": manifest.get("vault_included"),
                }
            )
        entries.append(entry)

    # Newest first. An archive without a usable timestamp sorts last: it is
    # the least trustworthy thing in the directory, so it is the first thing
    # pruning should remove.
    entries.sort(
        key=lambda item: (
            item["created_at_epoch"] is not None,
            item["created_at_epoch"] or 0.0,
            item["filename"],
        ),
        reverse=True,
    )
    return entries


# ---------------------------------------------------------------------------
# Restore
# ---------------------------------------------------------------------------


def _extract_member(archive: Path, member_name: str, target: Path) -> None:
    """Pull one member out of an archive into ``target``."""
    try:
        with tarfile.open(archive, "r:gz") as tar:
            member = None
            for candidate in tar.getmembers():
                if candidate.name == member_name:
                    member = candidate
                    break
            if member is None:
                raise SnapshotCorrupt(f"{archive.name} has no {member_name}")
            handle = tar.extractfile(member)
            if handle is None:
                raise SnapshotCorrupt(f"{archive.name} has an unreadable {member_name}")
            with target.open("wb") as out:
                shutil.copyfileobj(handle, out)
    except tarfile.TarError as exc:
        raise SnapshotCorrupt(f"{archive.name} is not a readable archive: {exc}") from exc


def restore_snapshot(
    archive_path: Path,
    db_path: Path,
    confirm: bool = False,
) -> Dict[str, Any]:
    """Put a snapshot back in place of the live database.

    The current database is copied aside first, so a restore is itself
    recoverable. Without ``confirm`` the call refuses: replacing a workspace
    is not something a stray request should be able to do.
    """
    if not confirm:
        raise ConfirmationRequired(
            "restore requires confirm=True; the live database is only replaced "
            "after an explicit confirmation"
        )

    archive = Path(archive_path)
    if not archive.is_file():
        raise SnapshotNotFound(f"no snapshot archive at {archive}")

    manifest = _read_manifest(archive)
    target_db = Path(db_path)

    staging = Path(tempfile.mkdtemp(prefix=".talos-restore-"))
    try:
        extracted = staging / DB_MEMBER
        _extract_member(archive, DB_MEMBER, extracted)

        expected = manifest.get("sqlite_sha256")
        actual = _sha256_file(extracted)
        if expected and actual != expected:
            raise SnapshotCorrupt(
                f"database checksum mismatch in {archive.name}: manifest says "
                f"{expected}, archive holds {actual}"
            )

        pre_restore: Optional[Path] = None
        if target_db.is_file():
            pre_restore = target_db.with_name(
                f"{target_db.name}.pre-restore-{_stamp()}"
            )
            _snapshot_database(target_db, pre_restore)

        mode = target_db.stat().st_mode & 0o777 if target_db.exists() else 0o600
        incoming = target_db.with_name(f".{target_db.name}.restore-{os.getpid()}")
        shutil.copyfile(extracted, incoming)
        _chmod_quiet(incoming, mode)
        os.replace(incoming, target_db)

        # The restored file is a complete, self-contained database. A stale
        # write-ahead log left over from the replaced database would corrupt
        # it, so the sidecars go.
        for suffix in ("-wal", "-shm"):
            sidecar = target_db.with_name(target_db.name + suffix)
            if sidecar.exists():
                sidecar.unlink()
    finally:
        shutil.rmtree(staging, ignore_errors=True)

    return {
        "restored": True,
        "filename": archive.name,
        "archive": str(archive),
        "db_path": str(target_db),
        "pre_restore_copy": str(pre_restore) if pre_restore else None,
        "restored_at": datetime.now(timezone.utc).isoformat(),
        "snapshot_created_at": manifest.get("created_at"),
        "sqlite_sha256": manifest.get("sqlite_sha256"),
        "row_counts": manifest.get("row_counts"),
        "message_count": manifest.get("message_count"),
        "files": manifest.get("files"),
        "bytes": target_db.stat().st_size,
        # The running server keeps one shared SQLite connection open. It still
        # points at the replaced file until the process restarts, so the
        # restored data is not visible to live queries yet.
        "server_restart_required": True,
    }


# ---------------------------------------------------------------------------
# Pruning
# ---------------------------------------------------------------------------


def prune_snapshots(
    dest_dir: Optional[Path] = None, keep: int = DEFAULT_KEEP
) -> List[str]:
    """Delete the oldest snapshots beyond ``keep`` and return their names."""
    if keep < 0:
        raise ValueError("keep must be zero or more")
    dest = Path(dest_dir) if dest_dir is not None else default_backup_dir()
    snapshots = list_snapshots(dest)
    if len(snapshots) <= keep:
        return []
    deleted: List[str] = []
    for entry in snapshots[keep:]:
        archive = Path(entry["archive"])
        try:
            archive.unlink()
        except FileNotFoundError:
            continue
        deleted.append(archive.name)
    return deleted
