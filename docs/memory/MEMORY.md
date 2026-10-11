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
- **Persistent Domain Cache:** Domain favicons are persistently stored as `domain_<sanitized_host>.png` and indexed in a thread-safe `ConcurrentHashMap`. Navigating or opening tabs immediately loads the cached domain favicon to prevent fallback letter glyph flicker.
- **Persistent Favorites:** When a tab is saved to favorites, its icon is copied to persistent storage (`fav_*.png`), surviving tab closure.
- **Asset URI Resolution:** Android asset URLs (`file:///android_asset/...`) must map to `android.net.Uri` (not `java.io.File`) so Coil routes them to `AssetUriFetcher`.
- **Domain-Aware Reset:** Cross-domain navigation clears `faviconUrl` to `null` only if no cached domain favicon exists; navigation to a previously visited domain pre-populates `faviconUrl` immediately from cache. Intra-domain navigation preserves existing favicons to eliminate flicker.

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
- **Search Screen Refinements & Fast Carousel Switching:**
  - Removed redundant Go/arrow forward button from `HeroSearchInputField`; kept clear ("x") button with IME submission.
  - Tighter search provider favicon spacing (`40dp` vs `64dp`) and dynamic container expansion from `68dp` to `160dp` during horizontal scroll/swipe, rendering ~3-4 providers at once.
  - Set `visibilityThreshold = 0.5f` on spatial animation offset and tuned to snappy spring physics, eliminating the ~0.5s freeze/delay when releasing dragged providers.
- **Adblock Stats & Protection Card:**

  - Persisted blocked ads and trackers counts in `SettingDao` via atomic SQL increments (`KEY_BLOCKED_ADS_COUNT`, `KEY_BLOCKED_TRACKERS_COUNT`).
  - Added tracker detection heuristics (`isTracker(url)`) in `AdBlockerEngine` for analytics, beacons, telemetry, and tracking pixels.
  - Intercepted and counted blocked ad and tracker requests asynchronously in `JestkWebViewClient` and `AdBlockerEngine`.
  - Exposed live statistics via `SettingsRepository` flows into `BrowserUiState` and `BrowserViewModel`.
  - Designed tactile Serene Canvas `PrivacyProtectionCard` on `HomeScreen` (positioned directly beneath `HeroSearchCapsule`) with solid card surfaces, subtle elevation, and dual stat chips ("Ads Blocked", "Trackers Stopped").
  - Cleaned up non-functional "ACCOUNT" sync stub in `SettingsScreen`.
  - Added deterministic tie-breaking secondary ordering (`h.id DESC`) to `HistoryDao` queries to eliminate sub-millisecond timestamp tie ambiguities.
- **Search Engine Selector Dynamic Active Highlight & Scrolling Dimming:**
  - Implemented `SearchEngineCarouselInterpolation` in `SearchEngineCarousel.kt` for smooth focus transitions during carousel scroll.
  - Active center provider dynamically scales to `1.15f` with full `1.0f` opacity and a tactile Serene Canvas accent ring + indicator pill dot.
  - Adjacent non-selected providers in the expanded 160dp row smoothly dim to `0.45f` alpha and scale down to `1.0f`.
  - Added dedicated unit and visual Robolectric tests in `SearchEngineCarouselTest` verifying scale, opacity, and indicator distance thresholds.
- **Floating Dock Drag & Drop Tactile Spring Release:**
  - Implemented `TabStripDragState` with Compose `Animatable` spring physics (`Spring.StiffnessMedium`, `Spring.DampingRatioLowBouncy`).
  - Added spatial `visibilityThreshold = 0.5f` on horizontal translation spring to prevent lingering subpixel oscillation tails.
  - Smoothly settles released tab `translationX` back to 0f and `scale` from lifted (1.05f active / 1.08f inactive) back to 1.0f.
  - Maintains `zIndex = 10f` during the spring settling phase so the landing tab floats neatly over adjacent tabs until rest.
  - Cleanly handles interruptions when a new drag gesture starts while an existing drop spring is in progress.
  - Full logic and visual test suite coverage in `TabStripDragSpringTest` and `DockRefinementsTest`.
