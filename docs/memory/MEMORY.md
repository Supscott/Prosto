# Project Memory & Active Architecture Log

## 1. Project Overview & Current State

- **Project:** Hori (package: `com.jestk.browser`)
- **Platform:** Android (minSdk: 26, targetSdk: 35, compileSdk: 35)
- **Tech Stack:** Kotlin (JVM 17), Jetpack Compose (BOM `2025.12.00`), AndroidX Room 2.6.1 (KSP, DB v8), Android WebKit, OkHttp 4.12.0, Coil 2.7.0.
- **Design System:** **Serene Canvas (STRICT NO BLUR)** — crisp, tactile solid surfaces, ambient dispersion shadows, precision micro-borders (`SereneBorders`), and Plus Jakarta Sans typography.
- **Current State:**
  - Branch: `feature/adblock-and-settings`.
  - Test Suite: 417 unit & Robolectric tests passing 100% green (`:app:testDebugUnitTest`).
  - Build: R8 minification enabled for release builds (`isMinifyEnabled = true`, `isShrinkResources = true`, ~4.0 MB APK).

---

## 2. Key Architectural Invariants & Rules

Every agent working on this codebase must adhere to the following architectural rules:

### 2.1 Design System: Serene Canvas (NO BLUR)
- **Strictly No Blur:** `dev.chrisbanes.haze` is completely purged. Never use `Modifier.blur()` or frosted glass shaders.
- **Surfaces:** Use solid Level 1 (`SereneCardSurface` `#FFFFFF` / Dark: `#1E293B`) and Level 2 floating cards with subtle ambient elevation shadows.
- **Micro-Borders:** Use `SereneBorders.hairlineBorder` and `SereneBorders.floatingBorder` for edge definition.
- **Overlays & Modals:** Modal backgrounds use semi-transparent scrim (`Color.Black.copy(alpha = 0.25f)`), never live backdrop blur.

### 2.2 Web Engine & Viewport Layout
- **No GraphicsLayer on Live WebView:** Never register a live `WebView` inside offscreen `RenderNode` or Compose `GraphicsLayer` (causes severe screen flicker and stale frames on Android 12+ / SDK 31+).
- **Dock Auto-Hide & Viewport Bottom Padding:** The floating bottom dock auto-hides on downward scroll **only below `h-screen`** (`scrollY > viewportHeight`). When hidden, `WebViewContainer` collapses bottom padding from `62.dp` to `0.dp`, while preserving `Modifier.navigationBarsPadding()` so web content never clips beneath the Android system navigation bar.
- **Synchronous Client Attachment:** Attach `JestkWebChromeClient` and `JestkWebViewClient` synchronously in `WebViewPool.onWebViewCreated`, never later during Compose `AndroidView.factory` layout pass.

### 2.3 ViewModel & Domain Coordinators
- **No Android Context in ViewModel:** `BrowserViewModel` does NOT hold Android `Context`. Filesystem asset operations (snapshots, favicons) are handled by engine singletons initialized at application startup.
- **Coordinator Pattern:** `BrowserViewModel` serves as a unified facade that delegates domain concerns to dedicated coordinators in `com.jestk.browser.ui.viewmodel`:
  - `TabCoordinator`: Tab lifecycle, WebKit event handling, snapshots, favicons, favorites.
  - `SearchCoordinator`: Search settings, engine CRUD, history suggestions pagination (`HISTORY_PAGE_SIZE = 20`).
  - `DownloadCoordinator`: Active download streaming from `HoriDownloadManager`, notifications, lifecycle actions.
- **State Integrity:** Screens and overlays must be rendered using derived state predicates (e.g. `isSettingsVisible`, `isDownloadsVisible`), never raw unvalidated enum comparisons.

### 2.4 Search Subsystem & Reordering
- **Modular Composables:** `SearchOverlay` is organized into single-responsibility components in `com.jestk.browser.ui.overlays`:
  - `SearchEngineCarousel`: Engine selector chips, circular fling decay, shortcut cycler.
  - `SearchEngineReorderList`: Dynamic provider reordering with physics-driven sibling displacement.
  - `SearchHistoryCard`: Bottom-anchored history list (newest items adjacent to bottom input) expanding upwards with scroll-driven pagination.
  - `SearchInputPill`: `HeroSearchInputField` using `BasicTextField` (avoids `OutlinedTextField` 56dp min-height clipping) with real-time shortcut switching (e.g. `g `, `y `).
  - `SearchFavoritesSection`: Quick-access favorite squircles docked above engine chips.
