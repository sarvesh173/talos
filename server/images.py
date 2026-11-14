"""Read the image gallery the core writes.

The Android gallery has always called ``/api/image/gallery``. That route lives
on the Rust core (port 8010) and never existed on the API (8000) the app pairs
with, so the screen could only ever answer 404. The core keeps the rows in its
own SQLite file, so the API reads that file directly instead of proxying: no new
configuration, no second copy of the data.
"""

from __future__ import annotations

import os
import sqlite3
from pathlib import Path
from typing import Any, Dict, List

_REPO_ROOT = Path(__file__).resolve().parent.parent
_DEFAULT_DB = _REPO_ROOT / "crates" / "talos-core" / "talos.db"


def gallery_db_path() -> Path:
    """Same default and same override the core uses (crates/talos-core)."""
    override = os.environ.get("TALOS_DB_PATH")
    return Path(override) if override else _DEFAULT_DB


def list_images(limit: int = 100) -> List[Dict[str, Any]]:
    path = gallery_db_path()
    if not path.exists():
        return []
    # Read-only: the core owns the writes, and a locked file must not become an
    # error the gallery shows as "couldn't read".
    uri = f"file:{path}?mode=ro"
    try:
        conn = sqlite3.connect(uri, uri=True, timeout=2.0)
    except sqlite3.Error:
        return []
    try:
        rows = conn.execute(
            "SELECT id, prompt, style, aspect_ratio, image_url, created_at "
            "FROM generated_images ORDER BY created_at DESC LIMIT ?",
            (max(1, min(int(limit), 500)),),
        ).fetchall()
    except sqlite3.Error:
        return []
    finally:
        conn.close()

    return [
        {
            "id": r[0],
            "prompt": r[1],
            "style": r[2],
            "aspect_ratio": r[3],
            "image_url": r[4],
            "created_at": r[5],
        }
        for r in rows
    ]


def delete_image(image_id: str) -> bool:
    path = gallery_db_path()
    if not path.exists() or not image_id:
        return False
    try:
        conn = sqlite3.connect(str(path), timeout=3.0)
        try:
            cur = conn.execute("DELETE FROM generated_images WHERE id = ?", (image_id,))
            conn.commit()
            return cur.rowcount > 0
        finally:
            conn.close()
    except sqlite3.Error:
        return False