- **Tab Loading State Isolation:**
  - Implemented thread-safe per-tab loading tracking in `TabCoordinator` via `tabLoadingProgress: MutableMap<String, Int>`.
  - Fixed cross-tab progress leakage when switching from a loading tab to a loaded tab.
  - Recorded progress and page finished events for background tabs, allowing seamless state restoration when returning to background tabs.
  - Added `TabCoordinatorTest` and `BrowserViewModelTest` unit test suites covering background tab progress tracking and tab switching isolation.
- **Floating Dock Tab Spring Release & Teleport Elimination:**
  - Resolved tab "teleport" bug by replacing hardcoded 44dp swap threshold with dynamic slot distance calculation `(currentWidth + targetWidth) / 2f + spacingPx`. For active tab (148dp) and inactive tab (38dp), exact 98dp distance is subtracted from `dragOffsetPx`, keeping the dragged tab rock-solid under the finger without jumping.
  - Added `Modifier.animateItem(placementSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioLowBouncy))` to neighbor tabs in `LazyRow`, so displaced tabs smoothly spring-glide to their new slots during reordering.
  - Keyed `ActiveTabPill` pointer gesture detection on `tabId` and upgraded gesture loops in `ActiveTabPill` and `InactiveTabButton` to `PointerEventPass.Initial`, consuming pointer deltas immediately and removing premature cancellation on `isConsumed`.
  - Set `userScrollEnabled = !dragState.isDragging && !dragState.isSettling` on `LazyRow` and guarded auto-centering `animateScrollToItem` from triggering while dragging or settling.
  - Updated `TabCoordinator.reorderTabs` to immediately update in-memory `uiState.tabs`, eliminating UI stutter while waiting for Room DB writes.
  - All unit and Robolectric tests passing 100% green.
- **Live Page Titles & URLs Synchronization and Stale Metadata Elimination:**
  - Added `BrowserUiEvent.OnTitleReceived` wired from both `JestkWebChromeClient` and `JestkWebViewClient` (via `doUpdateVisitedHistory` for SPAs and in-page history navigation).
  - Implemented `handleTitleReceived` in `TabCoordinator` with immediate in-memory update and asynchronous persistence to `TabRepository` and `HistoryRepository`.
  - Reset title to `"Loading..."` on cross-domain navigation in `handlePageStarted` to eliminate stale title flashes.
  - Synchronized live WebView `url` and `title` immediately in `selectTab` from `WebViewPool`.
  - Hardened `observeDatabaseFlows` against stale Room snapshot emissions overwriting in-flight active tab state or reverting `activeTabId` during rapid tab creation.
  - Updated `TabStripRow` `ActiveTabPill` to prioritize live `uiState.pageTitle` and `uiState.currentUrl`.
  - 100% unit tests passing green.
- **Prosto Official Landing Page Development (Astro + Tailwind CSS):**
  - Created dedicated open-source landing page in `landing/` using Astro 7 + Tailwind CSS v4.
  - Implemented `DeviceSimulator.astro` with 4 interactive scenario modes: 1-swipe dock gesture, engine carousel scaling, power shortcuts (`g `, `d `, `b `, `k `), and privacy shield statistics.
  - Authored `ThumbReach.astro` highlighting mobile ergonomic reach zones on 6.7"+ displays vs traditional browsers.
  - Designed Serene Canvas styled `FeaturesBento.astro`, `TechSpecs.astro`, and `DownloadSection.astro` (direct GitHub Releases APK download and MIT license).
  - Production build verified (`pnpm build` outputting zero-config static site to `landing/dist/`).
- **Landing Serene Canvas Color Migration & Subagent Review Fixes:**
  - Migrated entire site styling to dual Serene Canvas Light/Dark design tokens from `DESIGN.md` and `Color.kt`.
  - Added theme switcher (Sun/Moon) in `Navbar.astro` with anti-FOUC script in `Layout.astro` and persistent `localStorage` synchronization.
  - Implemented multi-point ambient radial mesh gradients mimicking Android engine's `Modifier.sereneSkyBackground`.
  - Dispatched specialized review subagent and resolved all identified issues:
    - Fixed mobile viewport horizontal overflow on <=360px viewports (`max-w-[310px] sm:max-w-[350px]`).
    - Fixed input state desync in shortcuts scenario (handled empty/invalid inputs resetting to neutral search icon).
    - Fixed engine carousel dot state and DOM desync across all provider buttons.
    - Added `type="button"` and `aria-label` across all interactive elements.
    - Converted brand logo anchor from `#` to `/`.
    - Adopted event delegation on engine carousel and shortcut buttons.
