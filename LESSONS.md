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

## 2. awaitEachGesture early return before suspending pointer method causes 100% CPU freeze (ANR)
- **Cause:** `awaitEachGesture` wraps an internal `while (currentCoroutineContext().isActive)` loop. If a condition exits the lambda before calling a suspending pointer method, the loop spins indefinitely without suspending on the main thread.
- **Do:** Ensure the very first statement inside `awaitEachGesture` is a suspending call (such as `awaitFirstDown()`), and suspend on `awaitPointerEvent` until pointers release if waiting for an ongoing animation.
- **Don't:** Return early from `awaitEachGesture` before a suspending pointer method is invoked.

## 3. PointerEventPass.Initial required for draggable rows containing clickable child buttons
- **Cause:** By default, child clickables consume pointer events on `PointerEventPass.Main` before parent gesture handlers can observe displacement past touch slop.
- **Do:** Observe pointer displacement on `PointerEventPass.Initial` without consuming while below `touchSlop`, and consume changes (`change.consume()`) on `PointerEventPass.Initial` once past `touchSlop` so child buttons cleanly cancel.
- **Don't:** Rely solely on `PointerEventPass.Main` or default draggable modifiers for rows that contain clickable child controls.

## 4. Kotlin default parameter evaluation trap with mutable state during drag-and-drop reorder
- **Cause:** Kotlin evaluates default arguments at the call site before entering the function body. Mutating state inside the function before using a default-initialized parameter binds to the stale pre-mutation state.
- **Do:** Declare the parameter as nullable (`arg: Type? = null`) and resolve it inside the function body (`val effective = arg ?: mutableState`) after prerequisite mutations run.
- **Don't:** Use mutable state properties directly as default function argument values when the function mutates that state.

## 5. External-app handoffs bypass `shouldOverrideUrlLoading` for programmatic loads and subframes
- **Cause:** WebKit calls the callback only for renderer-initiated navigations: it is skipped for app-initiated `loadUrl()` (address bar, incoming `ACTION_VIEW`) and subframe requests arrive with `isForMainFrame == false`. An early main-frame or http-only return silently swallows a site's `intent://` / `tg://` handoff.
- **Do:** Route every entry point (main-frame clicks, subframe custom schemes, programmatic loads) through one resolver, resolve in `onPageStarted` / `doUpdateVisitedHistory` for direct loads, and load the site-declared `S.browser_fallback_url` when no app matches.
- **Don't:** Gate external-app resolution on `request.isForMainFrame`, or expect custom-scheme URLs to reach `onPageStarted`.

## 6. SQLite identical timestamp ties in rapid insertions cause non-deterministic query ordering
- **Cause:** `System.currentTimeMillis()` has millisecond resolution. High-speed insertions in automated tests or rapid actions yield identical timestamps, producing unpredictable row order with `ORDER BY timestamp DESC`.
- **Do:** Always specify a secondary deterministic tie-breaker in SQL queries: `ORDER BY timestamp DESC, id DESC`.
- **Don't:** Order SQLite queries or pagination solely by timestamp without an auto-incrementing secondary tie-breaker.

## 7. Download redirect filename desync between database item and filesystem rename
- **Cause:** When HTTP `Content-Disposition` changes the resolved filename during redirects, updating the database record without updating the active destination file path causes `partFile.renameTo()` and media scanning to reference stale file paths.
- **Do:** Maintain a mutable `targetDestinationFile` in download tasks and update it immediately whenever `Content-Disposition` yields a new filename during redirects.
- **Don't:** Rely on the pre-redirect destination path when renaming completed `.crdownload` temporary files.

## 8. Auto-dismissed transient hints must not become permanent suppression
- **Cause:** A 5 s auto-timeout that records the domain as "dismissed" makes a later deliberate tap on the site's own "Open in app" control a silent no-op (`tg:resolve`, `intent://applink.instagram.com`); the page stays unchanged, so it looks like a broken button.
- **Do:** Keep separate suppression tiers for explicit dismissal and for timeouts, pass whether the navigation came from a user gesture (`WebResourceRequest.hasGesture()`), and let gesture-initiated offers bypass timeout suppression.
- **Don't:** Treat `onTimeout` and the user pressing "✕" as the same state, or let a timeout block a subsequent explicit handoff.

## 9. Programmatic smooth scroll can override the selected carousel item
- **Cause:** Calling scrollTo({ behavior: 'smooth' }) to reveal an explicitly selected item fires intermediate scroll events. A scroll-position handler that infers the active item can then overwrite the selection, especially when the target is clamped at the rail edge.
- **Do:** Set the active item explicitly before starting the animation and hold a short guard that ignores scroll-driven selection until the programmatic scroll settles.
- **Don't:** Let a scroll listener infer the active item while a programmatic smooth-scroll animation is still running.

## 10. Regex color replacement in the landing stylesheet silently corrupts CSS
- **Cause:** A regex that consumes the closing parenthesis of rgba() while rewriting colors drops it, and a follow-up repair that matches across newlines relocates that parenthesis into an unrelated declaration. The stylesheet still parses, so the breakage only surfaces as mis-rendered CSS.
- **Do:** Rewrite colors with a pattern that preserves the closing parenthesis (or re-emit it in the same replacement), then diff the compiled rule set of the previous and new build - comparing the property list of every selector exposes the damage immediately.
- **Don't:** Don't repair a corrupted stylesheet with a newline-capable regex; restore the original file and redo the transform with a parser or a non-consuming pattern.

## 11. Kotlin LSP serves stale module snapshots, so its diagnostics on edited files are false positives
- **Cause:** The pi-lens Kotlin analysis server (fwcd kotlin-language-server 1.3.13) caches the project module snapshot (source file list, R class, Gradle classpath) at startup. After new source files, new string resources or a new Gradle dependency are added, it keeps analysing edited files against the old snapshot and reports phantom 'Unresolved reference' errors for brand-new symbols, new R fields and even the whole test classpath (junit/truth/robolectric).
- **Do:** Verify with the real compiler first (./gradlew :app:compileDebugKotlin and :app:testDebugUnitTest) and, when the LSP still disagrees, restart the analysis server (kill the kotlin-language-server java process) so it re-imports the project; only then trust its diagnostics.
- **Don't:** Do not chase LSP 'Unresolved reference' errors on freshly added files or resources, and do not rewrite correct code to satisfy them - a green Gradle compile plus a green test suite is the authority.

## 12. A data class that carries a ByteArray compares by reference, so equal domain states look different
- **Cause:** Kotlin generates equals/hashCode for a data class by comparing each property with ==. For a ByteArray that is reference equality, so two domain objects built from identical bytes are not equal and hash differently. Anything that compares such an object - a UI state equality check, a distinctUntilChanged on a flow, a recomposition key - then reads an unchanged value as a change (or misses a real one). The same trap applies to Bitmap and to every other array-like type that does not implement structural equality.
- **Do:** Wrap binary payloads in a small value class that compares with contentEquals and contentHashCode (see domain/model/AppIcon.kt, which carries the app icon as PNG bytes so that the domain layer stays free of android.graphics.Bitmap), and keep the raw array out of the data class property list.
- **Don't:** Do not put a ByteArray - or a Bitmap, or any other type without structural equality - directly into a data class that participates in equality, and do not rely on the generated equals/hashCode for arrays.
