#!/usr/bin/env bash
# Device smoke test for what unit tests cannot see: the WebView on screen, the task stack and
# intent delivery. Prints one PASS/FAIL line per step and exits non-zero on any failure.
#
#   tools/smoke.sh [--no-install] [--keep-state]
#
# Builds and installs the debug app first, so the phone never runs a stale build; pass
# --no-install to test what is already installed. The app's data is wiped first (leftover tabs
# change which tab is shown in step 4); pass --keep-state to keep it.
#
# Steps:
#   1. A link opened from another app shows its own page.
#   2. A second link shows its page, not the first one (tab switch draws the right WebView).
#   3. Back at the start of a link's history returns to the app the link came from.
#   4. Reopening the browser shows the previous link's live tab (switch to an existing WebView).
#   5. A link that cold-started the app is not opened again after process death + relaunch.
#   6. Tapping another tab in the dock shows that tab's page.
#   7. Back restores the previous page from the back/forward cache, and the dock shows its title
#      (no onPageFinished there; see LESSONS.md, "back/forward cache").
#   8. Pulling the page down reloads it.
#
# Needs an unlocked phone with network. Drives only the debug app (see tools/device.sh).
set -uo pipefail

D="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/device.sh"
URL_A="https://example.com/"
URL_B="https://www.iana.org/help/example-domains"
URL_C="https://www.wikipedia.org/"
URL_D="https://www.iana.org/domains"
TITLE_A="Example Domain"
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
install=1
reset=1
for arg in "$@"; do
    case "$arg" in
        --no-install) install=0 ;;
        --keep-state) reset=0 ;;
        *) echo "unknown option: $arg" >&2; exit 2 ;;
    esac
done
[[ $install -eq 0 ]] || "$D" install || exit 1
"$D" stop
[[ $reset -eq 0 ]] || "$D" reset
caller_pkg="com.android.settings"

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

# Steps 3 and 4b may have closed the link tabs; open both again.
"$D" open "$URL_A"
expect_visible "$URL_A" "6a. link opens a tab"
"$D" open "$URL_B"
expect_visible "$URL_B" "6b. second link opens a second tab"
sleep 2
if "$D" tapui "$TITLE_A"; then
    expect_visible "$URL_A" "6. dock tap switches to the other tab"
else
    fail "6. no dock tab labelled '$TITLE_A'"
fi

"$D" eval "$URL_A" "addEventListener('pageshow', e => { window.__smokeRestored = e.persisted }), 'ok'" >/dev/null
CDP_GESTURE=1 "$D" eval "$URL_A" "location.href = '$URL_D', 'ok'" >/dev/null
expect_visible "$URL_D" "7a. same-tab navigation"
sleep 2
"$D" back
expect_visible "$URL_A" "7b. Back shows the previous page"
restored="$("$D" eval "$URL_A" "String(window.__smokeRestored)")"
[[ "$restored" == '"true"' ]] || echo "note: page was reloaded, not restored from the back/forward cache ($restored)"
title_shown=0
for _ in 1 2 3 4 5; do
    "$D" ui | grep -q "^$TITLE_A |" && { title_shown=1; break; }
    sleep 1
done
if [[ $title_shown -eq 1 ]]; then pass "7. dock shows '$TITLE_A' after Back"; else fail "7. dock title not updated after Back"; fi

"$D" eval "$URL_A" "window.__smokeMark = 1, 'ok'" >/dev/null
read -r width height < <(adb shell wm size | sed -E 's/.* ([0-9]+)x([0-9]+).*/\1 \2/')
# The pull must start within the top 120 dp of the page (NestedWebView.MAX_PTR_INITIAL_DOWN_Y_DP).
"$D" swipe $((width / 2)) $((height / 11)) $((width / 2)) $((height * 3 / 5)) 600
reloaded=0
for _ in $(seq "$PAGE_TIMEOUT_S"); do
    sleep 1
    [[ "$("$D" eval "$URL_A" "typeof window.__smokeMark" 2>/dev/null)" == '"undefined"' ]] && { reloaded=1; break; }
done
if [[ $reloaded -eq 1 ]]; then pass "8. pull-to-refresh reloads the page"; else fail "8. pull-to-refresh did not reload $URL_A"; fi

echo
if [[ $failures -eq 0 ]]; then
    echo "SMOKE PASSED"
else
    echo "SMOKE FAILED: $failures step(s)"
    exit 1
fi
