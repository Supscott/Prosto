# Technical Debt Audit: Hori Browser

**Date:** 2026-10-04  
**Auditor:** Antigravity Codebase Auditor  
**Repository Scale:** ~38,372 LOC across Kotlin, Java, XML, Gradle KTS (1 module: `:app`)  
**Target File:** `tech-debt.md`

---

## 1. Executive Summary

1. **Critical Data-Loss Risk in Room DB Migrations (`AppDatabase.kt:35,61`):** Schema export is disabled (`exportSchema = false`) and the builder uses `.fallbackToDestructiveMigration()`. Any future Room database version bump (e.g. version 7 to 8) will silently erase all user tabs, bookmarks, custom search engines, downloads, and browsing history.
2. **Potemkin Test Suite (72 Mock Java Tests, ~8,000 LOC in `e2e/tier*`):** 72 test files in `app/src/test/java/com/jestk/browser/e2e/` assert exclusively against fake internal mock classes defined within the test files themselves (e.g. `MockTabManager`, `MockWebViewPool`, `MockBrowserNavigationState`), producing an illusion of extensive E2E coverage while exercising zero lines of real production application code.
3. **Severe God Objects on High-Churn Hot Paths (`SearchOverlay.kt` & `BrowserViewModel.kt`):** `SearchOverlay.kt` (2,525 LOC) and `BrowserViewModel.kt` (1,889 LOC) violate single-responsibility boundaries, packing gesture physics, fling momentum, drag-and-drop reordering, database pagination, file I/O, download events, and UI presentation into bloated monolithic classes.
4. **948 Lines of Dead, Duplicated Code in `SettingsSheet.kt` (`SettingsSheet.kt:1-948`):** `SettingsSheet.kt` was deprecated in favor of `screens/SettingsScreen.kt` (1,034 LOC), but instead of being removed or reduced to a forwarding stub (like `DownloadsSheet.kt`), the entire 948-line UI duplicate was retained in the source tree.
5. **Undeclared Direct Dependency on OkHttp (`app/build.gradle.kts:79-115`):** `HoriDownloadManager.kt` imports and relies heavily on `okhttp3.OkHttpClient`, `CookieJar`, `Request`, and `Response`, but `app/build.gradle.kts` does not declare OkHttp. It compiles solely because Coil 2.7.0 pulls it transitively; upgrading to Coil 3.0 or swapping image loaders will break compilation.
6. **O(N^2) Correlated Subquery on Hot Search Path (`HistoryDao.kt:14-75`):** History queries execute a correlated subquery per row (`WHERE h.id = (SELECT latest.id FROM history WHERE latest.url = h.url ORDER BY timestamp DESC LIMIT 1)`) without a composite index on `(url, timestamp)`. As history scales, search queries and home screen loads degrade exponentially.
7. **Phantom Parameter Rot (`hazeState: Any?`) Across UI Components:** Weeks after Haze was purged, untyped `hazeState: Any? = null` parameters remain threaded across `BottomBar.kt`, `Theme.kt`, `ActionDock.kt`, and `HomeScreen.kt`.
8. **Unbounded Touch Offsets in `FloatingVideoMiniPlayer.kt:55-60`:** Floating video mini-player drag gestures do not clamp coordinates to the device screen bounds, allowing users to drag the mini-player completely off-screen where it cannot be retrieved.
9. **Release Minification Disabled (`app/build.gradle.kts:34`):** `release { isMinifyEnabled = false }` disables R8/Proguard optimization in production builds, shipping unoptimized DEX, bloated APK binaries, and exposing internal classes.
10. **Documentation & Specification Contradiction (`AGENTS.md:16,32`):** `AGENTS.md` continues to instruct all future AI agents that the project uses "Liquid Pastel Sunset design system with frosted glass effects (Haze backdrop blur)", contradicting the "Serene Canvas (NO BLUR)" architecture documented in `DESIGN.md` and `MEMORY.md`. The repository also lacks a root `README.md`.

---

## 2. Architectural Mental Model

Hori is an Android web browser built on Jetpack Compose, Kotlin Coroutines/Flows, AndroidX Room, and Android WebKit.

