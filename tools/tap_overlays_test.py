#!/usr/bin/env python3
"""Tests for tools/tap_overlays.py (PRO-166): native overlays over the WebView's tap point.

The dumpsys sample is a real capture (no overlay); the other dumps are synthetic. A capture with the
dock and the "Group N tabs" pill is still needed (see the device check in the PRO-166 report).

Run: python3 -m unittest discover -s tools -p 'tap_overlays_test.py'
"""

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "tap_overlays.py"
sys.path.insert(0, str(SCRIPT.parent))

import tap_overlays  # noqa: E402

DUMPSYS_SAMPLE = SCRIPT.parent / "testdata" / "dumpsys_activity_component.txt"
WEBVIEW = (0, 128, 1220, 2486)  # the captured WebView, as uiautomator reported it


def dumpsys(*views):
    """A minimal `dumpsys activity` hierarchy; each view is one line body (indent is added)."""
    lines = ["    View Hierarchy:", "      a.Decor{1 V.E...... ........ 0,0-1220,2712}"]
    lines += ["        " + view for view in views]
    lines.append("    Looper (main, tid 2) {e261e68}")
    return "\n".join(lines) + "\n"


def uia(*body):
    """A `uiautomator dump` hierarchy around the given node elements."""
    return '<?xml version="1.0"?><hierarchy rotation="0">' + "".join(body) + "</hierarchy>"


WEBVIEW_NODE = '<node class="android.webkit.WebView" bounds="[0,128][1220,2486]" clickable="true"/>'


class DumpsysViewsTest(unittest.TestCase):
    def test_captured_sample_has_no_overlay_over_the_webview(self):
        text = DUMPSYS_SAMPLE.read_text()
        self.assertEqual(tap_overlays.dumpsys_overlays(text, WEBVIEW), [])
        self.assertEqual(tap_overlays.plan(WEBVIEW, 600, 1000, text, ""), "clear")

    def test_a_visible_view_after_the_webview_covers_its_box(self):
        text = dumpsys(
            "android.webkit.WebView{1 V.E...... ........ 0,0-1220,2358}",
            "android.widget.TextView{2 V.E...... ........ 300,2300-900,2400}",
        )
        webview = (0, 0, 1220, 2358)
        verdict = tap_overlays.plan(webview, 500, 2350, text, "")
        self.assertEqual(verdict.split("\t")[0], "covered")
        self.assertIn("android.widget.TextView", verdict)
        self.assertIn("bounds=[300,2300][900,2400]", verdict)

    def test_gone_containers_and_views_before_the_webview_do_not_cover(self):
        text = dumpsys(
            "android.widget.TextView{1 V.E...... ........ 0,2300-1220,2400}",  # drawn before the WebView
            "android.webkit.WebView{2 V.E...... ........ 0,0-1220,2358}",
            "android.widget.TextView{3 G.E...... ........ 0,2300-1220,2400}",  # gone
            "android.widget.FrameLayout{4 V.E...... ........ 0,0-1220,2400}",  # a container draws nothing
        )
        self.assertEqual(tap_overlays.plan((0, 0, 1220, 2358), 500, 2350, text, ""), "clear")

    def test_the_point_outside_the_overlay_is_clear(self):
        text = dumpsys(
            "android.webkit.WebView{1 V.E...... ........ 0,0-1220,2358}",
            "android.widget.TextView{2 V.E...... ........ 300,2300-900,2400}",
        )
        self.assertEqual(tap_overlays.plan((0, 0, 1220, 2358), 100, 2350, text, ""), "clear")


class UiaOverlaysTest(unittest.TestCase):
    def test_a_clickable_node_after_the_webview_covers_it(self):
        xml = uia(
            '<node class="android.widget.FrameLayout" bounds="[0,0][1220,2712]">'
            + WEBVIEW_NODE
            + '<node class="android.view.View" text="Group 4 tabs" resource-id="com.jestk.browser.debug:id/group_pill"'
            ' clickable="true" bounds="[400,2250][820,2350]"/>'
            "</node>"
        )
        verdict = tap_overlays.plan(WEBVIEW, 600, 2300, "", xml)
        verdict_parts = verdict.split("\t")
        self.assertEqual(verdict_parts[0], "covered")
        self.assertIn('text="Group 4 tabs"', verdict_parts[2])
        self.assertIn("id=com.jestk.browser.debug:id/group_pill", verdict_parts[2])
        self.assertIn("bounds=[400,2250][820,2350]", verdict_parts[2])

    def test_page_content_inside_the_webview_never_counts(self):
        xml = uia(
            '<node class="android.widget.FrameLayout" bounds="[0,0][1220,2712]">'
            '<node class="android.webkit.WebView" bounds="[0,128][1220,2486]">'
            '<node class="android.widget.TextView" text="example link" clickable="true" bounds="[0,2200][600,2300]"/>'
            "</node></node>"
        )
        self.assertEqual(tap_overlays.plan(WEBVIEW, 300, 2250, "", xml), "clear")

    def test_nodes_before_the_webview_do_not_cover_it(self):
        xml = uia(
            '<node class="android.view.View" text="Behind" clickable="true" bounds="[0,2200][1220,2400]"/>',
            WEBVIEW_NODE,
        )
        self.assertEqual(tap_overlays.plan(WEBVIEW, 300, 2250, "", xml), "clear")

    def test_unlabelled_unclickable_nodes_do_not_cover_it(self):
        xml = uia(WEBVIEW_NODE, '<node class="android.view.View" bounds="[0,2200][1220,2400]"/>')
        self.assertEqual(tap_overlays.plan(WEBVIEW, 300, 2250, "", xml), "clear")

    def test_a_broken_or_empty_dump_is_treated_as_no_overlay(self):
        self.assertEqual(tap_overlays.plan(WEBVIEW, 300, 2250, "", "<hierarchy"), "clear")
        self.assertEqual(tap_overlays.plan(WEBVIEW, 300, 2250, "", ""), "clear")


