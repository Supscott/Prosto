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
#   tools/device.sh open <url>      open a link the way another app does (ACTION_VIEW)
#   tools/device.sh caller          open Settings: a stand-in for "the app the link came from"
#   tools/device.sh back | home     press Back | Home
#   tools/device.sh enter           press Enter (KEYCODE_ENTER), e.g. to submit a typed search
#   tools/device.sh tap <x> <y>     tap at device pixels
#   tools/device.sh tap <url-prefix> <css-selector>
#                                   scroll the first match into view, then tap its centre with a real
#                                   touch (the page sees a user gesture); fails when nothing matches
#                                   or the centre is still off screen
#   tools/device.sh taps x1,y1 x2,y2 ...   rapid taps at device pixels, sent in ONE adb shell call
#                                   (no round trip between taps; every pair is validated first)
#   tools/device.sh ui              labelled UI nodes on screen: text | content-desc | bounds; checkable
#                                   nodes (switches) add " | checked=true|false"
#   tools/device.sh tapui <label>   tap the node whose text or content-desc is exactly <label>; exit 1
#                                   with a message when it is not on screen (dock tabs scroll out of view)
#   tools/device.sh type <text>     type into the focused field (adb input text): printable ASCII only;
#                                   "%" and non-ASCII are refused, nothing is sent
#   tools/device.sh swipe <x1> <y1> <x2> <y2> [ms]   swipe at device pixels (default 400 ms)
#   tools/device.sh top             package of the resumed activity
#   tools/device.sh pages           WebView pages over CDP: "visible <url>" or "hidden <url>"
#   tools/device.sh visible         URL of the page that is on screen
#   tools/device.sh eval <url> <js> run JS in the page whose URL starts with <url>; prints JSON
#                                   (CDP_GESTURE=1: run it as a user gesture)
#   tools/device.sh serve [stop]    serve tools/fixtures/adblock/ to the phone at http://127.0.0.1:8765/
#                                   (adb reverse; loopback needs no HTTP warning); "stop" ends it
#   tools/device.sh pill-timeout <ms>|reset   hold undo pills for <ms> (Android's accessibility timeout,
#                                   saved once); reset, or sleep, puts the saved value back
#   tools/device.sh secure off|on   debug build: off lets shot capture incognito tabs (FLAG_SECURE is on
#                                   by default and persists across restarts); turn it back on with "on"
#   tools/device.sh log <regex> [n] last n (default 20) logcat lines matching regex
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

# The current window hierarchy as uiautomator XML.
ui_dump() { adb exec-out uiautomator dump /dev/tty 2>/dev/null | sed 's/UI hierchary dumped to.*//'; }

# Captures the UI dump for command $1, or dies with "<command>: could not read the UI dump" when adb
# fails or the dump is empty. Capturing first keeps these failures apart from "label not on screen".
ui_dump_or_die() {
    local dump
    dump="$(ui_dump)" || die "$1: could not read the UI dump"
    [[ -n "$dump" ]] || die "$1: could not read the UI dump"
    printf '%s\n' "$dump"
}

pid() { adb shell pidof "$PKG" | tr -d '\r'; }

forward_cdp() {
    local p
    p="$(pid)"
    [[ -n "$p" ]] || die "App $PKG is not running"
    adb forward "tcp:$CDP_PORT" "localabstract:webview_devtools_remote_$p" >/dev/null 2>&1 \
        || die "cannot forward CDP to port $CDP_PORT (in use: lsof -nP -iTCP:$CDP_PORT); set PROSTO_CDP_PORT"
}

pages() {
    forward_cdp
    curl -s --max-time 5 "http://127.0.0.1:$CDP_PORT/json/list" | python3 -c '
import json, sys
for target in json.load(sys.stdin):
    if target.get("type") != "page":
        continue
    try:
        info = json.loads(target.get("description") or "{}")
    except ValueError:
        info = {}
    shown = info.get("visible") and info.get("attached")
    print(("visible " if shown else "hidden  ") + target.get("url", ""))
'
}

