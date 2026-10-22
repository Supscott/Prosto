# Technical Debt Audit: Prosto (Hori) Browser

**Date:** 2026-10-12  
**Auditor:** Antigravity Codebase Auditor  
**Repository Scale:** ~37,392 LOC across Kotlin, Java, XML, Gradle KTS, and Astro (1 Android module: `:app`, 1 Web module: `landing/`)  
**Target File:** `TECH_DEBT_AUDIT.md` (Successor to `tech-debt.md` from 2026-10-04)

---

## 1. Executive Summary

1. **Active Download File Renaming & MediaStore Desync (`HoriDownloadManager.kt:341-410`):** When server redirects supply Content-Disposition filenames, `newFinalFile` is updated in the Room database and domain item, but `partFile` is renamed to the pre-redirect `finalFile` path on disk. This results in a broken file path, failing Android `openFile` and `MediaStore` scans with silent `FileNotFoundException`.
2. **Android Lint Hard Build Failure on minSdk 26 (`themes.xml:7` in `values/` and `values-night/`):** Both theme files declare `<item name="android:windowLightNavigationBar">`, which requires API level 27. With `minSdk = 26`, Android Lint aborts the build with 5 errors.
3. **Missing WebKit Renderer Crash Resilience (`JestkWebViewClient.kt:45-180`):** `JestkWebViewClient` does not override `onRenderProcessGone`. When Chromium's render process crashes or is killed by Android due to low memory (OOM), the OS terminates the entire host application process (`killProcess(myPid())`) rather than allowing tab recovery.
4. **Frequent Compose Recomposition & Cancellation Churn on Scroll (`SearchOverlay.kt:183-189`):** `LaunchedEffect` is keyed directly on `scrollState.value`, causing pagination coroutines to cancel and restart on every single subpixel scroll event (up to 120 times/sec) rather than debouncing through `snapshotFlow`.
5. **Over 60 Instances of Silent Exception Swallowing (`app/src/main/`):** Blanket `catch (_: Throwable) {}` and `catch (_: Exception) {}` blocks across `HoriDownloadManager`, `SnapshotEngine`, `FaviconManager`, `JestkWebChromeClient`, and `DefaultBrowserHelper` suppress errors without logcat visibility.
6. **Domain Layer Architectural Inversion (`ExternalAppPrompt.kt:3,14`):** The domain model directly imports and holds uncompressed `android.graphics.Bitmap` instances, violating Clean Architecture framework decoupling and creating heap retention risk on navigation flows.
7. **Engine Singleton Mutable Repository Reference (`AdBlockerEngine.kt:457`):** `AdBlockerEngine` holds a public mutable static field `var settingsRepository: SettingsRepository? = null` injected lazily by `AppContainer.kt:80`, coupling the web engine singleton to repository instances.
8. **Monolithic UI Files on Active Churn Paths (`HomeScreen.kt` 1,334 LOC, `SettingsScreen.kt` 1,277 LOC, `SearchEngineCarousel.kt` 1,104 LOC):** High churn components mix unrelated controls (e.g. search page switcher edit popup bundled inside omnibox carousel, and 116 lines of commented-out Account & Sync code in `SettingsScreen.kt`).
9. **Fragmented Clipboard Copying Across 5 Files (`HomeScreen.kt`, `TabStripRow.kt`, `LinkContextMenuBottomSheet.kt`, etc.):** 4 distinct patterns for copying text to clipboard (Compose `LocalClipboard`, Android `ClipboardManager`, custom helpers, and direct system calls) with inconsistent Toast notifications and string resources.
10. **Design System Naming & Dead Token Baggage (`Color.kt:105-156`, `Theme.kt:156-162, 297-359`):** Over 70 lines of legacy "Liquid Sunset" aliases, `LiquidShapes`, `liquidFrostedGlass`, and `RecentTabSunsetCard` remain in the codebase despite full transition to Serene Canvas (NO BLUR).

---

## 2. Architectural Mental Model

Prosto Browser is an ultra-fast, gesture-driven Android web browser built around single-handed thumb ergonomics, Serene Canvas styling (NO BLUR), Android WebKit, AndroidX Room (v8), and Jetpack Compose.

