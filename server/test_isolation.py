"""Pytest plugin that keeps the test suite out of the live TALOS database.

The suite used to run against ``server/data/talos.db`` — the same file the
always-on ``talos-api`` service has open. Every run inserted ``api_tokens``
and ``messages`` rows into production data (6 424 of the 6 828 token rows
were test artefacts), and the table grew without bound.

Run the suite isolated with::

    pytest -p server.test_isolation tests -q

Both stores are pointed at a throwaway copy of the live database, so the
tests still see the master token row and the schema they expect, but
nothing they write reaches the live file.

The copy is taken before ``server.db`` / ``server.auth`` are imported, so
the paths have to be set here rather than in a fixture.
"""

import os
import sqlite3
import tempfile
from pathlib import Path

_LIVE_DB = Path(__file__).resolve().parent / "data" / "talos.db"
_ISOLATED_DB = Path(tempfile.mkdtemp(prefix="talos-test-db-")) / "talos.db"

# Snapshot with the SQLite backup API rather than copying the file: the live
# database is in WAL mode, so a plain file copy would miss the frames still
# sitting in the -wal file. Several tests expect the master token row and
# the FTS tables to already exist, so the copy must be complete.
if _LIVE_DB.exists():
    src = sqlite3.connect(f"file:{_LIVE_DB}?mode=ro", uri=True)
    dst = sqlite3.connect(str(_ISOLATED_DB))
    src.backup(dst)
    dst.close()
    src.close()
else:  # fresh checkout: let init_db()/init_auth_db() create the schema
    _ISOLATED_DB.touch()

os.environ["TALOS_DB_PATH"] = str(_ISOLATED_DB)
os.environ["TALOS_AUTH_DB_PATH"] = str(_ISOLATED_DB)
