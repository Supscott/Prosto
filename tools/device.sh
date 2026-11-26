#!/usr/bin/env bash
# One-word device commands for checking Prosto on a phone over adb. Output is short text, so a
# check costs a few lines instead of a screenshot.
#
#   tools/device.sh wake            wake the screen, keep it on over USB, fail if the phone is locked
#   tools/device.sh sleep           restore the normal screen timeout (undoes "wake")
#   tools/device.sh install [--force]  build and install the debug app (retries once); records the
#                                   build in build/device/installed.txt, which later commands check.
#                                   Skipped when that stamp matches the tree and the app is installed;
#                                   --force builds anyway
#   tools/device.sh start | stop    launch from the launcher | force-stop
#   tools/device.sh kill            kill the process in the background (simulates process death)
#   tools/device.sh reset           wipe the app's data (tabs, settings) and pre-grant notifications
#   tools/device.sh perms reset [perm...]  revoke runtime permissions and clear their "don't ask again"
#                                   flags (default CAMERA RECORD_AUDIO; names without android.permission.)
#   tools/device.sh open <url>      open a link the way another app does (ACTION_VIEW)
#   tools/device.sh caller          open Settings: a stand-in for "the app the link came from"
#   tools/device.sh back | home     press Back | Home
#   tools/device.sh enter           press Enter (KEYCODE_ENTER), e.g. to submit a typed search
#   tools/device.sh tap <x> <y>     tap at device pixels
#   tools/device.sh tap <url-prefix> <css-selector>
#                                   scroll the first match into view, then tap its centre with a real
#                                   touch (the page sees a user gesture); matches whose centre is covered
#                                   by another element are skipped; fails when nothing matches or the
#                                   centre is still off screen. The WebView's bounds come from
#                                   `dumpsys activity` (uiautomator is the fallback). A native overlay over
#                                   the centre (the dock, the "Group N tabs" pill) is scrolled clear of, or
#                                   the tap fails naming it (PRO-166)
#   tools/device.sh taps x1,y1 x2,y2 ...   rapid taps at device pixels, sent in ONE adb shell call
#                                   (no round trip between taps; every pair is validated first)
#   tools/device.sh ui [--compressed] [--native]  labelled UI nodes on screen: text | content-desc | bounds;
#                                   checkable nodes (switches) add " | checked=true|false". Plain = full view
#                                   tree; --compressed = what TalkBack-style services see (accessibility checks);
#                                   --native drops the nodes inside a WebView (page content), so only the dock,
#                                   pills and prompts remain (PRO-186)
#   tools/device.sh tapui [--compressed] <label>  tap the node whose text or content-desc is exactly
#                                   <label>; exit 1 with a message when it is not on screen (dock tabs scroll
#                                   out of view). --compressed reads the same dump as "ui --compressed"
#   tools/device.sh type <text>     type into the focused field (adb input text): printable ASCII only;
#                                   "%" and non-ASCII are refused, nothing is sent
#   tools/device.sh swipe <x1> <y1> <x2> <y2> [ms]   swipe at device pixels (default 400 ms)
#   tools/device.sh top             package of the resumed activity
#   tools/device.sh pages           WebView pages over CDP: "visible <url>" or "hidden <url>"
#   tools/device.sh visible         URL of the page that is on screen
#   tools/device.sh js <url> <js>   run JS in the page whose URL starts with <url>; prints JSON
#                                   (CDP_GESTURE=1: run it as a user gesture). Use js: the worktree
#                                   guard refuses the word eval. eval <url> <js> is the same command.
#                                   Several pages can match: the visible one is used (a warning names a
#                                   hidden one when only hidden ones match). Waits up to 5 s for the page to
#                                   finish loading first (CDP_WAIT=0 skips the wait; PRO-171)
#   tools/device.sh bounds [n] [interval_ms] [class]  n samples (default 10, 100 ms apart) of the bounds of
#                                   the visible view of <class> (default NestedWebView): "<ms> <l,t,r,b>" or
#                                   "none". <class> is the simple name (NestedWebView) or the fully qualified
#                                   one (com.jestk.browser.engine.NestedWebView). One adb shell call runs them
#                                   all (PRO-169)
#   DEVICE_TIMING=1 (any tap/js/bounds command) prints each adb or CDP step's time to stderr (PRO-170)
#   tools/device.sh serve [stop]    serve tools/fixtures/adblock/ to the phone at http://127.0.0.1:8765/
#                                   (adb reverse; loopback needs no HTTP warning); "stop" ends it
#   tools/device.sh pill-timeout <ms>|reset   hold undo pills for <ms> (Android's interactive and
#                                   non-interactive accessibility timeouts, both saved once); reset,
#                                   or sleep, puts the saved values back
#   tools/device.sh secure off|on   debug build: off lets shot capture incognito tabs (FLAG_SECURE is on
#                                   by default and persists across restarts); turn it back on with "on"
#   tools/device.sh log [-i] <regex> [n]  last n (default 20) logcat lines matching regex (-i: ignore case)
#   tools/device.sh clearlog        clear logcat
#   tools/device.sh shot [name]     screenshot, downscaled to 540px wide; prints the PNG path
#
# Exit codes: 3 = phone locked (wake), 4 = BLOCKED, adb sees no device at all (reconnect or re-pair).
# PROSTO_PKG picks the package (default com.jestk.browser.debug), ANDROID_SERIAL the device.
# The user's own install (com.jestk.browser) is refused unless PROSTO_ALLOW_MAIN_APP=1.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="${PROSTO_PKG:-com.jestk.browser.debug}"
ACTIVITY="com.jestk.browser.ui.MainActivity"
# Not 9222: a desktop Chrome started with --remote-debugging-port holds it, and the page
# checks would then read the desktop browser (or nothing) instead of the phone.
CDP_PORT="${PROSTO_CDP_PORT:-9333}"
SHOT_DIR="${PROSTO_SHOT_DIR:-$ROOT/build/device}"
# Saved user settings (screen timeout, undo-pill timeout) live in the git common dir, so every worktree
# sees the same saved value and a wake in one checkout cannot save another checkout's wake value (PRO-186).
STATE_DIR="$(cd "$ROOT" && cd "$(git rev-parse --git-common-dir)" && pwd)/device-state" || STATE_DIR="$SHOT_DIR/state"