- Authored comprehensive product and landing page technical specification in `landing/SPEC.md`.
  - Re-verified production build (`pnpm build` 100% clean in <450ms).

## Landing Page Review — 2026-10-08
- Fixed review findings in `DeviceSimulator.astro`: upward pointer swipes now trigger the demo, the search trigger uses button semantics, and carousel selection follows horizontal scrolling with `aria-pressed` state.
- Updated the primary download CTA to the versioned APK release asset URL specified by `landing/SPEC.md`.
- Current status: changes made; build and tests were not run.

## Landing Visual Refresh — 2026-10-08
- Compared the provided Prosto home and search screenshots with the live Helium landing page and the local landing page in Orca’s browser.
- Reworked the landing page into a quieter dark-navy visual system, shorter hero, compact feature list, simple ergonomic comparison, and reduced technical/download sections. Updated the simulator to reflect the real Prosto home screen.
- Verified the static Astro build and visually reviewed the rendered desktop hero in Orca. Android emulator was unavailable; the supplied device screenshots were used as the app reference.

## Search Engine Switcher Edit & Provider Visibility Management — 2026-10-11
- **Feature Completed:** Added absolute-positioned Edit button and floating dropdown/popup to manage visible search providers directly on search results URL pages (`SearchEngineSwitcherRow`) and in `SettingsScreen`.
- **UI & Ergonomics:**
  - Absolute Edit button anchored to the right of available providers (32dp CircleShape, rim border, elevation shadow, pencil icon).
  - Horizontal scrolling for provider chips with `padding(end = 52.dp)` applied to the scroll container, ensuring chips never overlap or hide beneath the Edit button.
  - Serene Canvas floating card popup anchored above the Edit button with `LiquidSunsetSwitch` toggles for each registered provider and bounded vertical scroll (`heightIn(max = 280.dp)`).
  - Toggling OFF removes the provider from the search results quick switcher while keeping it available on the main search screen (`SearchOverlay`, `SearchEngineCarousel`).
  - Hides the switcher row when all providers are disabled (once the edit popup closes).
  - Added identical provider visibility management dialog in `SettingsScreen` under the Search Engine section (`SearchPageProvidersDialog`).
- **Persistence & Architecture:**
  - Persisted state via `SettingKeys.HIDDEN_SEARCH_PAGE_ENGINES` in `SettingDao` (comma-delimited set, zero DB schema bump from v8).
  - Extended `BrowserSettings`, `SettingsRepository`, `BrowserUiState`, and `BrowserViewModel`.
  - Back-press and downward web scroll dismiss the floating popup. Tab switching/closing cleanly resets edit state via `TabCoordinator`.
- **Quality & Verification:**
  - 100% unit tests passing (`SettingsRepositoryTest`, `BrowserViewModelTest`).
  - Robolectric Native Graphics visual preview tests passing (`SearchEngineSwitcherEditPreviewTest`).
  - Full test suite passing green (`:app:testDebugUnitTest`).

