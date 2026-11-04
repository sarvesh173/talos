"""CLI engine discovery tests.

The screen this feeds used to render two hardcoded engines with invented
delegates. These tests pin the honest contract: only binaries that exist on
this machine report available, and a delegate is grouped by the engine that can
actually drive it.
"""

import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server.cli_engines import (
    CLI_ENGINES,
    build_hierarchy,
    delegate_owner_engine,
    discover_engines,
)


class DiscoveryTest(unittest.TestCase):
    def test_every_catalog_entry_is_reported(self):
        """Unavailable engines stay visible so the operator can install them."""
        engines = discover_engines()
        self.assertEqual(len(engines), len(CLI_ENGINES))
        self.assertEqual(
            [e.id for e in engines], [spec.id for spec in CLI_ENGINES]
        )

    def test_availability_matches_binary_presence(self):
        """Nothing on PATH and no fallback file ⇒ every engine unavailable."""
        with patch("server.cli_engines.shutil.which", return_value=None), patch(
            "server.cli_engines.os.path.isfile", return_value=False
        ):
            engines = discover_engines()
        self.assertTrue(all(not e.is_available for e in engines))

    def test_fallback_path_is_used_when_not_on_path(self):
        """A binary installed outside PATH is still found."""
        with patch("server.cli_engines.shutil.which", return_value=None), patch(
            "server.cli_engines.os.path.isfile", return_value=True
        ), patch("server.cli_engines.os.access", return_value=True):
            engines = discover_engines()
        self.assertTrue(all(e.is_available for e in engines))

    def test_resolved_path_is_reported_when_found(self):
        with patch("server.cli_engines.shutil.which", return_value="/usr/bin/opencode"):
            engines = discover_engines()
        opencode = next(e for e in engines if e.id == "opencode")
        self.assertTrue(opencode.is_available)
        self.assertEqual(opencode.resolved_path, "/usr/bin/opencode")

    def test_opencode_is_in_the_catalog(self):
        """The user asked for OpenCode specifically; it must not vanish."""
        self.assertIn("opencode", [spec.id for spec in CLI_ENGINES])


class OwnershipTest(unittest.TestCase):
    def test_explicit_engine_wins(self):
        delegate = {"engine": "opencode", "model": "deepseek/v4"}
        self.assertEqual(delegate_owner_engine(delegate), "opencode")

    def test_free_models_belong_to_opencode(self):
        self.assertEqual(
            delegate_owner_engine({"model": "glm-5.3-flash-free"}), "opencode"
        )

    def test_vendor_models_default_to_hermes(self):
        self.assertEqual(
            delegate_owner_engine({"model": "deepseek/deepseek-v4-pro"}), "hermes"
        )
        self.assertEqual(delegate_owner_engine({}), "hermes")

    def test_claude_and_gpt_models_route_to_their_engines(self):
        self.assertEqual(delegate_owner_engine({"model": "claude-sonnet-4.5"}), "claude-code")
        self.assertEqual(delegate_owner_engine({"model": "gpt-5.5"}), "codex")


class HierarchyTest(unittest.TestCase):
    def test_empty_registry_yields_no_fabricated_delegates(self):
        tree = build_hierarchy([])
        self.assertEqual(tree["total_delegates"], 0)
        self.assertEqual(tree["total_active_children"], 0)
        for engine in tree["engines"]:
            self.assertEqual(engine["delegates"], [])

    def test_delegate_lands_under_its_engine(self):
        delegates = [
            {
                "id": "d1",
                "name": "Kotlin Auditor",
                "model": "deepseek/deepseek-v4-pro",
                "reasoning_effort": "high",
                "command_limit": 40,
                "turns_used": 12,
                "status": "running",
                "children": [
                    {
                        "id": "c1",
                        "name": "Leak Scanner",
                        "task": "find retained contexts",
                        "status": "running",
                        "turns_used": 3,
                    }
                ],
            }
        ]
        tree = build_hierarchy(delegates)
        hermes = next(e for e in tree["engines"] if e["id"] == "hermes")
        self.assertEqual(len(hermes["delegates"]), 1)
        entry = hermes["delegates"][0]
        self.assertEqual(entry["name"], "Kotlin Auditor")
        self.assertEqual(entry["reasoning_effort"], "High")
        self.assertEqual(entry["turns_consumed"], 12)
        self.assertEqual(entry["command_turn_limit"], 40)
        self.assertEqual(len(entry["children"]), 1)
        self.assertEqual(tree["total_active_children"], 1)

    def test_unknown_engine_is_surfaced_not_dropped(self):
        tree = build_hierarchy([{"id": "d2", "name": "Ghost", "engine": "gone-cli"}])
        ghost = next(e for e in tree["engines"] if e["id"] == "gone-cli")
        self.assertFalse(ghost["is_available"])
        self.assertEqual(len(ghost["delegates"]), 1)

    def test_paused_delegate_is_flagged(self):
        tree = build_hierarchy([{"id": "d3", "name": "P", "status": "paused"}])
        entry = next(
            d for e in tree["engines"] for d in e["delegates"] if d["id"] == "d3"
        )
        self.assertTrue(entry["is_paused"])


if __name__ == "__main__":
    unittest.main()
