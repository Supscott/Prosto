#!/usr/bin/env python3
"""Tests for tools/list_new_pngs.sh: preview.sh lists only the PNGs newer than its run marker.

Run: python3 -m unittest discover -s tools -p 'list_new_pngs_test.py'
"""

import os
import subprocess
import tempfile
import time
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "list_new_pngs.sh"


class ListNewPngsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.build = Path(self.tmp.name) / "app" / "build"
        (self.build / "previews").mkdir(parents=True)
        now = time.time()
        self.old = self.build / "old_preview.png"
        self.old.write_bytes(b"old")
        os.utime(self.old, (now - 200, now - 200))
        self.marker = Path(self.tmp.name) / "run-start"
        self.marker.write_bytes(b"")
        os.utime(self.marker, (now - 100, now - 100))
        self.new = self.build / "previews" / "new_preview.png"
        self.new.write_bytes(b"new")
        os.utime(self.new, (now, now))
        self.named = self.build / "home_screen_dark_feedback_card.png"
        self.named.write_bytes(b"new, no 'preview' in its name")
        os.utime(self.named, (now, now))
        self.other = self.build / "intermediates" / "res" / "icon.png"
        self.other.parent.mkdir(parents=True)
        self.other.write_bytes(b"merged resource, not a preview")
        os.utime(self.other, (now, now))

    def run_script(self, build=None):
        result = subprocess.run(["bash", str(SCRIPT), str(build or self.build), str(self.marker)],
                                capture_output=True, text=True, check=True)
        return result.stdout

    def test_lists_only_the_preview_written_after_the_marker(self):
        self.assertEqual(self.run_script().splitlines(), sorted([str(self.named), str(self.new)]))

    def test_prints_nothing_when_no_preview_is_newer(self):
        now = time.time()
        os.utime(self.new, (now - 300, now - 300))
        os.utime(self.named, (now - 300, now - 300))
        self.assertEqual(self.run_script(), "")

    def test_missing_build_directory_prints_nothing(self):
        self.assertEqual(self.run_script(build=Path(self.tmp.name) / "missing"), "")


if __name__ == "__main__":
    unittest.main()