## Open Source Preparation & GitHub Repository Showcase — 2026-10-11
- **GitHub & Open Source Identity:** Established official open-source repository branding as **Prosto Browser** under GNU General Public License v3.0 (GPL-3.0).
- **Comprehensive Documentation Suite:**
  - Authored industry-standard English [`README.md`](file:///Users/ilya/CODING/browser/README.md) featuring dynamic shields badges, one-handed ergonomics rationale, key features breakdown (Floating Gesture Dock, Search Carousel, Adblock Shield, Download Manager), ASCII visual layout, APK download links, build-from-source guides, Clean Architecture map, and roadmap.
  - Added [`LICENSE`](file:///Users/ilya/CODING/browser/LICENSE) (GNU General Public License v3.0).
  - Authored [`CONTRIBUTING.md`](file:///Users/ilya/CODING/browser/CONTRIBUTING.md) with guidelines for forks, PRs, Conventional Commits, Serene Canvas rules (NO BLUR), and test execution.
  - Added [`CODE_OF_CONDUCT.md`](file:///Users/ilya/CODING/browser/CODE_OF_CONDUCT.md) (Contributor Covenant 2.1) and [`SECURITY.md`](file:///Users/ilya/CODING/browser/SECURITY.md) for private vulnerability reporting.
- **GitHub Actions Workflows:**
  - Created `.github/workflows/ci.yml` for automated linting and unit test execution on PRs and pushes to `main`.
  - Created `.github/workflows/release.yml` for automated APK compilation (`./gradlew assembleRelease`), signing (with Secrets or reproducible CI key fallback), SHA-256 checksum calculation, and automatic GitHub Release creation on tag push `v*.*.*`.
- **Community Templates:**
  - Configured GitHub Issue Forms (`.github/ISSUE_TEMPLATE/bug_report.yml`, `feature_request.yml`, `config.yml`).
  - Added PR review checklist template (`.github/PULL_REQUEST_TEMPLATE.md`).
- **Version Alignment:** Updated `app/build.gradle.kts` and `landing/src/components/DownloadSection.astro` to initial release version `0.1.0`. All unit tests 100% green.

## External App Open Prompts ("Открыть в приложении") — 2026-10-11
- **Feature Completed:** Added native prompt pill offering to open web pages, intents, and custom schemes in installed external applications (e.g. YouTube, Telegram, Google Play, dialer, email, etc.).
- **Detection & Resolution Engine (`ExternalAppResolver`):**
  - Intercepts `intent://`, `market://`, `tel:`, `mailto:`, `sms:`, and custom schemes (`tg://`, `whatsapp://`, etc.) in `JestkWebViewClient.shouldOverrideUrlLoading`.
  - For `http://` and `https://` URLs, queries `PackageManager` and identifies dedicated native apps by excluding generic web browsers via `https://generic-browser-probe.example.internal` activity probe.
  - Excludes search engine queries (Google, Yandex, DuckDuckGo, Bing, etc.) to keep search browsing frictionless.
  - Declared `<queries>` elements in `AndroidManifest.xml` for package visibility on Android 11+ (API 30+ through targetSdk 35).
  - Handles uninstalled apps: provides "market://details?id=..." intent fallback to Google Play.
- **WebView & Tab Lifecycle Coordination:**
  - For custom schemes / intents: cancels WebView navigation (`return true`) to avoid `ERR_UNKNOWN_URL_SCHEME` error pages and preserves current page state.
  - For HTTP/HTTPS links: continues loading page in WebView in parallel (`return false`) while presenting prompt pill.
  - Spam prevention: tracks dismissed domains per tab in `TabCoordinator.dismissedExternalAppDomains`; suppresses repeated prompts for the same domain until page reload or navigation to another domain.
  - Resets active prompt on tab switch or close.
- **Serene Canvas UI (`ExternalAppNotificationPill`):**
  - Floating pill anchored above navigation dock with Level 1 solid surface, floating border, 8dp elevation shadow, and NO blur.
  - Displays app icon and specific app name ("Открыть в YouTube") or generic "Открыть в приложении", action button "Открыть", and close button "✕".
  - Horizontal swipe-to-dismiss gesture with spring physics in either direction.
  - 5-second auto-dismiss countdown with interactive pause during touch and swipe gestures.
- **Settings Integration:**
  - Added "Open in apps" ("Открыть в приложениях") setting row with `LiquidSunsetSwitch` toggle in `SettingsScreen`.
  - Persisted state via `SettingKeys.OPEN_IN_APPS_ENABLED` in `SettingDao`.
- **Quality & Verification:**
  - Added unit test suite in `ExternalAppResolverTest`.
  - Added `shouldOverrideUrlLoading` tests in `JestkWebViewClientTest`.
  - Added dismissal suppression and domain clearing tests in `TabCoordinatorTest`.
  - Added persistence tests in `SettingsRepositoryTest`.
  - Added Robolectric Native Graphics visual preview test in `ExternalAppNotificationPillTest` verifying Light and Dark theme pill renders.
  - All 500 unit and Robolectric tests passing 100% green (`:app:testDebugUnitTest`).

## External App Prompt Navigation Preservation & Direct URL Detection — 2026-10-11
- **Bug Fixed (Instagram / Search Result Click Prompt Disappearance):**
  - In `TabCoordinator.handlePageStarted`, previously `externalAppPrompt = if (!sameDomain) null else state.externalAppPrompt`. When clicking an Instagram link from Google Search, `!sameDomain("google.com", "instagram.com")` wiped out the newly created prompt within ~50ms of navigation start.
  - Fix: Checked `isPromptForCurrentNavigation` in `TabCoordinator.handlePageStarted`:
    `isSameDomain(prompt.domain, url) || isSameDomain(prompt.intentUri, url) || ExternalAppResolver.areDomainAliases(prompt.domain, ExternalAppResolver.extractDomain(url))`
    Preserves the prompt if it was offered for the destination URL or an alias of it, while clearing it if navigating to an unrelated domain.
- **Feature Completed (Direct Navigation & Domain Alias App Detection):**
  - Android WebKit's `shouldOverrideUrlLoading` is only called for link clicks and HTTP redirects, never for `loadUrl()` (such as entering URLs via address bar or bookmarks).
  - Added `checkExternalAppOffer(view, url)` inside `JestkWebViewClient.onPageStarted` and `JestkWebViewClient.doUpdateVisitedHistory` so opening URLs directly or through SPA transitions detects installed dedicated apps.
  - Added `DOMAIN_ALIASES` mapping (`vk.ru -> vk.com`, `wb.ru -> wildberries.ru`, `ok.ru -> odnoklassniki.ru`, `fb.com -> facebook.com`, `x.com -> twitter.com`, etc.) and `areDomainAliases()` in `ExternalAppResolver`. When probing `vk.ru`, if the VK app is registered under `vk.com`, it automatically probes the canonical alias and resolves `com.vkontakte.android`.
  - In `ExternalAppResolver.queryActivities`, queries `CATEGORY_BROWSABLE` first, falling back to non-categorized intent to guarantee 100% coverage across manifest configurations.
- **Verification:**
  - 506 unit and Robolectric tests passing 100% green (`:app:testDebugUnitTest`).

## External App Detection Fix for Reddit & VK (Package Visibility & Known Apps) — 2026-10-11
- **Root Cause Identified:**
  1. On Android 11+ (API 30-35), Android's package visibility security model filters out third-party packages from `PackageManager.queryIntentActivities` unless the caller declares them in `<queries>` or holds `QUERY_ALL_PACKAGES` (a permission explicitly permitted by Google Play policy for Web Browsers).
  2. Apps like Reddit declare intent filters strictly for `www.reddit.com` or specific deep paths (`/r/...`), while users entering `reddit.com` hit bare domain URLs that don't match strict host/path intent filters.
  3. Apps like VK declare intent filters for `vk.com` and `m.vk.com`, but not `vk.ru`, and often require deep-link paths.
- **Fixes Applied:**
  1. Added `QUERY_ALL_PACKAGES` permission and explicit `<package>` declarations for 20+ major services (Reddit, VK, Telegram, YouTube, Twitter/X, Instagram, Wildberries, Ozon, Avito, Kinopoisk, 2GIS, Discord, Twitch, Spotify, TikTok, etc.) to `AndroidManifest.xml`.
  2. Implemented candidate host generation in `ExternalAppResolver.resolveHttpScheme` probing `cleanHost`, `www.$cleanHost`, `m.$cleanHost`, and alias hosts.
  3. Added `KNOWN_DOMAIN_APPS` registry in `ExternalAppResolver` with `isPackageInstalled` check. When on root domains (`reddit.com`, `vk.ru`), if an app is installed on the phone, its real label and icon are resolved and presented with fallback to launch intent.
  4. Added `getLaunchIntentForPackage` fallback in `JestkApp.kt` when opening an app via URI scheme throws.
- **Verification:**
  - Added test cases in `ExternalAppResolverTest.kt` verifying `www.` subdomain resolution, Reddit package installed fallback, and VK installed package resolution on `vk.ru`.
  - All 508 unit and Robolectric tests passing 100% green (`:app:testDebugUnitTest`).




