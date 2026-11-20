#!/usr/bin/env bash
# Sourced by verify.sh, preview.sh and device.sh install. One Gradle build at a time across all
# worktrees of this repo: two parallel builds on the 8 GB machine swap for half an hour (2026-11-20).
# The lock is a directory in the shared git dir, so every worktree sees the same one. It holds the
# owner's PID: a lock whose owner is gone is taken over. Released on exit of the sourcing script.
# GRADLE_LOCK=0 skips it.
gradle_lock() {
    [[ "${GRADLE_LOCK:-1}" == "0" ]] && return 0
    local dir waited=0 owner
    dir="$(cd "$(git rev-parse --git-common-dir)" && pwd)/prosto-gradle.lock"
    until mkdir "$dir" 2>/dev/null; do
        owner="$(cat "$dir/pid" 2>/dev/null || true)"
        if [[ -n "$owner" ]] && ! kill -0 "$owner" 2>/dev/null; then
            echo "==> Gradle lock: owner $owner is gone, taking it over"
            rm -rf "$dir"
            continue
        fi
        if (( waited == 0 )); then
            echo "==> Gradle lock: another build is running (pid ${owner:-?}: $(cat "$dir/what" 2>/dev/null || echo unknown)); waiting..."
        fi
        sleep 5
        waited=$((waited + 5))
    done
    (( waited > 0 )) && echo "==> Gradle lock: acquired after ${waited}s"
    echo $$ > "$dir/pid"
    echo "$(basename "$PWD") $(basename "$0") $*" > "$dir/what"
    # shellcheck disable=SC2064
    trap "rm -rf '$dir'" EXIT
}