```
                          ┌──────────────────────────┐
                          │       MainActivity       │
                          └────────────┬─────────────┘
                                       │ (Hosts Compose)
                          ┌────────────▼─────────────┐
                          │         JestkApp         │
                          └────────────┬─────────────┘
                                       │
     ┌───────────────────────┬─────────┴─────────┬──────────────────────┐
     │                       │                   │                      │
┌────▼──────────┐   ┌────────▼─────────┐   ┌─────▼──────────┐   ┌───────▼────────┐
│  HomeScreen   │   │ WebViewContainer │   │ SearchOverlay  │   │ SettingsScreen │
│ (SereneCanvas)│   │  (NestedWebView) │   │ (Search/Fling) │   │  & Downloads   │
└───────────────┘   └────────┬─────────┘   └────────────────┘   └────────────────┘
                             │
                    ┌────────▼─────────┐
                    │   WebViewPool    │ (Up to 5 concurrent WebViews, LRU eviction,
                    │  (WebKit Engine) │  MutableContextWrapper for Activity/App)
                    └────────┬─────────┘
                             │
     ┌───────────────────────┼────────────────────────┐
     │                       │                        │
┌────▼──────────────┐  ┌─────▼─────────────┐   ┌──────▼─────────────────┐
│ AdBlockerEngine   │  │ SnapshotEngine    │   │  HoriDownloadManager   │
│ & AdBlockerScript │  │ & FaviconManager  │   │  & HoriDownloadService │
└───────────────────┘  └───────────────────┘   └────────────────────────┘
                             │
                    ┌────────▼─────────┐
                    │ BrowserViewModel │ (God Object: 1,889 LOC, 11 dependencies)
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │   Repositories   │ (Tab, History, Favorite, Settings,
                    │   & Room DAOs    │  RecentTab, Download, SearchEngine)
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │   AppDatabase    │ (Room DB v7, Destructive Migration)
                    └──────────────────┘
```

### Key Subsystems:
1. **Presentation Layer (`com.jestk.browser.ui`):** Compose-driven single activity architecture. Navigation between screens (`HomeScreen`, `SettingsScreen`, `DownloadsScreen`) and overlays (`SearchOverlay`, `TabOverviewGrid`, context menus) is coordinated by `BrowserUiState` in `JestkApp.kt`. Bottom navigation is rendered by `UnifiedBottomBar.kt`, featuring an active tab pill, tab strip row, and an action popup menu.
2. **Web Engine & Pooling (`com.jestk.browser.engine`):** `WebViewPool` manages up to 5 concurrent `NestedWebView` instances to balance DOM/scroll state preservation against memory pressure. Context attachment uses `MutableContextWrapper` to allow safe detachment without leaking `Activity` references while retaining window soft-keyboard focus. `JestkWebViewClient` and `JestkWebChromeClient` handle navigation events, title/progress, fullscreen video, and ad interception.
3. **Ad-Blocking Subsystem:** Hybrid network interception and DOM sanitization. `AdBlockerEngine` matches hosts against `DEFAULT_HOSTS` and compiled EasyList rules, returning smart dummy responses (1x1 PNG, JS stubs, HTTP 204). `AdBlockerScript` injects cosmetic CSS rules (`display: none !important`) and mocks (`window.adsbygoogle`, `window.yaContextCb`).
4. **Download Engine:** Multi-threaded streaming engine decoupled from Android's system `DownloadManager`. Uses `HoriDownloadManager` and `HoriDownloadService` (a foreground service with `dataSync` type) streaming HTTP chunks, tracking speed/progress, supporting pause/resume via HTTP `Range` headers, and writing to `.crdownload` temporary files.
5. **Data Layer (`com.jestk.browser.data`):** AndroidX Room SQLite database (`AppDatabase`, version 7) with 7 DAOs and entities. Repository implementations expose Kotlin Coroutines suspend functions and `Flow` streams to `BrowserViewModel`.

---

## 3. Tooling Coverage

| Tool | Purpose | Status | Substitution / Impact on Blind Spots |
|---|---|---|---|
| `rg` (ripgrep) | Code pattern & symbol search | **PASS** | Primary AST and textual pattern scanner. |
| `./gradlew` (8.14.5) | Build & execution toolchain | **PASS** | JDK 23 daemon, builds `:app` module successfully. |
| `:app:testDebugUnitTest` | JVM Unit & Robolectric test suite | **PASS** | 620 tests executed green in 59s. |
| `:app:compileDebugKotlin` | Kotlin compiler warnings | **PASS** | Detected deprecations (`LocalClipboardManager`, `Icons.Filled.ArrowBack`). |
| `:app:dependencies` | Dependency graph inspection | **PASS** | Audited compile classpath; identified undeclared OkHttp leak. |
| Android Lint (`lintDebug`) | Static analysis for Android/Compose | **MISS / TIMEOUT** | Killed after 4m+ hang on `lintAnalyzeDebug`. Substituted with compiler warnings, manifest review, and manual lint audit. |
| `ktlint` | Kotlin code style & formatting | **MISS** | Not installed in environment or Gradle. Checked conventions via `rg`. |
| `detekt` | Static code analysis & code smells | **MISS** | Not configured in Gradle. Substituted with manual smell & pattern inspection. |
| `dependency-check` / CVE audit | Known vulnerability scanning | **MISS** | Gradle plugin not configured. Audited dependency versions manually against known advisories. |

