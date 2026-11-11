"""Local snapshot / restore / prune, and the routes that expose them.

These tests build a real SQLite database, snapshot it, and restore it. Nothing
is stubbed: the point of a backup feature is that the bytes come back, so a
mock would test the mock. A restore without confirmation must refuse, and a
fresh database must never be replaced without a copy of the old one.
"""

import json
import sqlite3
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server.backup_service import (
    ConfirmationRequired,
    DatabaseNotFound,
    SnapshotNotFound,
    create_snapshot,
    list_snapshots,
    prune_snapshots,
    restore_snapshot,
)


def _make_db(path: Path, message_count: int = 3) -> Path:
    """A minimal database with the two tables this feature touches."""
    conn = sqlite3.connect(str(path))
    conn.execute(
        "CREATE TABLE rooms (id TEXT PRIMARY KEY, name TEXT)"
    )
    conn.execute(
        "CREATE TABLE messages (id TEXT PRIMARY KEY, room_id TEXT, content TEXT)"
    )
    conn.execute("INSERT INTO rooms VALUES ('the-council', 'The Council')")
    for i in range(message_count):
        conn.execute(
            "INSERT INTO messages VALUES (?, 'the-council', ?)",
            (f"m{i}", f"message {i}"),
        )
    conn.commit()
    conn.close()
    return path


def _read_messages(db_path: Path):
    conn = sqlite3.connect(str(db_path))
    rows = conn.execute("SELECT id, content FROM messages ORDER BY id").fetchall()
    conn.close()
    return rows


