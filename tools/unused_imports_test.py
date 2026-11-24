#!/usr/bin/env python3
"""Tests for tools/unused_imports.py on Kotlin fixture strings.

Run: python3 -m unittest discover -s tools -p 'unused_imports_test.py'
"""

import unittest

from unused_imports import unused_imports


def names(source):
    return [line for _, line in unused_imports(source)]


class UnusedImportsTest(unittest.TestCase):
    def test_flags_import_whose_name_is_never_used(self):
        source = "package a\n\nimport com.x.Tab\nimport com.x.Other\n\nfun f() = Other()\n"
        self.assertEqual(names(source), ["import com.x.Tab"])

    def test_keeps_import_used_as_a_whole_word_only(self):
        source = "import com.x.Tab\n\nval tabs = Tabs()\n"
        self.assertEqual(names(source), ["import com.x.Tab"])
        self.assertEqual(names("import com.x.Tab\n\nval t: Tab? = null\n"), [])

    def test_qualified_use_counts(self):
        self.assertEqual(names("import com.x.Icons\n\nval i = Material.Icons.Default\n"), [])

    def test_alias_is_the_name_that_must_be_used(self):
        self.assertEqual(names("import com.x.Public as Pub\n\nval p = Public\n"), ["import com.x.Public as Pub"])
        self.assertEqual(names("import com.x.Public as Pub\n\nval p = Pub\n"), [])

    def test_star_import_is_skipped(self):
        self.assertEqual(names("import com.x.*\n\nval a = 1\n"), [])

    def test_implicit_operator_and_delegate_names_are_never_flagged(self):
        source = ("import androidx.compose.runtime.getValue\n"
                  "import androidx.compose.runtime.setValue\n"
                  "import androidx.compose.runtime.provideDelegate\n"
                  "import kotlin.collections.plus\n"
                  "import kotlin.collections.component1\n"
                  "import kotlin.collections.component12\n\n"
                  "val x by state\n")
        self.assertEqual(names(source), [])

    def test_name_in_kdoc_counts_as_used(self):
        source = "import com.x.Tab\n\n/** Opens a [Tab]. */\nfun open() {}\n"
        self.assertEqual(names(source), [])

    def test_name_in_string_counts_as_used(self):
        self.assertEqual(names('import com.x.Tab\n\nval s = "Tab"\n'), [])

    def test_reports_the_line_number(self):
        source = "package a\nimport com.x.Keep\nimport com.x.Drop\n\nfun f() = Keep\n"
        self.assertEqual(unused_imports(source), [(3, "import com.x.Drop")])

    def test_package_line_and_non_import_text_are_not_imports(self):
        self.assertEqual(names("package com.x.Tab\n\nfun f() {}\n"), [])


if __name__ == "__main__":
    unittest.main()
