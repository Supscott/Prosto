#!/usr/bin/env python3
"""Tests for tools/hooks/invariants_after_edit.py, run against a throwaway git repository.

Run: python3 tools/hooks/invariants_after_edit_test.py

The repository, its worktree and the hook's state file all live in a temp directory (TMPDIR), so no
real repo state is read or written and nothing touches the network. The worktree gets a stub
tools/check_invariants.py: the real one checks the whole app tree, which a temp repo cannot satisfy.
The real tools/ensure_worktree_files.sh is copied in, because the hook relies on it.
"""

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HOOK = Path(__file__).resolve().parent / "invariants_after_edit.py"
ENSURE_SCRIPT = Path(__file__).resolve().parents[1] / "ensure_worktree_files.sh"

# Stands in for tools/check_invariants.py. Reports "TASKS.md is missing" like the real check, plus
# any lines of the FAILS marker file, and prints the same "FAIL:" lines the hook parses.
STUB_CHECKS = """#!/usr/bin/env python3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
failures = []
if not (ROOT / "TASKS.md").exists():
    failures.append("TASKS.md is missing")
marker = ROOT / "FAILS"
if marker.exists():
    failures.extend(marker.read_text(encoding="utf-8").splitlines())
if failures:
    print("=== Architectural Invariant Violations ===")
    for failure in failures:
        print(f"  FAIL: {failure}")
    sys.exit(1)
print("All architectural invariants satisfied (stub).")
"""


def git(cwd, *args):
    """Runs git with the caller's GIT_* variables removed and hooks and signing disabled."""
    env = {key: value for key, value in os.environ.items() if not key.startswith("GIT_")}
    env.update(
        GIT_AUTHOR_NAME="test", GIT_AUTHOR_EMAIL="test@example.invalid",
        GIT_COMMITTER_NAME="test", GIT_COMMITTER_EMAIL="test@example.invalid",
    )
    subprocess.run(
        ["git", "-c", "commit.gpgsign=false", "-c", "core.hooksPath=/dev/null", *args],
        cwd=cwd, env=env, check=True, capture_output=True, text=True,
    )


class InvariantsAfterEditTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        base = Path(temp.name)
        self.main = base / "main"
        self.worktree = base / "worktree"
        self.state_dir = base / "tmp"
        self.state_dir.mkdir()

        self.main.mkdir()
        git(self.main, "init", "-q")
        (self.main / "tools").mkdir()
        (self.main / "tools" / "ensure_worktree_files.sh").write_bytes(ENSURE_SCRIPT.read_bytes())
        (self.main / "tools" / "check_invariants.py").write_text(STUB_CHECKS, encoding="utf-8")
        git(self.main, "add", "tools")
        git(self.main, "commit", "-q", "-m", "init")
        git(self.main, "worktree", "add", "-q", "--detach", str(self.worktree))

        # Git-ignored files exist only in the main checkout, as on a real machine. Written after
        # the commit so they stay untracked and the worktree does not get them from git.
        (self.main / "TASKS.md").write_text("# Tasks\n", encoding="utf-8")
        (self.main / "local.properties").write_text("sdk.dir=/nonexistent\n", encoding="utf-8")

    def run_hook(self, file_path):
        payload = json.dumps({"tool_name": "Edit", "tool_input": {"file_path": str(file_path)}})
        env = {key: value for key, value in os.environ.items() if not key.startswith("GIT_")}
        env["TMPDIR"] = str(self.state_dir)
        return subprocess.run(
            [sys.executable, str(HOOK)], input=payload, capture_output=True, text=True,
            env=env, timeout=120,
        )

    def test_copies_missing_worktree_files_and_passes(self):
        self.assertFalse((self.worktree / "TASKS.md").exists())
        result = self.run_hook(self.worktree / "tools" / "check_invariants.py")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("TASKS.md is missing", result.stdout + result.stderr)
        self.assertEqual((self.worktree / "TASKS.md").read_text(encoding="utf-8"), "# Tasks\n")
        self.assertTrue((self.worktree / "local.properties").exists())

    def test_repeated_identical_failures_are_reported_once(self):
        (self.worktree / "FAILS").write_text("sample invariant broke\n", encoding="utf-8")
        edit = self.worktree / "tools" / "check_invariants.py"

        first = self.run_hook(edit)
        self.assertEqual(first.returncode, 2)
        self.assertIn("FAIL: sample invariant broke", first.stderr)

        second = self.run_hook(edit)
        self.assertEqual(second.returncode, 0)
        self.assertNotIn("FAIL:", second.stderr)

        # A different failure set is reported again.
        (self.worktree / "FAILS").write_text("another invariant broke\n", encoding="utf-8")
        third = self.run_hook(edit)
        self.assertEqual(third.returncode, 2)
        self.assertIn("FAIL: another invariant broke", third.stderr)


if __name__ == "__main__":
    unittest.main()
