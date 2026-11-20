#!/usr/bin/env python3
"""Tests for the stale-build detection and the summary output of test_summary.py.

Run: python3 -m unittest discover -s tools -p '*_test.py'
"""

import contextlib
import io
import tempfile
import time
import unittest
from pathlib import Path
from xml.sax.saxutils import escape, quoteattr

import test_summary

NO_SUCH_METHOD = "java.lang.NoSuchMethodError: BrowserUiState.<init>(Lcom/jestk/Foo;)V"
ASSERTION = "expected:<true> but was:<false>"


def write_report(directory, name, failures, passing=0):
    """Writes one JUnit-style suite; each failure is a (message, stack trace text) pair."""
    cases = [f'<testcase classname="com.jestk.browser.Sample" name="test{i}">'
             f'<failure message={quoteattr(message)}>{escape(text)}</failure></testcase>'
             for i, (message, text) in enumerate(failures)]
    cases += [f'<testcase classname="com.jestk.browser.Sample" name="ok{i}"/>' for i in range(passing)]
    xml = (f'<?xml version="1.0"?><testsuite name="Sample" tests="{len(cases)}" skipped="0" '
           f'failures="{len(failures)}">{"".join(cases)}</testsuite>')
    (Path(directory) / name).write_text(xml, encoding="utf-8")


class StaleBuildTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        original = test_summary.RESULTS
        test_summary.RESULTS = Path(self.directory.name)
        self.addCleanup(setattr, test_summary, "RESULTS", original)

    def exit_code(self, started="0"):
        return test_summary.main([started, "--stale-check"])

    def test_mass_no_such_method_is_stale(self):
        write_report(self.directory.name, "TEST-a.xml", [(NO_SUCH_METHOD, "")] * 6)
        self.assertEqual(self.exit_code(), test_summary.STALE_EXIT_CODE)

    def test_below_minimum_failures_is_not_stale(self):
        write_report(self.directory.name, "TEST-a.xml", [(NO_SUCH_METHOD, "")] * 4)
        self.assertEqual(self.exit_code(), 0)

    def test_below_share_threshold_is_not_stale(self):
        failures = [(NO_SUCH_METHOD, "")] * 7 + [(ASSERTION, "")] * 3
        write_report(self.directory.name, "TEST-a.xml", failures)
        self.assertEqual(self.exit_code(), 0)

    def test_exactly_at_share_threshold_is_stale(self):
        failures = [(NO_SUCH_METHOD, "")] * 8 + [(ASSERTION, "")] * 2
        write_report(self.directory.name, "TEST-a.xml", failures)
        self.assertEqual(self.exit_code(), test_summary.STALE_EXIT_CODE)

    def test_ordinary_assertion_failures_are_not_stale(self):
        write_report(self.directory.name, "TEST-a.xml", [(ASSERTION, "")] * 6)
        self.assertEqual(self.exit_code(), 0)

    def test_marker_only_in_stack_trace_counts(self):
        trace = "java.lang.NoSuchFieldError: mTabs\n\tat com.jestk.browser.Sample.run(Sample.kt:1)"
        write_report(self.directory.name, "TEST-a.xml", [("boom", trace)] * 5)
        self.assertEqual(self.exit_code(), test_summary.STALE_EXIT_CODE)

    def test_no_reports_is_not_stale(self):
        self.assertEqual(self.exit_code(), 0)

    def test_reports_written_before_the_run_are_ignored(self):
        write_report(self.directory.name, "TEST-a.xml", [(NO_SUCH_METHOD, "")] * 6)
        future = str(time.time() + 3600)
        self.assertEqual(self.exit_code(started=future), 0)

    def test_summary_output_still_lists_failures(self):
        write_report(self.directory.name, "TEST-a.xml", [(ASSERTION, "")], passing=1)
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.assertEqual(test_summary.main(["0"]), 0)
        self.assertIn("==> Tests: 1/2 passed in 1 classes, 1 FAILED", output.getvalue())
        self.assertIn("  FAIL Sample.test0: expected:<true> but was:<false>", output.getvalue())


if __name__ == "__main__":
    unittest.main()
