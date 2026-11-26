#!/usr/bin/env python3
"""`tools/verify.sh --changed --dry-run` prints the final class list it would run (PRO-193).

The "related tests:" notes on stderr count hits per source file, with overlaps and a truncated
name list, so the count and names of the classes that actually run come from the final
`classes (N): A, B` line. This checks that line against the `--tests <fqcn>` pairs above it.
"""
import pathlib
import subprocess
import unittest

ROOT = pathlib.Path(__file__).resolve().parent.parent


def dry_run():
    result = subprocess.run(
        ["bash", str(ROOT / "tools/verify.sh"), "--changed", "--dry-run"],
        cwd=ROOT, capture_output=True, text=True, check=True,
    )
    return result.stdout.splitlines()


class VerifyChangedClassListTest(unittest.TestCase):
    def test_class_line_matches_the_tests_pairs(self):
        lines = dry_run()
        pairs = [line for line in lines if line.startswith("--tests ")]
        class_lines = [line for line in lines if line.startswith("classes (")]
        if not pairs:
            self.assertEqual(class_lines, [], "no classes to run, so no class line")
            return
        self.assertEqual(len(class_lines), 1, lines)
        header, names = class_lines[0].split(": ", 1)
        self.assertEqual(header, f"classes ({len(pairs)})")
        expected = [pair.split(" ", 1)[1].rsplit(".", 1)[1] for pair in pairs]
        self.assertEqual(names.split(", "), expected)


if __name__ == "__main__":
    unittest.main()
