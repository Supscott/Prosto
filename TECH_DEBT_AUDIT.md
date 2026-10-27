# Technical Debt & Architecture Audit: Prosto Browser

**Date:** 2026-10-27  
**Auditor:** Antigravity Codebase Auditor  
**Repository Scale:** ~52,648 LOC (Kotlin: 28,668 main, 20,126 test; TypeScript/CSS: 3,737 landing; Python: 117 tools)  
**Baseline & Comparison:** Successor to the 2026-10-22 audit re-verification (commit `fe24c43`), which confirmed that all 22 historical findings recorded in `8a4aae9~1:TECH_DEBT_AUDIT.md` were resolved. This pass evaluates changes from commit `8a4aae9` (2026-10-24, v0.5.0 open source cleanup) through `561cc9b` (HEAD, 164 total commits).

---

## 1. Executive Summary

1. **Cross-Origin Execution & Script Template Interpolation in Blob Downloads (`WebViewPool.kt:467, 476-513`):** `handleBlobDownload` falls back to `pool.values.firstOrNull()` when the calling `webView` reference is omitted, executing download scripts in an arbitrary tab's origin. The script template interpolates string parameters with naive escaping (`replace("'", "\\'")`), which breaks on newlines (`\n`, `\r`) or Unicode line terminators in headers and risks script injection.
2. **Intent Category Relaxation & Package Launch Fallback in External App Offers (`ExternalAppResolver.kt:272-277, 308-320`):** When resolving `intent:` schemes from web pages, if strict browsable matching fails, `ExternalAppResolver` strips `CATEGORY_BROWSABLE` or falls back to direct package launch intents (`getLaunchIntentForPackage`). While the browser does not launch these intents automatically (it presents an `ExternalAppNotificationPill` offer banner requiring user tap), tapping "Open" can launch non-browsable, internal exported activities of installed applications.
3. **Unchecked First-Asset APK Selection in In-App Update Engine (`AppUpdateManager.kt:252-255`):** `parseGithubRelease` selects the first release asset ending in `.apk`. If multi-APK releases (per-ABI splits such as `arm64-v8a` or debug APKs) are attached to a GitHub release, devices download arbitrary architectures without validation.
4. **Blanket `Throwable` Catches Suppressing Coroutine Cancellation (`SearchEngineRepositoryImpl.kt:142`, `DownloadRepositoryImpl.kt:106, 119`):** Suspending functions catch `Throwable` without rethrowing `CancellationException`. This suppresses coroutine cancellation, breaking Kotlin structured concurrency when enclosing scopes are cancelled.
5. **Continuous Composition Scope Invalidation from Animated Float in Dock Hint (`ActionDock.kt:257-279`):** An infinite bounce animation updates an animated float state that is passed to `SwipeUpSearchHint` via non-lambda `Modifier.offset(y = bounceOffset.dp)`. This causes continuous composition invalidation of the hint composable scope on every display frame while on the home screen. Using the lambda offset `.offset { IntOffset(...) }` avoids composition work by deferring updates directly to the layout phase.
6. **Data URL Download Parser Assumes Base64 and Contiguous Memory Allocation (`HoriDownloadManager.kt:493-498`):** `handleDataUrl` extracts metadata and unconditionally passes the payload to `Base64.decode(base64Data, Base64.DEFAULT)` into a single contiguous byte array. Standard URL-encoded data URLs (`data:text/plain,...`) fail with corruption or exceptions, and large data payloads risk triggering an `OutOfMemoryError` on Android.
7. **Non-Atomic File Writing in SnapshotEngine (`SnapshotEngine.kt:207-209`):** `SnapshotEngine` writes tab thumbnail JPEGs directly to final disk paths (`tab_${tabId}.jpg`) without atomic temporary staging. If the coroutine is cancelled or the process is killed during compression, a truncated 0-byte file remains, causing image decoding failures in Coil and blank thumbnail previews in the tab overview grid.
8. **Monolithic UI and State Coordinators on Core Churn Paths (`JestkApp.kt` 956 LOC, `BrowserViewModel.kt` 1,072 LOC):** In git history (`6bc8e4c..HEAD`), `JestkApp.kt` (46 commits) and `BrowserViewModel.kt` (42 commits) represent the highest churn in the repository. `JestkApp.kt` couples 12 distinct overlay cards and navigation states into a single file. `BrowserViewModel.kt` handles 94 distinct `BrowserUiEvent` branches and holds a 51-property monolithic `BrowserUiState`.
9. **Privacy & Cloud Auto-Backup of Local Browser State (`AndroidManifest.xml:81`):** `android:allowBackup="true"` is declared without `android:dataExtractionRules`. While modern Android 12+ disables `adb backup` for release builds by default, Google Drive Auto Backup still uploads unencrypted Room database files and WebView caches to Google Drive cloud accounts.
10. **Repository and Documentation URL Drift (`README.md`, `SECURITY.md`, `site.ts` vs `git remote` and `AppUpdateManager.kt`):** The git remote and `AppUpdateManager.DEFAULT_REPO` point to `Supscott/Prosto`, whereas `README.md`, `SECURITY.md`, `CONTRIBUTING.md`, `landing/src/lib/site.ts`, and `landing/src/components/BentoSection.tsx` all refer to `https://github.com/Supscott/browser`.

