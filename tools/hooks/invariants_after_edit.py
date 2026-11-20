#!/usr/bin/env python3
"""PostToolUse hook: run tools/check_invariants.py after an Edit/Write and report new failures.

The repo root is found from the edited file, so an agent in a worktree checks its own worktree.
Exit code 2 shows stderr to the model. The same failure set is reported once, not after every
following edit (state in the temp dir, keyed by repo root).
"""

import hashlib
import json
import subprocess
import sys
import tempfile
from pathlib import Path

WATCHED = ("app/", "tools/", "TASKS.md")
MAX_LINES = 12


def find_root(path):
    for parent in path.parents:
        if (parent / "tools" / "check_invariants.py").is_file():
            return parent
    return None


def main():
    try:
        payload = json.load(sys.stdin)
    except ValueError:
        return 0
    raw = (payload.get("tool_input") or {}).get("file_path")
    if not raw:
        return 0
    path = Path(raw).resolve()
    root = find_root(path)
    if root is None:
        return 0
    rel = path.relative_to(root).as_posix()
    if not rel.startswith(WATCHED):
        return 0

    try:
        result = subprocess.run(
            [sys.executable, "tools/check_invariants.py"],
            cwd=root, capture_output=True, text=True, timeout=60,
        )
    except subprocess.TimeoutExpired:
        return 0
    failures = [line.strip() for line in result.stdout.splitlines() if "FAIL:" in line]

    key = hashlib.sha1(str(root).encode()).hexdigest()[:12]
    state = Path(tempfile.gettempdir()) / f"prosto-invariants-{key}.txt"
    current = "\n".join(failures)
    try:
        previous = state.read_text(encoding="utf-8")
    except OSError:
        previous = ""
    try:
        state.write_text(current, encoding="utf-8")
    except OSError:
        pass

    if result.returncode == 0 or not failures or current == previous:
        return 0
    shown = failures[:MAX_LINES]
    more = len(failures) - len(shown)
    print("tools/check_invariants.py fails after this edit (may be transient mid-change; "
          "must pass before you finish):", file=sys.stderr)
    for line in shown:
        print("  " + line, file=sys.stderr)
    if more:
        print(f"  ... and {more} more", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