---

## 4. Comprehensive Findings Table

Ranked by **Severity** (Critical -> High -> Medium -> Low), then by **Blast Radius**.

| ID | Category | File:Line | Severity | Effort | Description | Recommendation |
|---|---|---|---|---|---|---|
| **SEC-01** | Security / Data Loss | `app/src/main/java/com/jestk/browser/data/local/AppDatabase.kt:35,61` | **Critical** | **M** | `exportSchema = false` and `.fallbackToDestructiveMigration()` are enabled. Any database schema change without manual migrations wipes all user tabs, bookmarks, history, and search engines. | Set `exportSchema = true`, configure schema location in `app/build.gradle.kts`, and write explicit `Migration(7, 8)` scripts instead of destructive fallbacks. |
| **TEST-01** | Test Debt | `app/src/test/java/com/jestk/browser/e2e/tier1/` to `tier4/` (72 files) | **High** | **L** | 72 Java test files (~8,000 LOC) test fake inner `static class Mock...` objects that mutate their own fields, asserting trivial self-assignments without exercising production code. | Deprecate and delete synthetic mock tests. Replace with real unit tests on `BrowserViewModel`, `WebViewPool`, and Robolectric Compose screen tests. |
| **ARCH-01** | Architectural Decay | `app/src/main/java/com/jestk/browser/ui/overlays/SearchOverlay.kt:178` | **High** | **L** | God Composable (2,525 LOC): Combines search input, logo carousel physics, velocity tracking, drag-and-drop search engine reordering, history stack, and dropdown cards into one file. | Split into dedicated files: `SearchEngineCarousel.kt`, `SearchEngineReorderList.kt`, `SearchHistoryCard.kt`, and `SearchInputPill.kt`. |
| **ARCH-02** | Architectural Decay | `app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserViewModel.kt:47` | **High** | **L** | God Object (1,889 LOC): Injects 11 dependencies and manages tabs, history, favorites, downloads, search engine CRUD, video PiP, scroll hiding, and disk file I/O. | Decompose into focused domain coordinators: `TabManagerViewModelDelegate`, `SearchEngineViewModelDelegate`, and `DownloadsViewModelDelegate`. |
| **ARCH-03** | Architectural Decay | `app/src/main/java/com/jestk/browser/ui/overlays/SettingsSheet.kt:1-948` | **High** | **S** | 948 lines of dead, duplicated code. `SettingsSheet.kt` was superseded by `screens/SettingsScreen.kt`, but its entire 948-line duplicate body was left in the repository. | Reduce `SettingsSheet.kt` to a 20-line deprecated forwarding stub (matching `DownloadsSheet.kt`) or delete it entirely. |
| **DEP-01** | Dependency Debt | `app/build.gradle.kts:79-115` | **High** | **S** | Undeclared direct dependency: `HoriDownloadManager.kt:30-34` directly imports `okhttp3.OkHttpClient`, but `okhttp` is not declared in `dependencies`. It compiles only via transitive leakage from Coil 2.7.0. | Add explicit `implementation("com.squareup.okhttp3:okhttp:4.12.0")` in `app/build.gradle.kts`. |
| **PERF-01** | Performance | `app/src/main/java/com/jestk/browser/data/local/dao/HistoryDao.kt:14-75` | **High** | **M** | O(N^2) correlated subquery: `getAllHistoryFlow`, `getPagedHistory`, and `searchHistory` execute an inner query per row without a composite index on `(url, timestamp DESC)`. | Replace correlated subquery with SQLite `GROUP BY url` or window function `ROW_NUMBER() OVER (...)`, and add `Index(value = ["url", "timestamp"])` to `HistoryEntity`. |
| **TEST-02** | Test Debt | `app/src/main/java/com/jestk/browser/engine/download/HoriDownloadManager.kt:42` | **High** | **M** | Zero unit/integration tests for the core download subsystem (`HoriDownloadManager` and `HoriDownloadService`), including chunked streaming, range resumes, and state transitions. | Implement `HoriDownloadManagerTest` using MockWebServer and mock `DownloadRepository`. |
| **TEST-03** | Test Debt | `app/src/main/java/com/jestk/browser/engine/WebViewPool.kt:46` | **High** | **M** | 560 LOC of WebView lifecycle, context swapping, LRU eviction, force dark mode, and JS bridges has zero real tests (only synthetic mock tests). | Add `WebViewPoolRobolectricTest` verifying LRU eviction, context detachment, and tab cleanup. |
| **BUG-01** | Performance / UI Bug | `app/src/main/java/com/jestk/browser/ui/pip/FloatingVideoMiniPlayer.kt:55-60` | **High** | **S** | Mini-player drag offsets are never clamped to screen width/height, allowing users to drag the floating player completely off-screen with no way to retrieve it. | Clamp `offsetX` and `offsetY` within screen bounds `[0, screenWidth - 200.dp]` and `[0, screenHeight - 120.dp]`. |
| **SEC-02** | Security Hygiene | `app/src/main/res/xml/file_paths.xml:4,6` | **High** | **S** | Overly permissive FileProvider paths: `<external-path name="external_files" path="." />` and `<files-path name="files_download" path="." />` expose root internal and external storage directories. | Scope paths specifically to subdirectories: `<files-path name="downloads" path="Download/" />` and `<files-path name="snapshots" path="snapshots/" />`. |
| **DOC-01** | Documentation Drift | `AGENTS.md:16,32` | **High** | **S** | `AGENTS.md` explicitly instructs agents to use "Liquid Pastel Sunset design system with frosted glass effects (Haze backdrop blur)", directly contradicting `DESIGN.md` (Serene Canvas, zero blur). | Update `AGENTS.md` to reflect Serene Canvas design tokens and remove all Haze/blur guidelines. |
| **ARCH-04** | Architectural Decay | `app/src/main/java/com/jestk/browser/ui/navigation/ActionDock.kt:88-216` | **Medium** | **S** | 128 lines of dead code: `fun ActionDock` (5-button legacy dock) is never called; `UnifiedBottomBar` is used everywhere. | Remove the unused `ActionDock` composable from `ActionDock.kt`. |
| **ARCH-05** | Architectural Decay | `app/src/main/java/com/jestk/browser/domain/model/BrowserSettings.kt:7` | **Medium** | **S** | Orphaned setting: `isMenuExpandedOnWeb` is stored in DB, mapped, and tested, but no toggle or mention exists in `SettingsScreen` UI. | Either expose `isMenuExpandedOnWeb` in `SettingsScreen` or prune it from entity, model, and ViewModel. |
| **ARCH-06** | Architectural Decay | `app/src/main/java/com/jestk/browser/data/local/entity/SettingKeys.kt:7` | **Medium** | **S** | Dead constant: `const val ACTIVE_TAB_ID = "active_tab_id"` is never read or written; tab active status is managed via `TabEntity.isActive`. | Delete `ACTIVE_TAB_ID` from `SettingKeys`. |
| **ARCH-07** | Architectural Decay | `app/src/main/java/com/jestk/browser/ui/overlays/SearchOverlay.kt:2168-2214` | **Medium** | **S** | Dead function overload: 5-parameter `Modifier.searchEngineButtonGestures(...)` is completely uncalled; line 1318 uses the `carouselState` overload. | Delete the unused 5-parameter `searchEngineButtonGestures` overload. |
| **TYPE-01** | Type & Contract Debt | `app/src/main/java/com/jestk/browser/ui/navigation/BottomBar.kt:57,88,181,200` | **Medium** | **S** | Phantom parameter rot: Untyped `hazeState: Any? = null` is threaded through `UnifiedBottomBar`, `ActionPopupMenu`, `ActionDockMenuButton`, and `Theme.kt` long after Haze was uninstalled. | Remove `hazeState: Any?` parameter across all composables and theme functions. |
| **TYPE-02** | Type & Contract Debt | `app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserUiState.kt:56` | **Medium** | **S** | `val fullscreenCustomView: Any? = null` and `BrowserUiEvent.kt:104` `val view: Any?` bypass compile-time type safety and force downcasts `as? android.view.View` at `JestkApp.kt:713`. | Type `fullscreenCustomView` properly as `android.view.View?` (or encapsulate in a dedicated sealed state). |
| **TYPE-03** | Type & Contract Debt | `app/src/main/java/com/jestk/browser/engine/FaviconManager.kt:65` | **Medium** | **S** | `fun toImageModel(urlOrPath: String?): Any?` returns untyped `Any?` (could be `File`, `Uri`, `String`, or `null`). | Return a typed sealed class or `Any?` with explicit documentation and `@ColorInt` / model safety. |
| **TYPE-04** | Type & Contract Debt | `app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserViewModel.kt:41-45` | **Medium** | **S** | Layering violation: `DismissDownloadSnackbar` and `DownloadStarted` events are declared top-level in `BrowserViewModel.kt` instead of `BrowserUiEvent.kt`. | Move event declarations into `com.jestk.browser.ui.viewmodel.BrowserUiEvent`. |
| **CONS-01** | Consistency Rot | `app/src/main/java/com/jestk/browser/data/local/dao/TabDao.kt:100,127,134` | **Medium** | **S** | Contract inconsistency: `TabDao.reorderTabs` and `updatePosition` are blocking non-suspend functions, whereas `FavoriteDao:60` and `SearchEngineDao:45` use `suspend fun`. | Mark `reorderTabs`, `reorderTabIds`, and `updatePosition` as `suspend` in `TabDao`. |
| **CONS-02** | Consistency Rot | `app/src/main/java/com/jestk/browser/engine/download/HoriDownloadService.kt:55-77` | **Medium** | **S** | Duplicated utility code: `formatFileSize()` and `formatProgressText()` are copy-pasted identically in `DownloadsScreen.kt:649-671`. | Extract formatting functions into a shared `com.jestk.browser.util.FileSizeFormatter` utility. |
| **CONS-03** | Consistency Rot | `app/src/main/java/com/jestk/browser/engine/WebViewPool.kt:37` | **Medium** | **S** | Duplicated extension: `findActivity()` is defined publicly in `WebViewPool.kt` and re-defined privately in `JestkApp.kt:107`. | Move `findActivity()` to `com.jestk.browser.util.ContextExtensions.kt`. |
| **CONS-04** | Consistency Rot | `app/src/main/java/com/jestk/browser/ui/overlays/LinkContextMenuBottomSheet.kt:1` | **Medium** | **S** | Naming drift: File is named `*BottomSheet.kt` in `overlays/`, but renders a floating anchored card (`FloatingContextMenuContainer`), not a bottom sheet. | Rename file to `LinkContextMenuModal.kt` to match the composable name and behavior. |
| **CONS-05** | Consistency Rot | `app/src/main/java/com/jestk/browser/ui/overlays/AddEditSearchEngineDialog.kt:223-326` | **Medium** | **S** | Hardcoded English strings (`"Title"`, `"Shortcut (optional)"`, `"URL Template"`) without `stringResource`, bypassing Russian localization. | Move strings to `strings.xml` and `values-ru/strings.xml`. |
| **PERF-02** | Performance | `app/src/main/java/com/jestk/browser/engine/SnapshotEngine.kt:86-87` | **Medium** | **S** | Synchronous pixel extraction: `thumbnailBitmap.getPixel(centerX, topY)` runs on the UI thread inside `processCanvasCapture`. | Move bitmap color sampling to the background coroutine before writing to disk. |
| **PERF-03** | Resource Hygiene | `app/src/main/java/com/jestk/browser/engine/SnapshotEngine.kt:22` | **Medium** | **S** | Unmanaged CoroutineScopes: `SnapshotEngine` and `WebViewPool.kt:80` instantiate `CoroutineScope(Dispatchers.IO + SupervisorJob())` with no lifecycle teardown or cancellation. | Pass application lifecycle or tie scope to container teardown. |
| **PERF-04** | Performance | `app/src/main/java/com/jestk/browser/engine/AdBlockerEngine.kt:270-276` | **Medium** | **S** | Substring allocations in hot path: `while (currentHost.contains('.')) { currentHost.substring(...) }` allocates new string objects on every network request. | Use index-based subdomain matching without intermediate substring allocations. |
| **ERR-01** | Error Handling | `app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserViewModel.kt:1047,1184` | **Medium** | **S** | Silent exception swallowing: Over 25 instances of `catch (_: Throwable) {}` discard errors during tab closing, file deletion, and cookie sync without logging. | Add structured logging `Log.w(TAG, "...", e)` so transient failures are visible in logcat. |
| **ERR-02** | Observability | `app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserViewModel.kt:1-1890` | **Medium** | **S** | Total lack of logging: The entire 1,889-line core ViewModel contains zero log statements. Debugging production state regressions requires attaching a debugger. | Introduce structured debug/info logging for navigation, tab lifecycle, and database mutations. |
| **ARCH-08** | Layering Violation | `app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserViewModel.kt:1045,1182` | **Medium** | **S** | ViewModel performs direct disk I/O: Calls `File(ctx.filesDir, "snapshots").listFiles()?.forEach { it.delete() }` directly inside ViewModel coroutines. | Delegate tab disk cache cleanup to a `TabStorageManager` or `SnapshotRepository`. |
| **SEC-03** | Architecture Coupling | `app/src/main/java/com/jestk/browser/ui/views/WebViewContainer.kt:325-327` | **Medium** | **S** | Downcasting to concrete Activity: `context.findActivity() as? MainActivity` tightly couples the composable to `MainActivity`, breaking in previews or alternative hosts. | Inject an interface `FileChooserHandler` or use Compose activity result contracts. |
| **CFG-01** | Configuration Debt | `app/build.gradle.kts:34` | **Medium** | **S** | `release { isMinifyEnabled = false }`: R8 code and resource shrinking is disabled for release builds despite `proguard-rules.pro` being present. | Enable `isMinifyEnabled = true` and `isShrinkResources = true` in release build type. |
| **CFG-02** | Configuration Debt | `app/build.gradle.kts:51-57` | **Low** | **S** | Deprecated `kotlinOptions`: Uses `kotlinOptions { jvmTarget = "17" }` which is deprecated in modern Kotlin Gradle Plugin. | Migrate to `compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }`. |
| **DOC-02** | Documentation | `README.md` (root) | **Low** | **S** | Missing repository README: There is no `README.md` at the project root explaining building, architecture, or prerequisites. | Add a root `README.md` documenting build commands, architecture overview, and requirements. |
| **DOC-03** | Documentation Drift | `app/src/main/java/com/jestk/browser/ui/overlays/SearchOverlay.kt:170` | **Low** | **S** | Stale docstrings: Class header claims "Liquid Pastel Sunset design system", which was replaced by Serene Canvas. | Update docstrings to reflect Serene Canvas. |