if [[ "$PKG" == "com.jestk.browser" && "${PROSTO_ALLOW_MAIN_APP:-0}" != "1" ]]; then
    echo "Refusing to drive com.jestk.browser (the user's own install). Use the debug build." >&2
    exit 2
fi

die() { echo "$*" >&2; exit 1; }

# Records the installed build and warns when the working tree is newer (see the helper).
# shellcheck source=lib/install_stamp.sh
source "$ROOT/tools/lib/install_stamp.sh"

is_locked() { adb shell dumpsys window | grep -q "isKeyguardShowing=true"; }

# Exits 4 with one BLOCKED line when adb sees no device at all (unplugged, or wireless debugging
# dropped), so "gone" is not mistaken for "locked" (exit 3). Other adb errors (more than one device,
# offline, unauthorized) are left to the command, which reports them itself.
require_device() {
    local err
    if err="$(adb get-state 2>&1 >/dev/null)"; then
        return 0
    fi
    if [[ "$err" == *"no devices/emulators found"* ]]; then
        echo "BLOCKED: no device (adb devices is empty: reconnect USB or re-pair wireless debugging)" >&2
        exit 4
    fi
    return 0
}

# Quotes a value for the device shell, which re-parses the command line: "(", "&", "?" and
# spaces in URLs would break it otherwise.
# The replacement is held in a variable: written inline, bash 3.2 (macOS) keeps its backslashes.
device_quote() { local q="'\\''"; printf "'%s'" "${1//\'/$q}"; }

# The one place that runs uiautomator dump. ui_dump_try <full|compressed> sets UI_DUMP to the XML and
# returns 0 when it holds a <hierarchy>. Otherwise it returns 1 with UI_DUMP_PROBLEM saying why, with
# uiautomator's exit status and its stderr (trimmed; a tall page makes it return nothing, PRO-148).
# --compressed keeps only the views that accessibility services see.
# Not run inside $(...): the globals must reach the caller.
ui_dump_try() {
    local mode="$1" errfile raw status=0 err
    errfile="$(mktemp)"
    if [[ "$mode" == compressed ]]; then
        raw="$(adb exec-out uiautomator dump --compressed /dev/tty 2>"$errfile")" || status=$?
    else
        raw="$(adb exec-out uiautomator dump /dev/tty 2>"$errfile")" || status=$?
    fi
    err="$(tr '\r\n' '  ' < "$errfile" | sed -E 's/ +/ /g; s/^ //; s/ $//')"
    rm -f "$errfile"
    UI_DUMP="$(printf '%s\n' "$raw" | sed 's/UI hierchary dumped to.*//')"
    UI_DUMP_PROBLEM=""
    [[ "$UI_DUMP" == *"<hierarchy"* ]] && return 0
    local detail="exit $status, stderr was empty"
    [[ -n "$err" ]] && detail="exit $status, stderr: $err"
    if [[ -z "${UI_DUMP//[[:space:]]/}" ]]; then
        UI_DUMP_PROBLEM="uiautomator dump returned nothing ($detail)"
    else
        # Show what uiautomator printed instead (first 150 chars, newlines flattened).
        local said
        said="$(printf '%s' "$UI_DUMP" | tr '\r\n' '  ' | sed -E 's/ +/ /g; s/^ //; s/ $//' | cut -c1-150)"
        UI_DUMP_PROBLEM="uiautomator dump did not return a UI hierarchy ($detail; stdout: $said)"
    fi
    return 1
}

# Prints the UI dump for command $1 (mode: full, the default, or compressed), or dies with
# "<command>: <reason>" (see ui_dump_try). Capturing first keeps these failures apart from "label not on screen".
ui_dump_or_die() {
    ui_dump_try "${2:-full}" || die "$1: $UI_DUMP_PROBLEM"
    printf '%s\n' "$UI_DUMP"
}

