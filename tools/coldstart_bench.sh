#!/bin/bash
# Cold-start benchmark on a connected device.
#
# Usage: tools/coldstart_bench.sh [runs] [package]
#   runs     number of cold starts (default 8)
#   package  installed package (default com.jestk.browser.bench, see the "benchmark" build type:
#            ./gradlew :app:assembleBenchmark && adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk)
#
# The active tab should be a web page. For each run prints, from process start:
#   displayed    first app frame (ActivityTaskManager "Displayed")
#   request      the active tab's page request (PageLoadTiming)
#   first-frame  the page's first visible frame (PageLoadTiming, network dependent)
# and the medians at the end. Debug builds are 5x slower than release; compare release builds.
set -euo pipefail
RUNS=${1:-8}
PKG=${2:-com.jestk.browser.bench}
OUT=$(mktemp)
trap 'rm -f "$OUT"' EXIT

for _ in $(seq 1 "$RUNS"); do
  adb shell am force-stop "$PKG"
  sleep 1.5
  adb logcat -c
  adb shell am start -n "$PKG/com.jestk.browser.ui.MainActivity" >/dev/null 2>&1
  sleep 5
  adb logcat -d | python3 -I -c '
import sys, datetime
pkg = sys.argv[1]
def t(line): return datetime.datetime.strptime("2000-" + line[:18], "%Y-%m-%d %H:%M:%S.%f")
start = shown = request = frame = None
for line in sys.stdin:
    if start is None and "Start proc" in line and (pkg + "/") in line: start = t(line)
    if ("Displayed " + pkg + "/") in line: shown = t(line)
    if request is None and "PageLoadTiming" in line and " request " in line: request = t(line)
    if frame is None and "PageLoadTiming" in line and "first-frame" in line: frame = t(line)
ms = lambda x: str(int((x - start).total_seconds() * 1000)) if (x and start) else "-"
print(ms(shown), ms(request), ms(frame))
' "$PKG" | tee -a "$OUT"
done

python3 -I -c '
import sys, statistics
rows = [line.split() for line in open(sys.argv[1])]
for i, name in enumerate(["displayed", "request", "first-frame"]):
    values = [int(r[i]) for r in rows if r[i] != "-"]
    print(f"{name}: median {statistics.median(values):.0f} ms (n={len(values)})" if values else f"{name}: no data")
' "$OUT"