```
                             ┌──────────────────────────┐
                             │       MainActivity       │ (PiP, WindowInsets, ActivityResult)
                             └────────────┬─────────────┘
                                          │
                             ┌────────────▼─────────────┐
                             │         JestkApp         │ (Root Coordinator & Overlays)
                             └────────────┬─────────────┘
                                          │
       ┌──────────────────────────┬───────┴───────┬──────────────────────────┐
       │                          │               │                          │
  ┌────▼──────────┐      ┌────────▼─────────┐   ┌─▼──────────────┐   ┌───────▼────────┐
  │  HomeScreen   │      │ WebViewContainer │   │ SearchOverlay  │   │ SettingsScreen │
  │ (SereneCanvas)│      │  (NestedWebView) │   │ (Hero, Fling)  │   │  & Downloads   │
  └───────────────┘      └────────┬─────────┘   └────────────────┘   └────────────────┘
                                  │
                         ┌────────▼─────────┐
                         │   WebViewPool    │ (Concurrent WebViews, LRU eviction,
                         │  (WebKit Engine) │  MutableContextWrapper for Activity/App)
                         └────────┬─────────┘
                                  │
         ┌────────────────────────┼────────────────────────┐
         │                        │                        │
  ┌──────▼──────────────┐  ┌──────▼─────────────┐   ┌──────▼─────────────────┐
  │ AdBlockerEngine     │  │ SnapshotEngine     │   │  HoriDownloadManager   │
  │ & AdBlockerScript   │  │ & FaviconManager   │   │  & HoriDownloadService │
  │ & ExternalAppResolv │  │ (Local Disk Caches)│   │  (OkHttp Range Stream) │
  └─────────────────────┘  └────────────────────┘   └────────────────────────┘
                                  │
                         ┌────────▼─────────┐
                         │ BrowserViewModel │ (Unified State Facade: 856 LOC)
                         └────────┬─────────┘
                                  │
         ┌────────────────────────┼────────────────────────┐
         │                        │                        │
  ┌──────▼──────────────┐  ┌──────▼─────────────┐   ┌──────▼─────────────────┐
  │   TabCoordinator    │  │ SearchCoordinator  │   │  DownloadCoordinator   │
  │ (1,043 LOC)         │  │ (305 LOC)          │   │  (260 LOC)             │
  └─────────────────────┘  └────────────────────┘   └────────────────────────┘
                                  │
                         ┌────────▼─────────┐
                         │   Repositories   │ (Tab, History, Favorite, Settings,
                         │   & Room DAOs    │  RecentTab, Download, SearchEngine)
                         └────────┬─────────┘
                                  │
                         ┌────────▼─────────┐
                         │   AppDatabase    │ (Room v8, Export Schema, MIGRATION_7_8)
                         └──────────────────┘
```

### Key Subsystems:
1. **Presentation Layer (`com.jestk.browser.ui`):** Compose-driven single activity architecture. Navigation between screens (`HomeScreen`, `SettingsScreen`, `DownloadsScreen`) and overlays (`SearchOverlay`, `TabOverviewGrid`, context menus) is coordinated by `BrowserUiState` in `JestkApp.kt`. Bottom navigation is rendered by `UnifiedBottomBar.kt`, featuring an active tab pill, tab strip row with spring physics, and an action popup menu.
2. **ViewModel & Domain Coordinators (`com.jestk.browser.ui.viewmodel`):** Decoupled from Android Context. `BrowserViewModel` acts as a facade delegating state logic to `TabCoordinator`, `SearchCoordinator`, and `DownloadCoordinator`.
3. **Web Engine & Pooling (`com.jestk.browser.engine`):** `WebViewPool` manages concurrent `NestedWebView` instances. Context attachment uses `MutableContextWrapper` to allow detachment without leaking `Activity` references while retaining window soft-keyboard focus. `JestkWebViewClient` and `JestkWebChromeClient` handle navigation events, title/progress, fullscreen video, and ad interception.
4. **Ad-Blocking & App Resolution:** Hybrid network interception and DOM sanitization. `AdBlockerEngine` matches hosts against compiled EasyList rules and blocked paths, returning smart dummy responses (1x1 PNG, JS stubs, HTTP 204). `ExternalAppResolver` intercepts deep links and native app intents (`QUERY_ALL_PACKAGES`).
5. **Download Engine:** Resumable HTTP streaming engine decoupled from system `DownloadManager`. Uses `HoriDownloadManager` and `HoriDownloadService` (a foreground service with `dataSync` type) streaming HTTP chunks, tracking speed/progress, supporting pause/resume via HTTP `Range` headers, and writing to `.crdownload` temporary files.
6. **Data Layer (`com.jestk.browser.data`):** AndroidX Room SQLite database (`AppDatabase`, version 8) with 7 DAOs and entities. Repository implementations expose Kotlin Coroutines suspend functions and `Flow` streams.
7. **Landing Website (`landing/`):** Static open-source showcase built with Astro 7 + Tailwind CSS v4, containing an interactive `DeviceSimulator`.

---

## 3. Tooling Coverage

| Tool | Purpose | Status | Substitution / Impact on Blind Spots |
|---|---|---|---|
| `rg` (ripgrep 14.1.0) | Code pattern & symbol search | **PASS** | Primary AST and textual pattern scanner. |
| `./gradlew` (8.14.5) | Build & execution toolchain | **PASS** | JDK 23 daemon, builds `:app` module successfully. |
| `:app:testDebugUnitTest` | JVM Unit & Robolectric test suite | **PASS** | 508 tests executed 100% green in 9s. |
| `:app:compileDebugKotlin` | Kotlin compiler warnings | **PASS** | Detected deprecated WebSettings methods (`databaseEnabled`, `setForceDark`). |
| `:app:lintDebug` | Android Lint static analysis | **FAIL (5 errors, 141 warnings)** | Identified API 27 attribute violations on API 26 minSdk and compose resource queries. |
| `pnpm audit` (landing) | Landing dependency CVE scanning | **PASS** | Scanned 4 packages in `landing/`; 0 known vulnerabilities. |
| `pnpm build` (landing) | Landing page static compilation | **PASS** | Astro 7 build verified clean in 1.7s. |
| `ktlint` / `detekt` | Kotlin code style & linter | **MISS** | Not installed in environment or Gradle. Substituted with compiler warnings, lint text reports, and regex inspection. |

---

## 4. Comprehensive Findings Table

Ranked by **Severity** (Critical -> High -> Medium -> Low), then by **Blast Radius**.

