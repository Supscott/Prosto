# Technical Debt & Architecture Audit: Prosto Browser

**Date:** 2026-11-12
**Auditor:** Antigravity Codebase Auditor; findings re-verified against the code the same day (see "Review notes" below)
**Repository Scale:** ~78,750 Total LOC (~75,305 Kotlin across 359 `.kt` files; 130 test classes with 1,136 unit tests)
**Baseline & Comparison:** Repeat-run audit succeeding the 2026-10-27 baseline (commit `53c55ab` / `cd4c4cc~1`). Covers the god-file extractions (`JestkApp`, `TabCoordinator`, `BrowserViewModel`), the intent handoff hardening (PRO-14/15/16) and the new Compose components through commit `02b1b0f` (HEAD).

### Review notes

Each NEW finding of the first draft was checked against the code at `02b1b0f`:

- **8 kept**, several with a corrected severity or recommendation (WV-01's proposed fix would itself have misused the crashed WebView; SEC-01's proposed fix still adds a `window` property).
- **6 discarded** as false positives or not worth acting on; they are listed with the reason in §5.
- **2 new bugs** found while verifying (BUG-01, BUG-02), both in the search-engine repository.
- The RESOLVED entries from the 2026-10-27 audit were not re-verified in this pass.

### Remediation status (branch `fix/tech-debt-audit`, uncommitted)

All verified findings are fixed. Checks run: `tools/verify.sh --all` (1139/1139 passed), `tools/verify.sh --ui` (28/28), and `lintDebug` (0 errors, 141 → 135 warnings).

