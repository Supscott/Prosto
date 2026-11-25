"""Repository-hygiene invariants: no local home paths in tracked files, executable tracked scripts,
and a short TASKS.md."""

import subprocess

from invariants_common import ROOT

TASKS_FILE = ROOT / "TASKS.md"
MAX_TASKS_LINES = 40


def check_tasks_file_size():
    """TASKS.md stays short enough to read at the start of every task (done items are deleted)."""
    if not TASKS_FILE.exists():
        return ["TASKS.md is missing"]
    lines = len(TASKS_FILE.read_text(encoding="utf-8").splitlines())
    if lines > MAX_TASKS_LINES:
        return [f"TASKS.md: {lines} lines, over {MAX_TASKS_LINES}. Delete done tasks; git log is the history."]
    return []


def check_no_local_home_paths():
    """The repo is public: no tracked file (binaries included) may carry a macOS home path.

    Use repo-relative paths, or derive the main checkout from `git rev-parse --git-common-dir`.
    Native libraries are built with --remap-path-prefix (tools/build_native.sh).
    """
    pattern = "/" + "Users/[^/[:space:]]+/"
    result = subprocess.run(
        ["git", "grep", "-l", "-E", pattern],
        cwd=ROOT, capture_output=True, text=True,
    )
    return [
        f"{path}: contains a local home path ({pattern}); use a repo-relative path"
        for path in result.stdout.splitlines()
    ]


def check_script_modes():
    """Every git-tracked tools/**/*.sh with a shebang must be mode 100755.

    Modes come from the index (`git ls-files -s`), so a missing `chmod +x` plus
    `git update-index --chmod=+x` shows up even when the working copy is executable.
    """
    result = subprocess.run(
        ["git", "ls-files", "-s", "--", "tools"],
        cwd=ROOT, capture_output=True, text=True,
    )
    errors = []
    for line in result.stdout.splitlines():
        # Format: "<mode> <object> <stage>\t<path>"
        meta, _, rel = line.partition("\t")
        mode = meta.split()[0] if meta else ""
        # A tracked script deleted in the working tree has no first line to read.
        if not rel.endswith(".sh") or not (ROOT / rel).is_file():
            continue
        with open(ROOT / rel, "rb") as handle:
            first_line = handle.readline()
        if not first_line.startswith(b"#!"):
            continue
        if mode != "100755":
            errors.append(f"{rel}: has a shebang but index mode is {mode}; run chmod +x and git update-index --chmod=+x")
    return errors
