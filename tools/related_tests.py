#!/usr/bin/env python3
"""Test files that use what a changed main source file declares.

Reads changed paths on stdin (one per line) and prints the app/src/test/**Test.kt files that
mention, as a whole word, a capitalized top-level name (class, object, interface, composable)
declared in a changed app/src/main/**/*.kt file. One note per source file with hits goes to
stderr. `tools/verify.sh --changed` uses it, so an unedited test of an edited class runs too
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

def main():
    root = pathlib.Path(__file__).resolve().parent.parent
    tests = {p: p.read_text(errors="ignore") for p in (root / "app/src/test").rglob("*Test.kt")}
    out = set()
    for line in sys.stdin:
        rel = line.strip()
        src = root / rel
        if not (rel.startswith("app/src/main/") and rel.endswith(".kt") and src.is_file()):
            continue
        names = set(DECL.findall(src.read_text(errors="ignore"))) | {src.stem}
        pattern = re.compile(r"\b(?:" + "|".join(sorted(map(re.escape, names))) + r")\b")
        hits = sorted(str(p.relative_to(root)) for p, text in tests.items()
                      if p.stem.startswith(src.stem) or pattern.search(text))
        if hits:
            short = ",".join(pathlib.Path(h).stem for h in hits[:6]) + ("..." if len(hits) > 6 else "")
            print(f"related tests: {src.name} -> {len(hits)} ({short})", file=sys.stderr)
            out.update(hits)
    print("\n".join(sorted(out)))

if __name__ == "__main__":
    main()
