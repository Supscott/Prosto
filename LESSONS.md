# Lessons Learned (Gotchas & Invariants)

Active, non-obvious gotchas that cannot yet be prevented by static typing or linters.

> **Graduation Policy:**
> - Keep this list lean and high-signal (maximum 10-12 active lessons).
> - If an issue is permanently resolved by an automated test, assertion or linter rule, retire
>   it to the archive file (`docs/archive/lessons_archive.md`).
> - Always structure lessons as **Cause**, **Do** and **Don't**.
>
> **Single source of truth:** this is the only active lesson file. Graduated lessons go to
> [`docs/archive/lessons_archive.md`](docs/archive/lessons_archive.md); the pre-Harness^2 log is
> frozen in [`docs/archive/lessons_legacy.md`](docs/archive/lessons_legacy.md) and must not be edited.

## 1. Live WebView inside Compose GraphicsLayer or blur shader causes frame flicker on Android 12+ (SDK 31+)
- **Cause:** Modifiers recording into a `GraphicsLayer` (e.g. blur or render nodes) force Chromium's asynchronous hardware draw functor to re-record through an offscreen `RenderNode` each frame, causing severe screen flicker and stale frames.
- **Do:** Keep live `WebView` strictly outside any `GraphicsLayer`, offscreen render node, or dynamic blur modifiers; use solid surface fallbacks with elevation for overlays positioned above web content.
- **Don't:** Place a live `WebView` inside a Compose `GraphicsLayer`, offscreen render node, or dynamic blur shader.

## 2. Keying LaunchedEffect directly on scroll or drag values causes 120Hz coroutine thrashing
- **Cause:** Passing rapidly changing continuous values (`scrollState.value`, `dragOffsetPx`) as keys to `LaunchedEffect` restarts the entire coroutine effect on every single frame or subpixel change.
- **Do:** Pass stable keys to `LaunchedEffect` and collect continuous state changes inside the effect using `snapshotFlow { scrollState.value }.filter { ... }.collect { ... }`.
- **Don't:** Pass raw `scrollState.value` or continuous layout offsets directly as `LaunchedEffect` keys.

## 3. awaitEachGesture early return before suspending pointer method causes 100% CPU freeze (ANR)
- **Cause:** `awaitEachGesture` wraps an internal `while (currentCoroutineContext().isActive)` loop. If a condition exits the lambda before calling a suspending pointer method, the loop spins indefinitely without suspending on the main thread.
- **Do:** Ensure the very first statement inside `awaitEachGesture` is a suspending call (such as `awaitFirstDown()`), and suspend on `awaitPointerEvent` until pointers release if waiting for an ongoing animation.
- **Don't:** Return early from `awaitEachGesture` before a suspending pointer method is invoked.

## 4. PointerEventPass.Initial required for draggable rows containing clickable child buttons
- **Cause:** By default, child clickables consume pointer events on `PointerEventPass.Main` before parent gesture handlers can observe displacement past touch slop.
- **Do:** Observe pointer displacement on `PointerEventPass.Initial` without consuming while below `touchSlop`, and consume changes (`change.consume()`) on `PointerEventPass.Initial` once past `touchSlop` so child buttons cleanly cancel.
- **Don't:** Rely solely on `PointerEventPass.Main` or default draggable modifiers for rows that contain clickable child controls.

## 5. WebKit crash without onRenderProcessGone terminates host application process
- **Cause:** WebKit's default `WebViewClient.onRenderProcessGone()` returns `false`, which triggers the Android framework to terminate the host app process with `Process.killProcess(Process.myPid())`.
- **Do:** Always override `onRenderProcessGone(view, detail): Boolean` in `JestkWebViewClient`, detach and destroy the crashed `WebView` instance, and return `true` to signal clean recovery.
- **Don't:** Leave `onRenderProcessGone` unhandled or return `false` in any custom `WebViewClient`.

## 6. Kotlin default parameter evaluation trap with mutable state during drag-and-drop reorder
- **Cause:** Kotlin evaluates default arguments at the call site before entering the function body. Mutating state inside the function before using a default-initialized parameter binds to the stale pre-mutation state.
- **Do:** Declare the parameter as nullable (`arg: Type? = null`) and resolve it inside the function body (`val effective = arg ?: mutableState`) after prerequisite mutations run.
- **Don't:** Use mutable state properties directly as default function argument values when the function mutates that state.

## 7. External-app handoffs bypass `shouldOverrideUrlLoading` for programmatic loads and subframes
- **Cause:** WebKit calls the callback only for renderer-initiated navigations: it is skipped for app-initiated `loadUrl()` (address bar, incoming `ACTION_VIEW`) and subframe requests arrive with `isForMainFrame == false`. An early main-frame or http-only return silently swallows a site's `intent://` / `tg://` handoff.
- **Do:** Route every entry point (main-frame clicks, subframe custom schemes, programmatic loads) through one resolver, resolve in `onPageStarted` / `doUpdateVisitedHistory` for direct loads, and load the site-declared `S.browser_fallback_url` when no app matches.
- **Don't:** Gate external-app resolution on `request.isForMainFrame`, or expect custom-scheme URLs to reach `onPageStarted`.

## 8. Returning 0-byte text/plain for blocked web requests hangs host page scripts and video players
- **Cause:** Returning 0-byte empty responses without expected global stubs prevents client scripts (`adsbygoogle`, `googletag`) from initializing, causing unresolved promises and infinite loading spinners.
- **Do:** Use content-aware responses in `AdBlockerEngine.createSmartBlockedResponse`: transparent 1x1 PNG for images, benign surrogate scripts for scriptlets, 204 No Content for beacons, and clean empty HTML for subframes.
- **Don't:** Intercept and respond to blocked ad or tracker requests with raw 0-byte `text/plain` responses.

## 9. SQLite identical timestamp ties in rapid insertions cause non-deterministic query ordering
- **Cause:** `System.currentTimeMillis()` has millisecond resolution. High-speed insertions in automated tests or rapid actions yield identical timestamps, producing unpredictable row order with `ORDER BY timestamp DESC`.
- **Do:** Always specify a secondary deterministic tie-breaker in SQL queries: `ORDER BY timestamp DESC, id DESC`.
- **Don't:** Order SQLite queries or pagination solely by timestamp without an auto-incrementing secondary tie-breaker.

## 10. Download redirect filename desync between database item and filesystem rename
- **Cause:** When HTTP `Content-Disposition` changes the resolved filename during redirects, updating the database record without updating the active destination file path causes `partFile.renameTo()` and media scanning to reference stale file paths.
- **Do:** Maintain a mutable `targetDestinationFile` in download tasks and update it immediately whenever `Content-Disposition` yields a new filename during redirects.
- **Don't:** Rely on the pre-redirect destination path when renaming completed `.crdownload` temporary files.

## 11. Auto-dismissed transient hints must not become permanent suppression
- **Cause:** A 5 s auto-timeout that records the domain as "dismissed" makes a later deliberate tap on the site's own "Open in app" control a silent no-op (`tg:resolve`, `intent://applink.instagram.com`); the page stays unchanged, so it looks like a broken button.
- **Do:** Keep separate suppression tiers for explicit dismissal and for timeouts, pass whether the navigation came from a user gesture (`WebResourceRequest.hasGesture()`), and let gesture-initiated offers bypass timeout suppression.
- **Don't:** Treat `onTimeout` and the user pressing "✕" as the same state, or let a timeout block a subsequent explicit handoff.