| ID | Fix |
|---|---|
| WV-01 | `onRenderProcessGone` in the clean player: detaches and destroys the WebView, clears `playerWebView`, closes. `removeWebView` now swaps in `DestroyingWebViewClient`, so lint reports no `MissingOnRenderProcessGone` left. |
| BUG-01 | `SearchEngineDao.updateIconUrl` replaces the full-row `update` in `downloadIcons` and the legacy-icon migration. Passes are serialized by a `Mutex`. Regression test added. |
| BUG-02 / DATA-01 | `nextOrderPosition()` (`MAX + 1`) for new and re-seeded engines, and `, id ASC` tie-breakers. Regression test added. |
| SEC-01 | Blob downloads use a one-off `WebMessageChannel` (`engine/BlobDownloadChannel.kt`); `JestkBlobBridge` is gone. `document_start.js` takes `HoriAdBlockBridge` into a closure and deletes it from `window` before the challenge-page check. The unused `isCleanPlayerEnabled()` JS method is removed. A new invariant fails the build if a bridge name added in `WebViewPool` is not deleted by `document_start.js`. **Not verified on a device**: see TASKS.md. |
| PERF-01 | Drag offsets, scale and alpha are read inside `graphicsLayer`. The remove progress reaches composition quantized to 1/20 steps through `derivedStateOf`. |
| ARCH-01 | `loadingProgress` moved to `BrowserViewModel.loadingProgress` and `LocalLoadingProgress`, so a progress tick no longer emits a new `BrowserUiState`. The fullscreen `View` moved to `BrowserViewModel.fullscreenCustomView`, with `hasFullscreenCustomView: Boolean` in the state. |
| PERF-02 | `engine/HttpClients.root`; the three subsystems derive their clients with `newBuilder()`. |
| ROBUST-01 | `Uri.fromFile` fallback removed. Tests now use a FileProvider-shared directory, plus a test for the unshared case. |
| LINT-01 | `data_extraction_rules.xml` excludes everything from cloud backup and device transfer. PiP auto-enter is on while a video plays on Android 12+. |
| CONFIG-01 | The 27 `<package>` queries are removed. The intent queries stay: see the correction in §7 Q1. |
| DEAD-01, ENV-01 | Resources, `SearchEngineIconDownloader.downloadIcons` and its test, and the absolute `.env` path are removed. |
| TYPE-01 nit | Redundant cast in `TabSessionSync` removed. |
| ARCH-03 | `TabStripDragState.kt` 974 → 289 and `SearchEngineCarousel.kt` 905 → 309, both move-only; their baseline entries are dropped. `TabOverviewGrid.kt` was not split: it needs new composable boundaries, which is not a move. |
| TEST-01 | Not done (Low; the split would have collided with ARCH-01's test changes). |

#### On-device verification (motorola edge 60 fusion, Android 16 / SDK 36, debug build)

| Check | Result |
|---|---|
| CDP probe on `example.com` and `m.youtube.com` | `HoriAdBlockBridge` and `JestkBlobBridge` absent from `window` and from a fresh `about:blank` iframe. `ScreenOrientation.prototype.lock` is native. On example.com, the only extra globals (`B`, `P`, `p`) come from the page's own `s.js`. |
| Blob download (`a[download]` with a `Blob`) | Arrives over the message channel; the file content is correct. |
| Clean player (clean player setting turned on for the test, then restored to off) | A YouTube `/watch` click opens the clean player through the captured `hostBridge`. |
| Fullscreen video | Rotates to landscape through `hostBridge.lockOrientation`. |
| Home during fullscreen playback | The task enters PiP (`mode=pinned`) through auto-enter. |
| **WV-01** with CDP `Page.crash` | **The first attempt crashed the app** ("Render process's crash wasn't handled by all associated webviews"). See BUG-03. After that fix, the app survives a crash of the tab's renderer (spare and tab both handle it) and of the clean player's renderer (the player closes). |
| `tools/smoke.sh` | Steps 1, 2, 3, 5a and 5 pass. Steps 4 and 4b fail, **and fail the same way on the base commit `02b1b0f`**. See BUG-04. |

| ID | Severity | Finding (found during device verification) | Status |
|---|---|---|---|
| **BUG-03** | **High** | `SpareWebView` (`engine/SpareWebView.kt`) builds a spare WebView ahead of time. The spare already shares the app's renderer (CDP lists it as `never_attached`) but has no client of its own, so the default client returns `false` from `onRenderProcessGone`. **Any** renderer death (OOM, GPU fault) killed the whole app whenever a spare was waiting, with or without the clean player. | **FIXED**: the spare gets a client that drops and destroys it. Every configured WebView starts with `DestroyingWebViewClient` until the tab's client is bound. Unit test `SpareWebViewTest`; verified on device. |
| **BUG-04** | Medium | Smoke step 4: after Back from an external-link tab returns to the caller, reopening the browser shows an older tab (YouTube, or `mozilla.org` on the base build) instead of the previous external tab. Back in it then stays in the browser (4b). It depends on state: the debug install had about 33 tabs. | **PRE-EXISTING**: reproduced on `02b1b0f`, so not a regression. |
| **BUG-05** | Low | Blob downloads lose the page's `download` attribute filename and are saved as `download_<timestamp>.<ext>`. The app passes the WebView-supplied `contentDisposition`, which is empty for `a[download]` blobs. | **PRE-EXISTING**: same arguments as before the change. A fix could have the page script send `a.download` back over the message channel. |

Observation: `assets/scripts/youtube_fast_start.js` replaces natives (`JSON.parse`, `Response.prototype.json/text`, the XHR `response` getters) and sets an `__yt_url` expando on XHRs. It runs only on YouTube hosts, which are not challenge-protected, so it was left as is; it would fail the CDP probe there.

---

## 1. Executive Summary

1. **App process killed when the renderer dies while the clean video player is open (`CleanVideoPlayerOverlay.kt:314`, WV-01).** Android WebView runs every WebView of the app in one renderer process. When that renderer dies, every WebView with a page gets `onRenderProcessGone`, and the app is killed if any of them does not handle it. The tab WebViews handle it (`JestkWebViewClient.kt:54`); the clean player's anonymous `WebViewClient` does not.
2. **Background icon download overwrites the user's search-engine edits and order (`SearchEngineRepositoryImpl.kt:136-139`, BUG-01).** `downloadIcons` reads all engine rows, downloads icons for several seconds, then writes each full stale row back with `update(...)`. A reorder, rename or URL edit made in the meantime is reverted.
3. **New search engines get duplicate positions (`SearchEngineRepositoryImpl.kt:66-69`, BUG-02, together with DATA-01).** A new engine is given `orderPosition = count`. After any deletion, that value already belongs to the last engine, and the DAO query has no tie-breaker, so the carousel order of the two engines is undefined.
4. **The JS bridges are visible to every page and frame (`WebViewPool.kt:553, 600`, SEC-01).** `JestkBlobBridge` and `HoriAdBlockBridge` show up in `Object.getOwnPropertyNames(window)` on every site and in every third-party iframe. This breaks the project's own stealth rule in CLAUDE.md ("Do not add `window.*` names at all").
5. **Tab drag recomposes the overlay pill on every pointer move (`TabStripDragOverlays.kt:73-113, 232-245`, PERF-01).** Drag offsets are read in the composable body rather than inside `graphicsLayer {}`.
6. **`BrowserUiState` fan-out (ARCH-01).** `JestkApp` collects the whole 86-property state and passes it to `AppOverlays`, `AppDialogs` and the other children, so any field change (for example `loadingProgress` during every page load) recomposes all of them. The `android.view.View` stored in the state is a smaller, mostly cosmetic part of this problem (see §7 Q2).

---

## 2. Architectural Mental Model

Prosto Browser is a single-activity, gesture-driven Android browser built on Android Chromium WebView (`NestedWebView`) and Jetpack Compose (BOM `2025.12.00`). It follows an adapted Clean Architecture pattern:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Presentation (ui)                               │
│  MainActivity  ──►  JestkApp  ──►  AppOverlays / AppDialogs / Effects  │
│          ▲                ▲                                            │
│          │                │ (collectAsStateWithLifecycle)              │
│  BottomBar / TabStrip ──► BrowserViewModel ──► Domain Event Handlers   │
│                           (450 LOC)            (Tab, Overlay, Settings,│
│                                                 Page, Media, Safety)   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
         ┌──────────────────────────┼──────────────────────────┐
         │                          │                          │
  ┌──────▼──────────────┐    ┌──────▼─────────────┐     ┌──────▼───────────────┐
  │   TabCoordinator    │    │ SearchCoordinator  │     │ DownloadCoordinator  │
  │  (114 LOC facade)   │    │ (305 LOC)          │     │ (260 LOC)            │
  └──────────┬──────────┘    └────────────────────┘     └──────────────────────┘
             │ (delegates to 9 focused parts)
             ▼
  ┌────────────────────────────────────────────────────────────────────────┐
  │                           Engine Layer                                 │
  │  WebViewPool (943 LOC)  ──►  NestedWebView & Context Wrapping          │
  │  JestkWebViewClient     ──►  Request Interception & Anti-Bot Stealth   │
  │  AdBlockerEngine        ──►  47k Fast Domain Filtering & CSS Injection │
  │  ExternalAppResolver    ──►  Two-Tier Intent Resolution & Custom Schemes│
  │  HoriDownloadManager    ──►  Multi-Threaded Resumable Transfers        │
  └─────────────────────────────────┬──────────────────────────────────────┘
                                    │
                         ┌──────────▼─────────┐
                         │   Repositories     │ (Tab, History, Favorite, Settings,
                         │   & Room DAOs      │  RecentTab, Download, SearchEngine)
                         └──────────┬─────────┘
                                    │
                         ┌──────────▼─────────┐
                         │    AppDatabase     │ (Room v13, Explicit Migrations
                         │                    │  MIGRATION_7_8..12_13, Down. Fallback)
                         └────────────────────┘
```

### Reality vs Documentation
The architecture closely matches its documentation. The split of the three primary god files (`JestkApp.kt` from 1,002 to 260 LOC; `BrowserViewModel.kt` from 1,494 to 450 LOC; `TabCoordinator.kt` from 1,973 to 114 LOC facade) holds. The remaining large files (`TabStripDragState.kt` 974, `SearchEngineCarousel.kt` 905, `TabOverviewGrid.kt` 891, `SearchOverlay.kt` 661) are already capped by `tools/file_size_baseline.json`, so they cannot grow.

---

## 3. Tooling Coverage & Reproducible Commands

| Tool | Invocation Command | Status | Output / Findings Summary | Blind Spots & Gaps |
|---|---|---|---|---|
| JVM Unit Suite | `rtk ./gradlew :app:testDebugUnitTest` | **PASSED** | 1,136 unit tests passed in 130 classes (~45s, JDK 23 runtime). | On-device instrumentation (`connectedAndroidTest`) not run without attached hardware. |
| Android Lint | `rtk ./gradlew :app:lintDebug` | **PASSED** | 0 errors, 141 warnings (1 `StaticFieldLeak`, 4 `MissingOnRenderProcessGone`, 1 `DataExtractionRules`, 1 `PictureInPictureIssue`, 2 `FrequentlyChangingValue`, 17 `ModifierParameter`, 4 `UnusedResources`). | Checks Android/Kotlin code only; excludes `tools/*.py`. |
| Architecture Invariants | `rtk tools/verify.sh` | **PASSED** | Checked no-blur, domain purity, crash resilience, favicon safety, localization, script stealth, fullscreen sync, file size ratchet. | Regex-based pattern checks, not compiler AST analysis. |
| Git Churn & Scale | `python3 -c "..."` (git log numstat) | **RAN** | Computed top 20 churn files and 20 largest Kotlin files across the last 6 months. | Squashed merge commits lose micro-commit granularity. |
| `ast-grep` | `which ast-grep` | **MISSING** | Not installed; substituted with `rg` and Python regex analyzers. | Structural queries approximated with regex. |
| `ktlint` / `detekt` | `which ktlint && which detekt` | **MISSING** | Neither installed globally nor as a Gradle plugin. | Substituted with Android Lint Compose checks and custom Python invariants. |

---

## 4. Findings Table (verified)

Ranked by severity, then blast radius.

| ID | Category | File:Line | Severity | Effort | Description | Recommendation | Status |
|---|---|---|---|---|---|---|---|
| **WV-01** | Crash Resilience / WebKit | `ui/overlays/CleanVideoPlayerOverlay.kt:314` | **High** | **S** | The clean player's anonymous `WebViewClient` does not override `onRenderProcessGone`. All WebViews share one renderer process, so a renderer death (OOM, GPU fault, CDP kill) while the player is open kills the whole app, even though the tab WebViews handle it. | Override it as shown in §6.1. Do **not** just call `onClose()`: that runs the `onDispose` at line 185, which calls `stopLoading()`/`loadUrl()` on the dead WebView, and a WebView whose renderer is gone may only be destroyed. | **CONFIRMED** (severity lowered from Critical: it needs a renderer death while the overlay is open) |
| **BUG-01** | Data Integrity / Race | `data/repository/SearchEngineRepositoryImpl.kt:125-139` | **Medium** | **S** | `downloadIcons` reads every engine row, then for each engine without a local icon it runs a network download (up to 2 URLs × 12 s timeouts) and writes back `engine.copy(iconUrl = ...)`, which is the **whole row as read before the download**. A reorder (`updatePosition`), rename or URL edit made during the download is reverted, and a reverted position can collide with another engine's. This is most likely right after a custom engine is added, because the user is still on the engine screen while its icon downloads. `ensureDefaultEnginesSeeded()` launches a new `downloadIcons` on every save and delete, so several runs can also write the same `engine_<id>.png.tmp` at once (`SearchEngineIconDownloader.kt:91`). | Add `@Query("UPDATE search_engines SET iconUrl = :iconUrl WHERE id = :id")` and use it instead of the full-row `update`. Serialize `downloadIcons` with a `Mutex`, or skip it while a run is in flight. | **NEW (found in review)** |
| **BUG-02** / **DATA-01** | Deterministic Ordering | `data/repository/SearchEngineRepositoryImpl.kt:66-69`, `data/local/dao/SearchEngineDao.kt:14, 17` | **Medium** | **S** | A new engine gets `orderPosition = count`. Positions run 0..n-1 only until the first delete: with 5 engines at 0..4, deleting the one at 2 leaves 0,1,3,4 and `count = 4`, so the next new engine also gets 4. `ORDER BY orderPosition ASC` has no tie-breaker (LESSON #5), so the order of the two engines is undefined and may flip between emissions. `ensureDefaultEnginesSeeded` has the same pattern (`startPos = existing.size`, line 98). | Assign `MAX(orderPosition) + 1` (a new DAO query) instead of `count`, and append `, id ASC` to both `ORDER BY` clauses. | **CONFIRMED + root cause added** |
| **SEC-01** | Anti-Bot Stealth / IPC | `engine/WebViewPool.kt:553, 600` | **Medium** | **M** | `addJavascriptInterface` puts `JestkBlobBridge` and `HoriAdBlockBridge` on `window` in every frame of every origin. Any site or third-party iframe can enumerate them, which is a stable fingerprint of this browser. This violates the CLAUDE.md stealth rule. The bridges are hardened (blob URLs must be pre-registered; `openCleanPlayer` checks the main-frame host; orientation lock needs fullscreen), so the risk is fingerprinting, not code execution. | Move to `WebViewCompat.addWebMessageListener`. Note that it **also** injects a `window.<name>` object, but only into frames that match `allowedOriginRules` and that run `postMessage`. Limit it to the origins that need it (YouTube for the clean player and orientation; the page's own origin for blobs, wired per navigation). Use a per-install random object name. For `isEnabled()`, pass the value into the document-start script instead of calling a bridge. The first-draft claim that this "avoids injecting global properties" was wrong. | **CONFIRMED, recommendation corrected** |
| **PERF-01** | Performance / Frame Jitter | `ui/navigation/TabStripDragOverlays.kt:73-113, 232-245` | **Medium** | **S** | `lastVisualXPx` and `dragOffsetYPx` are `mutableFloatStateOf` and are read in the composable body, together with `removeProgress`/scale/alpha derived from them. Every pointer move invalidates `TabStripDragOverlays`, and because a new `graphicsLayer` lambda is built each time, `ActiveTabPill`/`InactiveTabButton`/`FolderDockIndicator` recompose too. | Read the offsets and the derived scale/alpha inside `graphicsLayer { }` (sketch in §6.2). `removeProgress` is also a parameter of the pills, so vertical drags still recompose; quantize it (for example to 1/20 steps) or pass it as a `() -> Float` provider. | **CONFIRMED** (High → Medium: the overlay is small and only on screen during a drag) |
| **ARCH-01** | State Fan-out | `ui/viewmodel/BrowserUiState.kt:20-86`, `ui/JestkApp.kt:60, 234-238` | **Medium** | **M** | `JestkApp` collects the entire 86-property `BrowserUiState` and passes it whole to `AppOverlays`, `AppNotificationPills` and `AppDialogs`. Any field change, such as `loadingProgress` ticking during each page load, makes all of them recompose and re-run their bodies. Storing `fullscreenCustomView: View?` in the state is a separate, smaller issue: identity `equals` on `View` works, so the first draft's "breaks data class equality" claim was wrong. | Pass narrowed slices or `derivedStateOf`-backed values to the children, and keep high-frequency fields (`loadingProgress`, `isLoading`) out of the props of components that do not show them. See §7 Q2 for the View. | **CONFIRMED, reframed** |
| **PERF-02** | Resource Hygiene | `engine/download/HoriDownloadManager.kt:52`, `engine/update/AppUpdateManager.kt:23`, `engine/SearchEngineIconDownloader.kt:17` | **Low** | **S** | Three separately built `OkHttpClient`s, so three connection pools. All three use synchronous `execute()`, so their `Dispatcher` thread pools are never started, and OkHttp 4 shares the pool-cleanup `TaskRunner`. The actual cost is a few idle sockets that are not reused across subsystems. `AppContainer` currently has no client, so the first draft's citation of `AppContainer.kt:61-100` was wrong. | Optional: one root client in `AppContainer`, with subsystems deriving from it via `newBuilder()` (keep the download manager's cookie jar and timeouts there). | **CONFIRMED** (High → Low) |
| **ROBUST-01** | Dead Fallback | `util/DownloadFileOpener.kt:70-73` | **Low** | **S** | `Uri.fromFile(file)` fallback on a `FileProvider` failure. It does **not** crash: the resulting `FileUriExposedException` is a `RuntimeException`, and the outer `catch (e: Exception)` at line 101 catches it and shows "cannot open". The fallback can never work on API 24+ (minSdk is 26), so it is dead code that hides the real error. | Delete the fallback and return `false` with the `FileProvider` error logged. | **CONFIRMED** (no crash; Medium → Low) |
| **LINT-01** | Privacy / Manifest | `AndroidManifest.xml:85, 99` | **Low** | **S** | On Android 12+, `allowBackup="false"` still allows **device-to-device transfer**, so history, cookies and tabs move to a new phone during setup. The `DataExtractionRules` lint warning points at exactly this. PiP works (`MainActivity.kt:225-249`), but entering it from `onUserLeaveHint` gives a worse gesture-nav animation than `setAutoEnterEnabled(true)` (API 31+). | Add `android:dataExtractionRules` that excludes everything from both `cloud-backup` and `device-transfer`, if D2D transfer is unwanted. Optionally update `PictureInPictureParams` with `setAutoEnterEnabled` while a video plays. | **CONFIRMED** |
| **CONFIG-01** | Configuration Drift | `AndroidManifest.xml:22-79` | **Low** | **S** | The 27 `<package>` entries in `<queries>` are redundant with `QUERY_ALL_PACKAGES`, and also with the bare `<intent><action VIEW/></intent>` query at the end of the same block. | See §7 Q1. | **CONFIRMED** |
| **DEAD-01** | Dead Resources | `res/drawable/ic_update_notif.xml`, `strings.xml` (`settings_section_interaction`, `clean_player_fab_label`, `clean_player_fab_content_desc`) | **Low** | **S** | No references in `app/src/main` (checked with grep). | Delete them from both `values` and `values-ru`. | **CONFIRMED** |
| **ENV-01** | Local Environment Leak | `tools/test_with_jev.py:27` | **Low** | **S** | A hardcoded absolute path to the developer's home directory sits in a tracked file. `ROOT / ".env"` is already the first candidate. | Drop the absolute path. | **CONFIRMED** |
| **TEST-01** | Test Maintainability | `app/src/test/.../ui/viewmodel/BrowserViewModelTest.kt` (2,486 LOC, 106 tests), `TabCoordinatorTest.kt` (1,427 LOC) | **Low** | **M** | The test suites were not split along the new handlers. File size does not slow CI (Gradle runs test classes, and `--tests` filters by method), so the cost is navigation and merge conflicts only. The size ratchet skips `src/test`. | Split opportunistically when a handler's tests are touched: move them to `*EventHandlerTest` files. | **CONFIRMED** (High → Low) |
| **ARCH-03** | Large Files | `TabStripDragState.kt` 974, `SearchEngineCarousel.kt` 905, `TabOverviewGrid.kt` 891 | **Low** | **L** | Large, but already listed in `tools/file_size_baseline.json`, so they cannot grow. | Split when next touched for a feature; lower the baseline with `file_size_ratchet.py --update`. | **KNOWN (tracked by ratchet)** |

### Resolved findings from the 2026-10-27 audit (not re-verified in this pass)

| ID | Category | File:Line | Description | Resolution |
|---|---|---|---|---|
| SEC-01 (Old) | Origin Isolation / Downloads | `engine/WebViewPool.kt:779-780` | Cross-origin execution & injection in blob downloads. | Parameters serialized via `JSONObject.quote()`. |
| SEC-02 (Old) | Intent Routing | `engine/ExternalAppResolver.kt:283` | Non-browsable and launcher fallback in external app offers. | `CATEGORY_BROWSABLE` enforced on all resolution stages. |
| SEC-03 (Old) | Supply Chain / Updates | `engine/update/AppUpdateManager.kt:89-93` | Unchecked first-asset APK selection. | Switched to the GitHub release page redirect URL. |
| ERR-01 (Old) | Concurrency | `SearchEngineRepositoryImpl.kt:142`, `DownloadRepositoryImpl.kt:106, 121` | Blanket `Throwable` catches broke coroutine cancellation. | `CancellationException` rethrown explicitly. |
| PERF-01 (Old) | Performance | `ui/navigation/ActionDock.kt` | Continuous composition invalidation on the floating dock. | Switched to a layout-phase offset. |
| ROBUST-01 (Old) | Robustness | `engine/download/HoriDownloadManager.kt` | Data URL parser assumed base64. | Checks for `;base64` before decoding. |
| RELIAB-01 (Old) | Storage | `engine/SnapshotEngine.kt:219` | Non-atomic thumbnail writing. | Writes a `.tmp` file and renames it atomically. |
| ARCH-01 (Old) | Architecture | `ui/JestkApp.kt` | Monolithic root UI coordinator. | Split into `ui/app/*` (260 LOC). |
| ARCH-02 (Old) | Architecture | `ui/viewmodel/BrowserViewModel.kt` | ViewModel god class. | Split into event handlers (450 LOC). |
| PRIV-01 (Old) | Privacy | `AndroidManifest.xml:85` | Cloud auto-backup of browser state. | `allowBackup="false"` (see LINT-01 for D2D transfer). |
| DB-01 (Old) | Migrations | `data/local/AppDatabase.kt:193, 200` | Room migration hygiene. | Explicit migrations 7_8..12_13 and downgrade fallback. |
| DEAD-01 (Old) | Dead Code | `BrowserUiState.kt` | Dead `bottomBarOffsetY`. | Deleted. |
| CONS-01 (Old) | Consistency | `engine/SearchEngineIconDownloader.kt` | Raw `HttpURLConnection`. | Moved to `OkHttpClient`. |
| DOC-01 (Old) | Docs | `README.md`, `SECURITY.md`, `CONTRIBUTING.md` | Repository URL mismatch. | URLs synchronized. |
| PERF-02 (Old) | Performance | `ui/components/ProstoToggleSwitch.kt` | Non-lambda offset on animated state. | `.offset { IntOffset(...) }`. |
| DEAD-02 (Old) | Dead Code | `strings.xml` | Unused `update_section_title`. | Deleted. |
| LINT-02 (Old) | API Hygiene | `util/VibrationFeedback.kt` | `InlinedApi` warning. | Annotated. |
| DEP-01 (Old) | Deprecated API | `engine/WebViewPool.kt` | `databaseEnabled`. | Removed. |
| RES-01 (Old) | Resources | `mipmap-*/ic_launcher_round.png` | Square round icons. | Replaced with circular cutouts. |

---

## 5. Discarded Findings

| ID | Claim | Why it was discarded |
|---|---|---|
| **ERR-01** | `catch (_: Throwable)` in `SearchEngineIconDownloader.kt:102` swallows `CancellationException`. | The catch is in the **non-suspending** `downloadToFile`, which only makes blocking OkHttp and file calls. A `CancellationException` cannot be thrown inside it, so cancellation is unaffected; it takes effect at the next suspension point in the caller. (Catching `Throwable` also swallows `OutOfMemoryError`; narrowing it to `Exception` is cosmetic.) Separately, `downloadIcons()` in this object (line 59) has no callers and can be deleted. |
| **TYPE-01** | `(pairs as Array<Pair<...>>)` in `TabSessionSync.kt:356` can throw `ClassCastException`. | The vararg `combine` is `inline` with `reified T`, so `pairs` is already created as an `Array<Pair<String, WorkspaceHomeData>>` (a `Pair[]` at runtime). The cast cannot fail. It is redundant; removing it and its `@Suppress` is a nicety, not a fix. |
| **PERF-03** | Synchronous PNG decode in `ExternalAppNotificationPill.kt:181` stutters the slide-in. | The icon is a launcher icon of a few KB drawn at 20 dp. It is decoded once per prompt in `remember(prompt.appIcon)`, and `AppIcon` has content-based `equals`/`hashCode`, so the key is stable. The decode takes well under 1 ms, so the change is not worth making. |
| **ARCH-02** | `HoriDownloadService` (engine) imports `MainActivity` (ui). | A foreground service's notification has to open the activity. The domain-purity rule covers `domain/`, not the Android-facing engine layer. `getLaunchIntentForPackage` would change the intent flags (`SINGLE_TOP or CLEAR_TOP` at line 340) for no gain. |
| **CODE-01** | Duplicated `URI(url).host?.removePrefix("www.")` should use `UrlSecurityUtils.extractHost`. | The two call sites are display formatting: they strip `www.` and keep the case. `extractHost` does neither, so swapping them changes what is shown. That leaves two lines of duplication, too little to act on. |
| **LINT-02** | 17 composables with `modifier` not first among optional parameters. | A lint style rule with no effect on behavior. The calls use named arguments, so reordering the parameters only adds churn. |

Also corrected in §8: the ad-block asset is 0.9 MB / 47,133 hosts, not ~3 MB / 48k.

---

## 6. Recommended Fixes for the Top Items

### 6.1 WV-01: handle renderer death in the clean player

```kotlin
override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
    // A WebView whose renderer is gone may only be destroyed: no stopLoading/loadUrl.
    (view?.parent as? android.view.ViewGroup)?.removeView(view)
    view?.destroy()
    playerWebView = null   // so onDispose does not touch it again
    onClose()
    return true
}
```

### 6.2 PERF-01: read drag offsets in the draw phase

```kotlin
.graphicsLayer {
    val offsetY = if (isDraggingThis) dragState.dragOffsetYPx else if (isSettlingThis) dragState.settleOffsetYAnim.value else 0f
    translationX = when {
        isDraggingThis -> dragState.lastVisualXPx
        isSettlingThis -> (lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == elevatedTab.id }
            ?.offset?.toFloat() ?: dragState.lastVisualXPx) + dragState.settleOffsetAnim.value
        else -> 0f
    }
    translationY = offsetY
    // compute removeProgress / scale / alpha from offsetY here as well
}
```

### 6.3 BUG-01: update only the icon column

```kotlin
@Query("UPDATE search_engines SET iconUrl = :iconUrl WHERE id = :id")
suspend fun updateIconUrl(id: String, iconUrl: String)
```

### 6.4 BUG-02 / DATA-01: unique positions and a tie-breaker

```kotlin
@Query("SELECT COALESCE(MAX(orderPosition), -1) + 1 FROM search_engines")
suspend fun nextOrderPosition(): Int

