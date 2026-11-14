# Content blocking: baseline and per-phase measurements

Measured on a phone with a release (or `benchmark`) build before Phase 1 starts, then repeated at
the end of each phase. Cells say "to be measured" until someone with a device fills them in.
Compare release builds only: debug builds are about 5x slower.

## Device and build

| Field | Value |
| --- | --- |
| Device / Android version | to be measured |
| Build type and commit | to be measured |
| Date | to be measured |

## Metrics

| Metric | Baseline | Phase 1 | Phase 2 | Phase 3 | Phase 4 | Phase 5 |
| --- | --- | --- | --- | --- | --- | --- |
| Cold start, median of 8 (displayed, ms) | to be measured | | | | | |
| Cold start, page request (ms) | to be measured | | | | | |
| APK size, arm64-v8a (bytes) | to be measured | | | | | |
| APK size, armeabi-v7a (bytes) | to be measured | | | | | |
| APK size, x86_64 (bytes) | to be measured | | | | | |
| PSS after three sites (MB) | to be measured | | | | | |
| Rules loaded (ads / privacy) | host list only: see below | | | | | |
| Request latency p50 / p99 (ms) | to be measured | | | | | |

Rules loaded at baseline is the host list: `head -3 app/src/main/assets/adblock_domains.txt`
(the third line gives the counts).

## Commands

Cold start (device connected, active tab is a web page; defaults to the `benchmark` package):

    tools/coldstart_bench.sh 8

APK size per ABI (build the release or benchmark APK first, then list the native libraries and the
compressed total):

    ./gradlew :app:assembleRelease
    ls -l app/build/outputs/apk/release/*.apk
    unzip -l app/build/outputs/apk/release/app-release.apk | grep -E "lib/|assets/" | sort -k1 -n -r | head -20

Memory after three fixed sites (load the three sites below in three tabs, wait 10 s, then):

    adb shell dumpsys meminfo <package> | grep -E "TOTAL PSS|Native Heap|Java Heap"

Fixed sites for the memory run: the three first rows of the site matrix that the owner fills in.

Request latency: read the debug latency histogram added in CB-06 (logcat tag to be named there).
Fixture site for manual and instrumented checks: `tools/fixture_server.sh`, then open the printed
URL on the phone (`10.0.2.2` on an emulator) and load `<base>/fixture-filters.txt` as an extra list.

## Manual site matrix (owner fills in before Phase 1)

Each phase is accepted when no row regresses against uBlock Origin in Firefox for Android on the
same lists.

| Category | Example sites | Must hold | Phase 1 | Phase 2 | Phase 3 | Phase 4 | Phase 5 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Russian news and portals | to be filled | no banners, no layout holes | | | | | |
| Video hosting | YouTube | no pre-roll or mid-roll ads, playback intact | | | | | |
| Popup-heavy streaming or file sites | to be filled | no new tabs or redirects on first tap | | | | | |
| Anti-adblock wall | to be filled | page readable, no wall | | | | | |
| Login and payments | Google, Yandex, a bank, Arkose captcha | sign-in and payment flows work | | | | | |
