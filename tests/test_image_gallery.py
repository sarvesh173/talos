"""The gallery route reads the core's table instead of answering 404.

The screen paired with the API, but the gallery rows live in the core's SQLite
file, so every open answered 404. These tests pin the read path and the honest
empty/error behaviour.
"""

import sqlite3
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server import images


def _make_db(directory: str, rows):
    path = Path(directory) / "talos.db"
    conn = sqlite3.connect(path)
    conn.execute(
        "CREATE TABLE generated_images (id TEXT PRIMARY KEY, prompt TEXT NOT NULL,"
        " style TEXT NOT NULL, aspect_ratio TEXT NOT NULL, image_url TEXT NOT NULL,"
        " created_at REAL NOT NULL)"
    )
    for row in rows:
        conn.execute(
            "INSERT INTO generated_images VALUES (?,?,?,?,?,?)",
            (row["id"], row["prompt"], "cinematic", "16:9", "data:image/png;base64,AA", row["created_at"]),
        )
    conn.commit()
    conn.close()
    return path


class GalleryReadTest(unittest.TestCase):
    def test_rows_come_back_newest_first(self):
        with TemporaryDirectory() as d:
            _make_db(d, [
                {"id": "a", "prompt": "older", "created_at": 100.0},
                {"id": "b", "prompt": "newer", "created_at": 200.0},
            ])
            with patch.object(images, "gallery_db_path", return_value=Path(d) / "talos.db"):
                rows = images.list_images(10)
            self.assertEqual([r["id"] for r in rows], ["b", "a"])
            self.assertEqual(rows[0]["prompt"], "newer")

    def test_missing_file_is_an_empty_list_not_an_error(self):
        with patch.object(images, "gallery_db_path", return_value=Path("/nonexistent/talos.db")):
            self.assertEqual(images.list_images(), [])

    def test_limit_is_clamped(self):
        with TemporaryDirectory() as d:
            _make_db(d, [{"id": "a", "prompt": "p", "created_at": 1.0}])
            with patch.object(images, "gallery_db_path", return_value=Path(d) / "talos.db"):
                self.assertEqual(len(images.list_images(0)), 1)
                self.assertEqual(len(images.list_images(9999)), 1)

    def test_delete_reports_whether_a_row_went(self):
        with TemporaryDirectory() as d:
            _make_db(d, [{"id": "a", "prompt": "p", "created_at": 1.0}])
            with patch.object(images, "gallery_db_path", return_value=Path(d) / "talos.db"):
                self.assertTrue(images.delete_image("a"))
                self.assertFalse(images.delete_image("a"))
                self.assertEqual(images.list_images(), [])


if __name__ == "__main__":
    unittest.main()