---

## 2. Architectural Mental Model

Prosto Browser is an ultra-fast, gesture-driven Android web browser built around single-handed thumb ergonomics, Prosto Canvas styling (strict NO BLUR), Android WebKit, AndroidX Room (v8), and Jetpack Compose.

```
                             ┌──────────────────────────┐
                             │       MainActivity       │ (Edge-to-edge, PiP, WindowInsets)
                             └────────────┬─────────────┘
                                          │
                             ┌────────────▼─────────────┐
                             │         JestkApp         │ (Root Overlay & Sheet Coordinator)
                             └────────────┬─────────────┘
                                          │
       ┌──────────────────────────┼────────────────────────┬──────────────────────────┐
       │                          │                        │                          │
  ┌────▼──────────┐      ┌────────▼─────────┐   ┌──────────▼─────┐          ┌─────────▼────────┐
  │  HomeScreen   │      │ WebViewContainer │   │ SearchOverlay  │          │ SettingsScreen   │
  │ (ProstoCanvas)│      │  (NestedWebView) │   │ (Hero, Fling)  │          │  & Downloads     │
  └───────────────┘      └────────┬─────────┘   └────────────────┘          └──────────────────┘
                                  │
                         ┌────────▼─────────┐
                         │   WebViewPool    │ (Concurrent WebViews, LRU eviction,
                         │  (WebKit Engine) │  MutableContextWrapper, Blob/Data bridge)
                         └────────┬─────────┘
                                  │
         ┌────────────────────────┼────────────────────────┐
         │                        │                        │
  ┌──────▼──────────────┐  ┌──────▼─────────────┐   ┌──────▼─────────────────┐
  │ AdBlockerEngine     │  │ SnapshotEngine     │   │  HoriDownloadManager   │
  │ (48k domain rules,  │  │ & FaviconManager   │   │  & HoriDownloadService │
  │  compact index)     │  │ (Disk Caches)      │   │  (OkHttp Range Stream) │
  └─────────────────────┘  └────────────────────┘   └────────────────────────┘
                                  │
                         ┌────────▼─────────┐
                         │ BrowserViewModel │ (Unified State Facade: 1,072 LOC)
                         └────────┬─────────┘
                                  │
         ┌────────────────────────┼────────────────────────┐
         │                        │                        │
  ┌──────▼──────────────┐  ┌──────▼─────────────┐   ┌──────▼─────────────────┐
  │   TabCoordinator    │  │ SearchCoordinator  │   │  DownloadCoordinator   │
  │ (1,198 LOC)         │  │ (305 LOC)          │   │  (260 LOC)             │
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
2. **Engine Layer (`com.jestk.browser.engine`):** Manages a pool of `NestedWebView` instances with `MutableContextWrapper` for soft keyboard attachment. Integrates `AdBlockerEngine` (sub-millisecond domain blocking over 48k rules), `ExternalAppResolver` (two-tier intent suppression), `SnapshotEngine`, and `FaviconManager`.
3. **Data & Storage Layer (`com.jestk.browser.data`):** Room database (`AppDatabase` v8) with SQLite 3.18 support. Repositories expose reactive Kotlin `Flow`s to ViewModel coordinators.
4. **Domain Layer (`com.jestk.browser.domain`):** Pure Kotlin models and repository interfaces. Enforces zero Android framework or Compose dependencies via CI check (`tools/check_invariants.py`).
5. **Marketing & Landing (`landing/`):** React 19 + TypeScript + Vite + Tailwind CSS v4 landing site with Lenis smooth scroll and Playwright smoke tests.

---

## 3. Tooling Coverage & Reproducible Commands

| Tool | Invocation Command | Status | Output / Findings Summary | Blind Spots & Gaps |
|---|---|---|---|---|
| JVM Unit Suite | `./gradlew :app:testDebugUnitTest --rerun-tasks` | **PASSED** | 698 tests passed in 1m 29s (JDK 23 runtime, JVM 17 bytecode target). | On-device hardware tests (`connectedAndroidTest`) not run without emulator/physical device. |
| Android Lint | `./gradlew :app:lintDebug` | **PASSED** | 0 errors, 103 warnings (`InlinedApi`, `ModifierParameter`, `StaticFieldLeak`, `UseKtx`). | Does not check non-Android Kotlin code in `tools/`. |
| Architecture Invariants | `python3 tools/check_invariants.py` | **PASSED** | Prosto Canvas (no blur), WebKit crash resilience, domain purity satisfied. | Regex pattern checks rather than full compiler AST analysis. |
| Node Security | `cd landing && npm audit` | **PASSED** | 0 vulnerabilities across landing package tree. | Scopes to web dependencies only. |
| TypeScript Compiler | `cd landing && npm run typecheck` | **PASSED** | Clean compile (`tsc --noEmit`) under `strict: true`. | Does not check Kotlin/Java code. |
| Git Churn Analysis | `git log --pretty=format: --name-only 6bc8e4c..561cc9b \| grep -v "^$" \| sort \| uniq -c \| sort -nr` | **RAN** | Verified top churn files across all 164 repository commits. | Squashed branches lose granular per-commit file changes. |
| Event & State Counts | `grep -E "^\s*(data class\|data object)\s+" app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserUiEvent.kt \| wc -l` | **RAN** | Verified 94 events in `BrowserUiEvent` and 51 properties in `BrowserUiState`. | None. |
| `ast-grep` | `which ast-grep` | **MISSING** | Tool not present in environment; substituted with `rg` (ripgrep). | Structural AST queries performed via targeted regex patterns. |
| `ktlint` / `detekt` | `which ktlint && which detekt` | **MISSING** | Neither tool installed globally or as Gradle plugin. | Non-lint formatting and Kotlin style drift not automated. |

---

## 4. Findings Table

Findings are ranked first by Severity (High, Medium, Low), then by blast radius across call sites and modules.

| ID | Category | File:Line | Severity | Effort | Description | Recommendation | Status |
|---|---|---|---|---|---|---|---|
| **SEC-01** | Security / Origin Isolation | `app/src/main/java/com/jestk/browser/engine/WebViewPool.kt:467, 476-513` | **High** | **S** | Cross-origin execution & injection in blob downloads: If `webView` is null, script executes in `pool.values.firstOrNull()`. String interpolation in `evaluateJavascript` naively escapes single quotes, breaking on newlines (`\r\n`) or line terminators and risking script injection from malicious `Content-Disposition` headers. | Require originating `webView` reference (fail cleanly if unavailable); serialize parameters via `JSONObject.quote()` instead of raw string interpolation. | NEW |
| **SEC-02** | Security / Intent Routing | `app/src/main/java/com/jestk/browser/engine/ExternalAppResolver.kt:272-277, 308-320` | **High** | **S** | Non-browsable and launcher fallback in external app offers: `ExternalAppResolver` strips `CATEGORY_BROWSABLE` if strict match fails, or falls back to direct package launch intents (`getLaunchIntentForPackage`). When the user taps the resulting "Open in app" pill, this launches activities not declared with `CATEGORY_BROWSABLE`. | Enforce `CATEGORY_BROWSABLE` on all resolution stages; do not strip browsable category or fall back to unfiltered launch intents. | NEW |
| **SEC-03** | Supply Chain / Updates | `app/src/main/java/com/jestk/browser/engine/update/AppUpdateManager.kt:252-255` | **High** | **S** | Unchecked first-asset APK selection: `parseGithubRelease` picks the first release asset ending in `.apk`. If multi-APK releases (per-ABI splits such as `arm64-v8a` or debug builds) are uploaded to GitHub, devices download arbitrary incompatible binaries. | Enforce regex matching on release asset names (e.g. `prosto-v*.apk`) and verify ABI compatibility before selecting download URL. | NEW |
| **ERR-01** | Error Handling / Concurrency | `app/src/main/java/com/jestk/browser/data/repository/SearchEngineRepositoryImpl.kt:142`, `DownloadRepositoryImpl.kt:106, 119` | **Medium** | **S** | Blanket `Throwable` catches break coroutine cancellation: Catching `Throwable` in suspending functions suppresses `CancellationException`, breaking Kotlin structured concurrency when scopes are cancelled. | Catch `Exception` instead of `Throwable` or explicitly rethrow `CancellationException`. | NEW |
| **PERF-01** | Performance & Battery | `app/src/main/java/com/jestk/browser/ui/navigation/ActionDock.kt:257-279` | **Medium** | **S** | Continuous composition invalidation on floating dock: `infiniteTransition.animateFloat` updates `bounceOffset`, which feeds into `Modifier.offset(y = bounceOffset.dp)`. This invalidates the `SwipeUpSearchHint` composable scope on every frame during animation. | Use lambda offset `.offset { IntOffset(0, bounceOffset.dp.roundToPx()) }` to avoid composition invalidation by deferring offset to layout phase. | NEW |
| **ROBUST-01** | Robustness / Memory | `app/src/main/java/com/jestk/browser/engine/download/HoriDownloadManager.kt:493-498` | **Medium** | **S** | Data URL download parser assumes base64: Unconditionally calls `Base64.decode(base64Data, Base64.DEFAULT)` without checking for `;base64`, corrupting URL-encoded data URLs (`data:text/plain,...`). Allocating large byte arrays in heap also risks JVM `OutOfMemoryError`. | Check for `;base64` before decoding, URL-decode plaintext, and stream to disk instead of allocating monolithic byte arrays. | NEW |
| **RELIAB-01** | Reliability / Storage | `app/src/main/java/com/jestk/browser/engine/SnapshotEngine.kt:207-209` | **Medium** | **S** | Non-atomic thumbnail writing: `SnapshotEngine` writes `tab_${tabId}.jpg` directly via `FileOutputStream`. Interrupted captures or killed processes leave corrupted or 0-byte JPEGs, causing image decoding failures in Coil and blank thumbnail previews. | Write to `.tmp` file first and atomically rename upon completion, matching `FaviconManager.writeBytesAtomically`. | NEW |
| **ARCH-01** | Architectural Decay | `app/src/main/java/com/jestk/browser/ui/JestkApp.kt:1-956` | **Medium** | **M** | Monolithic root UI coordinator on high churn path: 956 LOC with 46 commits (`6bc8e4c..HEAD`). Directly couples 12 distinct overlay cards, bottom bar animations, and external app intents into a single file. | Extract overlay dialogs, external app banner coordinator, and PiP listeners into dedicated presenter composables. | NEW |
| **ARCH-02** | Architectural Decay | `app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserViewModel.kt:1-1072` | **Medium** | **M** | ViewModel god class: 1,072 LOC with 42 commits (`6bc8e4c..HEAD`), 11 constructor parameters, 94 flat `BrowserUiEvent` branches, and a monolithic 51-property `BrowserUiState`. | Segment state into granular state holders (`NavigationState`, `SearchState`, `DownloadState`) to prevent broad state updates. | NEW |
| **TEST-01** | Test Debt | `app/src/main/java/com/jestk/browser/ui/JestkApp.kt` & `MainActivity.kt` | **Medium** | **M** | Untested root coordinator & Activity: Despite 698 unit tests, `JestkApp.kt` (highest churn file) and `MainActivity.kt` have zero Robolectric UI integration tests for overlay transitions or PiP. | Add Robolectric Compose tests verifying modal transitions, intent launches, and overlay state changes. | NEW |
| **PRIV-01** | Security / Privacy | `app/src/main/AndroidManifest.xml:81` | **Low** | **S** | Unrestricted cloud auto-backup of local browser state: `android:allowBackup="true"` without `android:dataExtractionRules` allows Google Drive Auto Backup to upload unencrypted Room database files and WebView caches. | Set `android:allowBackup="false"` or specify `android:dataExtractionRules` explicitly excluding private browsing databases and webview caches. | NEW |
| **DB-01** | Migration Hygiene | `app/src/main/java/com/jestk/browser/data/local/AppDatabase.kt:69-70` | **Low** | **S** | Room schema migration hygiene: `AppDatabase` is at schema v8 and only defines `MIGRATION_7_8`. Release history confirms all tagged releases (`v0.1.0`–`v0.5.0`) were built on v8 (versions 1–6 were pre-v0.1.0 internal commits). Adding blind destructive fallback is unsafe as it drops user data on unhandled migrations; explicit migrations must be maintained for v9+. | Maintain explicit step migrations for future versions (v9+) and verify with exported schema JSON; do not add destructive fallback to production builds. | NEW |
| **DEAD-01** | Dead Code | `app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserUiState.kt:57`, `app/src/main/java/com/jestk/browser/ui/navigation/BottomBar.kt:73` | **Low** | **S** | Dead state property and unused translation: `bottomBarOffsetY` is never updated or dispatched anywhere in the codebase. Auto-hide is driven by `isBottomBarVisible`. | Remove `bottomBarOffsetY` from `BrowserUiState` and remove the redundant `graphicsLayer { translationY = uiState.bottomBarOffsetY }`. | NEW |
| **CONS-01** | Consistency Rot | `app/src/main/java/com/jestk/browser/engine/SearchEngineIconDownloader.kt:77-100` | **Low** | **S** | Inconsistent HTTP client: Codebase standardizes on `OkHttpClient`, but `SearchEngineIconDownloader` opens raw `HttpURLConnection` with manual redirect loop and timeouts. | Refactor icon downloader to use the shared `OkHttpClient` instance. | NEW |
| **DOC-01** | Documentation Drift | `README.md:11,62`, `SECURITY.md:16`, `landing/src/lib/site.ts:11` vs `git remote` | **Low** | **S** | Repository URL mismatch: Remote is `Supscott/Prosto` and `AppUpdateManager` queries `Supscott/Prosto`, but README, SECURITY, CONTRIBUTING, and landing page cite `Supscott/browser`. | Synchronize repository URLs across docs and landing site to match the canonical GitHub repo name. | NEW |
| **LINT-01** | Compose API Debt | `ActionDock.kt:206, 318`, `ActiveTabPill.kt:99, 405`, `HomeScreen.kt:562`, `DownloadsScreen.kt:292` | **Low** | **S** | Modifier parameter placement: 13 composables violate Compose guideline where `modifier: Modifier = Modifier` must be the first optional parameter. | Reorder composable parameter lists so `modifier` is the first optional argument. | NEW |
| **PERF-02** | Performance | `app/src/main/java/com/jestk/browser/ui/components/ProstoToggleSwitch.kt:64` | **Low** | **S** | Non-lambda offset on animated state: `.offset(x = thumbOffset)` forces recomposition of switch Surface on every animation frame. | Use `.offset { IntOffset(thumbOffset.roundToPx(), 0) }`. | NEW |
| **DEAD-02** | Dead Code | `app/src/main/res/values/strings.xml:59`, `values-ru/strings.xml:59` | **Low** | **S** | Unused string resource: `<string name="update_section_title">UPDATES</string>` is unreferenced in both English and Russian resource bundles. | Delete `update_section_title` from both files. | NEW |
| **LINT-02** | API Hygiene | `app/src/main/java/com/jestk/browser/util/VibrationFeedback.kt:55, 61` | **Low** | **S** | InlinedApi warning on API 29 constants: `VibrationEffect.EFFECT_TICK` and `EFFECT_CLICK` referenced with `minSdk = 26`. | Annotate helper with `@SuppressLint("InlinedApi")` or guard access inside a `@RequiresApi(Build.VERSION_CODES.Q)` helper. | NEW |
| **DEP-01** | Deprecated API | `app/src/main/java/com/jestk/browser/engine/WebViewPool.kt:254, 295` | **Low** | **S** | Deprecated WebSettings: `databaseEnabled = true` triggers compiler warnings; Web SQL has been obsolete since API 19. | Remove `databaseEnabled = true` from `WebViewPool.kt` and test fixtures. | NEW |
| **RES-01** | Resource Hygiene | `app/src/main/res/mipmap-*/ic_launcher_round.png` | **Low** | **S** | Duplicate square PNGs for round launcher icons: Standard and round launcher PNGs have identical contents, triggering Android Lint `IconDuplicates`. | Replace `ic_launcher_round.png` with circular cutouts or use adaptive icon XML. | NEW |

---

## 5. Top 5 "If You Fix Nothing Else, Fix These"

### 1. Require Originating Tab & JSON-Quote Script in Blob Downloads (`WebViewPool.kt:464-495`)

**Problem:** Falling back to `pool.values.firstOrNull()` leaks blob fetches across origins if `webView` is omitted, and raw string interpolation breaks on newlines and allows script injection from unsanitized headers.

```diff
--- a/app/src/main/java/com/jestk/browser/engine/WebViewPool.kt
+++ b/app/src/main/java/com/jestk/browser/engine/WebViewPool.kt
@@ -464,19 +464,19 @@ class WebViewPool(
         webView: WebView?
     ) {
         val targetContext = context.findActivity() ?: applicationContext
-        val targetWebView = webView ?: pool.values.firstOrNull()
-        if (targetWebView == null) {
-            Toast.makeText(targetContext, "Download failed: Cannot resolve blob data", Toast.LENGTH_SHORT).show()
+        if (webView == null) {
+            Log.w(TAG, "Cannot handle blob download without originating WebView reference")
+            Toast.makeText(targetContext, "Download failed: Tab origin unavailable", Toast.LENGTH_SHORT).show()
             return
         }
 
-        val safeDisposition = contentDisposition?.replace("\\", "\\\\")?.replace("'", "\\'") ?: ""
-        val safeMime = mimeType?.replace("\\", "\\\\")?.replace("'", "\\'") ?: ""
-        val safeUrl = blobUrl.replace("\\", "\\\\")?.replace("'", "\\'") ?: ""
+        val jsonUrl = org.json.JSONObject.quote(blobUrl)
+        val jsonDisposition = org.json.JSONObject.quote(contentDisposition ?: "")
+        val jsonMime = org.json.JSONObject.quote(mimeType ?: "")
 
         val js = """
             (function() {
                 try {
                     var xhr = new XMLHttpRequest();
-                    xhr.open('GET', '$safeUrl', true);
+                    xhr.open('GET', $jsonUrl, true);
                     xhr.responseType = 'blob';
@@ -491,3 +491,3 @@ class WebViewPool(
                                 if (window.JestkBlobBridge) {
-                                    window.JestkBlobBridge.onBlobDownloaded(reader.result, '$safeDisposition', '$safeMime');
+                                    window.JestkBlobBridge.onBlobDownloaded(reader.result, $jsonDisposition, $jsonMime);
                                 }
@@ -512,3 +512,3 @@ class WebViewPool(
             })();
         """.trimIndent()
 
-        targetWebView.evaluateJavascript(js, null)
+        webView.evaluateJavascript(js, null)
```

---

### 2. Enforce CATEGORY_BROWSABLE Across All External App Fallbacks (`ExternalAppResolver.kt:269-325`)

**Problem:** Stripping `CATEGORY_BROWSABLE` or falling back to unconstrained package launch intents allows web pages to offer launch banners for internal or non-browsable activities.

```diff
--- a/app/src/main/java/com/jestk/browser/engine/ExternalAppResolver.kt
+++ b/app/src/main/java/com/jestk/browser/engine/ExternalAppResolver.kt
@@ -269,14 +269,7 @@ class ExternalAppResolver(
-        // Try the strict filter first, then relax the categories/flags. Many apps declare their
-        // deep-link filters without CATEGORY_BROWSABLE or CATEGORY_DEFAULT.
-        var matches = queryMatches(packageManager, parsedIntent, excludedPackages)
-        if (matches.isEmpty()) {
-            val withoutBrowsable = Intent(parsedIntent).apply {
-                removeCategory(Intent.CATEGORY_BROWSABLE)
-            }
-            matches = queryMatches(packageManager, withoutBrowsable, excludedPackages)
-        }
-        if (matches.isEmpty()) {
-            matches = queryMatches(packageManager, parsedIntent, excludedPackages, matchDefaultOnly = false)
-        }
+        // Enforce CATEGORY_BROWSABLE: web content must never match internal non-browsable activities.
+        val matches = queryMatches(packageManager, parsedIntent, excludedPackages)
@@ -310,12 +303,13 @@ class ExternalAppResolver(
             val directIntent = Intent(Intent.ACTION_VIEW, parsedIntent.data).apply {
                 setPackage(targetPackage)
+                addCategory(Intent.CATEGORY_BROWSABLE)
             }
             val canHandleDirectly = queryMatches(
                 packageManager,
                 directIntent,
                 excludedPackages,
-                matchDefaultOnly = false
+                matchDefaultOnly = true
             ).isNotEmpty()
-            val targetIntent = when {
-                canHandleDirectly -> directIntent
-                else -> packageManager.getLaunchIntentForPackage(targetPackage) ?: directIntent
-            }
+            if (!canHandleDirectly) continue
+            val targetIntent = directIntent
```

---

### 3. Restrict In-App Update Asset Selection to Expected Artifact Name (`AppUpdateManager.kt:251-255`)

**Problem:** `parseGithubRelease` picks the first asset with a `.apk` suffix, risking downloading incompatible architectures if multi-ABI or debug builds are published.

```diff
--- a/app/src/main/java/com/jestk/browser/engine/update/AppUpdateManager.kt
+++ b/app/src/main/java/com/jestk/browser/engine/update/AppUpdateManager.kt
@@ -251,5 +251,7 @@ fun parseGithubRelease(json: String): AppUpdate? {
         val name = asset.optString("name")
-        if (!name.endsWith(".apk", ignoreCase = true)) continue
+        // Release APKs match prosto-v<version>.apk or prosto-*.apk; ignore debug or secondary assets.
+        val isExpectedReleaseApk = name.startsWith("prosto-", ignoreCase = true) && name.endsWith(".apk", ignoreCase = true)
+        if (!isExpectedReleaseApk) continue
         val apkUrl = asset.optString("browser_download_url")
         if (apkUrl.isBlank()) continue
```

---

### 4. Preserve CancellationException in Suspending Repository Methods (`SearchEngineRepositoryImpl.kt:142`)

**Problem:** Catching `Throwable` suppresses coroutine cancellation, breaking structured concurrency when the enclosing scope is cancelled.

```diff
--- a/app/src/main/java/com/jestk/browser/data/repository/SearchEngineRepositoryImpl.kt
+++ b/app/src/main/java/com/jestk/browser/data/repository/SearchEngineRepositoryImpl.kt
@@ -141,4 +141,6 @@ class SearchEngineRepositoryImpl(
                 }
             }
+        } catch (e: kotlinx.coroutines.CancellationException) {
+            throw e
         } catch (_: Exception) {
             // Non-fatal, will retry on subsequent launches
```

---

### 5. Defer Offset Updates to Layout Phase in Floating Dock Hint (`ActionDock.kt:278-281`)

**Problem:** Using non-lambda `.offset(y = bounceOffset.dp)` invalidates the `SwipeUpSearchHint` composable scope on every animation frame. Switching to the lambda overload updates the layout node offset directly without triggering recomposition.

```diff
--- a/app/src/main/java/com/jestk/browser/ui/navigation/ActionDock.kt
+++ b/app/src/main/java/com/jestk/browser/ui/navigation/ActionDock.kt
@@ -276,7 +276,7 @@ fun SwipeUpSearchHint(
         shadowElevation = 2.dp,
         modifier =
             modifier
-                .offset(y = bounceOffset.dp)
+                .offset { IntOffset(0, bounceOffset.dp.roundToPx()) }
                 .clip(ProstoShapes.Pill)
                 .clickable(onClick = onClick),
     ) {
```

---

## 6. Quick Wins

Low effort, Medium-to-High impact fixes that can be safely applied immediately:

- [ ] **SEC-01**: In `WebViewPool.kt:467`, require non-null `webView` and wrap parameters with `JSONObject.quote()`.
- [ ] **SEC-02**: In `ExternalAppResolver.kt:272`, retain `CATEGORY_BROWSABLE` on all resolution and fallback paths.
- [ ] **SEC-03**: In `AppUpdateManager.kt:252`, validate that the release asset name matches `prosto-*.apk`.
- [ ] **ERR-01**: In `SearchEngineRepositoryImpl.kt:142` and `DownloadRepositoryImpl.kt:106, 119`, catch `Exception` and rethrow `CancellationException`.
- [ ] **PERF-01**: In `ActionDock.kt:279`, change `.offset(y = bounceOffset.dp)` to `.offset { IntOffset(0, bounceOffset.dp.roundToPx()) }`.
- [ ] **RELIAB-01**: In `SnapshotEngine.kt:207`, add `.tmp` staging and atomic rename.
- [ ] **DEAD-01**: In `BrowserUiState.kt:57` and `BottomBar.kt:73`, remove unused `bottomBarOffsetY`.
- [ ] **DEP-01**: In `WebViewPool.kt:254, 295`, remove obsolete `databaseEnabled = true`.
- [ ] **DEAD-02**: In `values/strings.xml` and `values-ru/strings.xml`, remove unused `update_section_title`.
- [ ] **PERF-02**: In `ProstoToggleSwitch.kt:64`, change `.offset(x = thumbOffset)` to `.offset { IntOffset(thumbOffset.roundToPx(), 0) }`.
- [ ] **DOC-01**: In `README.md`, `SECURITY.md`, and `landing/src/lib/site.ts`, synchronize repository URLs to `Supscott/Prosto`.

---

## 7. Things That Look Bad But Are Actually Fine

1. **Omitting Room migration paths for versions 1 through 6 (`AppDatabase.kt:69-70`):**
   - *Why it looks bad:* `AppDatabase` is at schema version 8 with only `MIGRATION_7_8` defined. It appears that users upgrading from older versions will hit an `IllegalStateException`.
   - *Why it is actually fine:* Git tag inspection confirms that all public releases (`v0.1.0`, `v0.2.0`, `v0.3.0`, `v0.4.0`, `v0.5.0`, `prod`) were authored on schema version 8. Schema versions 1 through 6 were internal development commits prior to the first release; no external users have installations on those schema versions. Adding `.fallbackToDestructiveMigration()` would be dangerous: if a future schema change (version 9) ever had an unhandled migration error, Room would silently wipe all user bookmarks, history, and tabs. Explicit migrations must be maintained instead.
2. **Retaining Room 2.6.1, OkHttp 4.12, and AGP 8.13 instead of cutting over to 2026 latest versions:**
   - *Why it looks bad:* Lint and dependency checkers flag newer available versions: Room 2.8.5, AGP 9.4.1, OkHttp 5.5, Coil 3.6.
   - *Why it is actually fine:* As established in ADR 0003, AGP 9.4 bundles Kotlin 2.2, which introduces a hard DSL conflict with KSP source sets unless KSP 2.3+ is used, and compileSdk 37 requires minor-version SDK platforms (`android-37.2`) not present on standard CI runners. The existing build configuration is fully stable, compiles in 1 second, and passes all 698 tests. Upgrading for the sake of version numbers without a feature requirement introduces high regression risk.
3. **Hosting tab drag-and-drop gestures on the container `LazyRow` instead of tab items (`TabStripRow.kt`):**
   - *Why it looks bad:* Hit-testing item coordinates from `LazyListState.layoutInfo` looks like an unusual reimplementation of gesture detection.
   - *Why it is actually fine:* As recorded in ADR 0004, `LazyRow` unmounts and disposes item `LayoutNode`s when an item moves across slots with differing content types (active pill vs inactive circular button). Disposing the node detaches the pointer input modifier and synthesizes a pointer cancel event that breaks the drag. Centralizing the gesture on the persistent `LazyRow` container host is the only robust design in Compose.
4. **`android:usesCleartextTraffic="true"` in AndroidManifest.xml:**
   - *Why it looks bad:* Cleartext traffic is enabled globally, which is flagged by security scanners as an HTTP vulnerability.
   - *Why it is actually fine:* As a general-purpose web browser, Android WebKit must be permitted to load cleartext HTTP sites that users enter or access on local development networks (`http://localhost:8080`). Application-level protection is enforced via `UrlSecurityUtils` and explicit user confirmation dialogs before opening unencrypted connections.
5. **Lightweight Python invariant checker (`tools/check_invariants.py`) instead of custom Detekt/Lint rules:**
   - *Why it looks bad:* Plain regex checks in a Python script feel less formal than compiler or Gradle plugins.
   - *Why it is actually fine:* The script executes in under 50ms without JVM startup overhead, has zero Gradle build cache dependencies, runs cleanly in pre-commit and CI, and reliably enforces the three non-negotiable architectural boundaries: zero blur (`Modifier.blur`), WebKit crash handling (`onRenderProcessGone`), and domain layer framework purity.
6. **Strict NO BLUR (`Prosto Canvas`) design tokens across all UI surfaces:**
   - *Why it looks bad:* Modern Android apps frequently reach for `Modifier.blur()` or Haze for frosted glass backdrops.
   - *Why it is actually fine:* Real-time Gaussian blur shaders consume significant GPU fill rate and cause frame drops and battery drain on mid-tier Android devices. The solid mint/ink surfaces with elevation shadows and micro-borders are a deliberate brand and performance design invariant.

---

## 8. Open Questions for the Maintainer

1. **Repository Naming Canonicalization (`Supscott/browser` vs `Supscott/Prosto`):**
   - The git remote is `https://github.com/Supscott/Prosto.git` and `AppUpdateManager` queries `Supscott/Prosto`. Is `Prosto` the canonical repository name, and should all marketing, README, and security links be updated to match?
2. **Cloud Auto-Backup Intent (`android:allowBackup="true"`):**
   - Is enabling full Google Drive Auto Backup intended to support device-to-device migration of user settings, or should browser cookies and browsing history databases be excluded via `android:dataExtractionRules`?
3. **Multi-APK vs Universal Release Distribution:**
   - Does GitHub release distribution intend to publish per-ABI split APKs in the future? If so, `AppUpdateManager.kt` needs an architecture filter before picking APK assets.
4. **Dock Offset Animation Integration:**
   - Was `bottomBarOffsetY` intended to support sub-pixel nested scroll translation (collapsing the dock proportionally as the page scrolls), or is the binary show/hide driven by `isBottomBarVisible` the intended final behavior?
