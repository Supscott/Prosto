#!/usr/bin/env python3
"""Test files that use what a changed main source file declares.

Reads changed paths on stdin (one per line) and prints the app/src/test/**Test.kt files that
mention, as a whole word, a capitalized top-level name (class, object, interface, composable)
declared in a changed app/src/main/**/*.kt file. It also takes one hop further: the main files
that use a name declared in a changed file (its capitalized names and its lowercase extension
functions such as `Modifier.swipeToDismissPill`) are callers, and their tests are added the same
way (a hub file with more than MAX_CALLERS callers skips this hop). One note per source file with
hits goes to stderr. `tools/verify.sh --changed` uses it, so an unedited test of an edited class runs too
(a PRO-58/PRO-60 change once broke two such tests while --changed stayed green).
"""
import pathlib
import re
import sys

DECL = re.compile(
    r"^(?:(?:public|internal|private|data|sealed|enum|abstract|open|value|annotation|inline)\s+)*"
    r"(?:fun|class|object|interface)\s+(?:<[^>]*>\s*)?(?:[A-Za-z0-9_.]+\.)?([A-Z][A-Za-z0-9_]+)(?![A-Za-z0-9_.])",
    re.MULTILINE,
)

# Lowercase extension functions (`fun Modifier.swipeToDismissPill(`), which DECL skips.
EXT_DECL = re.compile(
    r"^(?:(?:public|internal|private|inline)\s+)*fun\s+(?:<[^>]*>\s*)?[A-Za-z0-9_.]+\.([a-z][A-Za-z0-9_]*)\s*\(",
    re.MULTILINE,
)

# A changed file with more main callers than this is a hub: its second hop is skipped.
MAX_CALLERS = 12

def name_pattern(names):
    return re.compile(r"\b(?:" + "|".join(sorted(map(re.escape, names))) + r")\b")

def short_list(paths):
    stems = [pathlib.Path(p).stem for p in paths]
    return ",".join(stems[:6]) + ("..." if len(stems) > 6 else "")

def tests_for(tests, root, stem, names):
    pattern = name_pattern(names)
    return sorted(str(p.relative_to(root)) for p, text in tests.items()
                  if p.stem.startswith(stem) or pattern.search(text))

def main():
    root = pathlib.Path(__file__).resolve().parent.parent
    tests = {p: p.read_text(errors="ignore") for p in (root / "app/src/test").rglob("*Test.kt")}
    sources = {p: p.read_text(errors="ignore") for p in (root / "app/src/main").rglob("*.kt")}
    out = set()
    for line in sys.stdin:
        rel = line.strip()
        src = root / rel
        if not (rel.startswith("app/src/main/") and rel.endswith(".kt") and src.is_file()):
            continue
        text = src.read_text(errors="ignore")
        names = set(DECL.findall(text)) | {src.stem}
        hits = tests_for(tests, root, src.stem, names)
        if hits:
            print(f"related tests: {src.name} -> {len(hits)} ({short_list(hits)})", file=sys.stderr)
            out.update(hits)
        # Second hop: main files that use what this file declares, and their tests.
        caller_pattern = name_pattern(names | set(EXT_DECL.findall(text)))
        callers = [p for p, body in sources.items() if p != src and caller_pattern.search(body)]
        if len(callers) > MAX_CALLERS:
            print(f"related tests: {src.name} -> hub file, second hop skipped ({len(callers)} callers)",
                  file=sys.stderr)
            continue
        second = set()
        for caller in callers:
            second.update(tests_for(tests, root, caller.stem,
                                    set(DECL.findall(sources[caller])) | {caller.stem}))
        if second:
            print(f"related tests: {src.name} -> via callers {len(callers)} ({short_list(sorted(second))})",
                  file=sys.stderr)
            out.update(second)
    print("\n".join(sorted(out)))

if __name__ == "__main__":
    main()