pid() { adb shell pidof "$PKG" | tr -d '\r'; }

# now_ms prints the wall clock in milliseconds (macOS date has no %N).
now_ms() { python3 -c 'import time; print(int(time.time() * 1000))'; }

# timed <label> <command...> runs the command and returns its status. With DEVICE_TIMING=1 it also prints
# "timing: <label> <ms>" to stderr (PRO-170). Stdout passes through, so $(...) and redirections still work.
timed() {
    local label="$1" started status=0
    shift
    if [[ "${DEVICE_TIMING:-0}" != 1 ]]; then
        "$@" || status=$?
        return "$status"
    fi
    started="$(now_ms)"
    "$@" || status=$?
    echo "timing: $label $(( $(now_ms) - started )) ms" >&2
    return "$status"
}

forward_cdp() {
    local p
    # pidof exits 1 when the app is not running; under set -e that would end the script silently (PRO-151).
    p="$(pid || true)"
    [[ -n "$p" ]] || die "App $PKG is not running"
    adb forward "tcp:$CDP_PORT" "localabstract:webview_devtools_remote_$p" >/dev/null 2>&1 \
        || die "cannot forward CDP to port $CDP_PORT (in use: lsof -nP -iTCP:$CDP_PORT); set PROSTO_CDP_PORT"
}

# Lists the WebView pages over CDP. Dies with one line when CDP gives no JSON (app not running,
# phone locked): the python step exits 1 without a traceback when the body is not a JSON list.
pages() {
    local body
    forward_cdp
    body="$(curl -s --max-time 5 "http://127.0.0.1:$CDP_PORT/json/list" || true)"
    printf '%s' "$body" | python3 -c '
import json, sys
try:
    targets = json.load(sys.stdin)
except ValueError:
    sys.exit(1)
if not isinstance(targets, list):
    sys.exit(1)
printed = 0
for target in targets:
    if target.get("type") != "page":
        continue
    try:
        info = json.loads(target.get("description") or "{}")
    except ValueError:
        info = {}
    shown = info.get("visible") and info.get("attached")
    print(("visible " if shown else "hidden  ") + target.get("url", ""))
    printed += 1
# A list with no page target is a failure too: callers must not see an empty result with exit 0.
sys.exit(0 if printed else 1)
' || die "no WebView page found (is the app running and the phone unlocked?)"
}

# Writes the activity's view hierarchy to $TAP_TMP/dumpsys; tap_selector runs it in the background (PRO-170).
# A failure leaves an empty file, and the WebView lookup then falls back to uiautomator.
dumpsys_job() {
    timed dumpsys adb shell "dumpsys activity $PKG/$ACTIVITY" 2>/dev/null > "$TAP_TMP/dumpsys.raw" || true
    tr -d '\r' < "$TAP_TMP/dumpsys.raw" > "$TAP_TMP/dumpsys"
}

# Reads the compressed uiautomator dump (the dock and the pills, PRO-166) only when device row $1 is in the
# WebView's bottom band (tap_overlays.py band, PRO-170). Uses the caller's locals wv, uia_xml, uia_checked.
read_uia_if_band() {
    [[ "$uia_checked" == 0 ]] || return 0
    python3 "$ROOT/tools/tap_overlays.py" band "$wv" "$1" || return 0
    uia_checked=1
    if timed uiautomator ui_dump_try compressed; then
        uia_xml="$UI_DUMP"
    else
        echo "tap: no UI dump, so native overlays are not checked ($UI_DUMP_PROBLEM)" >&2
    fi
}

