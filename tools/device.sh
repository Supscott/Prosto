#!/usr/bin/env bash
# One-word device commands for checking Prosto on a phone over adb. Output is short text, so a
# check costs a few lines instead of a screenshot.
#
#   tools/device.sh wake            wake the screen, keep it on over USB, fail if the phone is locked
#   tools/device.sh sleep           restore the normal screen timeout (undoes "wake")
#   tools/device.sh install         build and install the debug app (retries once)
#   tools/device.sh start | stop    launch from the launcher | force-stop
#   tools/device.sh kill            kill the process in the background (simulates process death)
#   tools/device.sh open <url>      open a link the way another app does (ACTION_VIEW)
#   tools/device.sh caller          open Settings: a stand-in for "the app the link came from"
#   tools/device.sh back | home     press Back | Home
#   tools/device.sh tap <x> <y>     tap at device pixels
#   tools/device.sh top             package of the resumed activity
#   tools/device.sh pages           WebView pages over CDP: "visible <url>" or "hidden <url>"
#   tools/device.sh visible         URL of the page that is on screen
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

pid() { adb shell pidof "$PKG" | tr -d '\r'; }

forward_cdp() {
    local p
    p="$(pid)"
    [[ -n "$p" ]] || die "App $PKG is not running"
    adb forward "tcp:$CDP_PORT" "localabstract:webview_devtools_remote_$p" >/dev/null
}

pages() {
    forward_cdp
    curl -s "http://127.0.0.1:$CDP_PORT/json/list" | python3 -c '
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
        [[ $# -ge 2 ]] || die "usage: device.sh tap <x> <y>"
        adb shell input tap "$1" "$2"
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
        sed -n '2,22p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
        exit 1
        ;;
esac