@Query("SELECT * FROM search_engines ORDER BY orderPosition ASC, id ASC")
```

---

## 7. Answers to the Maintainer Questions

### Q1. Can the hardcoded `<queries>` package list be pruned?

**Yes. Delete the 27 `<package>` entries.**

- `QUERY_ALL_PACKAGES` is a normal, install-time permission. It is granted automatically to every install, including the GitHub-distributed APK; Google Play's restriction is a review policy, not a runtime gate. With the permission granted, `<queries>` has no effect.
- *Correction:* an earlier draft claimed the bare `<intent><action VIEW/></intent>` query makes every app with a VIEW filter visible. Under normal intent matching, an intent without data does not match filters that declare a scheme, so that claim is unproven. The scheme-specific intent queries were kept as the fallback for a build without the permission, and only the `<package>` list was removed. In such a build, custom-scheme apps (for example `tg://`) would be hidden; with the permission declared, nothing changes.

Nothing in the code depends on the list. `ExternalAppResolver` resolves through intents, not package names. After pruning, run `rtk tools/smoke.sh` and try one deep link to a listed app (for example a Telegram `tg://` link) to confirm.

### Q2. Should `fullscreenCustomView` move out of `BrowserUiState`?

**Yes, but as a small cleanup, and not into a `CompositionLocal`.**

