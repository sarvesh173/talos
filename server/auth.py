"""Authentication & Bot Token Gate for TALOS.
Prevents unauthorized mesh or local network peers from communicating with agents.
"""

from pathlib import Path
import secrets
import sqlite3
import time
from typing import Dict, List, Optional

_HERMES_DIR = Path.home() / ".hermes"
_TOKEN_FILE = _HERMES_DIR / "talos_token.txt"
_DATA_DIR = Path(__file__).resolve().parent / "data"
_DB_PATH = _DATA_DIR / "talos.db"


def _get_connection() -> sqlite3.Connection:
    _DATA_DIR.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(_DB_PATH), check_same_thread=False)
    conn.row_factory = sqlite3.Row
    return conn


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

    cur.execute("SELECT COUNT(*) as cnt FROM api_tokens WHERE is_revoked = 0")
    if cur.fetchone()["cnt"] == 0:
        # Create initial Master Bot Token for Sarvesh
        master_token = f"talos_sk_{secrets.token_hex(24)}"
        cur.execute("""
        INSERT INTO api_tokens (token, label, role, created_at, is_revoked)
        VALUES (?, ?, ?, ?, 0)
        """, (master_token, "Sarvesh Master (Phone)", "admin", time.time()))
        conn.commit()

        # Write to secure local file
        try:
            _HERMES_DIR.mkdir(parents=True, exist_ok=True)
            _TOKEN_FILE.write_text(master_token.strip() + "\n")
            _TOKEN_FILE.chmod(0o600)
        except Exception:
            pass

    conn.close()


def get_active_tokens() -> List[Dict[str, str]]:
    conn = _get_connection()
    cur = conn.cursor()
    cur.execute("SELECT token, label, role, created_at FROM api_tokens WHERE is_revoked = 0")
    rows = [dict(r) for r in cur.fetchall()]
    conn.close()
    return rows


def get_master_token() -> str:
    if _TOKEN_FILE.exists():
        return _TOKEN_FILE.read_text().strip()
    tokens = get_active_tokens()
    if tokens:
        return tokens[0]["token"]
    return ""


def verify_token(token: str) -> bool:
    """Check if token is valid and active."""
    if not token:
        return False
    clean_token = token.replace("Bearer ", "").strip()
    conn = _get_connection()
    cur = conn.cursor()
    cur.execute("SELECT token FROM api_tokens WHERE token = ? AND is_revoked = 0", (clean_token,))
    row = cur.fetchone()
    conn.close()
    return row is not None


def create_new_token(label: str) -> str:
    """Create a new bot API token."""
    token = f"talos_sk_{secrets.token_hex(24)}"
    conn = _get_connection()
    cur = conn.cursor()
    cur.execute("""
    INSERT INTO api_tokens (token, label, role, created_at, is_revoked)
    VALUES (?, ?, 'user', ?, 0)
    """, (token, label, time.time()))
    conn.commit()
    conn.close()
    return token
