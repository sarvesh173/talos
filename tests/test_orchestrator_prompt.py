"""`_build_prompt` decides what the model actually reads.

Attachments were stored, returned to clients and forked, but never put in front
of the agent: an image or a voice note was a row nobody read. These tests pin the
prompt down so that cannot silently regress.

Run: pytest tests/test_orchestrator_prompt.py -q
"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from core.models import Message, Role
from server.agent_orchestrator import AgentOrchestrator


def _prompt(**kwargs) -> str:
    orch = AgentOrchestrator.__new__(AgentOrchestrator)
    msg = Message(
        room_id="the-council",
        sender_id="sarvesh",
        sender_name="Sarvesh",
        sender_role=Role.human,
        **kwargs,
    )
    return orch._build_prompt(msg)


class BuildPromptTest(unittest.TestCase):
    def test_plain_content_passes_through(self):
        self.assertEqual(_prompt(content="hello"), "hello")

    def test_blank_content_is_not_empty_string(self):
        # An empty prompt would still be handed to the model; the fallback says so.
        self.assertEqual(_prompt(content="   "), "(empty message)")

    def test_thought_is_labelled(self):
        prompt = _prompt(content="do it", thought="in a hurry")
        self.assertIn("do it", prompt)
        self.assertIn("(sender note: in a hurry)", prompt)

    def test_attachment_urls_reach_the_prompt(self):
        prompt = _prompt(
            content="what is in this picture?",
            attachments=["http://192.168.31.209:8000/uploads/abc123.jpg"],
        )
        self.assertIn("http://192.168.31.209:8000/uploads/abc123.jpg", prompt)
        self.assertIn("what is in this picture?", prompt)

    def test_multiple_attachments_are_all_listed(self):
        urls = [f"http://host/uploads/{i}.jpg" for i in range(3)]
        prompt = _prompt(content="compare these", attachments=urls)
        for url in urls:
            self.assertIn(url, prompt)

    def test_no_attachments_section_when_empty(self):
        # The default is an empty list; the prompt must not grow a stray header.
        self.assertNotIn("Attachments", _prompt(content="hi", attachments=[]))


if __name__ == "__main__":
    unittest.main()