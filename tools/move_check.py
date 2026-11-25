#!/usr/bin/env python3
"""Shows what a move-only refactoring really changed.

Lines that only moved (to another place or file, at any indentation) cancel out; what is left
is the code that was added or removed. Import lines and blank lines are ignored.

  python3 tools/move_check.py <base> [<head>] [--sub OLD=NEW ...] [--files GLOB ...]

  <head> defaults to the working tree. --sub applies a textual substitution to both sides
  before comparing, for renames the refactoring made on purpose, e.g.
  --sub "_uiState.update=updateUiState" --sub "onEvent(=dispatch(".

Files: *.kt and *.py by default (--files replaces that list).

Blind spots, by design:
- Import lines are dropped, so an import change that the refactoring needed is not listed.
- Matching is by exact text (after --sub) across the whole diff, not per file: a line removed
  in one file cancels an identical line added in another, even when it was really edited.
- Identifiers are not normalised: a rename (`_code_lines` -> `code_lines`) shows as residual
  lines, so every renamed definition and call site is listed, and needs a --sub to cancel.

Exit code 0 always; the output is for review. The last line counts residual lines.
"""

import argparse
import collections
import re
import subprocess

# Kotlin `import x.y` and Python `import x` / `from x import y` lines.
IMPORT_LINE = re.compile(r"(import |from \S+ import )")


def diff_lines(base, head, globs):
    cmd = ["git", "diff", "-U0", base] + ([head] if head else []) + ["--"] + globs
    out = subprocess.run(cmd, capture_output=True, text=True, check=True).stdout
    current = ""
    for line in out.splitlines():
        if line.startswith("+++ "):
            current = line[6:] if line.startswith("+++ b/") else line[4:]
            continue
        if line.startswith("--- ") or line.startswith("@@"):
            continue
        if line[:1] in "+-":
            yield line[0], current, line[1:].strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("base")
    parser.add_argument("head", nargs="?")
    parser.add_argument("--sub", action="append", default=[], help="OLD=NEW applied to both sides")
    parser.add_argument("--files", action="append", default=None,
                        help="pathspec, default *.kt and *.py (repeat for more)")
    args = parser.parse_args()

    subs = [tuple(s.split("=", 1)) for s in args.sub]

    def norm(text):
        for old, new in subs:
            text = text.replace(old, new)
        return text

    rows = [
        (sign, path, norm(text))
        for sign, path, text in diff_lines(args.base, args.head, args.files or ["*.kt", "*.py"])
        if text and not IMPORT_LINE.match(text)
    ]
    pool = {"+": collections.Counter(), "-": collections.Counter()}
    for sign, _, text in rows:
        pool[sign][text] += 1

    residual = 0
    for sign, path, text in rows:
        other = pool["-" if sign == "+" else "+"]
        if other[text] > 0:
            other[text] -= 1
            continue
        if text.strip("{}(),") == "":
            continue  # lone braces and parens shift when blocks are wrapped
        residual += 1
        print(f"{sign} {path.rsplit('/', 1)[-1]}: {text[:160]}")
    print(f"--- {residual} lines changed beyond moves")


if __name__ == "__main__":
    main()