| ID | Category | File:Line | Severity | Effort | Description | Recommendation | Status |
|---|---|---|---|---|---|---|---|
| **NEW-BUG-01** | Production Bug / Data Desync | `app/src/main/java/com/jestk/browser/engine/download/HoriDownloadManager.kt:341-410` | **High** | **S** | When server redirects return a Content-Disposition filename, `newFinalFile` is updated in the database and `item`, but `partFile.renameTo(finalFile)` renames to the original `finalFile` path on disk. File managers and "Open" actions fail with `FileNotFoundException`. | Update `finalFile = newFinalFile` inside the `if (!respDisposition.isNullOrBlank())` block before renaming and scanning. | RESOLVED (2026-10-13) |
| **NEW-CRIT-01** | Build / Lint Failure | `app/src/main/res/values/themes.xml:7` and `values-night/themes.xml:7` | **High** | **S** | `android:windowLightNavigationBar` requires API 27 (app minSdk is 26). Android Lint fails `lintDebug` with hard `NewApi` errors. | Move attribute to `res/values-v27/themes.xml` or annotate with `tools:targetApi="27"`. | RESOLVED (2026-10-13) |
| **NEW-RELIAB-01** | Reliability / Resilience | `app/src/main/java/com/jestk/browser/engine/JestkWebViewClient.kt:45-180` | **High** | **High** | Missing `onRenderProcessGone`. When Chromium's render process crashes or is killed by OS on low memory, the system kills the entire application process (`killProcess(myPid())`). | Override `onRenderProcessGone`, detach the crashed WebView from view hierarchy, and offer reload. | RESOLVED (2026-10-13) |
| **NEW-PERF-01** | Performance & Resource | `app/src/main/java/com/jestk/browser/ui/overlays/SearchOverlay.kt:183-189` | **Medium** | **S** | `LaunchedEffect` is keyed directly on `scrollState.value`, causing pagination coroutine to cancel and restart on every single subpixel scroll delta. | Use `snapshotFlow { scrollState.value }.filter { it <= 20 }` inside a stable `LaunchedEffect`. | RESOLVED (2026-10-13) |
| **NEW-ARCH-01** | Architectural Decay | `app/src/main/java/com/jestk/browser/ui/screens/SettingsScreen.kt:238-353` | **Medium** | **S** | 116 lines of dead, commented-out code for non-existent "ACCOUNT" profile and "Sync Tabs & Bookmarks" features. | Delete the commented-out block entirely. | RESOLVED (2026-10-13) |
| **NEW-ARCH-02** | Layering Violation | `app/src/main/java/com/jestk/browser/engine/AdBlockerEngine.kt:457` | **Medium** | **S** | Engine singleton holds public mutable repository reference `var settingsRepository: SettingsRepository? = null` lazily assigned by container. | Pass statistics increment callbacks or event channels rather than coupling the engine to Room repositories. | RESOLVED (2026-10-22) |
| **NEW-TYPE-01** | Clean Architecture Violation | `app/src/main/java/com/jestk/browser/domain/model/ExternalAppPrompt.kt:3,14` | **Medium** | **S** | Domain model directly imports and holds `android.graphics.Bitmap`, preventing layer isolation and serialization. | Store icon as file cache path or byte array, resolving to `Bitmap` only in presentation layer. | RESOLVED (2026-10-22) |
| **NEW-ARCH-03** | Architectural Decay | `app/src/main/java/com/jestk/browser/ui/overlays/SearchEngineCarousel.kt:900-1104` | **Medium** | **M** | 1,104 LOC monolith: Bundles web search results switcher row (`SearchEngineSwitcherRow`) and edit popup inside omnibox carousel file. | Extract `SearchEngineSwitcherRow` and `SearchEngineSwitcherEditPopup` into `com.jestk.browser.ui.navigation.SearchEngineSwitcherRow.kt`. | RESOLVED (2026-10-13) |
| **NEW-ARCH-04** | Architectural Decay | `app/src/main/java/com/jestk/browser/ui/navigation/TabStripRow.kt:703-962` | **Medium** | **S** | 270-line `TabStripDragState` class is embedded at the bottom of `TabStripRow.kt`, inflating it to 979 LOC. | Extract `TabStripDragState` into dedicated `com.jestk.browser.ui.navigation.TabStripDragState.kt`. | RESOLVED (2026-10-13) |
| **NEW-LINT-01** | Consistency Rot | `app/src/main/java/com/jestk/browser/ui/overlays/ImageContextMenuBottomSheet.kt:269` | **Medium** | **S** | Android Lint error: `context.getString(...)` is called inside Compose lambda instead of `stringResource(...)`. Also in `LinkContextMenuBottomSheet.kt:222, 243`. | Use `stringResource()` or hoist string lookup to parent composable scope. | RESOLVED (2026-10-13) |
| **NEW-CONS-01** | Consistency Rot | `HomeScreen.kt:637`, `ActiveTabPill.kt:480`, `TabStripRow.kt:250`, `LinkContextMenuBottomSheet.kt:238` | **Medium** | **S** | 4 different implementations of copying to clipboard across 5 files with duplicated Toast invocations. | Consolidate into a unified `ClipboardHelper.copyToClipboard(context, label, text)`. | RESOLVED (2026-10-13) |
| **SEC-03** | Architecture Coupling | `app/src/main/java/com/jestk/browser/ui/views/WebViewContainer.kt:326-328` | **Medium** | **S** | Downcasting to concrete Activity: `context.findActivity() as? MainActivity` tightly couples composable to `MainActivity`. | Inject an interface or hoist file chooser launcher contract via Compose callback. | RESOLVED (2026-10-22) |
| **CONS-03** | Consistency Rot | `WebViewPool.kt:37` and `JestkApp.kt:107` | **Medium** | **S** | Duplicated extension: `findActivity()` is defined publicly in `WebViewPool.kt` and re-defined privately in `JestkApp.kt:107`. | Move `findActivity()` to `com.jestk.browser.util.ContextExtensions.kt`. | RESOLVED (2026-10-13) |
| **CONS-04** | Consistency Rot | `app/src/main/java/com/jestk/browser/ui/overlays/LinkContextMenuBottomSheet.kt:1` | **Medium** | **S** | Naming drift: File is named `*BottomSheet.kt`, but renders a floating anchored modal card (`FloatingContextMenuContainer`). Same for `ImageContextMenuBottomSheet.kt`. | Rename files to `LinkContextMenuModal.kt` and `ImageContextMenuModal.kt`. | RESOLVED (2026-10-22) |
| **CONS-05** | Consistency Rot | `app/src/main/java/com/jestk/browser/ui/overlays/AddEditSearchEngineDialog.kt:226-320` | **Medium** | **S** | Hardcoded English strings (`"Title"`, `"Shortcut (optional)"`, `"URL Template"`) without `stringResource`, bypassing Russian localization. | Move strings to `strings.xml` and `values-ru/strings.xml`. | RESOLVED (2026-10-13) |
| **PERF-03** | Resource Hygiene | `JestkWebViewClient.kt:88`, `SnapshotEngine.kt:22`, `WebViewPool.kt:48` | **Medium** | **S** | Unmanaged CoroutineScopes: `CoroutineScope(Dispatchers.IO).launch` instantiated on every blocked ad request in `JestkWebViewClient.kt:88`. | Inject application scope or use a dedicated single daemon scope for adblock telemetry. | RESOLVED (2026-10-13) |
| **PERF-04** | Performance | `app/src/main/java/com/jestk/browser/engine/AdBlockerEngine.kt:270-278` | **Medium** | **S** | Substring allocations in hot path: `while (currentHost.contains('.')) { currentHost.substring(...) }` allocates new string objects on every network request. | Use index-based subdomain scanning without intermediate string allocations. | RESOLVED (2026-10-22) |
| **ERR-01** | Error Handling | `HoriDownloadManager.kt:63,396`, `FaviconManager.kt:148,241`, `DefaultBrowserHelper.kt:28,45` | **Medium** | **S** | Silent exception swallowing: Over 60 instances of `catch (_: Throwable) {}` discard errors during download I/O, file deletion, and cookies without logging. | Add structured logging `Log.w(TAG, "...", e)` so transient failures are visible in logcat. | RESOLVED (2026-10-22) |
| **ERR-02** | Observability | `BrowserViewModel.kt:1-857`, `TabCoordinator.kt:1-1043` | **Medium** | **S** | Total lack of logging: Core ViewModel and Coordinators contain zero log statements. State desync and navigation issues cannot be traced in production. | Introduce structured debug/info logging for navigation, tab lifecycle, and database mutations. | RESOLVED (2026-10-22) |
| **TYPE-03** | Type & Contract Debt | `app/src/main/java/com/jestk/browser/engine/FaviconManager.kt:256` | **Low** | **S** | `fun toImageModel(urlOrPath: String?): Any?` returns untyped `Any?` (designed for Coil model). | Document polymorphism explicitly or wrap in sealed `FaviconModel`. | RESOLVED (2026-10-22) |
| **NEW-CONS-02** | Consistency Rot | `Color.kt:105-156`, `Theme.kt:156-162, 297-359`, `HomeScreen.kt:711` | **Low** | **S** | Over 70 lines of dead "Liquid Sunset" backwards-compatibility aliases (`LiquidShapes`, `liquidFrostedGlass`, `RecentTabSunsetCard`, `LiquidSunsetSwitch`). | Prune obsolete Liquid aliases and rename switch to `SereneToggleSwitch`. | RESOLVED (2026-10-22) |
| **NEW-CONS-03** | Consistency Rot | `app/src/main/java/com/jestk/browser/ui/views/HomeScreen.kt:628,644,698` | **Low** | **S** | Hardcoded English strings `"Copy URL"`, `"Delete Bookmark"`, `"Add"` bypass Russian localization. | Replace with `stringResource(R.string.*)`. | RESOLVED (2026-10-13) |
| **NEW-ARCH-05** | Layering Violation | `BrowserUiEvent.kt:130` and `BrowserViewModel.kt:158` | **Low** | **S** | `BrowserUiEvent.CheckDefaultBrowser(val context: Context)` passes Android `Context` inside UI events into ViewModel methods. | Check status in Activity or pass a boolean `isDefault` status event. | RESOLVED (2026-10-22) |
| **NEW-DEP-01** | Dependency & Config Debt | `app/src/main/java/com/jestk/browser/engine/WebViewPool.kt:204,243,468-477` | **Low** | **S** | Deprecated WebSettings: `databaseEnabled` is a no-op since API 19; `setForceDark` and `setForceDarkStrategy` are deprecated in WebSettingsCompat. | Migrate to `WebSettingsCompat.setAlgorithmicDarkeningAllowed`. | RESOLVED (2026-10-22) |
| **NEW-DEP-02** | Resource Hygiene | `app/src/main/res/values/colors.xml:3-9`, `strings.xml:4-24` | **Low** | **S** | Android Lint flags 12 unused color tokens and 15 unused string resources. | Clean up unreferenced resources. | RESOLVED (2026-10-22) |
| **NEW-CI-01** | CI / Release Debt | `.github/workflows/release.yml:74-85` | **Low** | **S** | Ad-hoc release key fallback generates a fresh self-signed keystore on every run, breaking APK upgrade signatures across releases. | Document requirement for `KEYSTORE_BASE64` or fail release workflow if signing secret is missing. | RESOLVED (2026-10-13) |

