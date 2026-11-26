#!/usr/bin/env python3
"""Tests for tools/view_bounds.py (PRO-169): bounds of a visible view, sample by sample.

Run: python3 -m unittest discover -s tools -p 'view_bounds_test.py'
"""

import subprocess
import sys
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "view_bounds.py"
sys.path.insert(0, str(SCRIPT.parent))

import view_bounds  # noqa: E402


def dump(*views):
    """A minimal `dumpsys activity` hierarchy; each view is one line body (indent is added)."""
    lines = ["    View Hierarchy:", "      a.Decor{1 V.E...... ........ 0,0-1220,2712}"]
    lines += ["        " + view for view in views]
    lines.append("    Looper (main, tid 2) {e261e68}")
    return "\n".join(lines) + "\n"


WEB = "com.jestk.browser.engine.NestedWebView{2 V.E...... ........ 0,128-1220,2486}"
HIDDEN = "com.jestk.browser.engine.NestedWebView{3 I.E...... ........ 0,128-1220,2486}"


class VisibleBoundsTest(unittest.TestCase):
    def test_the_visible_view_of_the_class_is_found_by_simple_name(self):
        self.assertEqual(view_bounds.visible_bounds(dump(WEB), "NestedWebView"), (0, 128, 1220, 2486))

    def test_a_hidden_view_of_the_class_does_not_count(self):
        self.assertIsNone(view_bounds.visible_bounds(dump(HIDDEN), "NestedWebView"))


class SamplesCliTest(unittest.TestCase):
    def test_one_line_per_sample_with_ms_since_the_first(self):
        stream = f"@@ 100.00\n{dump(WEB)}@@ 100.25\n{dump(HIDDEN)}"
        done = subprocess.run(
            [sys.executable, str(SCRIPT), "NestedWebView"], input=stream, capture_output=True, text=True,
        )
        self.assertEqual(done.stdout.splitlines(), ["0 0,128,1220,2486", "250 none"])

    def test_no_sample_is_an_error(self):
        done = subprocess.run([sys.executable, str(SCRIPT), "NestedWebView"], input="", capture_output=True, text=True)
        self.assertEqual(done.returncode, 1)


if __name__ == "__main__":
    unittest.main()