- **Reorder Gesture Rules:**
  - Detect row dragging on `PointerEventPass.Initial` past `touchSlop` so child buttons (edit, delete) remain clickable.
  - Authoritative reorder state must live in `SearchEngineReorderState.currentList by mutableStateOf(...)` to avoid stale closures.
  - Set `visibilityThreshold = 0.5f` on spatial pixel `Animatable` instances to eliminate lingering subpixel oscillation tails.
  - Snap displacement to `0f` immediately on drop settlement (`snapTo(0f)`).

### 2.5 Favicon Subsystem
- **Cache-Busting File Paths:** Tab favicons are stored as `favicon_${tabId}_${timestamp}.png`. Unique timestamps bust Coil memory and disk caches upon navigation.
- **Persistent Favorites:** When a tab is saved to favorites, its icon is copied to persistent storage (`fav_*.png`), surviving tab closure.
- **Asset URI Resolution:** Android asset URLs (`file:///android_asset/...`) must map to `android.net.Uri` (not `java.io.File`) so Coil routes them to `AssetUriFetcher`.
- **Domain-Aware Reset:** Cross-domain navigation clears `faviconUrl` to `null`; intra-domain navigation preserves existing favicons to eliminate flicker.

### 2.6 In-App Download Subsystem
- **Foreground Service:** Uses in-app `HoriDownloadService` (`START_NOT_STICKY`, `ensureForegroundStarted()` at entry) with `POST_NOTIFICATIONS` runtime permission check on Android 13+.
- **Resumable Downloads:** Partial files write to `.crdownload` and resume using HTTP `Range: bytes=X-` and `If-Range`.
- **Redirects & Headers:** `OkHttpClient` is configured with an Android `CookieManager`-backed `CookieJar`, standard Chrome `User-Agent`, and browser headers to support authenticated CDN redirects (e.g. GitHub releases).
- **Filename Resolution:** `DownloadFileNameResolver` parses RFC 5987 / 6266 UTF-8 headers and preserves media extensions (`.mp3`, `.pdf`, `.apk`) against generic `application/octet-stream` MIME types.

### 2.7 Database & Room (v8)
- **Room Schema:** Room DB version 8 with schema export enabled at `app/schemas/`.
- **SQLite 3.18 / minSdk 26 Compatibility:** History deduplication uses SQLite `MAX(timestamp)` grouping joins, avoiding window functions (`ROW_NUMBER()`).
- **No Destructive Migration on Upgrade:** Configured with `fallbackToDestructiveMigrationOnDowngrade()`. Explicit migrations (e.g. `MIGRATION_7_8`) handle index creation and schema updates.
- **Coroutines & Transactions:** DAO methods are `suspend`. Do not use `@Transaction` on Kotlin interface default methods looping over atomic queries, which causes `assertNotSuspendingTransaction` failures with active Room Flow observers.

---

## 3. Subsystem Architecture Map

```text
com.jestk.browser/
├── JestkApplication.kt                 # App startup, container setup, engine initialization
├── di/AppContainer.kt                  # Dependency injection container
├── data/
│   ├── local/                          # Room AppDatabase (v8), entities, and DAOs
│   ├── mapper/                         # Entity <-> Domain mappers
│   └── repository/                     # Repository implementations (History, Favorites, Tabs, Search)
├── domain/
│   ├── model/                          # Domain models (Tab, DownloadItem, SearchEngine, etc.)
│   └── repository/                     # Repository contracts
├── engine/
│   ├── AdBlockerEngine.kt              # Content blocking, path filtering, smart response generation
│   ├── AdBlockerScript.kt              # Cosmetic CSS injection, scriptlet surrogate mocks
│   ├── FaviconManager.kt               # Favicon persistence, timestamped cache-busting, Coil models
│   ├── JestkWebChromeClient.kt         # Progress, title, favicon, and fullscreen handling
│   ├── JestkWebViewClient.kt           # URL loading, ad interception, error handling
│   ├── NestedWebView.kt                # Touch coordinate tracking and nested scrolling
│   ├── SnapshotEngine.kt               # Tab thumbnail background capture & IO storage
│   ├── WebViewPool.kt                  # Reusable WebView instance pool & LRU lifecycle
│   └── download/                       # In-app download subsystem (Service, Manager, Resolver)
├── ui/
│   ├── MainActivity.kt                 # Single Activity host & runtime permissions
│   ├── JestkApp.kt                     # Root composable, fullscreen animated visibility, dock visibility
│   ├── navigation/                     # BottomBar, ActionDock, ActiveTabPill, TabStripRow
│   ├── overlays/                       # SearchOverlay, ContextMenus, AddFavoriteDialog
│   ├── screens/                        # SettingsScreen, DownloadsScreen, TabOverviewGrid
│   ├── theme/                          # Serene Canvas tokens (Color, Type, Theme, Shapes, Borders)
│   └── viewmodel/                      # BrowserViewModel, Coordinators (Tab, Search, Download), UiState
└── util/                               # FileSizeFormatter, DefaultBrowserHelper, etc.
```