---

## 5. Top 5: "If You Fix Nothing Else, Fix These"

### 1. Fix Critical Room Migration & Enable Schema Export
**File:** [`AppDatabase.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/data/local/AppDatabase.kt#L34-L65)  
**Issue:** `exportSchema = false` and `.fallbackToDestructiveMigration()` risk wiping all user browsing history, bookmarks, tabs, and saved search engines on the next database update.

```diff
--- a/app/src/main/java/com/jestk/browser/data/local/AppDatabase.kt
+++ b/app/src/main/java/com/jestk/browser/data/local/AppDatabase.kt
@@ -32,7 +32,7 @@ import com.jestk.browser.data.local.entity.SearchEngineEntity
         SearchEngineEntity::class
     ],
     version = 7,
-    exportSchema = false
+    exportSchema = true
 )
 @TypeConverters(Converters::class)
 abstract class AppDatabase : RoomDatabase() {
@@ -58,7 +58,7 @@ abstract class AppDatabase : RoomDatabase() {
                     context.applicationContext,
                     AppDatabase::class.java,
                     DATABASE_NAME
-                )
-                .fallbackToDestructiveMigration()
+                )
+                // Add explicit Room migrations: .addMigrations(MIGRATION_7_8)
                 .build()
                 .also { instance = it }
