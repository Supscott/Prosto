#!/usr/bin/env python3
"""Flags Kotlin imports whose simple name appears nowhere else in the file.

The Kotlin compiler and Android Lint do not report unused imports. An import counts as used when its
simple name (or its alias) occurs as a whole word anywhere in the file outside the import lines.
Comments and string literals are not stripped, so a name used only in KDoc or a string still counts as
used: the check errs on the side of not flagging. Star imports are skipped, and operator or delegate
names (`getValue`, `plus`, `component1`, ...) are never flagged because Kotlin uses them implicitly.

Enforced by check_invariants.py on every app/src Kotlin file (the whole tree is clean).

Usage: unused_imports.py [file ...]   (no arguments: every app/src/**/*.kt file)
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
IMPORT = re.compile(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?$")
IMPLICIT = re.compile(
    r"^(component\d+|getValue|setValue|provideDelegate|plus|minus|times|div|rem|rangeTo|rangeUntil"
    r"|contains|invoke|get|set|compareTo|iterator|next|hasNext|not|unaryPlus|unaryMinus|inc|dec"
    r"|plusAssign|minusAssign|timesAssign|divAssign|remAssign)$"
)


def unused_imports(text):
    """Returns (line number, import line) for each import whose simple name is not used in the file."""
    lines = text.splitlines()
    body = "\n".join(line for line in lines if not line.lstrip().startswith("import "))
    unused = []
    for number, line in enumerate(lines, 1):
        match = IMPORT.match(line.strip())
        if match is None:
            continue
        path, alias = match.groups()
        name = alias or path.rsplit(".", 1)[-1]
        if IMPLICIT.match(name):
            continue
        if re.search(rf"\b{re.escape(name)}\b", body) is None:
            unused.append((number, line.strip()))
    return unused


def kotlin_files():
    return sorted((ROOT / "app" / "src").glob("**/*.kt"))


def check_unused_imports():
    """Error lines for unused imports in every app/src Kotlin file (check_invariants.py entry point)."""
    errors = []
    for file in kotlin_files():
        for number, line in unused_imports(file.read_text(encoding="utf-8")):
            errors.append(f"{file.relative_to(ROOT)}:{number}: unused {line}")
    return errors


def main(argv):
    files = [Path(arg).resolve() for arg in argv] if argv else kotlin_files()
    count = 0
    for file in files:
        for number, line in unused_imports(file.read_text(encoding="utf-8")):
            print(f"{file.relative_to(ROOT)}:{number}: unused {line}")
            count += 1
    print(f"{count} unused import(s)")
    return 1 if count else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
