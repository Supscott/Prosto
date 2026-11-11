#!/usr/bin/env python3
"""Prints a compact result of the last unit test run: totals, then every failure with the
first lines of its message. Usage: test_summary.py <run start, epoch seconds>.

Gradle keeps reports of classes it did not run this time, so only reports written after the
run started are counted.
"""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RESULTS = Path(__file__).resolve().parent.parent / "app" / "build" / "test-results" / "testDebugUnitTest"
MAX_LISTED_FAILURES = 20


def main():
    started = float(sys.argv[1]) if len(sys.argv) > 1 else 0.0
    reports = [f for f in RESULTS.glob("TEST-*.xml") if f.stat().st_mtime >= started]
    if not reports:
        print("==> Tests: no new reports (Gradle found the tests up to date, or compilation failed above)")
        return
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
                # Truth puts "expected" and "but was" on the lines after the first.
                lines = [line.strip() for line in (problem.get("message") or problem.text or "").splitlines()]
                first = " | ".join(line for line in lines if line)[:240] if lines else ""
                failures.append(f"{case.get('classname', '').rsplit('.', 1)[-1]}.{case.get('name')}: {first}")
    passed = total - skipped - len(failures)
    print(f"==> Tests: {passed}/{total} passed in {len(reports)} classes"
          + (f", {skipped} skipped" if skipped else "")
          + (f", {len(failures)} FAILED" if failures else ""))
    for failure in failures[:MAX_LISTED_FAILURES]:
        print(f"  FAIL {failure}")
    if len(failures) > MAX_LISTED_FAILURES:
        print(f"  ... and {len(failures) - MAX_LISTED_FAILURES} more")


if __name__ == "__main__":
    main()
