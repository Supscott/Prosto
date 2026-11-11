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

## 1. awaitEachGesture early return before suspending pointer method causes 100% CPU freeze (ANR)
- **Cause:** `awaitEachGesture` wraps an internal `while (currentCoroutineContext().isActive)` loop. If a condition exits the lambda before calling a suspending pointer method, the loop spins indefinitely without suspending on the main thread.
- **Do:** Ensure the very first statement inside `awaitEachGesture` is a suspending call (such as `awaitFirstDown()`), and suspend on `awaitPointerEvent` until pointers release if waiting for an ongoing animation.
- **Don't:** Return early from `awaitEachGesture` before a suspending pointer method is invoked.

## 2. Host long-lived drag gestures on the stable list node, never inside a reorderable LazyList item
- **Cause:** Child clickables consume `Main`-pass events, so a parent drag handler must observe `PointerEventPass.Initial`. Worse, reordering changes a slot's content type (active pill vs. inactive circle), so `LazyList` disposes and recreates the dragged item's `LayoutNode`; that detach path calls `HitPathTracker.removePointerInputModifierNode` -> `Node.dispatchCancel`, synthesizing a pointer cancel that aborts the drag mid-reorder and springs the item back. The gesture node is destroyed, so no `onDragEnd` follows.
- **Do:** Host the long-press/drag loop on the stable `LazyRow`/list node, resolve the item id from the down position via `LazyListState.layoutInfo`, observe `PointerEventPass.Initial`, and consume only after the long-press timeout or touch slop. Keep item composables free of drag state and drag callbacks.
- **Don't:** Put a long-lived `awaitEachGesture` loop inside a `LazyList` item that can be reordered, or rely on `PointerEventPass.Main` when the item contains clickable children.

## 3. Kotlin default parameter evaluation trap with mutable state during drag-and-drop reorder
- **Cause:** Kotlin evaluates default arguments at the call site before entering the function body. Mutating state inside the function before using a default-initialized parameter binds to the stale pre-mutation state.
- **Do:** Declare the parameter as nullable (`arg: Type? = null`) and resolve it inside the function body (`val effective = arg ?: mutableState`) after prerequisite mutations run.
- **Don't:** Use mutable state properties directly as default function argument values when the function mutates that state.

## 4. External-app handoffs bypass `shouldOverrideUrlLoading` for programmatic loads and subframes
- **Cause:** WebKit calls the callback only for renderer-initiated navigations: it is skipped for app-initiated `loadUrl()` (address bar, incoming `ACTION_VIEW`) and subframe requests arrive with `isForMainFrame == false`. An early main-frame or http-only return silently swallows a site's `intent://` / `tg://` handoff.
- **Do:** Route every entry point (main-frame clicks, subframe custom schemes, programmatic loads) through one resolver, resolve in `onPageStarted` / `doUpdateVisitedHistory` for direct loads, and load the site-declared `S.browser_fallback_url` when no app matches.
- **Don't:** Gate external-app resolution on `request.isForMainFrame`, or expect custom-scheme URLs to reach `onPageStarted`.

## 5. SQLite identical timestamp ties in rapid insertions cause non-deterministic query ordering
- **Cause:** `System.currentTimeMillis()` has millisecond resolution. High-speed insertions in automated tests or rapid actions yield identical timestamps, producing unpredictable row order with `ORDER BY timestamp DESC`.
- **Do:** Always specify a secondary deterministic tie-breaker in SQL queries: `ORDER BY timestamp DESC, id DESC`.
- **Don't:** Order SQLite queries or pagination solely by timestamp without an auto-incrementing secondary tie-breaker.

## 6. Download redirect filename desync between database item and filesystem rename
- **Cause:** When HTTP `Content-Disposition` changes the resolved filename during redirects, updating the database record without updating the active destination file path causes `partFile.renameTo()` and media scanning to reference stale file paths.
- **Do:** Maintain a mutable `targetDestinationFile` in download tasks and update it immediately whenever `Content-Disposition` yields a new filename during redirects.
- **Don't:** Rely on the pre-redirect destination path when renaming completed `.crdownload` temporary files.

## 7. Auto-dismissed transient hints must not become permanent suppression
- **Cause:** A 5 s auto-timeout that records the domain as "dismissed" makes a later deliberate tap on the site's own "Open in app" control a silent no-op (`tg:resolve`, `intent://applink.instagram.com`); the page stays unchanged, so it looks like a broken button.
- **Do:** Keep separate suppression tiers for explicit dismissal and for timeouts, pass whether the navigation came from a user gesture (`WebResourceRequest.hasGesture()`), and let gesture-initiated offers bypass timeout suppression.
- **Don't:** Treat `onTimeout` and the user pressing "✕" as the same state, or let a timeout block a subsequent explicit handoff.

