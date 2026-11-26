#!/usr/bin/env python3
"""Tests for tools/ui_nodes.py tap-point: the CDP result shapes that tools/device.sh tap sends.

Run: python3 -m unittest discover -s tools -p 'ui_nodes_test.py'
"""

import json
import subprocess
import sys
import unittest
import xml.etree.ElementTree as ET
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


NATIVE_DUMP = (
    '<hierarchy rotation="0">'
    '<node class="android.widget.FrameLayout" text="" content-desc="" clickable="false" bounds="[0,0][1080,2400]">'
    '<node class="android.webkit.WebView" text="" content-desc="" clickable="false" bounds="[0,100][1080,2000]">'
    '<node class="android.view.View" text="Page link" content-desc="" clickable="true" bounds="[0,300][500,400]" />'
    '</node>'
    '<node class="android.view.View" text="Home" content-desc="" clickable="true" bounds="[0,2100][300,2300]" />'
    '<node class="android.view.View" text="" content-desc="" clickable="true" checkable="true" checked="true" bounds="[0,0][50,50]" />'
    '</node>'
    '</hierarchy>'
)


class NativeNodesTest(unittest.TestCase):
    def test_full_list_keeps_the_page_content(self):
        texts = [text for text, _desc, _bounds, _checked in ui_nodes.nodes(ET.fromstring(NATIVE_DUMP))]
        self.assertEqual(texts, ["Page link", "Home", ""])

    def test_native_list_drops_the_nodes_inside_the_webview(self):
        listed = list(ui_nodes.native_nodes(ET.fromstring(NATIVE_DUMP)))
        self.assertEqual([text for text, *_ in listed], ["Home", ""])
        self.assertEqual(listed[1][3], "true")

    def test_cli_list_native_flag(self):
        done = subprocess.run(
            [sys.executable, str(SCRIPT), "list", "--native"], input=NATIVE_DUMP, capture_output=True, text=True,
        )
        self.assertEqual(done.returncode, 0)
        self.assertEqual(
            done.stdout.splitlines(),
            ["Home |  | [0,2100][300,2300]", " |  | [0,0][50,50] | checked=true"],
        )


DUMPSYS_SAMPLE = SCRIPT.parent / "testdata" / "dumpsys_activity_component.txt"


class WebviewFromDumpsysTest(unittest.TestCase):
    """The sample is `dumpsys activity <component>` captured on a phone, where uiautomator reported
    the same WebView as [0,128][1220,2486]."""

    def test_sums_the_parent_offsets_and_skips_the_gone_webview(self):
        self.assertEqual(
            ui_nodes.webview_from_dumpsys(DUMPSYS_SAMPLE.read_text()), (0, 128, 1220, 2486)
        )

    def test_a_webview_under_a_hidden_ancestor_does_not_count(self):
        text = (
            "    View Hierarchy:\n"
            "      a.Decor{1 V.E...... ........ 0,0-100,200}\n"
            "        a.Holder{2 I.E...... ........ 0,50-100,200}\n"
            "          a.NestedWebView{3 V.E...... ........ 0,0-100,150}\n"
        )
        self.assertIsNone(ui_nodes.webview_from_dumpsys(text))

    def test_nested_offsets_add_up_and_the_largest_visible_one_wins(self):
        text = (
            "    View Hierarchy:\n"
            "      a.Decor{1 V.E...... ........ 0,0-100,200}\n"
            "        a.Holder{2 V.E...... ........ 10,20-100,200}\n"
            "          a.Layout{3 V.E...... ........ 5,7-90,180}\n"
            "            android.webkit.WebView{4 V.E...... ........ 1,2-31,52}\n"
            "          a.Layout{5 V.E...... ........ 0,100-90,180}\n"
            "            a.NestedWebView{6 V.E...... ........ 0,0-80,60}\n"
            "    Looper (main)\n"
            "      a.NestedWebView{7 V.E...... ........ 0,0-999,999}\n"
        )
        self.assertEqual(ui_nodes.webview_from_dumpsys(text), (10, 120, 90, 180))

    def test_no_hierarchy_means_none(self):
        self.assertIsNone(ui_nodes.webview_from_dumpsys("TASK 1:x id=1\n"))

    def test_cli_prints_the_bounds_and_exits_1_without_a_webview(self):
        done = subprocess.run(
            [sys.executable, str(SCRIPT), "webview-from-dumpsys"],
            input=DUMPSYS_SAMPLE.read_text(), capture_output=True, text=True,
        )
        self.assertEqual((done.returncode, done.stdout.strip()), (0, "0 128 1220 2486"))
        done = subprocess.run(
            [sys.executable, str(SCRIPT), "webview-from-dumpsys"],
            input="nothing here", capture_output=True, text=True,
        )
        self.assertEqual(done.returncode, 1)


if __name__ == "__main__":
    unittest.main()