# Taps the centre of the first element matching <selector> in the page whose URL starts with
# <url>. The page reports the element's CSS rect; the WebView's bounds on screen map CSS pixels to
# device pixels (x = left + cssX * dpr). adb sends a real touch, so the page sees a user gesture.
tap_selector() {
    local url="$1" selector="$2" sel_json js page wv xy
    sel_json="$(python3 -c 'import json, sys; print(json.dumps(sys.argv[1]))' "$selector")"
    # Scrolls the element into view first (a below-the-fold element would otherwise fail the viewport
    # check), then waits one frame, capped at 300 ms so a hidden page cannot hang the tap. Awaited by
    # cdp_eval.mjs (awaitPromise).
    js="(async()=>{const e=document.querySelector($sel_json);if(!e)return JSON.stringify(null);e.scrollIntoView({block:'center',inline:'center'});await Promise.race([new Promise(r=>requestAnimationFrame(()=>r())),new Promise(r=>setTimeout(r,300))]);const b=e.getBoundingClientRect();return JSON.stringify([b.left+b.width/2,b.top+b.height/2,innerWidth,innerHeight,devicePixelRatio]);})()"
    forward_cdp
    page="$(node "$ROOT/tools/cdp_eval.mjs" "$url" "$js" "$CDP_PORT")" || die "could not read the page at $url"
    dump="$(ui_dump_or_die tap)" || exit 1
    wv="$(printf '%s\n' "$dump" | python3 "$ROOT/tools/ui_nodes.py" webview)" || die "no WebView on screen (is the page visible?)"
    xy="$(python3 "$ROOT/tools/ui_nodes.py" tap-point "$wv" "$page")" || exit 1
    # shellcheck disable=SC2086
    adb shell input tap $xy
}

# The accessibility timeout that holds undo pills on screen (PRO-134). "pill-timeout <ms>" saves the
# current value once, into its own file; "reset" and "sleep" restore it. Written through a temp file,
# like wake's screen timeout, so a failed adb read leaves no empty saved value.
PILL_SAVED="$SHOT_DIR/pill_timeout"
PILL_KEY=accessibility_interactive_ui_timeout_ms

pill_timeout_save_once() {
    local now
    if [[ -s "$PILL_SAVED" ]]; then
        return 0
    fi
    now="$(adb shell settings get secure "$PILL_KEY" 2>/dev/null | tr -d '\r[:space:]')" || now=""
    if [[ "$now" == "null" || "$now" =~ ^[1-9][0-9]*$ ]]; then
        mkdir -p "$SHOT_DIR"
        printf '%s\n' "$now" > "$PILL_SAVED.tmp" && mv "$PILL_SAVED.tmp" "$PILL_SAVED"
    else
        die "could not read the undo-pill timeout (adb answered '${now:-nothing}'): nothing changed"
    fi
}

# Puts the saved value back; "null" means the setting was unset, so it is deleted. The saved file
# is removed only after adb succeeds, so a dropped connection can retry.
pill_timeout_restore() {
    local saved
    saved="$(tr -d '[:space:]' < "$PILL_SAVED")"
    if [[ "$saved" == "null" ]]; then
        adb shell settings delete secure "$PILL_KEY"
        rm -f "$PILL_SAVED"
        echo "undo-pill timeout restored: the setting was unset before, so it is unset again"
    elif [[ "$saved" =~ ^[1-9][0-9]*$ ]]; then
        adb shell settings put secure "$PILL_KEY" "$saved"
        rm -f "$PILL_SAVED"
        echo "undo-pill timeout restored: $saved ms"
    else
        rm -f "$PILL_SAVED"
        die "saved undo-pill timeout '$saved' is not a number: nothing written (the file is removed)"
    fi
}

cmd="${1:-}"
shift || true
case "$cmd" in
    wake|sleep|install|start|stop|kill|reset|open|caller|back|home|enter|tap|taps|ui|tapui|type|swipe|top|pages|visible|eval|serve|secure|log|clearlog|shot|pill-timeout) require_device ;;
esac
case "$cmd" in
    open|tap|tapui|eval|ui|log|visible|pages) install_stamp_check "$ROOT" ;;
