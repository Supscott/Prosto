#!/usr/bin/env bash
# Tests tools/lib/install_stamp.sh in a throwaway git repo: no phone, no Gradle.
# Run: tools/install_stamp_test.sh. Prints one line per check; exits 1 if any check fails.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/install_stamp.sh
source "$HERE/lib/install_stamp.sh"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
fails=0

git_t() { git -C "$tmp" -c user.email=test@example.invalid -c user.name=test "$@"; }

# check <name> <silent|note|warn>: runs the check and compares the kind of stderr output.
check() {
    local name="$1" want="$2" err rc
    err="$(install_stamp_check "$tmp" 2>&1 >/dev/null)"
    rc=$?
    local got="silent"
    [[ "$err" == NOTE:* ]] && got="note"
    [[ "$err" == WARN:* ]] && got="warn"
    if [[ "$got" == "$want" && $rc -eq 0 ]]; then
        echo "PASS $name: ${err:-no output}"
    else
        echo "FAIL $name: wanted $want, got $got (rc=$rc): $err"
        fails=$((fails + 1))
    fi
}

stamp_now() { install_stamp_write "$tmp" "$(install_stamp_state "$tmp")"; }

git_t init -q
mkdir -p "$tmp/app/src"
echo "class A" >"$tmp/app/src/A.kt"
git_t add -A
git_t commit -q -m init

check "no stamp yet" note
stamp_now
check "stamp matches tree" silent
echo "// edit" >>"$tmp/app/src/A.kt"
check "tracked edit under app" warn
stamp_now
check "restamp after edit" silent
echo "class B" >"$tmp/app/src/B.kt"
check "new untracked file under app" warn
stamp_now
echo "// more" >>"$tmp/app/src/B.kt"
check "edit to untracked file" warn
stamp_now
echo "# notes" >>"$tmp/README.md"
check "change outside app ignored" silent
git_t add -A
git_t commit -q -m second
check "new commit, same tree" warn
stamp_now
check "restamp after commit" silent

echo "installed.txt: $(cat "$tmp/build/device/installed.txt" | tr '\n' ' ')"
if [[ $fails -eq 0 ]]; then
    echo "==> install_stamp: all checks passed"
else
    echo "==> install_stamp: $fails check(s) failed"
    exit 1
fi