## 8. Programmatic smooth scroll can override the selected carousel item
- **Cause:** Calling scrollTo({ behavior: 'smooth' }) to reveal an explicitly selected item fires intermediate scroll events. A scroll-position handler that infers the active item can then overwrite the selection, especially when the target is clamped at the rail edge.
- **Do:** Set the active item explicitly before starting the animation and hold a short guard that ignores scroll-driven selection until the programmatic scroll settles.
- **Don't:** Let a scroll listener infer the active item while a programmatic smooth-scroll animation is still running.

## 9. Kotlin LSP serves stale module snapshots, so its diagnostics on edited files are false positives
- **Cause:** The pi-lens Kotlin analysis server (fwcd kotlin-language-server 1.3.13) caches the project module snapshot (source file list, R class, Gradle classpath) at startup. After new source files, new string resources or a new Gradle dependency are added, it keeps analysing edited files against the old snapshot and reports phantom 'Unresolved reference' errors for brand-new symbols, new R fields and even the whole test classpath (junit/truth/robolectric).
- **Do:** Verify with the real compiler first (./gradlew :app:compileDebugKotlin and :app:testDebugUnitTest) and, when the LSP still disagrees, restart the analysis server (kill the kotlin-language-server java process) so it re-imports the project; only then trust its diagnostics.
- **Don't:** Do not chase LSP 'Unresolved reference' errors on freshly added files or resources, and do not rewrite correct code to satisfy them - a green Gradle compile plus a green test suite is the authority.

## 10. A data class that carries a ByteArray compares by reference, so equal domain states look different
- **Cause:** Kotlin generates equals/hashCode for a data class by comparing each property with ==. For a ByteArray that is reference equality, so two domain objects built from identical bytes are not equal and hash differently. Anything that compares such an object - a UI state equality check, a distinctUntilChanged on a flow, a recomposition key - then reads an unchanged value as a change (or misses a real one). The same trap applies to Bitmap and to every other array-like type that does not implement structural equality.
- **Do:** Wrap binary payloads in a small value class that compares with contentEquals and contentHashCode (see domain/model/AppIcon.kt, which carries the app icon as PNG bytes so that the domain layer stays free of android.graphics.Bitmap), and keep the raw array out of the data class property list.
- **Don't:** Do not put a ByteArray - or a Bitmap, or any other type without structural equality - directly into a data class that participates in equality, and do not rely on the generated equals/hashCode for arrays.

## 11. Anti-bot checks see every artefact an injected script leaves on the page
- **Cause:** Cloudflare/WAF scripts compare the page with real Chrome: `Function.prototype.toString.call(fn)` reveals replaced natives (an own `toString` override does not help), `Object.getOwnPropertyNames(window)` lists every injected global (also non-enumerable ones), and the Cloudflare "Just a moment..." interstitial is served on the protected site's own domain, so a host allowlist does not exempt it. Client Hints were already correct; the injected `matchMedia` polyfill and ad-block stubs were the signals.
- **Do:** Keep Client Hints native (rename only "Android WebView"), keep injected scripts free of `window.*` names and native replacements, patch a native only while it is needed, skip pages whose title is "Just a moment...", and re-run the CDP fingerprint probe (review spec §5) against Chrome on the same device after touching any injected script.
- **Don't:** Never add globals or `toString` "stealth", never polyfill `matchMedia` for the theme (use the Activity theme), and never assume a challenge is only on challenges.cloudflare.com. `X-Requested-With` and the missing `window.chrome` are WebView-inherent (DuckDuckGo has them too); do not fake them.

## 12. Pages restored from the back/forward cache never call onPageFinished
- **Cause:** With WebSettingsCompat.setBackForwardCacheEnabled, Back restores the frozen page: WebView reports onPageStarted, onPageCommitVisible and 100% progress, but no onPageFinished. Everything hung on onPageFinished (tab snapshot, title and history update, theme color, PageLoadTiming) silently skips, so the tab snapshot keeps showing the page the user went back from.
- **Do:** Treat 100% progress without onPageFinished as a finished load: JestkWebViewClient.onProgressComplete runs the end-of-load work once after a short delay and ignores a late real onPageFinished for the same navigation. Re-check Back on a real device after touching page lifecycle code.
- **Don't:** Do not assume onPageStarted and onPageFinished come in pairs, and do not put work that must run after every visible navigation only in onPageFinished.