esac
case "$cmd" in
    wake)
        adb shell input keyevent KEYCODE_WAKEUP
        adb shell svc power stayon usb
        # "stayon usb" needs a USB cable; over wireless adb the phone locked after its 60 s timeout
        # while an install waited for the Gradle lock. Raise the timeout too; sleep restores it.
        mkdir -p "$SHOT_DIR"
        # Saved only when adb answered with a number or "null" (unset, which sleep deletes again), through
        # a temp file: a failed read leaves no empty file for sleep to report as a saved value.
        if [[ ! -s "$SHOT_DIR/screen_timeout" ]]; then
            saved_now="$(adb shell settings get system screen_off_timeout 2>/dev/null | tr -d '\r[:space:]')" || saved_now=""
            if [[ "$saved_now" == "null" || "$saved_now" =~ ^[1-9][0-9]*$ ]]; then
                printf '%s\n' "$saved_now" > "$SHOT_DIR/screen_timeout.tmp" && mv "$SHOT_DIR/screen_timeout.tmp" "$SHOT_DIR/screen_timeout"
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
        if [[ -s "$SHOT_DIR/screen_timeout" ]]; then
            saved_ms="$(tr -d '[:space:]' < "$SHOT_DIR/screen_timeout")"
            # The saved value is removed only after adb succeeds, so a dropped connection can retry.
            # Android answers "null" when the setting was never set: delete it, do not write "null".
            if [[ "$saved_ms" == "null" ]]; then
                adb shell settings delete system screen_off_timeout
                rm -f "$SHOT_DIR/screen_timeout"
                echo "normal screen timeout restored: the setting was unset before wake, so it is unset again"
            elif [[ "$saved_ms" =~ ^[1-9][0-9]*$ ]]; then
                adb shell settings put system screen_off_timeout "$saved_ms"
                rm -f "$SHOT_DIR/screen_timeout"
                echo "normal screen timeout restored: $saved_ms ms"
            else
                rm -f "$SHOT_DIR/screen_timeout"
                echo "saved screen timeout '$saved_ms' is not a positive integer: nothing written (the file is removed)" >&2
                exit 1
            fi
        else
            echo "no saved screen timeout: nothing restored (wake did not save one)"
        fi
        if [[ -s "$PILL_SAVED" ]]; then
            pill_timeout_restore
        fi
        ;;
    pill-timeout)
        case "${1:-}" in
            reset)
                if [[ -s "$PILL_SAVED" ]]; then
                    pill_timeout_restore
                else
                    echo "no saved undo-pill timeout: nothing restored"
                fi
                ;;
            *)
                [[ "${1:-}" =~ ^[1-9][0-9]*$ ]] || die "usage: device.sh pill-timeout <ms>|reset (ms: a positive integer)"
                pill_timeout_save_once
                adb shell settings put secure "$PILL_KEY" "$1"
                echo "undo-pill timeout set to $1 ms (device.sh pill-timeout reset restores it)"
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
        dump="$(ui_dump_or_die ui)" || exit 1
        printf '%s\n' "$dump" | python3 "$ROOT/tools/ui_nodes.py" list
        ;;
    tapui)
        [[ $# -ge 1 ]] || die "usage: device.sh tapui <label>"
        # Exit 1 (die) when the label is missing: with 5 tabs open the dock scrolls Home out of view.
        dump="$(ui_dump_or_die tapui)" || exit 1
        status=0
        xy="$(printf '%s\n' "$dump" | python3 "$ROOT/tools/ui_nodes.py" find "$1")" || status=$?
        case "$status" in
            0) ;;
            1) die "tapui: '$1' is not on screen; it may be scrolled out of view (for example a dock tab)" ;;
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
    eval)
        [[ $# -ge 2 ]] || die "usage: device.sh eval <url-prefix> <expression>"
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
        [[ $# -ge 1 ]] || die "usage: device.sh log <regex> [lines]"
        adb logcat -d | grep -E "$1" | tail -n "${2:-20}" || true
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