```

### 2. Prune 948 Lines of Dead Duplicate Code in `SettingsSheet.kt`
**File:** [`SettingsSheet.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/overlays/SettingsSheet.kt)  
**Issue:** `SettingsSheet.kt` is a 948-line duplicate of `SettingsScreen.kt`. It is marked `@Deprecated`, is not rendered in `JestkApp.kt`, and bloats maintenance overhead.

```diff
--- a/app/src/main/java/com/jestk/browser/ui/overlays/SettingsSheet.kt
+++ b/app/src/main/java/com/jestk/browser/ui/overlays/SettingsSheet.kt
@@ -1,60 +1,28 @@
 package com.jestk.browser.ui.overlays
 
-import android.content.Intent
-import android.net.Uri
-import ... (948 lines of duplicate UI implementations)
+import androidx.compose.runtime.Composable
+import androidx.compose.ui.Modifier
+import com.jestk.browser.ui.screens.SettingsScreen
+import com.jestk.browser.ui.viewmodel.BrowserUiEvent
+import com.jestk.browser.ui.viewmodel.BrowserUiState
 
 @Deprecated(
-    message = "SettingsSheet has been refactored into a full screen. Use SettingsScreen in com.jestk.browser.ui.screens"
+    message = "SettingsSheet has been refactored into a full screen. Use SettingsScreen in com.jestk.browser.ui.screens",
+    replaceWith = ReplaceWith("SettingsScreen(uiState, onEvent, modifier)", "com.jestk.browser.ui.screens.SettingsScreen")
 )
 @Composable
 fun SettingsSheet(
     uiState: BrowserUiState,
     onEvent: (BrowserUiEvent) -> Unit,
-    modifier: Modifier = Modifier
+    modifier: Modifier = Modifier,
+    initialOpenSection: Any? = null
 ) {
-    // 900+ lines of duplicated composables...
+    SettingsScreen(
+        uiState = uiState,
+        onEvent = onEvent,
+        modifier = modifier
+    )
 }
```

