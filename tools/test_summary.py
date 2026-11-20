#!/usr/bin/env python3
"""Prints a compact result of the last unit test run: totals, then every failure with the
first lines of its message. Usage: test_summary.py <run start, epoch seconds> [--stale-check].

Gradle keeps reports of classes it did not run this time, so only reports written after the
run started are counted.

--stale-check prints nothing and exits 3 when the failures look like stale incremental test
classes (a constructor or field they call changed and they were not recompiled), else 0.
"""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RESULTS = Path(__file__).resolve().parent.parent / "app" / "build" / "test-results" / "testDebugUnitTest"
MAX_LISTED_FAILURES = 20
# Stale-build detection: at least this many failures, and at least this share of them linkage errors.
STALE_MIN_FAILURES = 5
STALE_MIN_SHARE = 0.8
STALE_MARKERS = ("NoSuchMethodError", "NoSuchFieldError", "NoClassDefFoundError")
STALE_EXIT_CODE = 3


def load_reports(started):
    return [f for f in RESULTS.glob("TEST-*.xml") if f.stat().st_mtime >= started]


def collect(reports):
    """Returns (total, skipped, failures). A failure is (label, full text of its message and trace)."""
    total = skipped = 0
    failures = []
    for report in reports:
        suite = ET.parse(report).getroot()
        total += int(suite.get("tests", 0))
        skipped += int(suite.get("skipped", 0))
        for case in suite.iter("testcase"):
            problem = case.find("failure")
            if problem is None:
                problem = case.find("error")
            if problem is not None:
                message = problem.get("message") or ""
                text = problem.text or ""
                # Truth puts "expected" and "but was" on the lines after the first.
                lines = [line.strip() for line in (message or text).splitlines()]
                first = " | ".join(line for line in lines if line)[:240] if lines else ""
                label = f"{case.get('classname', '').rsplit('.', 1)[-1]}.{case.get('name')}: {first}"
                failures.append((label, message + "\n" + text))
    return total, skipped, failures


def is_stale_build(raw_messages):
    """True when the failures are mostly linkage errors, the signature of stale incremental classes."""
    if len(raw_messages) < STALE_MIN_FAILURES:
        return False
    linkage = sum(1 for raw in raw_messages if any(marker in raw for marker in STALE_MARKERS))
    return linkage / len(raw_messages) >= STALE_MIN_SHARE


def main(argv):
    stale_check = "--stale-check" in argv
    args = [arg for arg in argv if arg != "--stale-check"]
    started = float(args[0]) if args else 0.0
    reports = load_reports(started)
    if stale_check:
        _, _, failures = collect(reports)
        return STALE_EXIT_CODE if is_stale_build([raw for _, raw in failures]) else 0
    if not reports:
        print("==> Tests: no new reports (Gradle found the tests up to date, or compilation failed above)")
        return 0
    total, skipped, failures = collect(reports)
    passed = total - skipped - len(failures)
    print(f"==> Tests: {passed}/{total} passed in {len(reports)} classes"
          + (f", {skipped} skipped" if skipped else "")
          + (f", {len(failures)} FAILED" if failures else ""))
    for label, _ in failures[:MAX_LISTED_FAILURES]:
        print(f"  FAIL {label}")
    if len(failures) > MAX_LISTED_FAILURES:
        print(f"  ... and {len(failures) - MAX_LISTED_FAILURES} more")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
