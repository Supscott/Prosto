#!/usr/bin/env python3
"""Tests for tools/ui_nodes.py tap-point: the CDP result shapes that tools/device.sh tap sends.

Run: python3 -m unittest discover -s tools -p 'ui_nodes_test.py'
"""

import json
import subprocess
import sys
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "ui_nodes.py"
sys.path.insert(0, str(SCRIPT.parent))

import ui_nodes  # noqa: E402

WEBVIEW = (0, 100, 1080, 2000)


def cdp(value):
    """The string cdp_eval.mjs prints for a page expression that returned `value`."""
    return json.dumps(json.dumps(value))


class TapPointTest(unittest.TestCase):
    def test_five_values_tap_the_centre_and_skip_nothing(self):
        x, y, skipped = ui_nodes.tap_point(WEBVIEW, cdp([100, 200, 400, 800, 2]))
        self.assertEqual((x, y, skipped), (200, 500, 0))

    def test_sixth_value_reports_the_skipped_matches(self):
        x, y, skipped = ui_nodes.tap_point(WEBVIEW, cdp([100, 200, 400, 800, 2, 3]))
        self.assertEqual((x, y, skipped), (200, 500, 3))

    def test_no_match_says_so(self):
        with self.assertRaisesRegex(ValueError, "no element matches"):
            ui_nodes.tap_point(WEBVIEW, cdp(None))

    def test_matches_but_none_visible_names_the_count_and_selector(self):
        result = cdp({"total": 2, "selector": ".buy"})
        with self.assertRaisesRegex(ValueError, r"^tap: 2 match\(es\) for \.buy, none visible$"):
            ui_nodes.tap_point(WEBVIEW, result)

    def test_centre_outside_the_webview_is_refused(self):
        with self.assertRaisesRegex(ValueError, "outside the WebView"):
            ui_nodes.tap_point(WEBVIEW, cdp([100, 2000, 400, 2200, 2, 0]))


class TapPointCliTest(unittest.TestCase):
    def run_tap_point(self, value):
        return subprocess.run(
            [sys.executable, str(SCRIPT), "tap-point", "0 100 1080 2000", cdp(value)],
            capture_output=True,
            text=True,
        )

    def test_prints_the_device_point_and_the_skip_message(self):
        done = self.run_tap_point([100, 200, 400, 800, 2, 1])
        self.assertEqual(done.returncode, 0)
        self.assertEqual(done.stdout.strip(), "200 500")
        self.assertEqual(done.stderr.strip(), "tap: skipped 1 hidden match(es)")

    def test_prints_nothing_extra_when_nothing_was_skipped(self):
        done = self.run_tap_point([100, 200, 400, 800, 2])
        self.assertEqual(done.returncode, 0)
        self.assertEqual(done.stderr, "")

    def test_none_visible_exits_1_with_the_message(self):
        done = self.run_tap_point({"total": 1, "selector": "#x"})
        self.assertEqual(done.returncode, 1)
        self.assertEqual(done.stderr.strip(), "tap: 1 match(es) for #x, none visible")


if __name__ == "__main__":
    unittest.main()
