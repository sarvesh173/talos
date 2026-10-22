"""Authentication & Bot Token Gate for TALOS.
Prevents unauthorized mesh or local network peers from communicating with agents.

The token store is a SQLite table next to the rest of the TALOS data. Tokens
are secrets, so the list APIs never return the raw value: they return a
stable ``token_id`` plus a masked preview. The raw secret is only ever handed
out once, at creation time.
"""

from __future__ import annotations

import os
from pathlib import Path
import secrets
import sqlite3
import time
import uuid
from typing import Any, Dict, List, Optional, Tuple

from core.models import TokenInfo, TokenSummary
from core.providers import mask_token

_HERMES_DIR = Path.home() / ".hermes"
_TOKEN_FILE = Path(
    os.environ.get("TALOS_TOKEN_FILE", str(_HERMES_DIR / "talos_token.txt"))
)
_DATA_DIR = Path(__file__).resolve().parent / "data"
_DB_PATH = Path(
    os.environ.get("TALOS_AUTH_DB_PATH", str(_DATA_DIR / "talos.db"))
)

#: Role that may manage other tokens.
ROLE_ADMIN = "admin"
ROLE_USER = "user"

#: Columns added after the first release. They are migrated in place so that
#: existing installs keep their tokens.
_MIGRATION_COLUMNS: Tuple[Tuple[str, str], ...] = (
    ("token_id", "TEXT"),
    ("last_used_at", "REAL"),
    ("use_count", "INTEGER DEFAULT 0"),
    ("revoked_at", "REAL"),
    ("expires_at", "REAL"),
    ("note", "TEXT"),
)


def _get_connection() -> sqlite3.Connection:
    _DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(_DB_PATH), check_same_thread=False)
    conn.row_factory = sqlite3.Row
    return conn


def _table_columns(conn: sqlite3.Connection, table: str) -> List[str]:
    try:
        cur = conn.cursor()
        cur.execute(f"PRAGMA table_info({table})")
        return [row["name"] for row in cur.fetchall()]
    except sqlite3.Error:
        return []


def _migrate(conn: sqlite3.Connection) -> None:
    """Add any missing token-management columns."""
    existing = _table_columns(conn, "api_tokens")
    if not existing:
        return
    cur = conn.cursor()
    for column, ddl in _MIGRATION_COLUMNS:
        if column not in existing:
            cur.execute(f"ALTER TABLE api_tokens ADD COLUMN {column} {ddl}")
    conn.commit()

    # Backfill a stable id for tokens created before the migration.
    cur.execute("SELECT token, token_id FROM api_tokens WHERE token_id IS NULL OR token_id = ''")
    for row in cur.fetchall():
        cur.execute(
            "UPDATE api_tokens SET token_id = ? WHERE token = ?",
            (uuid.uuid4().hex[:16], row["token"]),
        )
    conn.commit()


def init_auth_db() -> None:
    """Ensure token table exists and seed master token."""
    conn = _get_connection()
    cur = conn.cursor()
    cur.execute("""
    CREATE TABLE IF NOT EXISTS api_tokens (
        token TEXT PRIMARY KEY,
        label TEXT NOT NULL,
        role TEXT DEFAULT 'admin',
        created_at REAL,
        is_revoked INTEGER DEFAULT 0
    );
    """)
    conn.commit()
    _migrate(conn)

    cur.execute("SELECT COUNT(*) as cnt FROM api_tokens WHERE is_revoked = 0")
    if cur.fetchone()["cnt"] == 0:
        # Create initial Master Bot Token for Sarvesh
        master_token = f"talos_sk_{secrets.token_hex(24)}"
        cur.execute("""
        INSERT INTO api_tokens (token, token_id, label, role, created_at, use_count, is_revoked)
        VALUES (?, ?, ?, ?, ?, 0, 0)
        """, (master_token, uuid.uuid4().hex[:16], "Sarvesh Master (Phone)", ROLE_ADMIN, time.time()))
        conn.commit()

        # Write to secure local file
        try:
            _TOKEN_FILE.parent.mkdir(parents=True, exist_ok=True)
            _TOKEN_FILE.write_text(master_token.strip() + "\n")
            _TOKEN_FILE.chmod(0o600)
        except Exception:
            pass

    conn.close()


# ---------------------------------------------------------------------------
# Queries
# ---------------------------------------------------------------------------


def _row_to_info(row: sqlite3.Row, master_token: str = "") -> TokenInfo:
    token = row["token"]
    return TokenInfo(
        token_id=row["token_id"] or "",
        label=row["label"],
        role=row["role"] or ROLE_USER,
        created_at=row["created_at"] or 0.0,
        last_used_at=row["last_used_at"],
        use_count=int(row["use_count"] or 0),
        is_revoked=bool(row["is_revoked"]),
        revoked_at=row["revoked_at"],
        expires_at=row["expires_at"],
        note=row["note"],
        masked_token=mask_token(token),
        is_master=bool(master_token) and token == master_token,
    )


def get_active_tokens() -> List[Dict[str, str]]:
    """Return the raw active token rows (internal use only)."""
    conn = _get_connection()
    cur = conn.cursor()
    cur.execute("SELECT token, label, role, created_at FROM api_tokens WHERE is_revoked = 0")
    rows = [dict(r) for r in cur.fetchall()]
    conn.close()
    return rows