# Taps the centre of the first visible element matching <selector> in the page whose URL starts with
# <url>. The page reports the element's CSS rect; the WebView's bounds on screen map CSS pixels to
# device pixels (x = left + cssX * dpr). adb sends a real touch, so the page sees a user gesture.
tap_selector() {
    local url="$1" selector="$2" sel_json js page before wv xy first tap_x tap_y verdict_line scroll_dy label index moved_verdict
    local dumpsys_pid uia_xml="" uia_checked=0
    sel_json="$(python3 -c 'import json, sys; print(json.dumps(sys.argv[1]))' "$selector")"
    # Walks every match and takes the first one that is shown (not display:none or visibility:hidden,
    # non-empty box). It is scrolled into view, then waits one frame, capped at 300 ms so a hidden page
    # cannot hang the tap; its centre must then be inside the viewport. A rejected candidate scrolls the
    # window back to where it was first (nested scrollers are not restored). Returns [cssX, cssY, innerWidth,
    # innerHeight, dpr, skipped], "null" when nothing matches, or {total, selector} when none is shown.
    # Awaited by cdp_eval.mjs (awaitPromise).
    js="(async()=>{const S=$sel_json;const all=[...document.querySelectorAll(S)];if(!all.length)return JSON.stringify(null);const sx=scrollX,sy=scrollY;const frame=()=>Promise.race([new Promise(r=>requestAnimationFrame(()=>r())),new Promise(r=>setTimeout(r,300))]);let skipped=0;for(const e of all){const cs=getComputedStyle(e);if(cs.display==='none'||cs.visibility==='hidden'){skipped++;continue;}const r0=e.getBoundingClientRect();if(!r0.width||!r0.height){skipped++;continue;}e.scrollIntoView({block:'center',inline:'center'});await frame();const b=e.getBoundingClientRect();const cx=b.left+b.width/2,cy=b.top+b.height/2;if(cx<0||cy<0||cx>innerWidth||cy>innerHeight){scrollTo(sx,sy);await frame();skipped++;continue;}const hit=document.elementFromPoint(cx,cy);if(!hit||!(e===hit||e.contains(hit))){scrollTo(sx,sy);await frame();skipped++;continue;}return JSON.stringify([cx,cy,innerWidth,innerHeight,devicePixelRatio,skipped]);}return JSON.stringify({total:all.length,selector:S});})()"
    forward_cdp
    # The temp files outlive this function only until the tap (or a die): the EXIT trap removes them.
    TAP_TMP="$(mktemp -d)"
    trap 'rm -rf "$TAP_TMP"' EXIT
    # dumpsys and the page scan are independent adb and CDP calls, so they run side by side (PRO-170).
    dumpsys_job &
    dumpsys_pid=$!
    page="$(timed cdp node "$ROOT/tools/cdp_eval.mjs" "$url" "$js" "$CDP_PORT")" || die "could not read the page at $url"
    wait "$dumpsys_pid" || true
    # Only the WebView's bounds are needed. The activity's view hierarchy gives them without uiautomator,
    # which returns nothing on a very tall page (PRO-148); uiautomator is the fallback, retried once
    # with --compressed (that drops the page's subtree but keeps the WebView).
    if ! wv="$(python3 "$ROOT/tools/ui_nodes.py" webview-from-dumpsys < "$TAP_TMP/dumpsys" 2>/dev/null)"; then
        if ! timed uiautomator ui_dump_try full; then
            first="$UI_DUMP_PROBLEM"
            timed uiautomator ui_dump_try compressed || die "tap: $UI_DUMP_PROBLEM (full dump: $first; dumpsys found no WebView)"
        fi
        uia_xml="$UI_DUMP"
        uia_checked=1
        wv="$(printf '%s\n' "$uia_xml" | python3 "$ROOT/tools/ui_nodes.py" webview)" || die "no WebView on screen (is the page visible?)"
    fi
    # Compose draws the dock and the pills inside one view, so dumpsys cannot show them: the compressed
    # uiautomator dump is read for them too (PRO-166), but only for a tap in the WebView's bottom band
    # (PRO-170). Without it only the view hierarchy is checked.
    xy="$(python3 "$ROOT/tools/ui_nodes.py" tap-point "$wv" "$page")" || exit 1
    read -r tap_x tap_y <<< "$xy"
    read_uia_if_band "$tap_y"
    verdict_line="$(overlay_verdict "$wv" "$tap_x" "$tap_y")"
    if [[ "$verdict_line" != clear ]]; then
        # Scroll the element so its centre moves into the part of the WebView the overlay leaves clear,
        # then measure it again. Scrolling is one step: a point still covered after it is refused.
        IFS=$'\t' read -r _ scroll_dy label <<< "$verdict_line"
        [[ "$scroll_dy" != "-" ]] || die "tap: $label covers the element, and the WebView has no part clear of it to scroll into"
        index="$(python3 "$ROOT/tools/tap_overlays.py" index "$page")" || die "tap: could not read the chosen match"
        before="$page"
        page="$(timed cdp node "$ROOT/tools/cdp_eval.mjs" "$url" "$(scroll_js "$sel_json" "$index" "$scroll_dy")" "$CDP_PORT")" \
            || die "could not read the page at $url"
        # A scroll that leaves the centre where it was means the page is at its end in that direction.
        # "moved" prints "element no longer in the page" (exit 3) when the element is gone (PRO-186).
        moved_verdict="$(python3 "$ROOT/tools/tap_overlays.py" moved "$before" "$page")" || die "tap: $moved_verdict"
        [[ "$moved_verdict" == moved ]] \
            || die "tap: $label still covers the element and the page cannot scroll further, so it cannot be tapped"
        xy="$(python3 "$ROOT/tools/ui_nodes.py" tap-point "$wv" "$page")" || exit 1
        read -r tap_x tap_y <<< "$xy"
        read_uia_if_band "$tap_y"
        verdict_line="$(overlay_verdict "$wv" "$tap_x" "$tap_y")"
        if [[ "$verdict_line" != clear ]]; then
            IFS=$'\t' read -r _ _ label <<< "$verdict_line"
            die "tap: $label still covers the element after scrolling it into the WebView; it cannot be tapped"
        fi
    fi
    timed input adb shell input tap "$tap_x" "$tap_y"
    echo "tapped $selector at $tap_x,$tap_y"
}

