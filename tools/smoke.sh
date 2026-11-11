#!/usr/bin/env bash
# Device smoke test for what unit tests cannot see: the WebView on screen, the task stack and
# intent delivery. Prints one PASS/FAIL line per step and exits non-zero on any failure.
#
#   tools/smoke.sh [--no-install]
#
# Builds and installs the debug app first, so the phone never runs a stale build; pass
# --no-install to test what is already installed.
#
# Steps:
#   1. A link opened from another app shows its own page.
#   2. A second link shows its page, not the first one (tab switch draws the right WebView).
#   3. Back at the start of a link's history returns to the app the link came from.
#   4. Reopening the browser shows the previous link's live tab (switch to an existing WebView).
#   5. A link that cold-started the app is not opened again after process death + relaunch.
#
# Needs an unlocked phone with network. Drives only the debug app (see tools/device.sh).
set -uo pipefail

D="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/device.sh"
URL_A="https://example.com/"
URL_B="https://www.iana.org/help/example-domains"
URL_C="https://www.wikipedia.org/"
PAGE_TIMEOUT_S=20
failures=0

pass() { echo "PASS $*"; }
fail() { echo "FAIL $*"; failures=$((failures + 1)); }

# Waits until the page on screen starts with the expected URL prefix.
expect_visible() {
    local expected="$1" step="$2" shown=""
    for _ in $(seq "$PAGE_TIMEOUT_S"); do
        shown="$("$D" visible 2>/dev/null || true)"
        [[ "$shown" == "$expected"* ]] && { pass "$step ($shown)"; return; }
        sleep 1
    done
    fail "$step: expected $expected on screen, got '${shown:-nothing}'"
}

expect_top() {
    local expected="$1" step="$2" top=""
    for _ in 1 2 3 4 5; do
        top="$("$D" top)"
        [[ "$top" == "$expected" ]] && { pass "$step"; return; }
        sleep 1
    done
    fail "$step: expected $expected in front, got '${top:-nothing}'"
}

created_tabs() { "$D" log "TabCoordinator: Created tab" 1000 | wc -l | tr -d ' '; }

"$D" wake || exit 3
[[ "${1:-}" == "--no-install" ]] || "$D" install || exit 1
caller_pkg="com.android.settings"

"$D" stop
"$D" start
sleep 4
"$D" caller
sleep 2

"$D" open "$URL_A"
expect_visible "$URL_A" "1. link from another app shows its page"

"$D" open "$URL_B"
expect_visible "$URL_B" "2. second link shows its own page"

"$D" back
expect_top "$caller_pkg" "3. Back returns to the app the link came from"

"$D" start
expect_visible "$URL_A" "4. reopened browser shows the previous live tab"
"$D" back
expect_top "$caller_pkg" "4b. Back in that tab returns to the caller too"

"$D" stop
"$D" clearlog
"$D" open "$URL_C"
expect_visible "$URL_C" "5a. cold start from a link"
before="$(created_tabs)"
"$D" home
sleep 2
"$D" kill
sleep 1
"$D" start
sleep 6
after="$(created_tabs)"
if [[ "$before" == "1" && "$after" == "1" ]]; then
    pass "5. relaunch after process death does not reopen the link"
else
    fail "5. tabs created: $before at cold start, $after after relaunch (want 1 and 1)"
fi
# The cold-start tab lost its "external" mark with the process; Back just closes it.
"$D" back

echo
if [[ $failures -eq 0 ]]; then
    echo "SMOKE PASSED"
else
    echo "SMOKE FAILED: $failures step(s)"
    exit 1
fi