def list_tokens() -> List[TokenInfo]:
    """Return token metadata with masked secrets."""
    master = get_master_token()
    conn = _get_connection()
    cur = conn.cursor()
    cur.execute("SELECT * FROM api_tokens ORDER BY created_at DESC")
    rows = cur.fetchall()
    conn.close()
    return [_row_to_info(row, master) for row in rows]


def get_token(token_id: str) -> Optional[TokenInfo]:
    """Return one token's metadata by its public id."""
    if not token_id:
        return None
    master = get_master_token()
    conn = _get_connection()
    cur = conn.cursor()
    cur.execute("SELECT * FROM api_tokens WHERE token_id = ?", (token_id,))
    row = cur.fetchone()
    conn.close()
    if row is None:
        return None
    return _row_to_info(row, master)


def get_master_token() -> str:
    if _TOKEN_FILE.exists():
        try:
            value = _TOKEN_FILE.read_text().strip()
            if value:
                return value
        except OSError:
            pass
    tokens = get_active_tokens()
    if tokens:
        return tokens[0]["token"]
    return ""


def verify_token(token: str) -> bool:
    """Check if token is valid, active and not expired."""
    if not token:
        return False
    clean_token = token.replace("Bearer ", "").strip()
    if not clean_token:
        return False
    conn = _get_connection()
    cur = conn.cursor()
    cur.execute(
        "SELECT expires_at FROM api_tokens WHERE token = ? AND is_revoked = 0", (clean_token,)
    )
    row = cur.fetchone()
    conn.close()
    if row is None:
        return False
    expires_at = row["expires_at"]
    if expires_at and expires_at <= time.time():
        return False
    return True


def touch_token(token: str) -> None:
    """Record a successful authentication (last use + usage counter)."""
    if not token:
        return
    clean_token = token.replace("Bearer ", "").strip()
    if not clean_token:
        return
    try:
        conn = _get_connection()
        cur = conn.cursor()
        cur.execute(
            """
            UPDATE api_tokens
               SET last_used_at = ?, use_count = COALESCE(use_count, 0) + 1
             WHERE token = ?
            """,
            (time.time(), clean_token),
        )
        conn.commit()
        conn.close()
    except sqlite3.Error:
        pass


def create_new_token(
    label: str,
    role: str = ROLE_USER,
    expires_in: Optional[float] = None,
    note: Optional[str] = None,
) -> str:
    """Create a new bot API token and return the raw secret."""
    token = f"talos_sk_{secrets.token_hex(24)}"
    now = time.time()
    expires_at = (now + float(expires_in)) if expires_in else None
    conn = _get_connection()
    cur = conn.cursor()
    cur.execute("""
    INSERT INTO api_tokens (
        token, token_id, label, role, created_at, use_count, is_revoked, expires_at, note
    ) VALUES (?, ?, ?, ?, ?, 0, 0, ?, ?)
    """, (
        token,
        uuid.uuid4().hex[:16],
        label,
        role or ROLE_USER,
        now,
        expires_at,
        note,
    ))
    conn.commit()
    conn.close()
    return token


def revoke_token(token_id: str) -> bool:
    """Revoke a token by its public id. Returns True when a token was revoked."""
    if not token_id:
        return False
    master = get_master_token()
    conn = _get_connection()
    cur = conn.cursor()
    cur.execute("SELECT token FROM api_tokens WHERE token_id = ?", (token_id,))
    row = cur.fetchone()
    if row is None:
        conn.close()
        return False
    if master and row["token"] == master:
        # The master token is the last resort credential; it may not be revoked.
        conn.close()
        return False
    cur.execute(
        "UPDATE api_tokens SET is_revoked = 1, revoked_at = ? WHERE token_id = ?",
        (time.time(), token_id),
    )
    conn.commit()
    conn.close()
    return True


def delete_token(token_id: str) -> bool:
    """Permanently delete a token row by its public id."""
    if not token_id:
        return False
    master = get_master_token()
    conn = _get_connection()
    cur = conn.cursor()
    cur.execute("SELECT token FROM api_tokens WHERE token_id = ?", (token_id,))
    row = cur.fetchone()
    if row is None:
        conn.close()
        return False
    if master and row["token"] == master:
        conn.close()
        return False
    cur.execute("DELETE FROM api_tokens WHERE token_id = ?", (token_id,))
    conn.commit()
    conn.close()
    return True


def token_summary() -> TokenSummary:
    """Aggregate usage across every token."""
    now = time.time()
    summary = TokenSummary()
    master = get_master_token()
    conn = _get_connection()
    cur = conn.cursor()
    cur.execute("SELECT * FROM api_tokens")
    rows = cur.fetchall()
    conn.close()
    for row in rows:
        summary.total += 1
        role = row["role"] or ROLE_USER
        summary.by_role[role] = summary.by_role.get(role, 0) + 1
        summary.requests_total += int(row["use_count"] or 0)
        if row["last_used_at"]:
            if summary.last_used_at is None or row["last_used_at"] > summary.last_used_at:
                summary.last_used_at = row["last_used_at"]
        if row["is_revoked"]:
            summary.revoked += 1
        elif row["expires_at"] and row["expires_at"] <= now:
            summary.expired += 1
        else:
            summary.active += 1
    return summary