# Prints "clear", or "covered<TAB>scroll-dy<TAB>label" for the device point x y (tap_overlays.py plan).
overlay_verdict() {
    printf '%s\n' "$uia_xml" > "$TAP_TMP/uia.xml"
    python3 "$ROOT/tools/tap_overlays.py" plan "$1" "$2" "$3" "$TAP_TMP/dumpsys" "$TAP_TMP/uia.xml" \
        || die "tap: could not check the native overlays"
}

# The page script that scrolls the match at index $2 by $3 device pixels (down when positive) and
# measures it again, in the same shape as the first script in tap_selector.
scroll_js() {
    local sel_json="$1" index="$2" dy="$3"
    printf '%s' "(async()=>{const e=document.querySelectorAll($sel_json)[$index];if(!e)return JSON.stringify(null);scrollBy(0,$dy/devicePixelRatio);await Promise.race([new Promise(r=>requestAnimationFrame(()=>r())),new Promise(r=>setTimeout(r,300))]);const b=e.getBoundingClientRect();return JSON.stringify([b.left+b.width/2,b.top+b.height/2,innerWidth,innerHeight,devicePixelRatio,$index]);})()"
}

# The accessibility timeout that holds undo pills on screen (PRO-134). "pill-timeout <ms>" saves the
# current value once, into its own file; "reset" and "sleep" restore it. Written through a temp file,
# like wake's screen timeout, so a failed adb read leaves no empty saved value.
#
# Two settings are held: the interactive one (pills with controls or a swipe) and the non-interactive
# one (PRO-201), each saved in its own file. A pill whose content counts as non-interactive would
# otherwise ignore the hold.
PILL_SAVED="$STATE_DIR/pill_timeout"
PILL_KEY=accessibility_interactive_ui_timeout_ms
PILL_SAVED_NI="$STATE_DIR/pill_timeout_non_interactive"
PILL_KEY_NI=accessibility_non_interactive_ui_timeout_ms

# pill_setting_save_once <saved-file> <key>
pill_setting_save_once() {
    local file="$1" key="$2" now
    if [[ -s "$file" ]]; then
        return 0
    fi
    now="$(adb shell settings get secure "$key" 2>/dev/null | tr -d '\r[:space:]')" || now=""
    if [[ "$now" == "null" || "$now" =~ ^[1-9][0-9]*$ ]]; then
        mkdir -p "$STATE_DIR"
        printf '%s\n' "$now" > "$file.tmp" && mv "$file.tmp" "$file"
    else
        die "could not read $key (adb answered '${now:-nothing}'): nothing changed"
    fi
}

# Saves both settings before either is written, so a failed read changes nothing.
pill_timeout_save_once() {
    pill_setting_save_once "$PILL_SAVED" "$PILL_KEY"
    pill_setting_save_once "$PILL_SAVED_NI" "$PILL_KEY_NI"
}

# pill_setting_restore <saved-file> <key>: puts the saved value back; "null" means the setting was
# unset, so it is deleted. The saved file is removed only after adb succeeds, so a dropped
# connection can retry. Nothing happens when no value was saved.
pill_setting_restore() {
    local file="$1" key="$2" saved
    [[ -s "$file" ]] || return 0
    saved="$(tr -d '[:space:]' < "$file")"
    if [[ "$saved" == "null" ]]; then
        adb shell settings delete secure "$key"
        rm -f "$file"
        echo "$key restored: the setting was unset before, so it is unset again"
    elif [[ "$saved" =~ ^[1-9][0-9]*$ ]]; then
        adb shell settings put secure "$key" "$saved"
        rm -f "$file"
        echo "$key restored: $saved ms"
    else
        rm -f "$file"
        die "saved $key '$saved' is not a number: nothing written (the file is removed)"
    fi
}

pill_timeout_saved() {
    [[ -s "$PILL_SAVED" || -s "$PILL_SAVED_NI" ]]
}

pill_timeout_restore() {
    pill_setting_restore "$PILL_SAVED" "$PILL_KEY"
    pill_setting_restore "$PILL_SAVED_NI" "$PILL_KEY_NI"
}

cmd="${1:-}"
shift || true
case "$cmd" in
    wake|sleep|install|start|stop|kill|reset|perms|open|caller|back|home|enter|tap|taps|ui|tapui|type|swipe|top|pages|visible|eval|js|serve|secure|log|clearlog|shot|pill-timeout|bounds) require_device ;;
esac
case "$cmd" in
    open|tap|tapui|eval|js|ui|log|visible|pages) install_stamp_check "$ROOT" ;;