---

### Re-verification (2026-10-22)

Every row of the table above was re-checked against the tree on 2026-10-22. The 2026-10-13 tracks had
already closed most of them; the six rows that were genuinely still open — **NEW-ARCH-02**,
**NEW-TYPE-01**, **NEW-ARCH-05**, **NEW-DEP-01**, **NEW-CONS-02** and **NEW-DEP-02** — were closed
in the `fix/tech-debt` pass, together with **SEC-03** and **ERR-01** / **ERR-02**.

Verification for that pass: `python3 tools/check_invariants.py` clean, `./gradlew :app:testDebugUnitTest`
green (606 tests, 0 failures), `:app:lintDebug` with 0 errors, and the visual gate pixel-identical to
`runs/baselines/bottom_bar.png`. The recorded probes and the verdict live in
[`runs/tech-debt-audit/iter-02/ledger.md`](runs/tech-debt-audit/iter-02/ledger.md).

The domain-purity guard that protects NEW-TYPE-01 was hardened at the same time: it now rejects every
`import android.*` in the domain layer, not an enumerated subset of packages — the old enumeration
missed `android.graphics`, which is exactly how the finding got in.

### Previous Audit Resolution Status (`tech-debt.md` from 2026-10-04)

| Prior Finding ID | Original Severity | Current Status | Verification / Resolution Notes |
|---|---|---|---|
| **CRIT-01** (Room Migrations) | **Critical** | **RESOLVED** | Room v8 configured with `exportSchema = true`, `MIGRATION_7_8` composite index migration, and `fallbackToDestructiveMigrationOnDowngrade()`. |
| **TEST-01** (Potemkin Java Mock Tests) | **High** | **RESOLVED** | All 72 mock Java test files under `e2e/tier*` were deleted. Replaced by 508 genuine Robolectric & unit tests. |
| **ARCH-01** (`SearchOverlay.kt` God Object) | **High** | **RESOLVED** | 2,525 LOC monolith decomposed into 5 modular components (`SearchEngineCarousel`, `SearchEngineReorderList`, `SearchHistoryCard`, `SearchInputPill`, `SearchFavoritesSection`). |
| **ARCH-02** (`BrowserViewModel.kt` God Object) | **High** | **RESOLVED** | Context decoupled; ViewModel modularized into `TabCoordinator`, `SearchCoordinator`, and `DownloadCoordinator`. |
| **ARCH-03** (`SettingsSheet.kt` Dead Code) | **High** | **RESOLVED** | 948 lines of duplicate UI pruned to a 28-line forwarding stub. |
| **DEP-01** (Undeclared OkHttp Dependency) | **High** | **RESOLVED** | `com.squareup.okhttp3:okhttp:4.12.0` explicitly declared in `app/build.gradle.kts`. |
| **PERF-01** (Correlated Subquery in `HistoryDao`) | **High** | **RESOLVED** | Replaced with `INNER JOIN (SELECT url, MAX(timestamp)...) GROUP BY url` + composite index `(url, timestamp)`. |
| **TYPE-01** (Phantom `hazeState` Rot) | **Medium** | **RESOLVED** | `hazeState: Any?` completely purged from all composables and theme signatures. |
| **BUG-01** (`FloatingVideoMiniPlayer` Drag Offset) | **High** | **RESOLVED** | Drag coordinates clamped within device screen bounds via `coerceIn()`. |
| **CFG-01** (Release Minification Disabled) | **Medium** | **RESOLVED** | Enabled `isMinifyEnabled = true` and `isShrinkResources = true` in release build type. |
| **DOC-01** (`AGENTS.md` Outdated Guidelines) | **High** | **RESOLVED** | Rewritten to enforce Serene Canvas (NO BLUR). |
| **SEC-02** (Overly Permissive FileProvider Paths) | **High** | **RESOLVED** | `path="."` removed; scoped specifically to `Download/`, `snapshots/`, `favicons/`. |
| **ARCH-04** (Dead `ActionDock` Composable) | **Medium** | **RESOLVED** | Legacy 5-button `fun ActionDock` removed from `ActionDock.kt`. |
| **ARCH-05** (Orphaned Setting `isMenuExpandedOnWeb`) | **Medium** | **RESOLVED** | Pruned from model, database, and repository. |
| **ARCH-06** (Dead Constant `ACTIVE_TAB_ID`) | **Medium** | **RESOLVED** | Removed from `SettingKeys.kt`. |
| **ARCH-07** (Dead `searchEngineButtonGestures` Overload) | **Medium** | **RESOLVED** | Uncalled 5-parameter overload deleted. |
| **TYPE-02** (Untyped `fullscreenCustomView: Any?`) | **Medium** | **RESOLVED** | Typed as `android.view.View?` in `BrowserUiState.kt`. |
| **TYPE-04** (Event Declarations in ViewModel) | **Medium** | **RESOLVED** | `DismissDownloadSnackbar` and `DownloadStarted` moved to `BrowserUiEvent.kt`. |
| **CONS-01** (Non-Suspend Functions in `TabDao`) | **Medium** | **RESOLVED** | `reorderTabs`, `reorderTabIds`, and `updatePosition` marked `suspend fun`. |
| **CONS-02** (Duplicated File Size Formatting) | **Medium** | **RESOLVED** | Extracted to shared `FileSizeFormatter.kt`. |
| **PERF-02** (Synchronous UI `getPixel` Extraction) | **Medium** | **RESOLVED** | Bitmap pixel sampling moved into background coroutine. |
| **TEST-02** (Missing Download Manager Tests) | **High** | **RESOLVED** | Authored `HoriDownloadManagerTest.kt` (462 LOC). |
| **TEST-03** (Missing WebViewPool Tests) | **High** | **RESOLVED** | Authored `WebViewPoolRobolectricTest.kt` (verifying LRU and lifecycle). |
| **CFG-02** (Deprecated `kotlinOptions`) | **Low** | **RESOLVED** | Migrated to Kotlin Gradle DSL `compilerOptions`. |
| **DOC-02** (Missing Root `README.md`) | **Low** | **RESOLVED** | Authored comprehensive English `README.md`. |

