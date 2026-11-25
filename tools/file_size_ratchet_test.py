#!/usr/bin/env python3
"""Tests for file_size_ratchet.counted_lines: import and package lines do not count.

Run: python3 -m unittest discover -s tools -p 'file_size_ratchet_test.py'
"""

import unittest

from file_size_ratchet import counted_lines


class CountedLinesTest(unittest.TestCase):
    def test_kotlin_package_and_imports_are_skipped(self):
        text = "package com.x\n\nimport a.B\nimport a.C as D\nclass E\n"
        self.assertEqual(counted_lines(text, ".kt"), 2)

    def test_kotlin_indented_import_text_still_counts(self):
        text = "fun f() {\n    import_count = 1\n}\n"
        self.assertEqual(counted_lines(text, ".kt"), 3)

    def test_python_top_level_imports_are_skipped(self):
        text = "import os\nfrom pathlib import Path\nfrom a.b import (\n\n)\nx = 1\n"
        self.assertEqual(counted_lines(text, ".py"), 3)

    def test_python_indented_import_counts(self):
        text = "def f():\n    import os\n"
        self.assertEqual(counted_lines(text, ".py"), 2)


if __name__ == "__main__":
    unittest.main()