class ScrollPlanTest(unittest.TestCase):
    def test_a_dock_at_the_bottom_scrolls_the_point_up_into_the_band_above(self):
        # Band above the overlay: 128..2300 (middle 1214). The point at 2350 moves up by 1136 px.
        text = dumpsys(
            "android.webkit.WebView{1 V.E...... ........ 0,128-1220,2486}",
            "android.widget.TextView{2 V.E...... ........ 0,2300-1220,2400}",
        )
        verdict = tap_overlays.plan(WEBVIEW, 500, 2350, text, "")
        self.assertEqual(verdict.split("\t")[1], "1136")

    def test_an_overlay_at_the_top_scrolls_the_other_way(self):
        xml = uia(WEBVIEW_NODE, '<node class="android.view.View" text="Top bar" clickable="true" bounds="[0,128][1220,400]"/>')
        verdict = tap_overlays.plan(WEBVIEW, 500, 300, "", xml)
        self.assertLess(int(verdict.split("\t")[1]), 0)

    def test_no_clear_band_gives_a_dash(self):
        xml = uia(WEBVIEW_NODE, '<node class="android.view.View" text="Everything" clickable="true" bounds="[0,0][1220,2712]"/>')
        self.assertEqual(tap_overlays.plan(WEBVIEW, 500, 1000, "", xml).split("\t")[1], "-")


class ChosenIndexTest(unittest.TestCase):
    def test_sixth_value_is_the_index(self):
        self.assertEqual(tap_overlays.chosen_index(json.dumps(json.dumps([1, 2, 3, 4, 2, 3]))), 3)

    def test_no_sixth_value_means_the_first_match(self):
        self.assertEqual(tap_overlays.chosen_index(json.dumps(json.dumps([1, 2, 3, 4, 2]))), 0)


class BottomBandTest(unittest.TestCase):
    # WEBVIEW is 128..2486 (height 2358): the bottom quarter starts at 128 + 1768.5 = 1896.5.
    def test_the_bottom_quarter_is_in_the_band(self):
        self.assertTrue(tap_overlays.in_bottom_band(WEBVIEW, 2300))
        self.assertTrue(tap_overlays.in_bottom_band(WEBVIEW, 1897))

    def test_the_rows_above_the_bottom_quarter_are_not(self):
        self.assertFalse(tap_overlays.in_bottom_band(WEBVIEW, 1896))
        self.assertFalse(tap_overlays.in_bottom_band(WEBVIEW, 1000))

    def test_cli_exit_status_tells_the_band(self):
        inside = subprocess.run([sys.executable, str(SCRIPT), "band", "0 128 1220 2486", "2300"], capture_output=True)
        outside = subprocess.run([sys.executable, str(SCRIPT), "band", "0 128 1220 2486", "1000"], capture_output=True)
        self.assertEqual((inside.returncode, outside.returncode), (0, 1))


class MaxScrollTest(unittest.TestCase):
    def test_a_moved_centre_is_moved(self):
        before = json.dumps(json.dumps([10, 500, 400, 800, 2.6, 0]))
        after = json.dumps(json.dumps([10, 420, 400, 800, 2.6, 0]))
        self.assertEqual(tap_overlays.centre_y(before), 500)
        done = subprocess.run([sys.executable, str(SCRIPT), "moved", before, after], capture_output=True, text=True)
        self.assertEqual(done.stdout.strip(), "moved")

    def test_an_unchanged_centre_is_stuck_at_the_end_of_the_page(self):
        before = json.dumps(json.dumps([10, 500, 400, 800, 2.6, 0]))
        after = json.dumps(json.dumps([10, 500.4, 400, 800, 2.6, 0]))
        done = subprocess.run([sys.executable, str(SCRIPT), "moved", before, after], capture_output=True, text=True)
        self.assertEqual(done.stdout.strip(), "stuck")

    def test_a_null_result_means_the_element_is_gone(self):
        before = json.dumps(json.dumps([10, 500, 400, 800, 2.6, 0]))
        after = json.dumps("null")  # JSON.stringify(null), as the scroll script returns it
        self.assertIsNone(tap_overlays.centre_y(after))
        done = subprocess.run([sys.executable, str(SCRIPT), "moved", before, after], capture_output=True, text=True)
        self.assertEqual((done.returncode, done.stdout.strip()), (3, "element no longer in the page"))
        self.assertEqual(done.stderr, "")


class PlanCliTest(unittest.TestCase):
    def test_cli_reads_the_dump_files_and_prints_the_verdict(self):
        with tempfile.TemporaryDirectory() as tmp:
            dumps = Path(tmp) / "dumpsys"
            uia_file = Path(tmp) / "uia.xml"
            dumps.write_text(DUMPSYS_SAMPLE.read_text())
            uia_file.write_text(uia(WEBVIEW_NODE))
            done = subprocess.run(
                [sys.executable, str(SCRIPT), "plan", "0 128 1220 2486", "600", "1000", str(dumps), str(uia_file)],
                capture_output=True, text=True,
            )
        self.assertEqual((done.returncode, done.stdout.strip()), (0, "clear"))

    def test_cli_index(self):
        done = subprocess.run(
            [sys.executable, str(SCRIPT), "index", json.dumps(json.dumps([1, 2, 3, 4, 2, 5]))],
            capture_output=True, text=True,
        )
        self.assertEqual(done.stdout.strip(), "5")


if __name__ == "__main__":
    unittest.main()
