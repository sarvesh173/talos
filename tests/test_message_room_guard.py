"""POST /api/messages to a room that does not exist must be refused.

Before the guard, any authenticated sender could invent a room id and the
message was saved: it sat in the database with no room listing it, invisible to
every list yet returned by a direct GET. That is an orphan row — reachable data
no UI can account for, which is exactly the kind of silent fabrication-adjacent
state this project refuses to create.

The ambient "the-council" room is exempt: the orchestrator answers there and it
is created lazily on first use, so blocking it would break every chat.
"""

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))



from server import db


class _IsolatedDatabase:
    """db pointed at a temp file, including its cached process-wide handle."""

    def __init__(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = Path(self.tmp.name) / "talos.db"
        self._orig_path = None
        self._orig_conn = None

    def __enter__(self):
        self._orig_path = db._DB_PATH
        self._orig_conn = db._CONN
        db._DB_PATH = self.path
        db._CONN = None
        db.init_db()
        return self.path

    def __exit__(self, *exc):
        if db._CONN is not None:
            try:
                db._CONN.close()
            except Exception:
                pass
        db._CONN = self._orig_conn
        db._DB_PATH = self._orig_path
        self.tmp.cleanup()
        return False


def _client():
    from fastapi.testclient import TestClient

    import server.main as m

    return TestClient(m.app, raise_server_exceptions=False)


class GhostRoomTest(unittest.TestCase):
    def test_message_to_a_ghost_room_is_a_404(self):
        with _IsolatedDatabase():
            from server.auth import create_new_token, init_auth_db

            init_auth_db()
            tok = create_new_token("probe", role="admin")
            hdr = {"Authorization": f"Bearer {tok}"}
            r = _client().post(
                "/api/messages",
                json={
                    "room_id": "ghost-room-xyz",
                    "content": "orphan?",
                    "sender_id": "s",
                    "sender_name": "S",
                },
                headers=hdr,
            )
            self.assertEqual(r.status_code, 404)
            self.assertIn("no such room", r.json()["detail"])

    def test_message_to_a_real_room_still_saves(self):
        from core.models import Role, Room

        with _IsolatedDatabase():
            from server.auth import create_new_token, init_auth_db

            init_auth_db()
            tok = create_new_token("probe", role="admin")
            hdr = {"Authorization": f"Bearer {tok}"}
            db.create_room(room_id="r1", name="Real")
            r = _client().post(
                "/api/messages",
                json={
                    "room_id": "r1",
                    "content": "hello",
                    "sender_id": "s",
                    "sender_name": "S",
                },
                headers=hdr,
            )
            self.assertEqual(r.status_code, 200)
            self.assertEqual(len(db.get_room_messages("r1")), 1)

    def test_the_council_stays_lazy(self):
        """The ambient room must not start 404ing every chat."""
        with _IsolatedDatabase():
            from server.auth import create_new_token, init_auth_db

            init_auth_db()
            tok = create_new_token("probe", role="admin")
            hdr = {"Authorization": f"Bearer {tok}"}
            r = _client().post(
                "/api/messages",
                json={
                    "room_id": "the-council",
                    "content": "ambient",
                    "sender_id": "s",
                    "sender_name": "S",
                },
                headers=hdr,
            )
            self.assertEqual(r.status_code, 200)


if __name__ == "__main__":
    unittest.main()
