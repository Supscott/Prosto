#!/usr/bin/env bash
# One-word device commands for checking Prosto on a phone over adb. Output is short text, so a
# check costs a few lines instead of a screenshot.
#
#   tools/device.sh wake            wake the screen, keep it on over USB, fail if the phone is locked
#   tools/device.sh sleep           restore the normal screen timeout (undoes "wake")
#   tools/device.sh install         build and install the debug app (retries once)
#   tools/device.sh start | stop    launch from the launcher | force-stop
#   tools/device.sh kill            kill the process in the background (simulates process death)
#   tools/device.sh reset           wipe the app's data (tabs, settings) and pre-grant notifications
#   tools/device.sh open <url>      open a link the way another app does (ACTION_VIEW)
#   tools/device.sh caller          open Settings: a stand-in for "the app the link came from"
#   tools/device.sh back | home     press Back | Home
#   tools/device.sh tap <x> <y>     tap at device pixels
#   tools/device.sh tap <url-prefix> <css-selector>
#                                   tap the centre of the first match with a real touch (the page
#                                   sees a user gesture); fails when nothing matches or the centre
#                                   is off screen
#   tools/device.sh ui              labelled UI nodes on screen: text | content-desc | bounds
#   tools/device.sh tapui <label>   tap the node whose text or content-desc is exactly <label>
#   tools/device.sh swipe <x1> <y1> <x2> <y2> [ms]   swipe at device pixels (default 400 ms)
#   tools/device.sh top             package of the resumed activity
#   tools/device.sh pages           WebView pages over CDP: "visible <url>" or "hidden <url>"
#   tools/device.sh visible         URL of the page that is on screen
#   tools/device.sh eval <url> <js> run JS in the page whose URL starts with <url>; prints JSON
#                                   (CDP_GESTURE=1: run it as a user gesture)
#   tools/device.sh serve [stop]    serve tools/fixtures/adblock/ to the phone at http://127.0.0.1:8765/
#                                   (adb reverse; loopback needs no HTTP warning); "stop" ends it
#   tools/device.sh log <regex> [n] last n (default 20) logcat lines matching regex
#   tools/device.sh clearlog        clear logcat
#   tools/device.sh shot [name]     screenshot, downscaled to 540px wide; prints the PNG path
#
# PROSTO_PKG picks the package (default com.jestk.browser.debug), ANDROID_SERIAL the device.
# The user's own install (com.jestk.browser) is refused unless PROSTO_ALLOW_MAIN_APP=1.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="${PROSTO_PKG:-com.jestk.browser.debug}"
ACTIVITY="com.jestk.browser.ui.MainActivity"
CDP_PORT="${PROSTO_CDP_PORT:-9222}"
SHOT_DIR="${PROSTO_SHOT_DIR:-$ROOT/build/device}"

if [[ "$PKG" == "com.jestk.browser" && "${PROSTO_ALLOW_MAIN_APP:-0}" != "1" ]]; then
    echo "Refusing to drive com.jestk.browser (the user's own install). Use the debug build." >&2
    exit 2
fi

die() { echo "$*" >&2; exit 1; }

is_locked() { adb shell dumpsys window | grep -q "isKeyguardShowing=true"; }

# Quotes a value for the device shell, which re-parses the command line: "(", "&", "?" and
# spaces in URLs would break it otherwise.
device_quote() { printf "'%s'" "${1//\'/\'\\\'\'}"; }

# The current window hierarchy as uiautomator XML.
ui_dump() { adb exec-out uiautomator dump /dev/tty 2>/dev/null | sed 's/UI hierchary dumped to.*//'; }

pid() { adb shell pidof "$PKG" | tr -d '\r'; }

forward_cdp() {
    local p
    p="$(pid)"
    [[ -n "$p" ]] || die "App $PKG is not running"
    adb forward "tcp:$CDP_PORT" "localabstract:webview_devtools_remote_$p" >/dev/null
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
    js="JSON.stringify((e=>e?(r=>[r.left+r.width/2,r.top+r.height/2,innerWidth,innerHeight])(e.getBoundingClientRect()).concat(devicePixelRatio):null)(document.querySelector($sel_json)))"
    forward_cdp
    page="$(node "$ROOT/tools/cdp_eval.mjs" "$url" "$js" "$CDP_PORT")" || die "could not read the page at $url"
    wv="$(ui_dump | python3 "$ROOT/tools/ui_nodes.py" webview)" || die "no WebView on screen (is the page visible?)"
    xy="$(python3 "$ROOT/tools/ui_nodes.py" tap-point "$wv" "$page")" || exit 1
    # shellcheck disable=SC2086
    adb shell input tap $xy
}

cmd="${1:-}"
shift || true
case "$cmd" in
    wake)
        adb shell input keyevent KEYCODE_WAKEUP
        adb shell svc power stayon usb
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
        echo "normal screen timeout restored"
        ;;
    install)
        (cd "$ROOT" && { ./gradlew -q :app:installDebug || { echo "install failed, retrying once" >&2; ./gradlew -q :app:installDebug; }; })
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
    tap)
        [[ $# -ge 2 ]] || die "usage: device.sh tap <x> <y> | device.sh tap <url-prefix> <css-selector>"
        if [[ "$1" =~ ^[0-9]+$ ]]; then
            [[ "$2" =~ ^[0-9]+$ ]] || die "tap: <x> and <y> must be device pixels; a selector needs <url-prefix> first"
            adb shell input tap "$1" "$2"
        else
            tap_selector "$1" "$2"
        fi
        ;;
    ui)
        ui_dump | python3 "$ROOT/tools/ui_nodes.py" list
        ;;
    tapui)
        [[ $# -ge 1 ]] || die "usage: device.sh tapui <label>"
        xy="$(ui_dump | python3 "$ROOT/tools/ui_nodes.py" find "$1")" || die "no node labelled '$1' on screen"
        # shellcheck disable=SC2086
        adb shell input tap $xy
        ;;
    swipe)
        [[ $# -ge 4 ]] || die "usage: device.sh swipe <x1> <y1> <x2> <y2> [ms]"
        adb shell input swipe "$1" "$2" "$3" "$4" "${5:-400}"
        ;;
    top)
        adb shell dumpsys activity activities | grep -m1 "topResumedActivity" | sed -E 's/.* ([a-zA-Z0-9_.]+)\/.*/\1/'
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
