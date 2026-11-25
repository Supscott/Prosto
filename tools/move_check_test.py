#!/usr/bin/env python3
"""Tests for move_check.py, run against throwaway git repositories.

Run: python3 -m unittest discover -s tools -p '*_test.py'
"""

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

MOVE_CHECK = Path(__file__).resolve().with_name("move_check.py")

BASE_MODULE = """import os


def _code_lines(text):
    out = []
    for line in text.splitlines():
        out.append(line.strip())
    return out


def use(text):
    return _code_lines(text)
"""

SPLIT_MODULE = """import os
from util import code_lines


def use(text):
    return code_lines(text)
"""

SPLIT_UTIL = """import os


def code_lines(text):
    out = []
    for line in text.splitlines():
        out.append(line.strip())
    return out
"""


def git(cwd, *args):
    subprocess.run(["git", "-c", "user.name=test", "-c", "user.email=test@example.com",
                    "-c", "commit.gpgsign=false", *args],
                   cwd=cwd, check=True, capture_output=True, text=True)


class MoveCheckTest(unittest.TestCase):
    def repo_with_commits(self, base_files, head_files):
        """Creates a repo with a base commit and a head commit; returns (directory, base, head)."""
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        root = Path(tmp.name)
        git(root, "init", "-q")
        for name, text in base_files.items():
            (root / name).write_text(text, encoding="utf-8")
        git(root, "add", "-A")
        git(root, "commit", "-qm", "base")
        base = subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, check=True,
                              capture_output=True, text=True).stdout.strip()
        for name in base_files:
            (root / name).unlink()
        for name, text in head_files.items():
            (root / name).write_text(text, encoding="utf-8")
        git(root, "add", "-A")
        git(root, "commit", "-qm", "head")
        return root, base

    def run_move_check(self, root, base, *extra):
        result = subprocess.run([sys.executable, str(MOVE_CHECK), base, "HEAD", *extra],
                                cwd=root, check=True, capture_output=True, text=True)
        return result.stdout.splitlines()

    def test_python_move_without_rename_is_clean(self):
        root, base = self.repo_with_commits(
            {"mod.py": BASE_MODULE},
            {"mod.py": SPLIT_MODULE.replace("code_lines", "_code_lines"),
             "util.py": SPLIT_UTIL.replace("code_lines", "_code_lines")})
        lines = self.run_move_check(root, base)
        self.assertEqual(lines[-1], "--- 0 lines changed beyond moves")

    def test_python_rename_in_move_is_listed(self):
        root, base = self.repo_with_commits(
            {"mod.py": BASE_MODULE},
            {"mod.py": SPLIT_MODULE, "util.py": SPLIT_UTIL})
        lines = self.run_move_check(root, base)
        self.assertIn("- mod.py: def _code_lines(text):", lines)
        self.assertIn("+ util.py: def code_lines(text):", lines)
        self.assertIn("- mod.py: return _code_lines(text)", lines)
        self.assertIn("+ mod.py: return code_lines(text)", lines)
        self.assertTrue(lines[-1].startswith("--- ") and not lines[-1].startswith("--- 0 "))

    def test_rename_with_sub_cancels(self):
        root, base = self.repo_with_commits(
            {"mod.py": BASE_MODULE},
            {"mod.py": SPLIT_MODULE, "util.py": SPLIT_UTIL})
        lines = self.run_move_check(root, base, "--sub", "_code_lines=code_lines")
        self.assertEqual(lines[-1], "--- 0 lines changed beyond moves")

    def test_kotlin_rename_in_move_is_listed(self):
        root, base = self.repo_with_commits(
            {"A.kt": "fun _codeLines(t: String): List<String> {\n    return t.lines()\n}\n"},
            {"A.kt": "fun codeLines(t: String): List<String> {\n    return t.lines()\n}\n"})
        lines = self.run_move_check(root, base)
        self.assertIn("- A.kt: fun _codeLines(t: String): List<String> {", lines)
        self.assertIn("+ A.kt: fun codeLines(t: String): List<String> {", lines)
        self.assertEqual(lines[-1], "--- 2 lines changed beyond moves")

    def test_other_file_types_are_not_diffed(self):
        root, base = self.repo_with_commits(
            {"notes.txt": "old text\n"},
            {"notes.txt": "new text\n"})
        lines = self.run_move_check(root, base)
        self.assertEqual(lines, ["--- 0 lines changed beyond moves"])


if __name__ == "__main__":
    unittest.main()
