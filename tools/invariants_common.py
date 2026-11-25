"""Paths and helpers shared by the invariant check modules (tools/invariants_*.py)."""

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAIN_DIR = ROOT / "app" / "src" / "main"


def code_lines(file):
    for lineno, line in enumerate(file.read_text(encoding="utf-8").splitlines(), 1):
        yield lineno, line