---

## 5. Top 5: "If You Fix Nothing Else, Fix These"

### 1. Fix Download File Rename & MediaStore Desync
**File:** [`HoriDownloadManager.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/engine/download/HoriDownloadManager.kt#L341-L410)  
**Issue:** When an HTTP download redirects to a server-suggested filename (e.g. GitHub release assets), `item` is updated with `newFinalFile.absolutePath`, but `partFile` is renamed to the pre-redirect `finalFile` path. The file exists on disk under the old name, causing `FileNotFoundException` when opening.

```diff
--- a/app/src/main/java/com/jestk/browser/engine/download/HoriDownloadManager.kt
+++ b/app/src/main/java/com/jestk/browser/engine/download/HoriDownloadManager.kt
@@ -260,7 +260,7 @@ class HoriDownloadManager(
                 return@launch
             }
 
-            val finalFile = File(item.filePath)
+            var targetDestinationFile = File(item.filePath)
             val partFile = File("${item.filePath}.crdownload")
             var downloadedBytes = if (partFile.exists()) partFile.length() else 0L
 
@@ -345,6 +345,7 @@ class HoriDownloadManager(
                         val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                         val newFinalFile = DownloadFileNameResolver.resolveUniqueFile(downloadDir, serverSuggestedName)
                         item = item.copy(fileName = newFinalFile.name, filePath = newFinalFile.absolutePath)
+                        targetDestinationFile = newFinalFile
                         downloadRepository.updateFilePath(id, newFinalFile.name, newFinalFile.absolutePath)
                     }
                 }
