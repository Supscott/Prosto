#!/usr/bin/env python3
"""Self-tests for the hardcoded UI string rule in check_invariants.py."""

import tempfile
import unittest
from pathlib import Path
from unittest import mock

import check_invariants


class HardcodedUiStringsTest(unittest.TestCase):
    def run_rule_on(self, lines):
        """Runs the rule over one Kotlin file holding the given lines; returns its errors."""
        with tempfile.TemporaryDirectory() as tmp:
            main_dir = Path(tmp) / "app" / "src" / "main"
            kt_dir = main_dir / "java" / "com" / "example"
            kt_dir.mkdir(parents=True)
            (kt_dir / "Sample.kt").write_text("\n".join(lines) + "\n", encoding="utf-8")
            with mock.patch.object(check_invariants, "MAIN_DIR", main_dir), \
                    mock.patch.object(check_invariants, "ROOT", Path(tmp)):
                return check_invariants.check_no_hardcoded_ui_strings()

    def test_typographic_separator_is_allowed(self):
        self.assertEqual(self.run_rule_on(['Text(text = " · ")']), [])

    def test_symbol_only_literal_is_allowed(self):
        self.assertEqual(self.run_rule_on(['Text(text = "→ 2/3")']), [])

    def test_latin_letters_are_rejected(self):
        errors = self.run_rule_on(['Text(text = "Hello")'])
        self.assertEqual(len(errors), 1)
        self.assertIn("'Hello'", errors[0])

    def test_cyrillic_letters_are_rejected(self):
        errors = self.run_rule_on(['Text(text = " · Привет")'])
        self.assertEqual(len(errors), 1)


if __name__ == "__main__":
    unittest.main()
