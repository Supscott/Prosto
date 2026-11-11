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

## Retired Lesson: Returning 0-byte text/plain for blocked web requests hangs host page scripts and video players
- **Graduated On:** 2026-10-22
- **Graduation Reason:** Fully enforced by four dedicated automated tests in AdBlockerEngineTest (createSmartBlockedResponse_returnsTransparentPngForImages, _returnsJavascriptWithSurrogatesForScripts, _returns204NoContentForBeaconsAndAnalytics, _returnsEmptyHtmlForFrames).
- **Cause:** Returning 0-byte empty responses without expected global stubs prevents client scripts (`adsbygoogle`, `googletag`) from initializing, causing unresolved promises and infinite loading spinners.
- **Do:** Use content-aware responses in `AdBlockerEngine.createSmartBlockedResponse`: transparent 1x1 PNG for images, benign surrogate scripts for scriptlets, 204 No Content for beacons, and clean empty HTML for subframes.
- **Don't:** Intercept and respond to blocked ad or tracker requests with raw 0-byte `text/plain` responses.

## Retired Lesson: Regex color replacement in the landing stylesheet silently corrupts CSS
- **Graduated On:** 2026-11-02
- **Graduation Reason:** One-off CSS regex replacement trap during marketing site migration; build now automated with Vite
- **Cause:** A regex that consumes the closing parenthesis of rgba() while rewriting colors drops it, and a follow-up repair that matches across newlines relocates that parenthesis into an unrelated declaration. The stylesheet still parses, so the breakage only surfaces as mis-rendered CSS.
- **Do:** Rewrite colors with a pattern that preserves the closing parenthesis (or re-emit it in the same replacement), then diff the compiled rule set of the previous and new build - comparing the property list of every selector exposes the damage immediately.
- **Don't:** Don't repair a corrupted stylesheet with a newline-capable regex; restore the original file and redo the transform with a parser or a non-consuming pattern.

## Retired Lesson: Live WebView inside Compose GraphicsLayer or blur shader causes frame flicker on Android 12+ (SDK 31+)
- **Graduated On:** 2026-11-09
- **Graduation Reason:** Enforced mechanically by tools/check_invariants.py (check_no_blur)
- **Cause:** Modifiers recording into a `GraphicsLayer` (e.g. blur or render nodes) force Chromium's asynchronous hardware draw functor to re-record through an offscreen `RenderNode` each frame, causing severe screen flicker and stale frames.
- **Do:** Keep live `WebView` strictly outside any `GraphicsLayer`, offscreen render node, or dynamic blur modifiers; use solid surface fallbacks with elevation for overlays positioned above web content.
- **Don't:** Place a live `WebView` inside a Compose `GraphicsLayer`, offscreen render node, or dynamic blur shader.

## Retired Lesson: Chromium WebView yields previous page favicon during cross-domain navigation
- **Graduated On:** 2026-11-11
- **Graduation Reason:** Enforced by tools/check_invariants.py check_webview_client_no_favicon and stated in CLAUDE.md (Favicon Subsystem)
- **Cause:** In Android Chromium WebView, onPageStarted(view, url, favicon) and view.favicon in onPageFinished frequently return the favicon of the previous page loaded in that WebView during cross-domain navigation. Relying on them corrupts both tab state and the domain cache.
- **Do:** Treat WebChromeClient.onReceivedIcon as the sole source of truth for favicons, enforce isSameDomain(tab.url, pageUrl) before applying icons, and evict stale per-tab icon files on cross-domain navigation.
- **Don't:** Never extract or forward favicons from WebViewClient callbacks, and never apply a foreign domain's favicon to a tab.
