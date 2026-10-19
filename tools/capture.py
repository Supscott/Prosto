#!/usr/bin/env python3
"""Capture the visual preview artifact for the Harness^2 gate.

The preview PNG is a *side effect* of a Robolectric test, so Gradle keeps the
test task UP-TO-DATE and never rewrites the file. Copying it unconditionally
would diff a stale frame against the baseline and report a false PASS.
The source is therefore deleted first and the test is forced to re-render.
"""

import hashlib
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "app" / "build" / "bottom_bar_preview.png"
DEST = ROOT / "runs" / "shots" / "bottom_bar.png"
PREVIEW_TEST = (
    "com.jestk.browser.ui.navigation.BottomBarPreviewTest.renderSimulatedScreen"
)


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def capture() -> int:
    DEST.parent.mkdir(parents=True, exist_ok=True)

    # A side-effect artifact is never invalidated by Gradle, so drop it: a stale
    # frame must never be mistaken for the current rendering.
    SOURCE.unlink(missing_ok=True)

    cmd = [
        "./gradlew",
        ":app:testDebugUnitTest",
        "--tests",
        PREVIEW_TEST,
        "--rerun-tasks",
        "--console=plain",
    ]
    res = subprocess.run(cmd, cwd=ROOT)
    if res.returncode != 0:
        print(f"Error: preview test failed ({PREVIEW_TEST}).")
        return res.returncode

    if not SOURCE.exists():
        print(f"Error: preview test did not produce {SOURCE.relative_to(ROOT)}.")
        return 1

    if DEST.exists():
        status = "unchanged" if _sha256(DEST) == _sha256(SOURCE) else "changed"
    else:
        status = "created"

    shutil.copy2(SOURCE, DEST)
    print(
        f"Capture successful: {DEST.relative_to(ROOT)} "
        f"({DEST.stat().st_size} bytes, {status})"
    )
    return 0


if __name__ == "__main__":
    sys.exit(capture())
