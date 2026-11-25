#!/usr/bin/env bash
# Sourced by verify.sh and preview.sh; run directly it copies the files in the current worktree
# (`tools/ensure_worktree_files.sh`). A new git worktree lacks the git-ignored files that Gradle
# (local.properties: SDK path) and check_invariants.py (TASKS.md) need; copy them from the main
# checkout, or stop with a one-line message saying what is missing.
ensure_worktree_files() {
    local root="$1" main_root f missing=()
    main_root="$(cd "$root" && cd "$(git rev-parse --git-common-dir)/.." && pwd)"
    # Copy every file that exists in the main checkout, then report all that are still missing.
    for f in local.properties TASKS.md; do
        [[ -e "$root/$f" ]] && continue
        if [[ "$main_root" != "$root" && -e "$main_root/$f" ]]; then
            cp "$main_root/$f" "$root/$f"
            echo "==> Copied git-ignored $f from the main checkout ($main_root)"
        else
            missing+=("$f")
        fi
    done
    if [[ ${#missing[@]} -gt 0 ]]; then
        for f in "${missing[@]}"; do
            echo "ERROR: $root/$f is missing (git-ignored, not in the main checkout either); create it first." >&2
        done
        return 1
    fi
}

# Executed directly: work on the worktree this script lives in.
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
    if [[ -e "$root/local.properties" && -e "$root/TASKS.md" ]]; then
        echo "==> Worktree files already present (local.properties, TASKS.md); nothing to copy"
    else
        ensure_worktree_files "$root"
        exit $?
    fi
fi