@@ -402,11 +403,11 @@ class HoriDownloadManager(
                 // Atomic rename from .crdownload to final destination
-                if (finalFile.exists()) {
-                    finalFile.delete()
+                if (targetDestinationFile.exists()) {
+                    targetDestinationFile.delete()
                 }
-                val renameSuccess = partFile.renameTo(finalFile)
+                val renameSuccess = partFile.renameTo(targetDestinationFile)
                 if (!renameSuccess) {
-                    partFile.copyTo(finalFile, overwrite = true)
+                    partFile.copyTo(targetDestinationFile, overwrite = true)
                     partFile.delete()
                 }
@@ -426,7 +427,7 @@ class HoriDownloadManager(
                 try {
                     MediaScannerConnection.scanFile(
                         context,
-                        arrayOf(finalFile.absolutePath),
+                        arrayOf(targetDestinationFile.absolutePath),
                         if (completedItem.mimeType != null) arrayOf(completedItem.mimeType) else null,
                         null
                     )
```

### 2. Fix Android Lint API 27 Hard Failure on minSdk 26
**Files:** [`values/themes.xml`](file:///Users/ilya/CODING/browser/app/src/main/res/values/themes.xml#L7), [`values-night/themes.xml`](file:///Users/ilya/CODING/browser/app/src/main/res/values-night/themes.xml#L7)  
**Issue:** `android:windowLightNavigationBar` requires API level 27. Setting it in base `res/values/` causes Android Lint to abort `./gradlew lintDebug`.

```diff
--- a/app/src/main/res/values/themes.xml
+++ b/app/src/main/res/values/themes.xml
@@ -1,10 +1,11 @@
 <?xml version="1.0" encoding="utf-8"?>
-<resources>
+<resources xmlns:tools="http://schemas.android.com/tools">
     <style name="Theme.JestkBrowser" parent="android:Theme.Material.Light.NoActionBar">
         <item name="android:statusBarColor">@android:color/transparent</item>
         <item name="android:navigationBarColor">@android:color/transparent</item>
         <item name="android:windowLightStatusBar">true</item>
-        <item name="android:windowLightNavigationBar">true</item>
+        <item name="android:windowLightNavigationBar" tools:targetApi="27">true</item>
     </style>
 </resources>
```

### 3. Implement WebKit `onRenderProcessGone` Crash Recovery
**File:** [`JestkWebViewClient.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/engine/JestkWebViewClient.kt)  
**Issue:** When Android OS terminates Chromium's render process under memory pressure, omitting `onRenderProcessGone` causes the operating system to forcefully kill the entire browser process.

```diff
--- a/app/src/main/java/com/jestk/browser/engine/JestkWebViewClient.kt
+++ b/app/src/main/java/com/jestk/browser/engine/JestkWebViewClient.kt
@@ -1,5 +1,6 @@
 package com.jestk.browser.engine
 
+import android.webkit.RenderProcessGoneDetail
 import android.graphics.Bitmap
 import android.net.Uri
@@ -102,6 +103,16 @@ open class JestkWebViewClient(
         return super.shouldInterceptRequest(view, request)
     }
 
+    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
+        Log.e("HoriEngine", "Chromium render process gone for tab $tabId (didCrash=${detail?.didCrash()})")
+        (view?.parent as? android.view.ViewGroup)?.removeView(view)
+        view?.destroy()
+        // Returning true informs the OS that the host app handled the event,
+        // preventing Android from killing the entire browser application process.
+        return true
+    }
+
     override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
```

### 4. Debounce Scroll Position Key in `SearchOverlay` Pagination
**File:** [`SearchOverlay.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/overlays/SearchOverlay.kt#L182-L198)  
**Issue:** Keying `LaunchedEffect` directly on `scrollState.value` restarts coroutines up to 120 times/sec during touch gestures.

```diff
--- a/app/src/main/java/com/jestk/browser/ui/overlays/SearchOverlay.kt
+++ b/app/src/main/java/com/jestk/browser/ui/overlays/SearchOverlay.kt
@@ -179,20 +179,20 @@ fun SearchOverlay(
                 prevMaxValue = currentMax
             }
 
-            // Pagination trigger: When user scrolls to the end of the loaded list (top of reversed list)
             LaunchedEffect(
-                scrollState.value,
                 uiState.isSearchHistoryExpanded,
                 uiState.hasMoreHistory,
-                uiState.isLoadingMoreHistory,
-                uiState.searchHistorySuggestions.size
+                uiState.isLoadingMoreHistory
             ) {
-                if (uiState.isSearchHistoryExpanded &&
-                    uiState.hasMoreHistory &&
-                    !uiState.isLoadingMoreHistory &&
-                    uiState.searchHistorySuggestions.isNotEmpty() &&
-                    scrollState.value <= 20
-                ) {
-                    onEvent(BrowserUiEvent.LoadMoreSearchHistory)
-                }
+                snapshotFlow { scrollState.value }
+                    .filter { it <= 20 }
+                    .collect {
+                        if (uiState.isSearchHistoryExpanded &&
+                            uiState.hasMoreHistory &&
+                            !uiState.isLoadingMoreHistory &&
+                            uiState.searchHistorySuggestions.isNotEmpty()
+                        ) {
+                            onEvent(BrowserUiEvent.LoadMoreSearchHistory)
+                        }
+                    }
             }
```

### 5. Prune 116 Lines of Dead Commented-out Account Stubs in `SettingsScreen`
**File:** [`SettingsScreen.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/screens/SettingsScreen.kt#L238-L353)  
**Issue:** Stale 116-line commented-out block for non-functional sync and account screens clutters the settings UI file.

```diff
--- a/app/src/main/java/com/jestk/browser/ui/screens/SettingsScreen.kt
+++ b/app/src/main/java/com/jestk/browser/ui/screens/SettingsScreen.kt
@@ -235,119 +235,6 @@ fun SettingsScreen(
 
         Spacer(modifier = Modifier.height(24.dp))
 
-        /*
-        // SECTION 2: ACCOUNT
-        SettingsSectionHeader(title = "ACCOUNT")
-        ... (116 lines of commented-out sync & profile UI) ...
-        */
-
         // SECTION: DEFAULT BROWSER (Only visible if not default)
         if (!uiState.isDefaultBrowser) {
```

---

## 6. Quick Wins (Low Effort × Medium+ Severity)

- [x] **Fix `themes.xml` API 27 attribute (`res/values/themes.xml:7`):** Add `tools:targetApi="27"` to unblock `./gradlew lintDebug`. *(Resolved 2026-10-13)*
- [x] **Fix download redirect renaming bug (`HoriDownloadManager.kt:347`):** Reassign `targetDestinationFile = newFinalFile`. *(Resolved 2026-10-13)*
- [x] **Add `onRenderProcessGone` to `JestkWebViewClient.kt:105`:** Return `true` to stop OOM renderer crashes from killing the browser process. *(Resolved 2026-10-13)*
- [x] **Remove 116 lines of commented-out Account code (`SettingsScreen.kt:238-353`):** Clean up dead code. *(Resolved 2026-10-13)*
- [x] **Debounce `SearchOverlay.kt:184` pagination scroll:** Replace `LaunchedEffect(scrollState.value)` with `snapshotFlow`. *(Resolved 2026-10-13)*
- [x] **Consolidate duplicated `findActivity()` (`WebViewPool.kt:37`, `JestkApp.kt:107`):** Move to `ContextExtensions.kt`. *(Resolved 2026-10-13)*
- [x] **Decouple `android.graphics.Bitmap` from `ExternalAppPrompt.kt:14`:** The icon travels as `domain/model/AppIcon` (PNG bytes with content-based equality) and is decoded to a `Bitmap` in the presentation layer only. *(Resolved 2026-10-22)*
- [x] **Extract `TabStripDragState` from `TabStripRow.kt:703`:** Move 270 lines to `TabStripDragState.kt`. *(Resolved 2026-10-13)*
- [x] **Consolidate clipboard copying into `ClipboardHelper`:** Replace 4 diverging implementations across UI. *(Resolved 2026-10-13)*
- [x] **Localize hardcoded strings in `AddEditSearchEngineDialog.kt:226-320` and `HomeScreen.kt:628,644,698`:** Move to `strings.xml` and `values-ru/strings.xml`. *(Resolved 2026-10-13)*

---

## 7. Things That Look Bad But Are Actually Fine

1. **`MutableContextWrapper` in `WebViewPool.kt:191-231`:**  
   *Why it looks bad:* Mutating `wrapper.baseContext` between Activity and Application at runtime looks like an unsafe reflection trick.  
   *Why it is fine:* Android's `WebView` retains its initial Context permanently. Pooling WebViews across tab switches without leaking `Activity` references while retaining soft-keyboard IME window focus requires `MutableContextWrapper` per official Android WebKit pooling guidelines.
2. **Synchronous `DEFAULT_HOSTS` in `AdBlockerEngine.kt:25-73`:**  
   *Why it looks bad:* Hardcoding 40+ ad and tracking domains in a static Kotlin `Set<String>` before loading rules from assets looks like redundant hardcoding.  
   *Why it is fine:* Parsing 80,000+ EasyList lines from assets takes 80–200ms on cold boot. Populating `DEFAULT_HOSTS` statically ensures that requests initiated on cold launch (such as tapping an external browser intent) are blocked immediately with zero latency and zero telemetry leaks.
3. **Manual `loadedUrl` tracking in `NestedWebView.kt:33-43`:**  
   *Why it looks bad:* Maintaining `var loadedUrl: String?` in `NestedWebView` seems redundant with Android's built-in `WebView.getUrl()`.  
   *Why it is fine:* `WebView.getUrl()` returns `null` or `about:blank` during the initial phase of `onPageStarted` and before redirects settle. Tracking `loadedUrl` synchronously on `loadUrl()` provides immediate URL state for UI pill rendering and tab restoration before Blink emits callbacks.
4. **Hardcoded Domain Color Fallbacks in `JestkWebViewClient.kt:131-135`:**  
   *Why it looks bad:* Hardcoding `#161617` for `apple.com` and `#0f0f0f` for `youtube.com` looks like brittle domain-specific hardcoding.  
   *Why it is fine:* Major mobile SPAs (YouTube, Apple) dynamically inject or omit `<meta name="theme-color">` during hydration, causing bright navigation bar flashes during page load. Hardcoded hints guarantee seamless status and navigation bar color matching from frame 0.
5. **Dynamic Viewport Inset Animation `(62.dp -> 0.dp)` in `WebViewContainer.kt:93-100`:**  
   *Why it looks bad:* Animating bottom padding between `62.dp` (when dock is visible) and `0.dp` (when dock is auto-hidden) looks like ad-hoc manual padding choreography.  
   *Why it is fine:* Android's `WebView` does not support Compose animated content size transitions natively without triggering full layout reflows and stutter. Animating the container padding while preserving `navigationBarsPadding()` ensures edge-to-edge web browsing while maintaining system gesture navigation bar safety.
6. **`toImageModel(urlOrPath: String?): Any?` in `FaviconManager.kt:256`:**  
   *Why it looks bad:* Returning `Any?` bypasses Kotlin compile-time type safety.  
   *Why it is fine:* Coil's `AsyncImage` model parameter is specifically typed as `Any?` to accept polymorphic input types (`android.net.Uri` for asset URLs, `java.io.File` for cached favicons, and `String` for remote web icons). Returning `Any?` directly feeds Coil's pipeline without artificial wrapper allocations.
7. **`QUERY_ALL_PACKAGES` permission in `AndroidManifest.xml:11`:**  
   *Why it looks bad:* `QUERY_ALL_PACKAGES` is a sensitive permission heavily scrutinized by Google Play.  
   *Why it is fine:* Google Play policy explicitly permits `QUERY_ALL_PACKAGES` for web browser applications (`isWebBrowser = true`), which must be able to query and offer opening links in any installed native application (YouTube, Telegram, Banking, etc.).

---

## 8. Open Questions for the Maintainer

1. **Should `SearchEngine` companion methods (`valueOf()`, `entries`) be converted into a cleaner repository lookup?**  
   `SearchEngine` mimics an enum via `valueOf()` and `entries` to support string deserialization from `SettingEntity`. Can selected engines be persisted via foreign key `engineId: String` joined against `search_engines` table?
2. **Is Android API 26 (Android 8.0) still a required target?**  
   If `minSdk` is raised from 26 to 27 or 28, all `windowLightNavigationBar` lint errors and several `Build.VERSION.SDK_INT >= 26` compatibility guards can be dropped cleanly.
3. **Should `Favorites` management be decoupled from `TabCoordinator`?**  
   `TabCoordinator` is 1,043 LOC and manages tab lifecycle, WebKit events, snapshots, and bookmarks (`addFavorite`, `deleteFavorite`). Would extracting a `FavoriteCoordinator` improve maintainability?
4. **Should CI enforce automated signing via GitHub Secrets only?**  
   Currently `.github/workflows/release.yml` generates an ad-hoc throwaway keystore if `KEYSTORE_BASE64` is missing. This allows CI to pass, but the resulting APK cannot be updated over previous installs. Should the workflow fail fast when signing secrets are absent?
