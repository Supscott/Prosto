#!/usr/bin/env python3
"""Prints a compact result of the last unit test run: totals, then every failure with the
first lines of its message. Usage: test_summary.py <run start, epoch seconds> [--stale-check].

Gradle keeps reports of classes it did not run this time, so only reports written after the
run started are counted.

With MASS_FAILURES or more failures it also names the earliest failure (by suite timestamp, then
test order) and copies this run's XML reports to build/test-results-failed/<stamp>/, keeping the
newest FAILED_KEEP copies, so the root exception survives a later rerun that overwrites the XML.

--stale-check prints nothing and exits 3 when the failures look like stale incremental test
classes (a constructor or field they call changed and they were not recompiled), else 0.
"""

import re
import shutil
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

RESULTS = Path(__file__).resolve().parent.parent / "app" / "build" / "test-results" / "testDebugUnitTest"
MAX_LISTED_FAILURES = 20
# Mass failure: at this many failures or more, name the first one and keep its XML.
MASS_FAILURES = 5
FAILED_KEEP = 3
# Stale-build detection: a run is stale when every failure is a linkage error, or when at least
# STALE_MIN_FAILURES failures exist and at least STALE_MIN_SHARE of them are linkage errors.
STALE_MIN_FAILURES = 5
STALE_MIN_SHARE = 0.8
STALE_MARKERS = ("NoSuchMethodError", "NoSuchFieldError")
# A NoClassDefFoundError counts only for an app class (a renamed or deleted file leaves its old class behind).
APP_CLASS_NOT_FOUND = re.compile(r"NoClassDefFoundError: com[/.]jestk[/.]browser[/.]")
STALE_EXIT_CODE = 3


def load_reports(started):
    return [f for f in RESULTS.glob("TEST-*.xml") if f.stat().st_mtime >= started]


def failed_root():
    """build/test-results-failed, next to the test-results directory of this run."""
    return RESULTS.parent.parent / "test-results-failed"


def failure_of(case):
    problem = case.find("failure")
    if problem is None:
        problem = case.find("error")
    return problem


def collect(reports):
    """Returns (total, skipped, failures). A failure is (label, full text of its message and trace)."""
    total = skipped = 0
    failures = []
    for report in reports:
        suite = ET.parse(report).getroot()
        total += int(suite.get("tests", 0))
        skipped += int(suite.get("skipped", 0))
        for case in suite.iter("testcase"):
            problem = failure_of(case)
            if problem is not None:
                message = problem.get("message") or ""
                text = problem.text or ""
                # Truth puts "expected" and "but was" on the lines after the first.
                lines = [line.strip() for line in (message or text).splitlines()]
                first = " | ".join(line for line in lines if line)[:240] if lines else ""
                label = f"{case.get('classname', '').rsplit('.', 1)[-1]}.{case.get('name')}: {first}"
                failures.append((label, message + "\n" + text))
    return total, skipped, failures


def exception_summary(problem):
    """'ExceptionType: first line of the message', from the type attribute or the trace's first line."""
    message = (problem.get("message") or "").strip()
    kind = problem.get("type") or ""
    if not kind:
        head = next((line.strip() for line in (problem.text or "").splitlines() if line.strip()), "")
        kind, _, rest = head.partition(":")
        if not message:
            message = rest.strip()
    kind = kind.rsplit(".", 1)[-1] or "unknown exception"
    first = next((line.strip() for line in message.splitlines() if line.strip()), "")
    return f"{kind}: {first}"[:240]


def first_failure(reports):
    """The earliest failure as 'Class.test: ExceptionType: message'. Suites are ordered by their
    timestamp attribute (then file name); tests keep their order inside a suite. None when no failure."""
    suites = sorted(((ET.parse(report).getroot(), report.name) for report in reports),
                    key=lambda item: (item[0].get("timestamp", ""), item[1]))
    for suite, _ in suites:
        for case in suite.iter("testcase"):
            problem = failure_of(case)
            if problem is not None:
                label = f"{case.get('classname', '').rsplit('.', 1)[-1]}.{case.get('name')}"
                return f"{label}: {exception_summary(problem)}"
    return None


def keep_failed_reports(reports):
    """Copies this run's reports to failed_root()/<stamp>, deletes all but the newest FAILED_KEEP copies
    and returns the copy's path."""
    root = failed_root()
    target = root / time.strftime("%Y%m%d-%H%M%S")
    target.mkdir(parents=True, exist_ok=True)
    for report in reports:
        shutil.copy2(report, target / report.name)
    copies = sorted(path for path in root.iterdir() if path.is_dir())
    for old in copies[:-FAILED_KEEP]:
        shutil.rmtree(old)
    return target


def is_linkage_error(raw):
    return any(marker in raw for marker in STALE_MARKERS) or APP_CLASS_NOT_FOUND.search(raw) is not None


def is_stale_build(raw_messages):
    """True when the failures are linkage errors, the signature of stale incremental classes:
    every failure is one, or at least STALE_MIN_FAILURES failures with STALE_MIN_SHARE of them."""
    if not raw_messages:
        return False
    linkage = sum(1 for raw in raw_messages if is_linkage_error(raw))
    if linkage == len(raw_messages):
        return True
    return len(raw_messages) >= STALE_MIN_FAILURES and linkage / len(raw_messages) >= STALE_MIN_SHARE


def timeout_line(started):
    """A FAIL line for the test the Gradle watchdog killed in this run (gradle/test-watchdog.gradle.kts
    writes 'Class.method<TAB>seconds' to build/test-timeout.txt), or None."""
    report = RESULTS.parent.parent / "test-timeout.txt"
    if not report.is_file() or report.stat().st_mtime < started:
        return None
    name, _, seconds = report.read_text(encoding="utf-8").strip().partition("\t")
    short = ".".join(name.rsplit(".", 2)[-2:])
    return (f"  FAIL {short}: timed out after {seconds or '?'}s, the test worker was killed "
            "(rerun it alone: tools/preview.sh Class.method; limit: ORG_GRADLE_PROJECT_testTimeoutSeconds=N)")


def main(argv):
    stale_check = "--stale-check" in argv
    args = [arg for arg in argv if arg != "--stale-check"]
    started = float(args[0]) if args else 0.0
    reports = load_reports(started)
    if stale_check:
        _, _, failures = collect(reports)
        return STALE_EXIT_CODE if is_stale_build([raw for _, raw in failures]) else 0
    timed_out = timeout_line(started)
    if not reports:
        if timed_out:
            print("==> Tests: the run was cut short by the per-test timeout, no reports were written")
            print(timed_out)
            return 0
        print("==> Tests: no new reports (Gradle found the tests up to date, or compilation failed above)")
        return 0
    total, skipped, failures = collect(reports)
    passed = total - skipped - len(failures)
    print(f"==> Tests: {passed}/{total} passed in {len(reports)} classes"
          + (f", {skipped} skipped" if skipped else "")
          + (f", {len(failures)} FAILED" if failures else ""))
    if len(failures) >= MASS_FAILURES:
        print(f"FIRST failure (by suite timestamp): {first_failure(reports)}")
        print(f"==> XML of this run kept in: {keep_failed_reports(reports)}")
    for label, _ in failures[:MAX_LISTED_FAILURES]:
        print(f"  FAIL {label}")
    if len(failures) > MAX_LISTED_FAILURES:
        print(f"  ... and {len(failures) - MAX_LISTED_FAILURES} more")
    if timed_out:
        print(timed_out)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
