"""Custom engine manifests (`~/.talos/agents/*.json`).

FEATURE.md §1 promises a custom manifest loader and the catalog only ever
shipped the built-in tuple, so a registered agent was invisible while the docs
claimed otherwise. These tests pin the real behaviour: a valid manifest becomes
a discoverable engine, and a broken one is reported by name instead of being
silently ignored (which would read as "not installed").
"""

import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from server.cli_engines import (
    CLI_ENGINES,
    ManifestError,
    build_hierarchy,
    discover_engines,
    load_custom_engines,
    manifest_errors,
)


def _write(directory, name, payload):
    path = os.path.join(directory, name)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle)
    return path


class ManifestLoadTest(unittest.TestCase):
    def test_missing_directory_yields_no_engines_and_no_errors(self):
        missing = os.path.join(tempfile.gettempdir(), "talos-manifests-absent-xyz")
        self.assertEqual(load_custom_engines(missing), [])
        self.assertEqual(manifest_errors(missing), [])

    def test_binary_on_path_becomes_available_engine(self):
        with tempfile.TemporaryDirectory() as tmp:
            _write(tmp, "vim.json", {"id": "vimesque", "binary": "sh", "name": "Shell"})
            specs = load_custom_engines(tmp)
            self.assertEqual(len(specs), 1)
            self.assertEqual(specs[0].id, "vimesque")

            engines = discover_engines(tmp)
            custom = [e for e in engines if e.id == "vimesque"]
            self.assertEqual(len(custom), 1)
            # `sh` exists on PATH in any POSIX test environment.
            self.assertTrue(custom[0].is_available)
            self.assertEqual(custom[0].name, "Shell")
            self.assertEqual(manifest_errors(tmp), [])

    def test_absolute_path_manifest_is_resolved_without_path_lookup(self):
        with tempfile.TemporaryDirectory() as tmp:
            binary = os.path.join(tmp, "my-agent")
            Path(binary).write_text("#!/bin/sh\necho hi\n")
            os.chmod(binary, 0o755)

            _write(tmp, "mine.json", {"id": "mine", "path": binary, "name": "Mine"})
            engines = {e.id: e for e in discover_engines(tmp)}
            self.assertIn("mine", engines)
            self.assertTrue(engines["mine"].is_available)
            self.assertEqual(engines["mine"].resolved_path, binary)

    def test_absolute_path_to_missing_binary_is_unavailable_not_hidden(self):
        with tempfile.TemporaryDirectory() as tmp:
            _write(tmp, "ghost.json", {"id": "ghost", "path": os.path.join(tmp, "nope")})
            engines = {e.id: e for e in discover_engines(tmp)}
            self.assertIn("ghost", engines)
            self.assertFalse(engines["ghost"].is_available)
            self.assertIsNone(engines["ghost"].resolved_path)

    def test_disabled_manifest_is_skipped_without_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            _write(tmp, "off.json", {"id": "off", "binary": "sh", "enabled": False})
            self.assertEqual(load_custom_engines(tmp), [])
            self.assertEqual(manifest_errors(tmp), [])
            self.assertNotIn("off", {e.id for e in discover_engines(tmp)})


class ManifestValidationTest(unittest.TestCase):
    def test_missing_id_is_named_in_the_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            _write(tmp, "noid.json", {"binary": "sh"})
            errors = manifest_errors(tmp)
            self.assertEqual(len(errors), 1)
            self.assertIn("noid.json", errors[0])
            self.assertIn("id", errors[0])

    def test_missing_binary_and_path_is_an_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            _write(tmp, "empty.json", {"id": "empty"})
            with self.assertRaises(ManifestError):
                load_custom_engines(tmp)
            errors = manifest_errors(tmp)
            self.assertIn("binary", errors[0])
            self.assertIn("path", errors[0])

    def test_duplicate_of_builtin_id_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            _write(tmp, "dupe.json", {"id": CLI_ENGINES[0].id, "binary": "sh"})
            with self.assertRaises(ManifestError):
                load_custom_engines(tmp)
            self.assertIn("already a built-in", manifest_errors(tmp)[0])

    def test_invalid_json_is_reported_not_swallowed(self):
        with tempfile.TemporaryDirectory() as tmp:
            Path(os.path.join(tmp, "broken.json")).write_text("{ not json")
            with self.assertRaises(ManifestError):
                load_custom_engines(tmp)
            errors = manifest_errors(tmp)
            self.assertEqual(len(errors), 1)
            self.assertIn("broken.json", errors[0])

    def test_non_boolean_enabled_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            _write(tmp, "en.json", {"id": "en", "binary": "sh", "enabled": "yes"})
            with self.assertRaises(ManifestError):
                load_custom_engines(tmp)

    def test_broken_manifest_does_not_remove_builtin_engines(self):
        """A typo in one manifest must not empty the fleet."""
        with tempfile.TemporaryDirectory() as tmp:
            Path(os.path.join(tmp, "broken.json")).write_text("{ nope")
            engines = discover_engines(tmp)
            builtin_ids = {spec.id for spec in CLI_ENGINES}
            self.assertTrue(builtin_ids.issubset({e.id for e in engines}))


class HierarchyTest(unittest.TestCase):
    def test_hierarchy_reports_manifest_errors_and_engines(self):
        """build_hierarchy is what the UI reads; it must carry the error list."""
        payload = build_hierarchy([])
        self.assertIn("manifest_errors", payload)
        self.assertIsInstance(payload["manifest_errors"], list)
        self.assertEqual(payload["total_engines"], len(payload["engines"]))
        self.assertGreaterEqual(payload["total_engines"], len(CLI_ENGINES))


if __name__ == "__main__":
    unittest.main()