class CreateSnapshotTest(unittest.TestCase):
    def test_missing_database_is_named_not_silently_empty(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(DatabaseNotFound):
                create_snapshot(
                    Path(tmp) / "nope.db", dest_dir=Path(tmp) / "backups"
                )

    def test_snapshot_writes_archive_with_manifest_and_row_counts(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = _make_db(tmp / "talos.db")
            dest = tmp / "backups"

            result = create_snapshot(db, dest_dir=dest, vault_path=tmp / "absent.json")

            archive = Path(result["archive"])
            self.assertTrue(archive.is_file())
            self.assertEqual(archive.suffixes[-2:], [".tar", ".gz"])

            with tarfile.open(archive, "r:gz") as tar:
                names = tar.getnames()
                self.assertIn("manifest.json", names)
                self.assertIn("talos.db", names)
                self.assertIn("messages.jsonl", names)

            self.assertEqual(result["row_counts"]["messages"], 3)
            self.assertEqual(result["row_counts"]["rooms"], 1)
            # The manifest travels inside the archive, so read it from there.
            with tarfile.open(archive, "r:gz") as tar:
                manifest = json.loads(
                    tar.extractfile("manifest.json").read().decode()
                )
            self.assertEqual(manifest["format_version"], 1)
            self.assertEqual(manifest["row_counts"]["messages"], 3)

    def test_message_archive_is_one_json_object_per_line(self):

        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = _make_db(tmp / "talos.db", message_count=4)
            result = create_snapshot(db, dest_dir=tmp / "backups", vault_path=tmp / "none.json")
            with tarfile.open(result["archive"], "r:gz") as tar:
                handle = tar.extractfile("messages.jsonl")
                lines = [ln for ln in handle.read().decode().splitlines() if ln.strip()]
            self.assertEqual(len(lines), 4)
            for line in lines:
                parsed = json.loads(line)
                self.assertIn("content", parsed)

    def test_vault_is_included_when_present(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = _make_db(tmp / "talos.db")
            vault = tmp / "providers.json"
            vault.write_text('{"providers": []}')

            result = create_snapshot(db, dest_dir=tmp / "backups", vault_path=vault)
            with tarfile.open(result["archive"], "r:gz") as tar:
                self.assertIn("vault/providers.json", tar.getnames())
            self.assertTrue(result["vault_included"])


class ListSnapshotTest(unittest.TestCase):
    def test_empty_directory_lists_nothing(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(list_snapshots(Path(tmp)), [])

    def test_newest_first(self):
        import time

        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = _make_db(tmp / "talos.db")
            dest = tmp / "backups"
            first = create_snapshot(db, dest_dir=dest, vault_path=tmp / "no.json")
            time.sleep(1.1)  # archive names carry a second-granularity stamp
            second = create_snapshot(db, dest_dir=dest, vault_path=tmp / "no.json")

            listed = list_snapshots(dest)
            self.assertEqual(len(listed), 2)
            self.assertEqual(Path(listed[0]["archive"]).name, Path(second["archive"]).name)
            self.assertEqual(Path(listed[1]["archive"]).name, Path(first["archive"]).name)


class RestoreSnapshotTest(unittest.TestCase):
    def test_restore_without_confirmation_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = _make_db(tmp / "talos.db")
            snap = create_snapshot(db, dest_dir=tmp / "backups", vault_path=tmp / "no.json")

            with self.assertRaises(ConfirmationRequired):
                restore_snapshot(Path(snap["archive"]), db)
            # The live database must be untouched by a refused restore.
            self.assertEqual(len(_read_messages(db)), 3)

    def test_missing_archive_is_a_real_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = _make_db(tmp / "talos.db")
            with self.assertRaises(SnapshotNotFound):
                restore_snapshot(tmp / "ghost.tar.gz", db, confirm=True)

    def test_confirmed_restore_brings_data_back_and_keeps_the_old_copy(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = _make_db(tmp / "talos.db", message_count=3)
            snap = create_snapshot(db, dest_dir=tmp / "backups", vault_path=tmp / "no.json")

            # Destroy data the snapshot still holds.
            conn = sqlite3.connect(str(db))
            conn.execute("DELETE FROM messages")
            conn.commit()
            conn.close()
            self.assertEqual(len(_read_messages(db)), 0)

            result = restore_snapshot(Path(snap["archive"]), db, confirm=True)

            self.assertEqual(len(_read_messages(db)), 3)
            pre_restore = Path(result["pre_restore_copy"])
            self.assertTrue(pre_restore.is_file(), "old database must be kept aside")
            self.assertEqual(len(_read_messages(pre_restore)), 0)


class PruneTest(unittest.TestCase):
    def test_prune_keeps_the_newest_n(self):
        import time

        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = _make_db(tmp / "talos.db")
            dest = tmp / "backups"
            made = []
            for _ in range(4):
                made.append(create_snapshot(db, dest_dir=dest, vault_path=tmp / "no.json"))
                time.sleep(1.1)

            deleted = prune_snapshots(dest, keep=2)

            self.assertEqual(len(deleted), 2)
            remaining = {Path(s["archive"]).name for s in list_snapshots(dest)}
            self.assertEqual(
                remaining,
                {Path(made[-1]["archive"]).name, Path(made[-2]["archive"]).name},
            )

    def test_prune_with_nothing_to_delete_returns_empty(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = _make_db(tmp / "talos.db")
            dest = tmp / "backups"
            create_snapshot(db, dest_dir=dest, vault_path=tmp / "no.json")
            self.assertEqual(prune_snapshots(dest, keep=5), [])


class BackupRoutesContractTest(unittest.TestCase):
    def test_create_accepts_an_empty_body(self):
        """Every CreateSnapshotRequest field is optional, so POST with no body
        must take the defaults instead of answering 422. Found live: the route
        demanded a body that carried nothing a caller had to supply."""
        import inspect

        from server.backup_routes import create_snapshot_endpoint

        signature = inspect.signature(create_snapshot_endpoint)
        self.assertIsNot(
            signature.parameters["req"].default,
            inspect.Parameter.empty,
            "create_snapshot_endpoint must default `req` so an empty POST works",
        )

    def test_prune_accepts_an_empty_body(self):
        import inspect

        from server.backup_routes import prune_snapshots_endpoint

        signature = inspect.signature(prune_snapshots_endpoint)
        self.assertIsNot(
            signature.parameters["req"].default,
            inspect.Parameter.empty,
            "prune_snapshots_endpoint must default `req` so an empty POST works",
        )


class BackupRoutesMountedTest(unittest.TestCase):
    def test_app_registers_all_four_backup_routes(self):
        """Guards the regression that started this: a written router nobody mounted.

        Included routers appear as `_IncludedRouter` entries in `app.routes` on
        this FastAPI version, so walking top-level `route.path` finds nothing
        even when the routes are live. Read the generated OpenAPI schema, which
        is what clients actually see.
        """
        from server.main import app

        paths = set(app.openapi()["paths"])
        expected = {
            "/api/backup/snapshots",
            "/api/backup/restore",
            "/api/backup/prune",
        }
        missing = expected - paths
        self.assertFalse(
            missing, f"backup routes not reachable by clients: {sorted(missing)}"
        )


if __name__ == "__main__":
    unittest.main()
