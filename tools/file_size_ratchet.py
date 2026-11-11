#!/usr/bin/env python3
"""Keeps Kotlin source files from growing past the size limit.

New files must stay within MAX_LINES. Files that were already larger are listed in
file_size_baseline.json with their size at the time; they may shrink but never grow. After
splitting a large file, run `python3 tools/file_size_ratchet.py --update` to lower its entry
(the update never raises a limit and drops files that fit again).
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE_DIR = ROOT / "app" / "src" / "main"
BASELINE = Path(__file__).resolve().parent / "file_size_baseline.json"
MAX_LINES = 500


def _line_counts():
    return {
        str(file.relative_to(ROOT)): len(file.read_text(encoding="utf-8").splitlines())
        for file in sorted(SOURCE_DIR.rglob("*.kt"))
    }


def _load_baseline():
    return json.loads(BASELINE.read_text(encoding="utf-8")) if BASELINE.exists() else {}


def check_file_sizes():
    baseline = _load_baseline()
    errors = []
    for path, lines in _line_counts().items():
        limit = max(baseline.get(path, 0), MAX_LINES)
        if lines > limit:
            if path in baseline:
                errors.append(
                    f"{path}: {lines} lines, grew past its baseline of {limit}. Move the new code "
                    f"into a separate file instead of growing this one."
                )
            else:
                errors.append(f"{path}: {lines} lines, over the {MAX_LINES}-line limit for source files.")
    return errors


def update_baseline():
    """Lowers baseline entries to current sizes; never raises one and never adds a file."""
    counts = _line_counts()
    old = _load_baseline()
    new = {
        path: min(limit, counts[path])
        for path, limit in old.items()
        if path in counts and counts[path] > MAX_LINES
    }
    BASELINE.write_text(json.dumps(new, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    for path in sorted(set(old) - set(new)):
        print(f"  dropped {path} (fits the limit now or was removed)")
    for path in sorted(new):
        if new[path] < old[path]:
            print(f"  lowered {path}: {old[path]} -> {new[path]}")


if __name__ == "__main__":
    if "--update" in sys.argv:
        update_baseline()
        sys.exit(0)
    problems = check_file_sizes()
    for problem in problems:
        print(f"FAIL: {problem}")
    sys.exit(1 if problems else 0)
