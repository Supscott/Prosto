# Graduated Lessons Archive

Retired lessons that are permanently guarded by automated tests, assertions, or linters.

## Retired Lesson: Keying LaunchedEffect directly on scroll or drag values causes 120Hz coroutine thrashing
- **Graduated On:** 2026-10-20
- **Graduation Reason:** enforced by tools/check_invariants.py (check_no_continuous_effect_keys)
- **Cause:** Passing rapidly changing continuous values (`scrollState.value`, `dragOffsetPx`) as keys to `LaunchedEffect` restarts the entire coroutine effect on every single frame or subpixel change.
- **Do:** Pass stable keys to `LaunchedEffect` and collect continuous state changes inside the effect using `snapshotFlow { scrollState.value }.filter { ... }.collect { ... }`.
- **Don't:** Pass raw `scrollState.value` or continuous layout offsets directly as `LaunchedEffect` keys.

## Retired Lesson: WebKit crash without onRenderProcessGone terminates host application process
- **Graduated On:** 2026-10-20
- **Graduation Reason:** enforced by tools/check_invariants.py (check_webkit_crash_resilience)
- **Cause:** WebKit's default `WebViewClient.onRenderProcessGone()` returns `false`, which triggers the Android framework to terminate the host app process with `Process.killProcess(Process.myPid())`.
- **Do:** Always override `onRenderProcessGone(view, detail): Boolean` in `JestkWebViewClient`, detach and destroy the crashed `WebView` instance, and return `true` to signal clean recovery.
- **Don't:** Leave `onRenderProcessGone` unhandled or return `false` in any custom `WebViewClient`.

## Retired Lesson: Timestamped favicon file names make the dock icon flicker
- **Graduated On:** 2026-10-20
- **Graduation Reason:** enforced by tools/check_invariants.py (check_stable_favicon_paths)
- **Cause:** Pages report their icon on every navigation and several times per second for dynamic sites. Saving every report under a new file name publishes a new URL each time, so every bound image (dock pill, tab strip, tab grid) drops its bitmap, reloads and renders empty for a frame; the deleted predecessor also fails to load, which flipped the UI to the text-initial fallback.
- **Do:** Store a tab favicon under a stable per-tab path (`favicon_<tabId>.png`), replace it atomically, and skip the write when the encoded bytes are unchanged so the modification time - part of the image loader cache key - stays stable.
- **Don't:** Don't embed a timestamp or any other changing value in a favicon file name, and don't delete the file that a still-visible consumer points to.
