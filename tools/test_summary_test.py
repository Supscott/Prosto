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
APP_CLASS_MISSING = "java.lang.NoClassDefFoundError: com/jestk/browser/ui/overlays/AddEditSearchEngineDialogKt"
NON_APP_CLASS_MISSING = "java.lang.NoClassDefFoundError: org/foo/Bar"


def write_report(directory, name, failures, passing=0, timestamp="2026-11-24T10:00:00"):
    """Writes one JUnit-style suite; each failure is a (message, stack trace text) pair. Like Gradle,
    the failure's type attribute is the exception class named on the trace's first line."""
    cases = []
    for i, (message, text) in enumerate(failures):
        kind = text.splitlines()[0].split(":", 1)[0] if text else ""
        type_attr = f' type="{escape(kind)}"' if kind else ""
        cases.append(f'<testcase classname="com.jestk.browser.Sample" name="test{i}">'
                     f'<failure message={quoteattr(message)}{type_attr}>{escape(text)}</failure></testcase>')
    cases += [f'<testcase classname="com.jestk.browser.Sample" name="ok{i}"/>' for i in range(passing)]
    xml = (f'<?xml version="1.0"?><testsuite name="Sample" tests="{len(cases)}" skipped="0" '
           f'failures="{len(failures)}" timestamp="{timestamp}">{"".join(cases)}</testsuite>')
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

    def test_few_failures_all_linkage_is_stale(self):
        write_report(self.directory.name, "TEST-a.xml", [(NO_SUCH_METHOD, "")] * 4)
        self.assertEqual(self.exit_code(), test_summary.STALE_EXIT_CODE)

    def test_below_minimum_mixed_failures_is_not_stale(self):
        write_report(self.directory.name, "TEST-a.xml", [(NO_SUCH_METHOD, "")] * 3 + [(ASSERTION, "")])
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

    def test_four_app_class_not_found_failures_are_stale(self):
        write_report(self.directory.name, "TEST-a.xml", [(APP_CLASS_MISSING, "")] * 4)
        self.assertEqual(self.exit_code(), test_summary.STALE_EXIT_CODE)

    def test_dotted_app_class_not_found_is_stale(self):
        dotted = APP_CLASS_MISSING.replace("com/jestk/browser/ui/overlays/AddEditSearchEngineDialogKt",
                                           "com.jestk.browser.ui.overlays.AddEditSearchEngineDialogKt")
        write_report(self.directory.name, "TEST-a.xml", [(dotted, "")] * 4)
        self.assertEqual(self.exit_code(), test_summary.STALE_EXIT_CODE)

    def test_non_app_class_not_found_is_not_stale(self):
        write_report(self.directory.name, "TEST-a.xml", [(NON_APP_CLASS_MISSING, "")] * 4)
        self.assertEqual(self.exit_code(), 0)

    def test_one_linkage_and_one_assertion_is_not_stale(self):
        write_report(self.directory.name, "TEST-a.xml", [(APP_CLASS_MISSING, ""), (ASSERTION, "")])
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


LINK_ERROR = ("dlopen failed", "java.lang.UnsatisfiedLinkError: dlopen failed\n\tat org.Native.load(Native.kt:1)")


class MassFailureTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.results = Path(self.tmp.name) / "app" / "build" / "test-results" / "testDebugUnitTest"
        self.results.mkdir(parents=True)
        original = test_summary.RESULTS
        test_summary.RESULTS = self.results
        self.addCleanup(setattr, test_summary, "RESULTS", original)
        self.failed = Path(self.tmp.name) / "app" / "build" / "test-results-failed"

    def run_main(self):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.assertEqual(test_summary.main(["0"]), 0)
        return output.getvalue()

    def test_first_failure_follows_suite_timestamp_not_file_name(self):
        write_report(self.results, "TEST-a.xml", [(ASSERTION, "")] * 5, timestamp="2026-11-24T10:00:05")
        write_report(self.results, "TEST-b.xml", [LINK_ERROR] + [(ASSERTION, "")] * 4,
                     timestamp="2026-11-24T10:00:00")
        output = self.run_main()
        self.assertIn("FIRST failure (by suite timestamp): Sample.test0: UnsatisfiedLinkError: dlopen failed",
                      output)

    def test_first_failure_follows_test_order_inside_a_suite(self):
        failures = [("expected:<true> but was:<false>", "java.lang.AssertionError: expected:<true>")]
        failures += [LINK_ERROR] * 4
        write_report(self.results, "TEST-a.xml", failures, timestamp="2026-11-24T10:00:00")
        output = self.run_main()
        self.assertIn("FIRST failure (by suite timestamp): Sample.test0: AssertionError: expected:<true>",
                      output)

    def test_below_mass_threshold_names_no_first_failure_and_keeps_no_xml(self):
        write_report(self.results, "TEST-a.xml", [LINK_ERROR] * 4)
        output = self.run_main()
        self.assertNotIn("FIRST failure", output)
        self.assertFalse(self.failed.exists())

    def test_mass_failure_copies_this_runs_reports_and_prints_the_path(self):
        write_report(self.results, "TEST-a.xml", [LINK_ERROR] * 5)
        output = self.run_main()
        copies = [path for path in self.failed.iterdir() if path.is_dir()]
        self.assertEqual(len(copies), 1)
        self.assertTrue((copies[0] / "TEST-a.xml").is_file())
        self.assertIn(f"==> XML of this run kept in: {copies[0]}", output)

    def test_only_the_newest_three_copies_are_kept(self):
        for stamp in ("19990101-000000", "19990102-000000", "19990103-000000", "19990104-000000"):
            (self.failed / stamp).mkdir(parents=True)
        write_report(self.results, "TEST-a.xml", [LINK_ERROR] * 5)
        self.run_main()
        remaining = sorted(path.name for path in self.failed.iterdir())
        self.assertEqual(len(remaining), 3)
        self.assertEqual(remaining[:2], ["19990103-000000", "19990104-000000"])


if __name__ == "__main__":
    unittest.main()
