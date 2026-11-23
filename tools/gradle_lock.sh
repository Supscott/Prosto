#!/usr/bin/env bash
# Sourced by verify.sh, preview.sh and device.sh install, or run as a wrapper for a bare Gradle
# command: `tools/gradle_lock.sh ./gradlew testDebugUnitTest --tests X`. One Gradle build at a time across all
# worktrees of this repo: two parallel builds on the 8 GB machine swap for half an hour (2026-11-20).
# The lock is a directory in the shared git dir, so every worktree sees the same one. It holds the
# owner's PID: a lock whose owner is gone is taken over. Released on exit of the sourcing script.
# GRADLE_LOCK=0 skips it.
# Prints one line per Gradle test JVM ("Gradle Test Executor"): elapsed time and %CPU. A test JVM
# can hang at 0% CPU while holding the lock (2026-11-22), so a waiter can see it. The JVM is a child
# of the Gradle daemon, not of the lock owner, so it is found by its command line. ps accepts the
# same -o keywords on macOS and Linux.
print_test_jvms() {
    local pid info etime cpu
    for pid in $(pgrep -f "Gradle Test Executor" 2>/dev/null || true); do
        info="$(ps -o etime=,%cpu= -p "$pid" 2>/dev/null || true)"
        [[ -n "$info" ]] || continue
        read -r etime cpu <<< "$info"
        echo "    test JVM pid $pid: elapsed $etime, ${cpu}% CPU"
    done
}

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
        if (( waited % 120 == 0 )); then
            echo "==> Gradle lock: still waiting after ${waited}s (pid ${owner:-?}: $(cat "$dir/what" 2>/dev/null || echo unknown))"
            print_test_jvms
        fi
    done
    (( waited > 0 )) && echo "==> Gradle lock: acquired after ${waited}s"
    echo $$ > "$dir/pid"
    echo "$(basename "$PWD") $(basename "$0") $*" > "$dir/what"
    # shellcheck disable=SC2064
    trap "rm -rf '$dir'" EXIT
}

# Run directly: take the lock, run the given command, release on exit.
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    [[ $# -ge 1 ]] || { echo "usage: tools/gradle_lock.sh <command...>" >&2; exit 2; }
    gradle_lock "$@"
    "$@"
fi
