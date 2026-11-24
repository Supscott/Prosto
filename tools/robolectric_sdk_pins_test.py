#!/usr/bin/env python3
"""Tests for tools/robolectric_sdk_pins.py on a temporary source tree.

Run: python3 -m unittest discover -s tools -p 'robolectric_sdk_pins_test.py'
"""

import tempfile
import unittest
from pathlib import Path

from robolectric_sdk_pins import check_robolectric_sdk_pins


def run_rule(default, files):
    """Builds a tree with robolectric.properties (default None: no file) and Kotlin test files."""
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        resources = root / "app" / "src" / "test" / "resources"
        resources.mkdir(parents=True)
        if default is not None:
            (resources / "robolectric.properties").write_text(f"# comment\nsdk={default}\n", encoding="utf-8")
        sources = root / "app" / "src" / "test" / "java" / "com" / "x"
        sources.mkdir(parents=True)
        for name, text in files.items():
            (sources / name).write_text(text, encoding="utf-8")
        return check_robolectric_sdk_pins(root)


class RobolectricSdkPinsTest(unittest.TestCase):
    def test_pin_equal_to_the_default_is_allowed(self):
        self.assertEqual(run_rule(35, {"A.kt": "@Config(sdk = [35])\nclass A\n"}), [])

    def test_modern_pin_other_than_the_default_is_rejected_with_file_and_reason(self):
        errors = run_rule(35, {"A.kt": "package x\n\n@Config(sdk = [34])\nclass A\n"})
        self.assertEqual(len(errors), 1)
        self.assertIn("A.kt:3", errors[0])
        self.assertIn("PRO-112", errors[0])
        self.assertIn("default 35", errors[0])

    def test_older_pin_is_allowed(self):
        self.assertEqual(run_rule(35, {"A.kt": "@Config(sdk = [32])\nclass A\n"}), [])

    def test_older_version_code_constants_are_allowed(self):
        source = "@Config(sdk = [Build.VERSION_CODES.O])\n@Config(sdk = [Build.VERSION_CODES.S_V2])\n"
        self.assertEqual(run_rule(35, {"A.kt": source}), [])

    def test_modern_version_code_constant_is_rejected(self):
        errors = run_rule(35, {"A.kt": "@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])\n"})
        self.assertEqual(len(errors), 1)

    def test_version_code_constant_equal_to_the_default_is_allowed(self):
        self.assertEqual(run_rule(35, {"A.kt": "@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])\n"}), [])

    def test_unknown_version_code_name_counts_as_modern(self):
        self.assertEqual(len(run_rule(35, {"A.kt": "@Config(sdk = [Build.VERSION_CODES.BAKLAVA])\n"})), 1)

    def test_pin_next_to_other_arguments_is_checked(self):
        source = '@Config(qualifiers = "w411dp-h891dp", sdk = [34])\nclass A\n'
        self.assertEqual(len(run_rule(35, {"A.kt": source})), 1)

    def test_multiline_annotation_is_checked(self):
        source = '@Config(\n    qualifiers = "w411dp",\n    sdk = [34],\n)\nclass A\n'
        errors = run_rule(35, {"A.kt": source})
        self.assertEqual(len(errors), 1)
        self.assertIn("A.kt:3", errors[0])

    def test_each_modern_sdk_in_a_list_is_reported(self):
        errors = run_rule(35, {"A.kt": "@Config(sdk = [32, 34, 33])\n"})
        self.assertEqual(len(errors), 2)

    def test_config_without_sdk_is_ignored(self):
        self.assertEqual(run_rule(35, {"A.kt": '@Config(qualifiers = "w411dp")\nclass A\n'}), [])

    def test_changing_the_default_changes_the_allowed_pin(self):
        source = "@Config(sdk = [35])\n"
        self.assertEqual(run_rule(35, {"A.kt": source}), [])
        self.assertEqual(len(run_rule(34, {"A.kt": source})), 1)

    def test_missing_properties_file_is_reported(self):
        errors = run_rule(None, {"A.kt": "class A\n"})
        self.assertEqual(len(errors), 1)
        self.assertIn("robolectric.properties", errors[0])


if __name__ == "__main__":
    unittest.main()
