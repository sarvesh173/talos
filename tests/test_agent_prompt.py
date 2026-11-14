"""The roster must carry the prompt an agent actually runs.

Every seeded agent had a NULL system_prompt, so the app could not show the
agent's instructions at all: the user asked to read the prompt and there was
nothing to read. The stored prompt wins; otherwise the persona the
orchestrator would really use is returned.
"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from fastapi.testclient import TestClient

import server.main as main
from core.models import AgentProfile
from server.auth import get_master_token
from server.db import init_db, upsert_agent


def _client():
    init_db()
    return TestClient(main.app)


def _auth():
    return {"Authorization": f"Bearer {get_master_token()}"}


class AgentPromptTest(unittest.TestCase):
    def test_missing_prompt_becomes_the_persona_the_agent_runs(self):
        upsert_agent(
            AgentProfile(
                id="test-no-prompt",
                name="Promptless",
                role_description="Answers in one line.",
                system_prompt=None,
            )
        )
        resp = _client().get("/api/agents", headers=_auth())
        self.assertEqual(resp.status_code, 200)
        agent = next(a for a in resp.json() if a["id"] == "test-no-prompt")
        self.assertTrue(agent["system_prompt"])
        self.assertIn("Promptless", agent["system_prompt"])
        self.assertIn("Answers in one line.", agent["system_prompt"])

    def test_a_stored_prompt_is_returned_unchanged(self):
        upsert_agent(
            AgentProfile(
                id="test-with-prompt",
                name="Custom",
                system_prompt="Exactly this, nothing else.",
            )
        )
        resp = _client().get("/api/agents", headers=_auth())
        agent = next(a for a in resp.json() if a["id"] == "test-with-prompt")
        self.assertEqual(agent["system_prompt"], "Exactly this, nothing else.")


if __name__ == "__main__":
    unittest.main()