### 3. Declare OkHttp Direct Dependency in Gradle
**File:** [`app/build.gradle.kts`](file:///Users/ilya/CODING/browser/app/build.gradle.kts#L110-L115)  
**Issue:** `HoriDownloadManager.kt` imports and relies directly on OkHttp, but it is not listed in `dependencies`. It compiles solely because Coil 2.7.0 brings OkHttp transitively.

```diff
--- a/app/build.gradle.kts
+++ b/app/build.gradle.kts
@@ -112,6 +112,7 @@ dependencies {
     // Image Loading (Favicons, Snapshot Thumbnails)
     implementation("io.coil-kt:coil-compose:2.7.0")
     implementation("io.coil-kt:coil-svg:2.7.0")
+    implementation("com.squareup.okhttp3:okhttp:4.12.0")
 
     // Debug Tooling
     debugImplementation("androidx.compose.ui:ui-tooling")
```

### 4. Purge Phantom `hazeState: Any?` Parameters Across UI
**Files:** [`BottomBar.kt:57`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/navigation/BottomBar.kt#L57), [`Theme.kt:109`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/theme/Theme.kt#L109), [`ActionDock.kt:93`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/navigation/ActionDock.kt#L93), [`HomeScreen.kt:146`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/views/HomeScreen.kt#L146)  
**Issue:** After removing Haze, obsolete `hazeState: Any? = null` arguments remain threaded through 4 layers of composables.

```diff
--- a/app/src/main/java/com/jestk/browser/ui/navigation/BottomBar.kt
+++ b/app/src/main/java/com/jestk/browser/ui/navigation/BottomBar.kt
@@ -55,7 +55,6 @@ fun UnifiedBottomBar(
     uiState: BrowserUiState,
     onEvent: (BrowserUiEvent) -> Unit,
     modifier: Modifier = Modifier,
-    hazeState: Any? = null,
 ) {
 ...
-            ActionPopupMenu(
-                expanded = uiState.isMenuExpanded,
-                uiState = uiState,
-                isDark = isDark,
-                hazeState = hazeState,
-                onEvent = onEvent
-            )
+            ActionPopupMenu(
+                expanded = uiState.isMenuExpanded,
+                uiState = uiState,
+                isDark = isDark,
+                onEvent = onEvent
+            )
```

### 5. Replace Correlated Subquery with Window Function / Grouping in `HistoryDao`
**File:** [`HistoryDao.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/data/local/dao/HistoryDao.kt#L14-L75)  
**Issue:** `WHERE h.id = (SELECT latest.id FROM history AS latest WHERE latest.url = h.url ...)` runs a nested loop over the entire history table on every query.

```diff
--- a/app/src/main/java/com/jestk/browser/data/local/dao/HistoryDao.kt
+++ b/app/src/main/java/com/jestk/browser/data/local/dao/HistoryDao.kt
@@ -14,13 +14,9 @@ interface HistoryDao {
     @Query(
         """
-        SELECT * FROM history AS h
-        WHERE h.id = (
-            SELECT latest.id FROM history AS latest
-            WHERE latest.url = h.url
-            ORDER BY latest.timestamp DESC, latest.id DESC
-            LIMIT 1
-        )
-        ORDER BY h.timestamp DESC
+        SELECT h.* FROM history AS h
+        INNER JOIN (
+            SELECT url, MAX(timestamp) AS max_ts FROM history GROUP BY url
+        ) AS latest ON h.url = latest.url AND h.timestamp = latest.max_ts
+        GROUP BY h.url
+        ORDER BY h.timestamp DESC
     """,
     )
     fun getAllHistoryFlow(): Flow<List<HistoryEntity>>
```

---

## 6. Quick Wins (Low Effort × Medium+ Severity)

- [ ] **Add direct OkHttp dependency (`app/build.gradle.kts:114`):** Prevent compile-time breakages upon future Coil upgrades.
- [ ] **Trim `SettingsSheet.kt` (`SettingsSheet.kt:1-948`):** Replace 948 lines of duplicated UI code with a 20-line forwarding stub.
- [ ] **Purge `hazeState: Any?` parameters:** Remove dead `hazeState` arguments from `BottomBar.kt`, `Theme.kt`, `ActionDock.kt`, and `HomeScreen.kt`.
- [ ] **Delete dead `ActionDock` composable (`ActionDock.kt:88-216`):** Remove 128 lines of unused legacy 5-button dock.
- [ ] **Delete dead `SettingKeys.ACTIVE_TAB_ID` (`SettingKeys.kt:7`):** Clean up unused constant.
- [ ] **Delete dead `searchEngineButtonGestures` overload (`SearchOverlay.kt:2168-2214`):** Remove 47 lines of uncalled gesture code.
- [ ] **Clamp `FloatingVideoMiniPlayer` offsets (`FloatingVideoMiniPlayer.kt:55-60`):** Prevent dragging the mini-player off-screen.
- [ ] **Scope FileProvider paths (`file_paths.xml:4,6`):** Remove root directory exposures `path="."`.
- [ ] **Add composite index on `(url, timestamp)` (`HistoryEntity.kt:9-12`):** Accelerate history queries.
- [ ] **Update `AGENTS.md`:** Replace references to "Liquid Pastel Sunset" and "Haze" with "Serene Canvas (NO BLUR)".

---

## 7. Things That Look Bad But Are Actually Fine

1. **`MutableContextWrapper` in `WebViewPool.kt:178-191`:**  
   *Why it looks bad:* Wrapping `WebView` context in `MutableContextWrapper` and mutating `wrapper.baseContext` at runtime looks like an unsafe reflection hack.  
   *Why it is fine:* Android's `WebView` retains its initial context for its entire lifespan. To reuse `WebView` instances across tab switches and Activity recreations without leaking the `Activity` Window context (or falling back to `Application` context, which disables software keyboard inputs and window-level dialogs), `MutableContextWrapper` is Google's recommended pattern for pooling Android WebViews safely.
2. **Synchronous `DEFAULT_HOSTS` in `AdBlockerEngine.kt:24-76`:**  
   *Why it looks bad:* Hardcoding 40+ ad and tracking domains in a static Kotlin `Set<String>` before loading rules from assets looks like redundant hardcoding.  
   *Why it is fine:* Loading rules from disk/assets takes 50–150ms on cold boot. Populating `DEFAULT_HOSTS` statically ensures that requests initiated on immediate cold launch (e.g. tapping an external URL intent) are blocked with zero lag and zero ad leakage without blocking the main thread.
3. **Manual `loadedUrl` tracking in `NestedWebView.kt:33-43`:**  
   *Why it looks bad:* Maintaining `var loadedUrl: String?` in `NestedWebView` seems redundant with Android's built-in `WebView.getUrl()`.  
   *Why it is fine:* `WebView.getUrl()` returns `null` or `about:blank` during the initial phase of `onPageStarted` and before redirects settle. Tracking `loadedUrl` synchronously on `loadUrl()` provides immediate URL state for UI pill rendering and tab restoration before Blink emits callbacks.
4. **Hardcoded Domain Color Fallbacks in `JestkWebViewClient.kt:53-56`:**  
   *Why it looks bad:* Hardcoding `#161617` for `apple.com` and `#0f0f0f` for `youtube.com` looks like brittle domain-specific hardcoding.  
   *Why it is fine:* Major web apps (YouTube mobile SPA, Apple) dynamically set or omit `<meta name="theme-color">` during client-side hydration, causing white navigation bar flashes during page load. Hardcoded hints guarantee seamless status and navigation bar color matching from frame 0.
5. **Dynamic Inset Animation `(62.dp -> 0.dp)` in `WebViewContainer.kt:93-100`:**  
   *Why it looks bad:* Animating bottom padding between `62.dp` (when dock is visible) and `0.dp` (when dock is auto-hidden) looks like ad-hoc manual padding choreography.  
   *Why it is fine:* Android's `WebView` does not support Compose animated content size transitions natively without triggering full layout reflows and stutter. Animating the container padding while preserving `navigationBarsPadding()` ensures edge-to-edge web browsing while maintaining system gesture navigation bar safety.

---

## 8. Open Questions for the Maintainer

1. **Is `isMenuExpandedOnWeb` in `BrowserSettings` intended for future development?**  
   It is stored in the database and mapped through the data layer, but no setting toggle exists in `SettingsScreen`. Should this be exposed in UI or pruned as dead code?
2. **Should the 72 Java mock tests in `app/src/test/java/com/jestk/browser/e2e/` be purged?**  
   Because they assert against local fake mock classes rather than the real application code, they inflate test count without providing regression safety. Can they be replaced with genuine Robolectric tests?
3. **Can `SearchEngine` companion methods (`values()`, `valueOf()`, `@JvmField`) be deprecated?**  
   These legacy helpers exist to maintain compatibility with Java test classes. Once the Java mock tests are modernized or removed, can `SearchEngine` become a clean Kotlin data model without synthetic enum adapters?
4. **Is disabling R8 / minification in `release` build type intentional?**  
   If minification was disabled due to a previous Proguard crash with Coroutines, Room, or WebKit JavaScript interfaces, we can provide the exact keep rules so release builds can safely benefit from shrinking and optimization.
