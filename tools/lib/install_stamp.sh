# shellcheck shell=bash
# Sourced by tools/device.sh. Records which build is on the phone and warns when the working tree
# has moved on since. Plain git and shell only (no Gradle). Nothing here may fail the caller: the
# warning goes to stderr and stdout is never touched.
#
# Stamp: build/device/installed.txt with two lines, "commit=<short sha>" and "dirty=<fingerprint>"
# (empty when no source file differs from HEAD).

# Prints the state of the repo at $1 as the two stamp lines. Prints nothing when it is not a repo.
# The fingerprint covers `git diff HEAD` for app/, gradle/ and *.kts, plus untracked files under app/
# (names and contents), so editing an uncommitted file changes it.
install_stamp_state() {
    local root="$1" commit diff untracked fp f
    commit="$(git -C "$root" rev-parse --short HEAD 2>/dev/null)" || return 1
    diff="$(git -C "$root" diff HEAD -- app gradle '*.kts' 2>/dev/null)" || return 1
    untracked="$(git -C "$root" ls-files --others --exclude-standard -- app 2>/dev/null)" || return 1
    fp=""
    if [[ -n "$diff" || -n "$untracked" ]]; then
        fp="$(
            printf '%s\n' "$diff"
            while IFS= read -r f; do
                [[ -n "$f" ]] || continue
                printf 'untracked %s\n' "$f"
                cat "$root/$f"
            done <<< "$untracked"
        )"
        fp="$(printf '%s' "$fp" | shasum -a 256 | cut -c1-12)"
    fi
    printf 'commit=%s\ndirty=%s\n' "$commit" "$fp"
}

# Writes the stamp for repo $1 from the state text $2 (captured before the build). Empty state: no-op.
install_stamp_write() {
    local root="$1" state="$2"
    [[ -n "$state" ]] || return 0
    mkdir -p "$root/build/device" 2>/dev/null || return 0
    printf '%s\n' "$state" >"$root/build/device/installed.txt" 2>/dev/null || true
    return 0
}

# "abc123" or "abc123+dirty" for a state text $1.
install_stamp_label() {
    local commit dirty
    commit="$(sed -n 's/^commit=//p' <<< "$1")"
    dirty="$(sed -n 's/^dirty=//p' <<< "$1")"
    printf '%s%s' "$commit" "${dirty:+ +dirty}"
}

# Warns on stderr when the stamp of repo $1 differs from its working tree, or notes that none exists.
# Always returns 0.
install_stamp_check() {
    local root="$1" now stamp recorded
    now="$(install_stamp_state "$root" 2>/dev/null)" || return 0
    [[ -n "$now" ]] || return 0
    stamp="$root/build/device/installed.txt"
    if [[ ! -f "$stamp" ]]; then
        echo "NOTE: no install recorded (build/device/installed.txt); run tools/device.sh install if the app is stale" >&2
        return 0
    fi
    recorded="$(cat "$stamp" 2>/dev/null || true)"
    if [[ "$recorded" != "$now" ]]; then
        echo "WARN: installed build is older than the working tree (installed $(install_stamp_label "$recorded"), now $(install_stamp_label "$now")) — run tools/device.sh install" >&2
    fi
    return 0
}