esac
case "$cmd" in
    wake)
        adb shell input keyevent KEYCODE_WAKEUP
        adb shell svc power stayon usb
        # "stayon usb" needs a USB cable; over wireless adb the phone locked after its 60 s timeout
        # while an install waited for the Gradle lock. Raise the timeout too; sleep restores it.
        mkdir -p "$STATE_DIR"
        # Saved only when no value is saved yet, and the current value is not the wake value already (1800000,
        # set by a wake in this or another checkout: saving it would make sleep "restore" the wake value; PRO-186).
        # Saved only when adb answered with a number or "null" (unset, which sleep deletes again), through
        # a temp file: a failed read leaves no empty file for sleep to report as a saved value.
        if [[ ! -s "$STATE_DIR/screen_timeout" ]]; then
            saved_now="$(adb shell settings get system screen_off_timeout 2>/dev/null | tr -d '\r[:space:]')" || saved_now=""
            if [[ "$saved_now" == "1800000" ]]; then
                : # already the wake value: the original is unknown, so nothing is saved
            elif [[ "$saved_now" == "null" || "$saved_now" =~ ^[1-9][0-9]*$ ]]; then
                printf '%s\n' "$saved_now" > "$STATE_DIR/screen_timeout.tmp" && mv "$STATE_DIR/screen_timeout.tmp" "$STATE_DIR/screen_timeout"
            else
                echo "could not read the screen timeout (adb answered '${saved_now:-nothing}'): not saved; sleep will restore nothing" >&2
            fi
        fi
        adb shell settings put system screen_off_timeout 1800000
        if is_locked; then
            adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
            sleep 1
        fi
        if is_locked; then
            echo "LOCKED: the phone shows its lock screen; ask the user to unlock it." >&2
            exit 3
        fi
        echo "awake, unlocked"
        ;;
    sleep)
        adb shell svc power stayon false
        if [[ -s "$STATE_DIR/screen_timeout" ]]; then
            saved_ms="$(tr -d '[:space:]' < "$STATE_DIR/screen_timeout")"
            # The saved value is removed only after adb succeeds, so a dropped connection can retry.
            # Android answers "null" when the setting was never set: delete it, do not write "null".
            if [[ "$saved_ms" == "null" ]]; then
                adb shell settings delete system screen_off_timeout
                rm -f "$STATE_DIR/screen_timeout"
                echo "normal screen timeout restored: the setting was unset before wake, so it is unset again"
            elif [[ "$saved_ms" == "1800000" ]]; then
                # Only a wake value can be saved this way (wake does not save 1800000): the original is lost.
                # Leave the setting as it is rather than guess a default.
                rm -f "$STATE_DIR/screen_timeout"
                echo "WARN: saved screen timeout is 1800000 ms (the wake value), so the original is unknown: left at 1800000 ms; set it in Settings > Display if needed" >&2
            elif [[ "$saved_ms" =~ ^[1-9][0-9]*$ ]]; then
                adb shell settings put system screen_off_timeout "$saved_ms"
                rm -f "$STATE_DIR/screen_timeout"
                echo "normal screen timeout restored: $saved_ms ms"
            else
                rm -f "$STATE_DIR/screen_timeout"
                echo "saved screen timeout '$saved_ms' is not a positive integer: nothing written (the file is removed)" >&2
                exit 1
            fi
        else
            echo "no saved screen timeout: nothing restored (wake did not save one)"
        fi
        if pill_timeout_saved; then
            pill_timeout_restore
        fi
        ;;
    pill-timeout)
        case "${1:-}" in
            reset)
                if pill_timeout_saved; then
                    pill_timeout_restore
                else
                    echo "no saved undo-pill timeout: nothing restored"
                fi
                ;;
            *)
                [[ "${1:-}" =~ ^[1-9][0-9]*$ ]] || die "usage: device.sh pill-timeout <ms>|reset (ms: a positive integer)"
                pill_timeout_save_once
                adb shell settings put secure "$PILL_KEY" "$1"
                adb shell settings put secure "$PILL_KEY_NI" "$1"
                echo "undo-pill timeouts (interactive and non-interactive) set to $1 ms (device.sh pill-timeout reset restores them)"
                ;;
        esac
        ;;
    install)
        force=0
        case "${1:-}" in
            "") ;;
            --force) force=1 ;;
            *) die "usage: device.sh install [--force]" ;;
        esac
        state="$(install_stamp_state "$ROOT" 2>/dev/null || true)"
        # Skip Gradle only when the stamp matches the working tree AND the app is on the phone.
        if [[ "$force" == 0 ]] && install_stamp_matches "$ROOT" "$state" \
            && [[ -n "$(adb shell pm path "$PKG" 2>/dev/null | tr -d '\r')" ]]; then
            echo "installed build is current; skipping (use --force)" >&2
            exit 0
        fi
        # shellcheck source=tools/gradle_lock.sh
        (cd "$ROOT" && source tools/gradle_lock.sh && gradle_lock install && { ./gradlew -q :app:installDebug || { echo "install failed, retrying once" >&2; ./gradlew -q :app:installDebug; }; })
        install_stamp_write "$ROOT" "$state"
        echo "installed $PKG"
        ;;
    start)
        adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
        ;;
    stop)
        adb shell am force-stop "$PKG"
        ;;
    kill)
        adb shell am kill "$PKG"
        ;;
    reset)
        adb shell pm clear "$PKG" >/dev/null
        # MainActivity asks for this at launch; a dialog on top would break later steps.
        adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
        echo "cleared $PKG"
        ;;
    perms)
        [[ "${1:-}" == reset ]] || die "usage: device.sh perms reset [permission...] (default CAMERA RECORD_AUDIO)"
        shift
        perms_list=("$@")
        [[ ${#perms_list[@]} -gt 0 ]] || perms_list=(CAMERA RECORD_AUDIO)
        # Every name is checked before anything is revoked.
        for perm in "${perms_list[@]}"; do
            [[ "$perm" =~ ^[A-Z][A-Z0-9_]*$ ]] || die "perms: '$perm' is not a permission name (e.g. CAMERA; android.permission. is added)"
        done
        for perm in "${perms_list[@]}"; do
            # pm revoke fails when the permission was never granted: that is the state we want, so go on.
            adb shell pm revoke "$PKG" "android.permission.$perm" 2>/dev/null || true
            adb shell pm clear-permission-flags "$PKG" "android.permission.$perm" user-fixed user-set
            echo "reset android.permission.$perm for $PKG"
        done
        ;;
    open)
        [[ $# -ge 1 ]] || die "usage: device.sh open <url>"
        adb shell "am start -a android.intent.action.VIEW -d $(device_quote "$1") $PKG" >/dev/null
        ;;
    caller)
        adb shell am start -a android.settings.SETTINGS >/dev/null
        ;;
    back)
        adb shell input keyevent KEYCODE_BACK
        ;;
    home)
        adb shell input keyevent KEYCODE_HOME
        ;;
    enter)
        adb shell input keyevent KEYCODE_ENTER
        ;;
    tap)
        [[ $# -ge 2 ]] || die "usage: device.sh tap <x> <y> | device.sh tap <url-prefix> <css-selector>"
        if [[ "$1" =~ ^[0-9]+$ ]]; then
            [[ "$2" =~ ^[0-9]+$ ]] || die "tap: <x> and <y> must be device pixels; a selector needs <url-prefix> first"
            adb shell input tap "$1" "$2"
        else
            tap_selector "$1" "$2"
        fi
        ;;
    taps)
        [[ $# -ge 1 ]] || die "usage: device.sh taps x1,y1 x2,y2 ..."
        # Validate every pair before the first tap is sent.
        taps_script=""
        for pair in "$@"; do
            [[ "$pair" =~ ^([0-9]+),([0-9]+)$ ]] || die "taps: '$pair' is not x,y (two integers, device pixels)"
            taps_script+="input tap ${BASH_REMATCH[1]} ${BASH_REMATCH[2]}; "
        done
        adb shell "$taps_script"
        ;;
    ui)
        mode=full
        native=""
        for arg in "$@"; do
            case "$arg" in
                --compressed) mode=compressed ;;
                --native) native="--native" ;;
                *) die "usage: device.sh ui [--compressed] [--native]" ;;
            esac
        done
        dump="$(ui_dump_or_die ui "$mode")" || exit 1
        printf '%s\n' "$dump" | python3 "$ROOT/tools/ui_nodes.py" list $native
        ;;
    tapui)
        mode=full
        if [[ "${1:-}" == "--compressed" ]]; then
            mode=compressed
            shift
        fi
        [[ $# -ge 1 ]] || die "usage: device.sh tapui [--compressed] <label>"
        # Exit 1 (die) when the label is missing: with 5 tabs open the dock scrolls Home out of view.
        dump="$(ui_dump_or_die tapui "$mode")" || exit 1
        status=0
        xy="$(printf '%s\n' "$dump" | python3 "$ROOT/tools/ui_nodes.py" find "$1")" || status=$?
        case "$status" in
            0) ;;
            1) die "tapui: '$1' is not on screen; it may be scrolled out of view (for example a dock tab)" ;;
            # Still reachable: ui_dump_try only checks for "<hierarchy", so a truncated dump gets here (ui_nodes exit 2).
            *) die "tapui: could not read the UI dump (it is not valid XML)" ;;
        esac
        # shellcheck disable=SC2086
        adb shell input tap $xy
        ;;
    type)
        [[ $# -ge 1 ]] || die "usage: device.sh type <text>"
        text="$*"
        [[ -n "$text" ]] || die "type: nothing to type; nothing was sent"
        # input text takes printable ASCII only: C locale, so every byte of a non-ASCII character
        # counts as non-printable; tabs and newlines are refused too.
        if (LC_ALL=C; [[ "$text" == *[![:print:]]* ]]); then
            die "type: input text can only type printable ASCII (no non-ASCII, tabs or newlines); nothing was sent"
        fi
        # input text reads "%s" as a space, so a "%" cannot be typed literally.
        [[ "$text" != *%* ]] || die "type: '%' cannot be typed (input text reads %s as a space); nothing was sent"
        # A space becomes %s, which input text turns back into a space. device_quote keeps every other
        # character literal through the device shell.
        adb shell "input text $(device_quote "${text// /%s}")"
        ;;
    swipe)
        [[ $# -ge 4 ]] || die "usage: device.sh swipe <x1> <y1> <x2> <y2> [ms]"
        adb shell input swipe "$1" "$2" "$3" "$4" "${5:-400}"
        ;;
    top)
        adb shell dumpsys activity activities | grep -m1 -E "(topResumedActivity|ResumedActivity)" | sed -E 's/.* ([a-zA-Z0-9_.]+)\/.*/\1/'
        ;;
    pages)
        pages
        ;;
    visible)
        pages | sed -n 's/^visible //p' | head -1
        ;;
    bounds)
        n="${1:-10}"
        interval="${2:-100}"
        cls="${3:-NestedWebView}"
        [[ "$n" =~ ^[1-9][0-9]*$ ]] || die "usage: device.sh bounds [n=10] [interval_ms=100] [class=NestedWebView]"
        [[ "$interval" =~ ^[0-9]+$ ]] || die "bounds: the interval is a whole number of milliseconds"
        [[ "$cls" =~ ^[A-Za-z_][A-Za-z0-9_.]*$ ]] || die "bounds: the class is a simple or fully qualified class name, e.g. NestedWebView"
        # One adb shell call runs every sample, so no adb round trip separates two reads. Each sample starts
        # with the device uptime ("@@ <seconds>", /proc/uptime: 10 ms steps); the pause is fractional seconds.
        pause="$(printf '%d.%03d' $((interval / 1000)) $((interval % 1000)))"
        loop="i=0; while [ \$i -lt $n ]; do echo \"@@ \$(cut -d' ' -f1 /proc/uptime)\"; dumpsys activity $PKG/$ACTIVITY; i=\$((i + 1)); if [ \$i -lt $n ]; then sleep $pause; fi; done"
        adb shell "$loop" | tr -d '\r' | python3 "$ROOT/tools/view_bounds.py" "$cls"
        ;;
    eval|js)
        [[ $# -ge 2 ]] || die "usage: device.sh js <url-prefix> <expression>"
        forward_cdp
        node "$ROOT/tools/cdp_eval.mjs" "$1" "$2" "$CDP_PORT"
        ;;
    serve)
        port="${FIXTURE_PORT:-8765}"
        if [[ "${1:-}" == "stop" ]]; then
            pkill -f "fixture_server.py --port $port" || true
            adb reverse --remove "tcp:$port" 2>/dev/null || true
            echo "stopped"
            exit 0
        fi
        if ! curl -s -o /dev/null "http://127.0.0.1:$port/__hits"; then
            mkdir -p "$SHOT_DIR"
            nohup python3 "$ROOT/tools/fixture_server.py" --port "$port" >"$SHOT_DIR/fixture_server.log" 2>&1 &
            for _ in 1 2 3 4 5 6 7 8 9 10; do curl -s -o /dev/null "http://127.0.0.1:$port/__hits" && break; sleep 0.3; done
        fi
        adb reverse "tcp:$port" "tcp:$port" >/dev/null
        # Debug builds load the fixture's filter list from this URL (prosto-debug://add-list, PRO-56).
        adb shell "am start -a android.intent.action.VIEW -d $(device_quote "prosto-debug://add-list?url=http%3A%2F%2F127.0.0.1%3A$port%2Ffixture-filters.txt") $PKG" >/dev/null
        echo "http://127.0.0.1:$port/"
        ;;
    secure)
        case "${1:-}" in
            off) value=0 ;;
            on) value=1 ;;
            *) die "usage: device.sh secure off|on" ;;
        esac
        # Debug builds only (PRO-82): FLAG_SECURE stays on by default; "off" lets shot capture incognito.
        adb shell "am start -a android.intent.action.VIEW -d $(device_quote "prosto-debug://secure?enabled=$value") $PKG" >/dev/null 2>&1
        # The flag changes a moment after the intent; wait for it so an immediate shot is not black.
        # Only checkable while an incognito tab is active (the flag is never set otherwise).
        for _ in 1 2 3 4 5 6 7 8 9 10; do
            secure_now="$(adb shell dumpsys window windows | grep -A12 "Window{.*$PKG/" | grep -c SECURE || true)"
            [[ "$value" == 0 && "$secure_now" == 0 ]] && break
            [[ "$value" == 1 && "$secure_now" != 0 ]] && break
            sleep 0.3
        done
        echo "FLAG_SECURE $1 (incognito)"
        ;;
    log)
        grep_flags=-E
        if [[ "${1:-}" == "-i" ]]; then
            grep_flags=-Ei
            shift
        fi
        [[ $# -ge 1 ]] || die "usage: device.sh log [-i] <regex> [lines]"
        adb logcat -d | grep "$grep_flags" -e "$1" | tail -n "${2:-20}" || true
        ;;
    clearlog)
        adb logcat -c
        ;;
    shot)
        mkdir -p "$SHOT_DIR"
        out="$SHOT_DIR/${1:-shot}.png"
        adb exec-out screencap -p >"$out"
        sips --resampleWidth 540 "$out" >/dev/null 2>&1 || true
        echo "$out"
        ;;
    *)
        sed -n '2,/^[^#]/p' "${BASH_SOURCE[0]}" | grep '^#' | sed 's/^# \{0,1\}//'
        exit 1
        ;;
esac