---

## 4. Work & Milestone Summary

- **Serene Canvas Design System Transition:** Completely removed Haze and all backdrop blur. Refactored navigation dock, home screen, overlays, and modal dialogs to solid tactile surfaces with specular rim borders and elevation shadows.
- **Dedicated Full Screens:** Migrated Settings and Downloads from bottom sheets to dedicated full-screen destinations with smooth slide transitions and edge-to-edge system bar handling.
- **Scroll-Driven Dock Auto-Hide:** Implemented smart auto-hiding of the floating dock below `h-screen` with edge-to-edge WebView expansion and navigation bar safe area retention.
- **Search Engine Ecosystem & Fast Switching:** Converted search engines to database-backed customizable entities with dynamic provider CRUD, real-time prefix shortcuts, shortest circular path animations, and tactile physics-driven drag-and-drop reordering.
- **Search Overlay Decomposition:** Modularized the 2,525 LOC monolith into discrete composables (`SearchEngineCarousel`, `SearchEngineReorderList`, `SearchHistoryCard`, `SearchInputPill`, `SearchFavoritesSection`). Search history now starts at the bottom and paginates upwards.
- **Ad-Blocker Hardening:** Added content-aware smart blocked responses (1×1 transparent PNG, 204 No Content, subdocument frames), cosmetic CSS collapsing, scriptlet surrogates (AdSense, GPT, ADFOX, X5 Media), and path-based filtering for portal domains (`yandex.ru/ads/`, `mail.ru/static/ads`).
- **Foreground Download Subsystem:** Replaced Android system `DownloadManager` with an in-app `HoriDownloadService` and `HoriDownloadManager` featuring resumable `.crdownload` streaming, notification channel actions, CookieJar redirect sharing, and RFC-compliant filename resolution.
- **Architecture & Performance Modernization (Tech Debt Tracks A–F):**
  - Migrated build configuration to modern Gradle Kotlin DSL `compilerOptions` and enabled R8 minification.
  - Purged legacy synthetic mock test files, standardizing on pure Kotlin tests and Robolectric Native Graphics.
  - Decoupled `Context` from `BrowserViewModel` and modularized it into `TabCoordinator`, `SearchCoordinator`, and `DownloadCoordinator`.
  - Upgraded Room to version 8 with schema exports, non-destructive migration on downgrade, and SQLite 3.18 compatible queries.
- **Floating Dock Refinements & Tab Interactivity:**
  - Lowered floating dock position (bottom padding 2dp, capsule height 50dp, row height 54dp) ensuring it sits strictly within the 62dp viewport underlay.
  - Compact inner tab layout with 42dp active tab and 38dp inactive tabs with tight 4dp vertical padding.
  - Long-press drag-and-drop tab reordering across dock tabs (both active and inactive) with threshold movement vs context menu release.
  - Replaced WebView top 3dp loading progress bar with an in-capsule active tab loading fill (100% height, 20% opacity brand primary).
  - Implemented centered tab scrolling on inactive tab selection via calculated negative scroll offsets.
- **Adblock Stats & Protection Card:**
  - Persisted blocked ads and trackers counts in `SettingDao` via atomic SQL increments (`KEY_BLOCKED_ADS_COUNT`, `KEY_BLOCKED_TRACKERS_COUNT`).
  - Added tracker detection heuristics (`isTracker(url)`) in `AdBlockerEngine` for analytics, beacons, telemetry, and tracking pixels.
  - Intercepted and counted blocked ad and tracker requests asynchronously in `JestkWebViewClient` and `AdBlockerEngine`.
  - Exposed live statistics via `SettingsRepository` flows into `BrowserUiState` and `BrowserViewModel`.
  - Designed tactile Serene Canvas `PrivacyProtectionCard` on `HomeScreen` (positioned directly beneath `HeroSearchCapsule`) with solid card surfaces, subtle elevation, and dual stat chips ("Ads Blocked", "Trackers Stopped").
  - Cleaned up non-functional "ACCOUNT" sync stub in `SettingsScreen`.
  - Added deterministic tie-breaking secondary ordering (`h.id DESC`) to `HistoryDao` queries to eliminate sub-millisecond timestamp tie ambiguities.