- The leak risk is lower than the audit implies. The View is created by Chromium with the WebView's `MutableContextWrapper` context, and the WebViews are already app-scoped in `WebViewPool`, so the ViewModel holding the View does not pin an Activity any more than the pool does. Equality also still works, since `View` uses identity `equals`.
- The real problems are:
  1. A mutable, parent-bearing Android View in what should be serializable, testable state.
  2. Every reader of `uiState` depends on it.
- A `CompositionLocal` is the wrong tool: the value is produced outside composition (`WebViewClientBinder.kt:87`, from `onShowCustomView`) and read in exactly one place (`JestkApp.kt:240`).
- Instead, keep `isFullscreenCustomView` and `fullscreenRequestedOrientation` in `BrowserUiState`, and expose the View from a dedicated holder: a `StateFlow<View?>` on the ViewModel, or on the `WebViewPool`/chrome client that creates it. Collect it next to `FullscreenVideoLayer` and `FloatingVideoMiniPlayer`.
- This is a change of about 30 lines. Fix ARCH-01's fan-out first, because that is where the recomposition cost is.

### Q3. Migrate the dependencies to `gradle/libs.versions.toml`?

**Not now.**

- The project has a single module (`:app`) and 38 dependency declarations, so a version catalog's main benefit, sharing versions across modules, does not apply.
- Dependabot and Renovate both update versions in `build.gradle.kts` directly.
- The migration would be a large diff with no behavior change and no reduction in risk.
- Do it when a second Gradle module is introduced (for example a `:domain` or `:adblock` module), or if you adopt Renovate with grouped catalog updates.

---

## 8. Things That Look Bad But Are Actually Fine

1. **`android:usesCleartextTraffic="true"` (`AndroidManifest.xml:91`):** A general-purpose browser must load plain-HTTP sites, intranet portals and local dev servers. HTTP risk is handled in the UI (`InsecureHttpWarningModal`, `UrlSecurityUtils`).
2. **`android.permission.QUERY_ALL_PACKAGES` (`AndroidManifest.xml:18`):** Browsers have a Play policy exception for it because sites hand off to arbitrary custom schemes. Without it, Android 11+ hides apps missing from `<queries>`. DuckDuckGo declares the same permission.
3. **126 `BrowserUiEvent` subclasses through one entry point:** They are grouped into domain sealed interfaces (`TabUiEvent`, `OverlayUiEvent`, ...) and routed to handlers that are each under 250 LOC. The single MVI pipeline is kept without a god handler.
4. **Ad-block domain index shipped as an asset (`assets/adblock_domains.txt`, 0.9 MB, 47,133 hosts):** It is precompiled from EasyList into a hash set for sub-millisecond lookups in `shouldInterceptRequest`, with no filter parsing on the device at startup.
5. **`allowMainThreadQueries()` in `AppDatabase.createInMemory()`:** It is confined to the in-memory factory used by JVM/Robolectric tests. The production builder at line 193 does not use it.

---

## 9. Quick Wins Checklist

- [ ] **WV-01:** Add `onRenderProcessGone` to the clean player (§6.1).
- [ ] **BUG-01:** Replace the full-row `update` in `downloadIcons` with `updateIconUrl` (§6.3).
- [ ] **BUG-02 / DATA-01:** Assign new engines `MAX(orderPosition)+1` and add `, id ASC` (§6.4).
- [ ] **ROBUST-01:** Remove the dead `Uri.fromFile` fallback.
- [ ] **DEAD-01:** Delete `ic_update_notif.xml` and the three unused strings (both locales). Delete the unused `SearchEngineIconDownloader.downloadIcons`.
- [ ] **ENV-01:** Drop the absolute `.env` path from `tools/test_with_jev.py`.
- [ ] **CONFIG-01:** Prune the `<queries>` package list (§7 Q1).
