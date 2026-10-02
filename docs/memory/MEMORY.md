# Project Memory & Progress Log

## Active Project Overview

- **Project:** Hori (Android Web Browser)
- **Language:** Kotlin / Jetpack Compose
- **Design:** Liquid Pastel Sunset (`DESIGN.md`)

---

## Work Log

### Session: Initial Agent Setup

- **Completed:**
  - Created [`AGENTS.md`](file:///Users/ilya/CODING/browser/AGENTS.md) with comprehensive operating guidelines for AI agents working on Hori.
  - Formulated standards for architecture, English documentation, logic and visual testing, and context logging in `docs/memory/`.
- **Current State:**
  - Project structure verified; unit and visual tests configured.
- **Next Steps:**
  - Read `AGENTS.md`, `MEMORY.md`, and `LESSONS.md` before starting any new tasks.

### Session: Gradle daemon JDK fix

- **Completed:**
  - Diagnosed the `IllegalArgumentException: 25.0.3` configuration failure as Android Studio's bundled JBR 25 being used as the Gradle daemon JVM.
  - Pinned the daemon JVM to JDK 23 globally via `org.gradle.java.home` in `~/.gradle/gradle.properties` (machine-level, not committed to the repo).
- **Current State:**
  - `./gradlew help` succeeds both from a JDK 23 shell and from a JBR 25 client; daemon verified running on JDK 23. Android Studio needs a Gradle sync to pick it up.
- **Key Architectural Decisions:**
  - No machine-specific JDK path is committed to the repo; the fix lives in the user-level Gradle properties so CI/other machines are unaffected. If the daemon JDK ever needs to be repo-visible, use `gradle/gradle-daemon-jvm.properties` (`toolchainVersion=23`) instead of hardcoding a path.

### Session: Fix Settings/Downloads screens not opening

- **Completed:**
  - Fixed Settings & Downloads sheets never rendering: `ActionDock` writes `activeScreen` while `JestkApp` read `activeOverlay`.
  - Added `BrowserUiState.isSettingsVisible` / `isDownloadsVisible` as the single render predicate used by `JestkApp`.
  - Extended `BrowserViewModelTest` and `BrowserViewModelDownloadsTest` to assert the predicates.
- **Current State:**
  - `:app:compileDebugKotlin` and the two ViewModel test classes pass. `JestkApp.kt` was reformatted by the editor's ktlint autofix (noisy but semantics-preserving).
- **Key Architectural Decisions:**
  - Settings/Downloads are opened via `activeScreen`; rendering must go through the `isSettingsVisible`/`isDownloadsVisible` state predicates, not raw enum comparisons.

### Session: WebView page flicker on Android 12+ (Haze GraphicsLayer capture)

- **Completed:**
  - Fixed the "page content flickers while/after opening a site, goes away when switching tabs" bug.
  - Root cause: `Modifier.haze(rootHazeState)` was applied to the `WebViewContainer` (the live WebView viewport). On SDK 31+ Haze's `HazeNode` records its content into a `GraphicsLayer` (`RenderNode`) re-recorded on every frame; a WebView painted through that layer emits blank/stale frames. The flicker only exists on Android 12+ (the only SDK-31-gated behaviour in the app), affects the whole page viewport, and stops when a tab switch disposes the `HazeNode` (which releases `HazeState.contentLayer`).
  - `JestkApp`: the WebView viewport is no longer registered as a Haze source (`WebViewContainer` gets a plain `Modifier.fillMaxSize()`); the bottom bar now uses Haze's scrim fallback over web pages.
  - `Theme.liquidFrostedGlass`: explicit `fallbackTint = HazeTint(tintedBg)` so the no-source scrim keeps its semi-transparent tint instead of Haze's `boostForFallback` (which clamped it to fully opaque).
  - New Robolectric test `ui/theme/FrostedGlassFallbackTest` renders the frosted tile over a white and a black background and asserts the pixels differ (i.e. the fallback is translucent). Verified it fails without the `fallbackTint` line.
- **Current State:**
  - `:app:testDebugUnitTest` fully green (includes `FrostedGlassFallbackTest`, `BottomBarPreviewTest`).
  - Haze still blurs safely where the source is pure Compose: Home screen overlays' own states, `SearchOverlay`, `TabOverviewGrid`.
- **Key Architectural Decisions:**
  - Never register a live `WebView` (or any `AndroidView` that repaints asynchronously) as a Haze source. Blur of page content is therefore unavailable by design; the frosted look over pages relies on the scrim fallback.
  - If real page blur is wanted later, it must come from a non-live source (e.g. the cached `SnapshotEngine` bitmap), never from the live WebView.

### Session: Tactile Floating Dock & Targeted Liquid Backdrop Blur

- **Completed:**
  - Removed `HazeState` and `liquidFrostedGlass` from the Floating Dock (`UnifiedBottomBar`, `ActionDock`, `TabStripRow`). The dock sits on `animatedSiteBg` (with `padding(bottom = 62.dp)`), where blurring a flat solid background was redundant and caused muddy opacity layering.
  - Redesigned the dock as a crisp, tactile floating capsule: solid surface with `LiquidBorders.specularBorder` (vertical gradient light rim) and `8.dp` elevation shadow.
  - Removed the redundant `62.dp` gradient strip beneath the dock in `BottomBar.kt`.
  - Refined `Theme.liquidFrostedGlass` tokens: increased `blurRadius` from `16.dp` to `24.dp`, lowered default opacity from `0.86f/0.82f` to `0.52f/0.46f`, and defaulted to specular gradient borders.
  - Connected `HomeScreen` to `LocalHazeState.current` from `JestkApp` so `HeroSearchCapsule` and home screen cards blur the pastel sunset background canvas.
  - Updated overlays (`SearchOverlay`, `TabOverviewGrid`, `SettingsSheet`, `DownloadsScreen`, context menus) to the new `24.dp` liquid glass tokens.
  - Implemented hardware-accelerated animated backdrop blur (`Modifier.blur(backdropBlurRadius)`) in `JestkApp` applied to the main viewport (`HomeScreen` / `WebViewContainer`) whenever modals, sheets, or popup menus are active (`isModalActive`).
  - Added subtle dimming scrim (`0.20f`) and outside tap dismiss for `isMenuExpanded`.
- **Current State:**
  - `:app:testDebugUnitTest` fully green (including `FrostedGlassFallbackTest` and `BottomBarPreviewTest`).
  - Verified on physical device (Motorola Edge 60 Fusion, Android 16 / SDK 35):
    - Popup menu smoothly blurs live WebViews and HomeScreen with 20.dp radius and outside tap dismiss.
    - Settings sheet blurs underlying content while silhouettes softly shine through translucent sheet background.
    - Viewport returns to 0.dp blur immediately upon modal dismissal with zero lingering overhead or flicker.
- **Key Architectural Decisions:**
  - The Floating Dock does not use Haze or backdrop blur; it is a tactile floating pill.
  - Rather than registering live WebViews into Haze (which triggers SDK 31+ GraphicsLayer flicker), modal backdrop blur is achieved by applying `Modifier.blur(backdropBlurRadius)` dynamically to the entire main viewport only while `isModalActive` is true. When browsing normally, blur radius is 0.dp.

### Session: Default Browser Recommendations & Intent Launcher Fix

- **Completed:**
  - Diagnosed bug where tapping "Set Default" in Settings or on the Home Screen did not open the system dialog or settings: `RoleManager.createRequestRoleIntent()` on Android Q+ requires an activity result to supply `getCallingPackage()`, without which Android's `RequestRoleActivity` finishes immediately.
  - Implemented `DefaultBrowserHelper.openDefaultBrowserSettings` accepting an `ActivityResultLauncher<Intent>?` with a robust 4-tier fallback: `RoleManager.ROLE_BROWSER` -> `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS` -> `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` -> `Settings.ACTION_SETTINGS`.
  - Integrated `rememberLauncherForActivityResult` in `HomeScreen` and `SettingsSheet` that triggers `BrowserUiEvent.CheckDefaultBrowser(context)`.
  - Added `viewModel.checkDefaultBrowser(this)` in `MainActivity.onCreate()` in addition to `onResume()` so `isDefaultBrowser` evaluates immediately on launch.
  - Confirmed and verified that when Hori is the default browser, all recommendations and mentions are completely hidden from both Home Screen and Settings Sheet.
  - Expanded unit test coverage in `DefaultBrowserHelperTest` covering `CheckDefaultBrowser` event dispatch, role intent launching with launcher, and settings fallbacks.
- **Current State:**
  - `:app:testDebugUnitTest` green.
- **Key Architectural Decisions:**
  - Always launch `RoleManager` intents using `ActivityResultLauncher` (or `startActivityForResult`), never bare `startActivity()`.
  - Multi-tier fallbacks ensure resilience across diverse Android OEM skins (MIUI, ColorOS, OneUI, EMUI) where `ACTION_MANAGE_DEFAULT_APPS_SETTINGS` or `RoleManager` might behave differently.

### Session: Favorites Favicons Fix

- **Completed:**
  - Fixed bug where favicons were not displayed in the Favorites section on the Home Screen and Search Overlay.
  - Added fallback favicon URLs (`FaviconManager.extractFallbackFaviconUrl(...)`) to all default presets in `DefaultPresets` and `HomeScreen.kt:itemsToDisplay`.
  - Added `FaviconManager.toImageModel()` to safely map both remote URLs (`http://`, `https://`) and local filesystem paths (`/...`, `file://...`) into Coil-compatible models (`File` or `String`), fixing the issue where raw path strings passed to `AsyncImage` failed to load in Coil.
  - Applied `toImageModel` across all favicon-rendering composables: `HomeScreen` (`FavoriteSquircleCard`), `SearchOverlay` (`SearchFavoriteSquircleCard`), `ActiveTabPill`, `TabStripRow`, and `TabOverviewGrid`.
  - Implemented `FaviconManager.persistFaviconForFavorite()` to copy temporary tab favicons to persistent favorite storage (`favicons/fav_*.png`), ensuring favorite icons survive tab closure.
  - Added `FavoriteDao.updateFavicon()`, `FavoriteRepository.updateFavicon()`, and wired `handleUpdateFavicon()` in `BrowserViewModel` so that when a webpage favicon is received, any existing favorite for that URL is automatically updated.
  - Added unit tests in `FaviconManagerTest`, `FavoriteRepositoryTest`, and `BrowserViewModelTest`.
- **Current State:**
  - `:app:testDebugUnitTest` fully green (33 actionable tasks executed/up-to-date, all assertions passing).
- **Key Architectural Decisions:**
  - Raw filesystem paths (`/...`) must be converted to `java.io.File` using `FaviconManager.toImageModel()` before being passed to Coil `AsyncImage`.
  - Tab favicon cache files (`favicon_$tabId.png`) are deleted when a tab is closed; favorite favicons must be copied to persistent files (`fav_*.png`) to ensure longevity.

### Session: Add Favorite Modal Dialog Fix

- **Completed:**
  - Fixed bug where tapping the "Add" button (`+` squircle card) in Favorites opened the Search overlay instead of a modal window to add a website.
  - Designed and implemented `AddFavoriteDialog` adhering to the Liquid Pastel Sunset design system (`LiquidShapes.Capsule` 24.dp corner radius, specular light rim border, website name and URL inputs, quick clipboard paste shortcut, and pill action buttons).
  - Added `showAddFavoriteDialog: Boolean` to `BrowserUiState`, along with `BrowserUiEvent.ShowAddFavoriteDialog` and `BrowserUiEvent.DismissAddFavoriteDialog`.
  - Added `showAddFavoriteDialog` to `isModalActive` in `JestkApp`, enabling automatic 20.dp hardware viewport backdrop blur while the dialog is visible.
  - Handled back press in `BrowserViewModel` so system back closes the dialog first if open.
  - Normalized URLs (automatically prepending `https://` if protocol omitted) and derived fallback titles from hostnames.
  - Added Russian and English string resources (`add_favorite_dialog_title`, `add_favorite_name_hint`, `add_favorite_url_hint`, `add_favorite_add_button`, `cancel`).
  - Added unit tests in `BrowserViewModelTest` covering state updates, back press dismissal, and URL normalization.
  - Created and verified Robolectric visual preview test `AddFavoriteDialogPreviewTest` rendering both Light and Dark modes to PNG.
- **Current State:**
  - `:app:testDebugUnitTest` fully green (all 33 tasks passed).
- **Key Architectural Decisions:**
  - Dialogs in the Liquid design system use `LiquidShapes.Capsule` (24.dp) and specular gradient borders.
  - The inner layout is extracted into an `AddFavoriteDialogContent` composable so it can be previewed and rendered in Robolectric tests without needing an attached platform `Window`.


### Session: Remove Preloaded Favorites

- **Completed:**
  - Removed hardcoded preloaded favorites (`DefaultPresets`) from [`HomeScreen.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/views/HomeScreen.kt).
  - Configured the Favorites section to render cleanly when empty, displaying an aligned `AddFavoriteButton` ("Add" with `+` icon) so users can populate their own favorites from scratch.
  - Retained dynamic 4-column grid layout and expand button when users add their own favorites.
- **Current State:**
  - `:app:testDebugUnitTest` fully green (33 actionable tasks executed/up-to-date, all assertions passing).
- **Key Architectural Decisions:**
  - No fake/preset bookmarks are seeded into UI or database; favorites block starts completely empty on fresh install and is user-driven.

### Session: Industrial Standard Ad-Blocker Enhancements

- **Completed:**
  - Implemented cosmetic CSS filtering rules in [`AdBlockerScript.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/engine/AdBlockerScript.kt) to collapse ad containers and slots (`ins.adsbygoogle`, DFP/GPT `div-gpt-ad`, Yandex `.yandex-adaptive`/`.ya-partner`, universal `.ad-banner`/`.ad-container`) down to 0 dimensions (`display: none !important; height: 0 !important; min-height: 0 !important; visibility: hidden !important; pointer-events: none !important;`).
  - Added scriptlet surrogates (mocks) for Google AdSense (`window.adsbygoogle`), Google Publisher Tag (`window.googletag`), and Yandex Direct (`window.yaContextCb`) to eliminate hanging promises, unhandled TypeErrors, and infinite loading spinners on host sites.
  - Added dynamic DOM Sanitizer powered by `MutationObserver` that actively detects dynamic ad wrappers and collapses single-child parent containers whose sole child was an ad.
  - Created content-aware network interception in [`AdBlockerEngine.createSmartBlockedResponse()`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/engine/AdBlockerEngine.kt):
    - 1×1 transparent PNG (`image/png`, 200 OK) for image requests to eliminate broken image icons and prevent layout reflow.
    - `application/javascript` (200 OK) with harmless stub or comments for scripts.
    - HTTP 204 No Content for beacons, telemetry, and tracking endpoints.
    - `text/html` empty document for subdocument iframes.
  - Registered `WebViewCompat.addDocumentStartJavaScript` in [`WebViewPool.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/engine/WebViewPool.kt) (when supported) coupled with `HoriAdBlockBridge` for zero-overhead, zero-flicker execution before page scripts run, with lifecycle fallback via `AdBlockerScript.injectAdBlocker` in [`JestkWebViewClient.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/engine/JestkWebViewClient.kt).
  - Synced `isAdBlockerEnabled` setting dynamically in [`WebViewContainer.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/views/WebViewContainer.kt).
  - Added unit test suites in [`AdBlockerScriptTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/engine/AdBlockerScriptTest.kt) and expanded [`AdBlockerEngineTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/engine/AdBlockerEngineTest.kt).
### Session: Russian Ad Networks & food.ru ADFOX Blocking

- **Completed:**
  - Diagnosed ad leak on `https://food.ru/recipes/181400-kak-svarit-ovsianku`:
    - `food.ru` is a Next.js app that serves ads through ADFOX (`window.Ya.adfoxCode`) dynamically loading `https://yandex.ru/ads/system/context.js`, `https://yandex.ru/ads/system/header-bidding.js`, `https://ads.x5media.ru/sdk/sdk.standalone.js`, and `https://ad.mail.ru/static/ads-async.js`.
    - `yandex.ru` is the search engine domain and could not be blocked at host level, and `AdBlockerEngine.shouldBlock` was ignoring URL paths entirely.
    - Russian ad networks were missing from `DEFAULT_HOSTS`.
    - CSS selectors missed Next.js CSS module banner classes and `[id^="adfox"]`.
  - Added URL path filtering in `AdBlockerEngine.isBlockedPath` blocking ad scripts on multi-purpose portal domains (Yandex `/ads/`, `/clck/`, `context.js`, `header-bidding.js`, Mail.ru `/static/ads`, `/ads/`, Rambler, etc.) without affecting regular search or services.
  - Expanded `DEFAULT_HOSTS` and `adblock_hosts.txt` with essential Russian ad & analytics networks (`adfox.ru`, `ad.mail.ru`, `ads.mail.ru`, `ads.x5media.ru`, `an.yandex.ru`, `yabs.yandex.ru`, `mc.yandex.ru`, `my.target.com`, `ads.vk.com`, `ssp.rambler.ru`, etc.).
  - Added surrogate mocks for ADFOX (`window.Ya.adfoxCode`, `window.Ya.Context`) and X5 Media (`window.x5media`) in `AdBlockerScript.kt` and `createSmartBlockedResponse`.
  - Added cosmetic CSS selectors for ADFOX and modern CSS module classes (`[id^="adfox"]`, `[class*="bannerContainer"]`, `[class*="incontentBanner"]`, `[class*="RightsideBanners"]`, `[class*="BannersBlock"]`, `[class*="adFeedback"]`, `[class*="tovarnayaCourusel"]`).
  - Added unit test coverage for Russian ad blocking, path interception, and Next.js selectors in `AdBlockerEngineTest` and `AdBlockerScriptTest`.
- **Current State:**
  - `:app:testDebugUnitTest` fully green (606 tests passing, 33 actionable tasks executed/up-to-date).
- **Key Architectural Decisions:**
  - When ad delivery scripts originate from top-level portal/search domains (e.g. `yandex.ru`), host-level blocking is insufficient and harmful; path-based interception (`isBlockedPath`) must be used to selectively neutralize ad endpoints without breaking primary site functionality.

### Session: Search Overlay Favorites Layout Refactor

- **Completed:**
  - Refactored [`SearchOverlay.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/overlays/SearchOverlay.kt) so the Favorites section (`SearchFavoritesSection`) is anchored immediately above the search engine selector chips (`SearchEngineSwitcherRow`).
  - Extracted `SearchFavoritesSection` out of the scrollable search history column, placing it in the bottom dock hierarchy: History suggestions (scrollable) -> Favorites -> Search engine switcher -> Search input field.
  - Updated [`SearchOverlayPreviewTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/overlays/SearchOverlayPreviewTest.kt) with sample `FavoriteItem` items to verify layout and rendering in both Light and Dark modes.
  - Re-rendered and visually inspected the preview bitmap (`build/search_overlay_preview.png`).
- **Current State:**
  - `:app:testDebugUnitTest` fully green (33 actionable tasks executed/up-to-date, all assertions passing).
- **Key Architectural Decisions:**
  - Favorites on the search overlay are docked above the search engine chips rather than scrolled inside the history column. This ensures quick one-tap access to frequent sites right above the bottom controls, while search history suggestions remain scrollable above favorites without pushing them off-screen.

### Session: Scroll-Driven Floating Dock Auto-Hide below h-screen

- **Completed:**
  - Implemented auto-hiding of the floating dock on downward scroll and reappearance on upward scroll.
  - Configured dock hiding to ONLY activate below `h-screen` (`scrollY > viewportHeight`): within the first screen height, the dock remains visible regardless of scroll direction.
  - Connected `WebView`'s `setOnScrollChangeListener` to dispatch `BrowserUiEvent.OnWebScroll(deltaY, scrollY, viewportHeight)` with threshold filtering (`abs(dy) > 8f`) and immediate boundary triggers (`scrollY <= 0` and crossing `h-screen`).
  - Wrapped `UnifiedBottomBar` in `JestkApp.kt` with `AnimatedVisibility` utilizing vertical slide (`slideOutVertically { it }` / `slideInVertically { it }`) and fade transitions (`fadeOut` / `fadeIn`), synchronized with snackbar bottom offset animations.
  - Dynamically adjusted bottom padding in `WebViewContainer.kt` between `(62.dp + navBarBottom)` when the dock is visible and `0.dp` when hidden, completely eliminating the bottom underlay (`animatedSiteBg`) and expanding the WebView to occupy the full screen height edge-to-edge.
  - Automatically restored dock visibility (`isBottomBarVisible = true`) on tab creation, tab switching, and page navigation (`handlePageStarted`).
  - Added unit test suite in [`BrowserViewModelTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/viewmodel/BrowserViewModelTest.kt) verifying scroll within first screen, hide/show below `h-screen`, Home screen dock persistence, and navigation reset.
  - Added visual preview comparison test `renderFloatingDockHiddenAndVisibleComparison` in [`BottomBarPreviewTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/navigation/BottomBarPreviewTest.kt) generating and asserting bitmaps for both visible and hidden states (`dock_visible_preview.png` and `dock_hidden_preview.png`).
- **Current State:**
  - All unit tests and visual preview tests passing (`BUILD SUCCESSFUL in 33s`).
- **Key Architectural Decisions:**
  - The WebView viewport padding dynamically collapses to `0.dp` when the dock is hidden, allowing web content to extend to the physical bottom of the display without leaving any residual background bar or underlay.
  - Scroll deltas and positions are captured directly from Android `WebView.setOnScrollChangeListener`, ensuring precise synchronization with native Chromium scroll and fling mechanics.

### Session: Bottom-Anchored Search History & Full Screen History Expansion

- **Completed:**
  - Refactored [`SearchOverlay.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/overlays/SearchOverlay.kt) so search history begins from the bottom directly above the search engine switcher and input dock (`Arrangement.Bottom` on the content column, `reversedItems` layout in `InsetGroupedHistoryCard`).
  - Beginning of search history (newest search queries, item 0) is positioned at the very bottom of the history card, immediately adjacent to the input field where user interaction occurs.
  - Placed Favorites section above search history in the content hierarchy.
  - Added full search history expansion feature:
    - Added button in the history card header (`TextButton` with `Icons.Default.OpenInFull` / `Icons.Default.CloseFullscreen`, localized label "All History" / "Collapse" / "Вся история" / "Свернуть").
    - Tapping the expand button immediately hides the software keyboard (`keyboardController?.hide()`), hides the favorites list, and expands the history card to fill the entire screen height above the bottom search input.
    - Search input field and engine switcher chips remain docked at the bottom for easy typing and query submission.
    - Expanded history queries full history via `historyRepository.getAllHistory()` rather than recent 10 items.
    - Hardware/system back press and tap on "Collapse" collapses full history back to normal view first before dismissing the overlay.
  - Added localized strings to `strings.xml` and `values-ru/strings.xml` (`search_history_title`, `expand_history`, `collapse_history`).
  - Added state property `isSearchHistoryExpanded: Boolean = false` to [`BrowserUiState`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserUiState.kt) and events `ToggleSearchHistoryExpanded`, `SetSearchHistoryExpanded` in [`BrowserUiEvent`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserUiEvent.kt).
  - Extended unit tests in [`BrowserViewModelTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/viewmodel/BrowserViewModelTest.kt) (`search_toggleSearchHistoryExpanded_updatesStateAndLoadsAllHistory` and `search_expandedHistory_backPressCollapsesHistoryFirst`).
  - Added visual preview test `renderSearchOverlayExpandedHistoryPreview` in [`SearchOverlayPreviewTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/overlays/SearchOverlayPreviewTest.kt) generating and verifying `search_overlay_expanded_preview.png`.
- **Current State:**
  - All 33 actionable build and test tasks passing (`:app:testDebugUnitTest` green).
  - Bitmaps visually verified in both collapsed and expanded states.
- **Key Architectural Decisions:**
  - `isSearchHistoryExpanded` is tracked in `BrowserUiState`, allowing `BrowserViewModel.updateHistorySuggestions` to switch between `historyRepository.getRecentHistory(10)` and `historyRepository.getAllHistory()`.
  - Back navigation hierarchy prioritizes collapsing `isSearchHistoryExpanded` before dismissing `activeOverlay = OverlayType.SEARCH`.

### Session: Context Menu Finger-Anchored Modal Refactor

- **Completed:**
  - Refactored hyperlink and image long-press context menus from full-screen `ModalBottomSheet` to floating contextual modal cards anchored near/under the user's finger, matching standard browser ergonomics (Chrome, Safari, Samsung Internet).
  - Added touch position coordinate tracking (`lastTouchX`, `lastTouchY`, `lastTouchRawX`, `lastTouchRawY`) to [`NestedWebView.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/engine/NestedWebView.kt) on `ACTION_DOWN` and `ACTION_MOVE`, with fallback touch listener in [`WebViewContainer.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/views/WebViewContainer.kt).
  - Calculated exact screen anchor coordinates (`location[0] + relX`, `location[1] + relY`) upon `setOnLongClickListener` firing, passing `anchorX` and `anchorY` via [`BrowserUiEvent.ShowLinkContextMenu`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserUiEvent.kt) and `ShowImageContextMenu`.
  - Stored `contextMenuAnchor: Offset?` and `imageContextMenuLinkUrl: String?` in [`BrowserUiState`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserUiState.kt).
  - Created [`FloatingContextMenuContainer.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/overlays/FloatingContextMenuContainer.kt) with smart positioning:
    - Anchors directly below finger (`anchor.y + 14.dp`) when space allows.
    - Automatically flips above finger (`anchor.y - 14.dp - cardHeight`) when long-press occurs near bottom navigation dock or screen edge.
    - Clamps horizontally within screen safe margins (16.dp margin).
    - Springs into view with tactile bloom animation (`scale` 0.92f -> 1f, `alpha` 0f -> 1f) originating from touch anchor (`TransformOrigin`).
    - Dismisses on tapping anywhere on outside dimming scrim or pressing system back.
  - Re-implemented [`LinkContextMenuModal`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/overlays/LinkContextMenuBottomSheet.kt) and [`ImageContextMenuModal`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/overlays/ImageContextMenuBottomSheet.kt) as compact floating cards (310.dp) adhering to Liquid Pastel Sunset tokens (`LiquidShapes.Card`, `specularBorder`, `liquidFrostedGlass`).
  - Maintained backwards-compatible aliases `LinkContextMenuBottomSheet` and `ImageContextMenuBottomSheet`.
  - Added unit test suite in [`FloatingContextMenuContainerTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/overlays/FloatingContextMenuContainerTest.kt) and expanded [`BrowserViewModelTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/viewmodel/BrowserViewModelTest.kt).
  - Added visual preview test [`LinkContextMenuPreviewTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/overlays/LinkContextMenuPreviewTest.kt) and updated [`ImageContextMenuPreviewTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/overlays/ImageContextMenuPreviewTest.kt), verifying rendered PNGs in `build/link_context_menu_preview.png` and `build/image_context_menu_preview.png`.
- **Current State:**
  - All unit tests and visual tests passing (`:app:testDebugUnitTest` green, 33 actionable tasks executed).
- **Key Architectural Decisions:**
  - Long-press contextual menus must anchor near the touch position rather than opening as full-width bottom sheets, keeping the user's visual and tactile focus in context.
  - Touch coordinates are passed via `BrowserUiEvent` and held in `BrowserUiState.contextMenuAnchor`, enabling declarative positioning while allowing pure Compose unit testing and fallback centering when no coordinates are present.

### Session: Restore Search Overlay Favorites Above Search Engine Selector

- **Completed:**
  - Restored the Favorites section (`SearchFavoritesSection`) in [`SearchOverlay.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/overlays/SearchOverlay.kt) immediately above the search engine selector chips (`SearchEngineSwitcherRow`), fixing regression where it had been placed above the search history list.
  - Preserved `isSearchHistoryExpanded` behavior so favorites hide cleanly when the user expands history to full view.
  - Re-ran [`SearchOverlayPreviewTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/overlays/SearchOverlayPreviewTest.kt) and visually validated both collapsed and expanded preview PNGs (`search_overlay_preview.png` and `search_overlay_expanded_preview.png`).
- **Current State:**
  - `:app:testDebugUnitTest` fully green (all 33 actionable tasks executed/up-to-date, all unit and preview tests passing).
- **Key Architectural Decisions:**
  - The Search screen layout structure is fixed: Search History (scrollable, expands upwards) -> Favorites (`SearchFavoritesSection`, docked above engine switcher) -> Search Engine Switcher (`SearchEngineSwitcherRow`) -> Search Input Bar (`HeroSearchInputField`).

### Session: Safearea Bottom for WebView Content

- **Completed:**
  - Preserved `Modifier.navigationBarsPadding()` on `contentModifier` in [`WebViewContainer.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/views/WebViewContainer.kt) while dynamically animating `bottomPadding` between `62.dp` (when floating dock is visible) and `0.dp` (when floating dock is hidden).
  - Fixed issue where web page content (footers, buttons, pagination, bottom text) would extend under the Android system navigation bar (gesture pill or 3-button navigation).
  - The WebView now takes all available space above the system navigation bar when the dock is hidden, leaving the system navigation bar safe area intact.
  - Updated visual preview test in [`BottomBarPreviewTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/navigation/BottomBarPreviewTest.kt) to test and visually verify the hidden state with safe area bottom (`dock_hidden_preview.png`).
- **Current State:**
  - All unit tests and visual preview tests passing (`:app:testDebugUnitTest` green, `BUILD SUCCESSFUL in 45s`).
- **Key Architectural Decisions:**
  - `navigationBarsPadding()` is kept on `contentModifier` so that the web page viewport bottom boundary always respects the system navigation safe area. The dock auto-hide animation strictly collapses the `62.dp` dock height padding down to `0.dp`.

### Session: Serene Canvas Core Theme & Token System Foundation

- **Completed:**
  - Downloaded and verified Plus Jakarta Sans variable TTF font asset (`app/src/main/res/font/plus_jakarta_sans.ttf`) supporting all weights from Google Fonts.
  - Completely removed `dev.chrisbanes.haze:haze:1.1.1` dependency from `app/build.gradle.kts`.
  - Implemented [`Type.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/theme/Type.kt) establishing `PlusJakartaSans` font family with fallbacks, custom `SereneTypographyTokens`, and Material 3 `SereneTypography` matching the exact scale from `DESIGN.md`.
  - Updated [`Color.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/theme/Color.kt) with Serene Canvas light and dark tokens (`SerenePrimary`, `SereneBrandPrimary`, `SereneAccent`, `SereneCanvasFloor` Level 0, `SereneCardSurface` Level 1, midnight navy and slate dark tokens, `sereneSkyBackground` cool-sky undertones, cool-sky gradient brushes, and backward-compatible Liquid aliases).
  - Updated [`Theme.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/theme/Theme.kt) removing all Haze and `LocalHazeState` usages, updating `LightColorScheme` and `DarkColorScheme` with Serene Canvas tokens, applying `SereneTypography`, updating `SereneShapes` (8.dp, 16.dp, 24.dp, 28.dp, 32.dp/CircleShape), implementing Level 1 micro-borders and Level 2 rim highlights (`SereneBorders`), and implementing solid `sereneCardSurface` / `sereneFloatingSurface` modifiers (no blur).
  - Cleaned up obsolete `dev.chrisbanes.haze` imports and `LocalHazeState` references across all dependent UI files (`JestkApp.kt`, `BottomBar.kt`, `ActionDock.kt`, `SearchOverlay.kt`, `TabOverviewGrid.kt`, `HomeScreen.kt`, `FrostedGlassFallbackTest.kt`).
  - Verified `:app:compileDebugKotlin` and `:app:compileDebugUnitTestKotlin` compile cleanly with 0 errors.
- **Current State:**
  - Foundation for Serene Canvas design system is in place; ready for Phase 2 (Navigation & Dock refactor) and Phase 3 (Home Screen & Overlays refactor).
- **Key Architectural Decisions:**
  - All blur and Haze dependencies are purged from the project in favor of crisp, tactile solid surfaces with ambient dispersion shadows and precision micro-borders / rim highlights.
  - Backward-compatibility aliases for Liquid tokens and shapes were preserved so other components compile without friction during their phased refactoring.

### Session: Serene Canvas Navigation & Floating Dock Refactor (NO BLUR)

- **Completed:**
  - Refactored `BottomBar.kt`, `ActionDock.kt`, `ActiveTabPill.kt`, and `TabStripRow.kt` strictly conforming to Serene Canvas design system (`DESIGN.md`) with zero blur.
  - Redesigned Floating Dock Bar as a solid Level 2 floating capsule (`SereneShapes.Dock` = 32.dp), pure white `#FFFFFF` (`SereneCardSurface`) in Light mode, `#1E293B` (`DarkSurface`) in Dark mode, with Level 2 ambient elevation shadow (`Color(0x242271B3)`) and crisp rim border (`SereneBorders.floatingBorder(isDark)`).
  - Refactored `ActiveTabPill` with `SereneBrandPrimary` tinted background (`rgba(34, 113, 179, 0.10)`) in Light mode and `rgba(96, 165, 250, 0.15)` in Dark mode, active micro-border ring, 14px-16px rounded squircle favicon container, bold Plus Jakarta Sans typography, and no blur.
  - Updated `TabStripRow` with transparent inactive tabs, `SereneMutedSlate` (`#64748B` / Dark: `#94A3B8`) icons and text, clean gradient edge fades, and persistent pinned '+' new tab button.
  - Upgraded `ActionDock` action buttons to 48px touch targets with `SereneMutedSlate` inactive icons and active press ripples.
  - Redesigned `ActionPopupMenu` and `TabContextMenu` as solid floating cards (`SereneShapes.Card` = 24.dp) with Level 2 elevation shadow, rim border, 48px touch targets, hairline dividers (`1px solid #F1F5F9` / `#334155`), Plus Jakarta Sans typography, and zero blur.
  - Updated `BottomBar.kt` Compose Previews to use `sereneSkyBackground` and `SereneCanvasFloor` instead of obsolete blur demonstration circles.
  - Verified `:app:compileDebugKotlin` and full test suite (`:app:testDebugUnitTest`, 620 tests) pass completely green.
### Session: Serene Canvas Screens & Overlays Refactor (NO BLUR)

- **Completed:**
  - `JestkApp.kt`: Completely purged all backdrop blur logic (`backdropBlurRadius`, `animateDpAsState` for blur, and `Modifier.blur()`). Main viewport renders clean and fast. Modal and overlay scrims standardized to `Color.Black.copy(alpha = 0.25f)`. App background set to `SereneCanvasFloor` / `DarkBackground`. Snackbars styled with solid `SereneShapes.Pill`.
  - `HomeScreen.kt`: Set background canvas to `sereneSkyBackground(isDark)`. Converted hero search capsule to 52px solid `CircleShape` with `SereneAccent` (`#60A5FA`) ring, ambient diffused shadow, `#64748B` glyphs, and zero blur. Favorites section wrapped in Level 1 solid card (`SereneShapes.Card`) with 44×44 squircles (`#F1F7FC` / `#334155`), 20px `SereneBrandPrimary` icon, and clean `AddFavoriteButton`. Recent tabs converted to Level 1 cards with hairline borders (`#F1F5F9` / `#334155`). Default browser prompt card refactored to solid Level 1 card with `SereneBrandPrimary` action button.
  - `WebViewContainer.kt`: Set viewport background to `SereneCanvasFloor` (Light: `#F1F7FC`, Dark: `#0F172A`). Loading progress bar and SwipeRefreshLayout updated to Serene primary and gradient brush tokens.
  - `SearchOverlay.kt`: Background set to `sereneSkyBackground(isDark)`. Search input field converted to 52px solid `CircleShape` with `SereneAccent` border and diffused shadow. Search engine chips updated to 32px pills with `SerenePaleBlue` / `DarkSurfaceVariant` selected state. History suggestions card converted to Level 1 solid card with hairline dividers and newest-first bottom anchoring. Favorites row styled with 44×44 squircles. Zero blur.
  - `TabOverviewGrid.kt`: Background set to `sereneSkyBackground(isDark)`. In-place search bar converted to solid `SereneShapes.Pill`. Tab cards converted to Level 1 solid cards (24dp radius), active tab indicator given 2dp `SereneBrandPrimary` border. Done floating button converted to solid `SereneShapes.Pill` with `SereneBorders.floatingBorder`. Zero blur.
  - `SettingsSheet.kt`: Configured top radius 28dp (`SereneShapes.BottomSheet`) and scrim `Color.Black.copy(alpha = 0.25f)`. Header, Account, Sync, AdBlock, Search Engine, About, and Helper composables (`InsetGroupedCard`, `SegmentedThemeControl`, `LiquidSunsetSwitch`, `HyperlinkRow`, `DefaultBrowserSettingTile`) refactored to solid Level 1 cards and Serene tokens with zero blur.
  - `DownloadsScreen.kt`: Configured 28dp top radius (`SereneShapes.BottomSheet`) and scrim `Color.Black.copy(alpha = 0.25f)`. Download items converted to solid Level 1 cards (`SereneShapes.Card` = 24dp), category badges updated to 42dp squircles with Serene pastel colors, progress bars to `SereneBrandPrimary`, and zero blur.
  - `FloatingContextMenuContainer.kt`, `LinkContextMenuBottomSheet.kt`, `ImageContextMenuBottomSheet.kt`: Converted floating containers to Level 2 solid cards (`SereneShapes.Card` = 24dp) with `SereneBorders.floatingBorder(isDark)` and ambient shadow. Touch targets standardized to 48px min height, hairline dividers (`#F1F5F9` / `#334155`), zero blur.
  - `AddFavoriteDialog.kt`: Refactored to Level 2 floating dialog card (24dp radius), `SereneBorders.floatingBorder(isDark)`, scrim `Color.Black.copy(alpha = 0.25f)`, `SereneBrandPrimary` button, and Plus Jakarta Sans typography.
  - Verified `:app:compileDebugKotlin` and full test suite (`:app:testDebugUnitTest`, 620 tests) pass 100% clean and green.
- **Current State:**
  - All screens, overlays, and main container across Hori are refactored to Serene Canvas with ZERO BLUR.
- **Key Architectural Decisions:**
  - Zero blur policy strictly executed across the entire app viewport and all overlays. Modals and popups use solid Level 2 surfaces over a semi-transparent scrim (`Color.Black.copy(alpha = 0.25f)`), eliminating blur performance overhead and visual artifacts entirely.

### Session: Expanded Search History Pagination

- **Completed:**
  - Implemented pagination for expanded search history (`isSearchHistoryExpanded == true`) triggered when the user scrolls to the end of the loaded list (top of the bottom-anchored, upward-expanding stack: `scrollState.value <= 20`).
  - Added Room DAO pagination queries in [`HistoryDao.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/data/local/dao/HistoryDao.kt):
    - `getPagedHistory(limit: Int, offset: Int): List<HistoryEntity>`
    - `searchHistoryPaged(query: String, limit: Int, offset: Int): List<HistoryEntity>`
  - Extended [`HistoryRepository.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/domain/repository/HistoryRepository.kt) and [`HistoryRepositoryImpl.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/data/repository/HistoryRepositoryImpl.kt) with `getPagedHistory` and `searchHistoryPaged` suspend methods.
  - Added `isLoadingMoreHistory: Boolean = false` and `hasMoreHistory: Boolean = true` to [`BrowserUiState.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserUiState.kt).
  - Added `BrowserUiEvent.LoadMoreSearchHistory` to [`BrowserUiEvent.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserUiEvent.kt).
  - Refactored [`BrowserViewModel.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserViewModel.kt):
    - Set `HISTORY_PAGE_SIZE = 20`.
    - `updateHistorySuggestions`: Loads initial page of 20 items on expansion, setting `hasMoreHistory = initialPage.size >= HISTORY_PAGE_SIZE`.
    - Added `loadMoreSearchHistory()` with guards against concurrent or redundant fetches (`isLoadingMoreHistory`, `hasMoreHistory`), deduplicating new items and appending seamlessly.
    - Reset pagination states on `OpenSearch`, `ToggleSearchHistoryExpanded`, `ClearAllHistory`, and back navigation.
  - Refactored [`SearchOverlay.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/overlays/SearchOverlay.kt):
    - Replaced blunt `LaunchedEffect(maxValue)` scrolling with smooth scroll preservation across pagination loads (calculating `delta = currentMax - prevMaxValue` when items are prepended at the top, maintaining viewport stability).
    - Added automatic end-of-loaded-list scroll detection triggering `BrowserUiEvent.LoadMoreSearchHistory`.
    - Added loading indicator inside `InsetGroupedHistoryCard` showing a 16dp `CircularProgressIndicator` with localized text "Loading more…" / "Загрузка…" when `isLoadingMore` is true.
  - Added localized strings in `strings.xml` and `values-ru/strings.xml` (`loading_more_history`).
  - Added unit test suites in:
    - [`HistoryDaoTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/data/local/HistoryDaoTest.kt) testing `getPagedHistory` and `searchHistoryPaged` offsets and limits.
    - [`HistoryRepositoryTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/data/repository/HistoryRepositoryTest.kt) testing domain mapping and pagination delegation.
    - [`BrowserViewModelTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/viewmodel/BrowserViewModelTest.kt) testing multi-page loading, query filtering pagination, boundary handling when `hasMoreHistory == false`, and collapse reset.
    - [`SearchOverlayPreviewTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/overlays/SearchOverlayPreviewTest.kt) testing visual rendering of the expanded history loading state (`search_overlay_loading_more_preview.png`).
- **Current State:**
  - Working in new git worktree `.worktrees/feat-history-pagination` on branch `feat-history-pagination`.
  - All 33 unit and visual test tasks pass green (`:app:testDebugUnitTest`).
- **Key Architectural Decisions:**
  - Expanded search history is paginated with `HISTORY_PAGE_SIZE = 20`.
  - In Hori's upward-expanding search history stack, the oldest loaded item sits at the top of the card (`scrollState.value <= 20`). Reaching this boundary triggers the next page fetch.
  - When new older items are added at the top, scroll offset is adjusted by the newly added layout delta (`scrollState.value + delta`), preserving the user's viewport on the visible item with zero jarring jumps.

### Session: Settings & Downloads Dedicated Screens Refactor

- **Completed:**
  - Refactored Settings and Downloads from bottom sheets (`ModalBottomSheet`) into dedicated, separate full screens (`SettingsScreen` and `DownloadsScreen`).
  - Created [`SettingsScreen.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/screens/SettingsScreen.kt) in package `com.jestk.browser.ui.screens`:
    - Full screen destination with `statusBarsPadding()` and `navigationBarsPadding()`.
    - Dedicated Serene top bar with Back button (`Icons.AutoMirrored.Filled.ArrowBackIos`), screen title ("Settings"), and Close button (`Icons.Default.Close`).
    - Scrollable layout with SegmentedThemeControl (System/Light/Dark), Account & Sync cards, Default Browser tile, Privacy & Protection cards (Ad Blocker, Tracking Protection, Search Engine dropdown), and About & Hori links.
    - Compose previews for Light and Dark modes.
  - Updated [`DownloadsScreen.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/screens/DownloadsScreen.kt):
    - Changed package to `com.jestk.browser.ui.screens`.
    - Removed redundant inner `navigationBarsPadding()` so the full screen edge-to-edge padding is cleanly managed.
    - Added Light and Dark Compose previews (`DownloadsScreenLightPreview`, `DownloadsScreenDarkPreview`).
    - Retained backwards-compatible `@Deprecated` wrappers for `DownloadsSheet` in `screens` and `overlays`.
  - Updated [`JestkApp.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/JestkApp.kt):
    - Switched rendering from `SettingsSheet` / `DownloadsSheet` modal sheets to full-screen `AnimatedVisibility` with smooth vertical slide + fade transitions for `SettingsScreen` and `DownloadsScreen`.
    - Updated `isBottomBarVisible` to automatically hide the bottom navigation dock whenever Settings or Downloads screen is active, and smoothly restore it upon dismissal.
    - Ensured system bars theme dynamically reflects `defaultBg` when Settings or Downloads screen is displayed, preventing site color bleed.
  - Updated tests and added visual previews:
    - Updated [`DownloadsScreenPreviewTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/screens/DownloadsScreenPreviewTest.kt) to test `DownloadsScreen` in `screens` package.
    - Created [`SettingsScreenPreviewTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/screens/SettingsScreenPreviewTest.kt) rendering full `SettingsScreen` in Light and Dark modes.
    - Verified all 621 tests passing green (`BUILD SUCCESSFUL in 37s`).
- **Current State:**
  - Merged cleanly into `main`.
  - All unit and visual preview tests passing green.
- **Key Architectural Decisions:**
  - Settings and Downloads are separate first-class screens rather than modal sheets, providing dedicated top navigation bars with Back/Close actions and edge-to-edge layout.
  - The floating navigation dock automatically hides when entering dedicated sub-screens (`!uiState.isSettingsVisible && !uiState.isDownloadsVisible`) and slides back when exiting.

### Session: Input Fields & Bottom Dock Refactor (Shadows & Blue Rim Removal)

- **Completed:**
  - Removed blue accent borders (`SereneAccent`) from search input fields:
    - `HomeScreen.kt:HeroSearchCapsule`: Replaced 1.5.dp `SereneAccent` border with subtle `SereneBorders.cardBorder(isDark)`.
    - `SearchOverlay.kt:HeroSearchInputField`: Replaced 1.5.dp `SereneAccent` border with `SereneBorders.cardBorder(isDark)`.
    - `AddFavoriteDialog.kt`: Replaced `focusedBorderColor = SereneAccent` on both name and URL `OutlinedTextField`s with neutral `fieldBorderColor`. Removed unused `SereneAccent` import.
  - Refactored Floating Dock and Active Tab Pill:
    - `Theme.kt:sereneFloatingSurface`: Enhanced elevation shadow by removing excessively attenuated low-alpha shadow colors (`Color(0x140F172A)`), using standard elevation shadow colors (`spotColor = Color.Black`, `ambientColor = if (isDark) Color.Black else Color.Black.copy(alpha = 0.85f)`) so the dock casts a rich, visible floating drop shadow.
    - `BottomBar.kt`: Removed blue `activePillBorder` (set to `null`), changed `activePillBg` to solid pastel tint (`Color(0xFFE9F1F7)` in Light / `Color(0xFF283C58)` in Dark), removed unused `SereneAccent` import, and increased dock container bottom padding from 4.dp to 8.dp for shadow breathing room.
    - `TabStripRow.kt`: Removed blue `centerPillBorder` default (set to `null`), updated `centerPillBg` to match `BottomBar.kt`, and removed unused `SereneAccent` import.
    - `ActiveTabPill.kt`: Set `border = null` default, added `shadowElevation = 2.dp` for a light, soft shadow under the active tab capsule, and moved `clip` and `combinedClickable` inside the `Surface` onto the inner `Row` to ensure the active tab shadow is never clipped.
    - `ActionDock.kt`: Aligned `ActionDockMenuButton` elevation to 10.dp.
  - Re-rendered and visually verified Robolectric Native Graphics previews (`search_overlay_preview.png`, `dock_visible_preview.png`, `bottom_bar_preview.png`, `many_tabs_bottom_bar_preview.png`).
  - Full test suite `:app:testDebugUnitTest` completely green (620 tests passing).
- **Current State:**
  - Changes implemented on branch `refactor-inputs-dock-styling` in git worktree `.worktrees/refactor-inputs-dock-styling`.
- **Key Architectural Decisions:**
  - Input fields in Serene Canvas use subtle micro-borders (`SereneBorders.cardBorder(isDark)`) rather than vibrant colored focus/outline rings.
  - In Android's elevation shadow system, RenderNode outline shadow opacity scales with the surface's background alpha. Active tab pills must have an opaque backing (solid pastel tint) rather than a 10% alpha background to cast a crisp elevation shadow without muddying transparency or disappearing.

### Session: Active Tab & Dock Styling (Figma Dark Palette & Bare Favicon)

- **Completed:**
  - Added Figma dark theme palette tokens to [`Color.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/theme/Color.kt): `DarkDockSurface` (`#0D1824`), `DarkCanvasBase` (`#080F19`), and `DarkTextHighContrast` (`#F8FAFC`).
  - Updated [`DESIGN.md`](file:///Users/ilya/CODING/browser/DESIGN.md) documenting the dark theme Figma palette (`#080F19`, `#0D1824`, `#1E293B`, `#F8FAFC`, `#94A3B8`, `#64748B`, `#1E3A8A`, `#2271B3`, `#60A5FA`) and opacity rules.
  - Refactored Floating Dock Container in [`BottomBar.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/navigation/BottomBar.kt) and [`TabStripRow.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/navigation/TabStripRow.kt):
    - Light theme: `#FFFFFF` at 80% opacity (`Color.White.copy(alpha = 0.80f)`).
    - Dark theme: `#0D1824` at 80% opacity (`DarkDockSurface.copy(alpha = 0.80f)`).
    - Smooth horizontal gradient edge fades fading from `dockBg` to transparent.
  - Refactored Active Tab Pill in [`ActiveTabPill.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/navigation/ActiveTabPill.kt):
    - Light theme: solid 100% white (`Color.White`) with light elevation shadow (2.dp) and no outline.
    - Dark theme: solid 100% `#0D1824` (`DarkDockSurface`) with light elevation shadow (2.dp) and no outline.
    - Favicon: completely removed background squircle box, rendering bare icon directly in 20.dp box.
    - Title: updated typography to bold (`FontWeight.Bold`), with `#0F172A` in light theme and `#F8FAFC` in dark theme.
    - Subtitle/Domain: maintained under title with `#64748B` in light theme and `#94A3B8` in dark theme.
    - Retained close ('x') action button on the right edge.
  - Added `renderLightDockPreview()` in [`BottomBarPreviewTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/navigation/BottomBarPreviewTest.kt) rendering `dock_light_preview.png`.
  - Re-rendered and visually verified Robolectric previews (`dock_light_preview.png`, `bottom_bar_preview.png`, `dock_visible_preview.png`).
  - Full test suite `:app:testDebugUnitTest` green (621 tests passing).
- **Current State:**
  - Branch: `feat-active-tab-and-dock-styling`
  - Worktree: `.worktrees/feat-active-tab-and-dock-styling`
- **Key Architectural Decisions:**
  - Active tab capsule is solid 100% opaque (`#FFFFFF` in light mode, `#0D1824` in dark mode) while the dock container is 80% translucent, allowing the active tab's elevation drop shadow to cast clearly onto the dock surface without muddy blending.
  - Favicon in the active tab pill is bare without background container to maximize icon clarity and match reference design.

### Session: Quick Search Engine Switcher Above Floating Dock

- **Completed:**
  - Enhanced [`SearchEngine.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/domain/model/SearchEngine.kt) with robust URL query matching and extraction (`matchesUrl`, `extractQuery`, and companion helper `findMatchingEngineAndQuery`), decoding `q` parameters and handling multi-parameter/regional query formats across Google, DuckDuckGo, and Bing.
  - Added computed property `searchPageMatch: Pair<SearchEngine, String>?` to [`BrowserUiState.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserUiState.kt) that accurately identifies when the active tab is on a search results page with a non-empty query.
  - Added `BrowserUiEvent.SwitchSearchEngineForQuery(targetEngine, query)` to [`BrowserUiEvent.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserUiEvent.kt) and implemented tab-aware switching logic in [`BrowserViewModel.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/viewmodel/BrowserViewModel.kt):
    - When another engine is clicked, if an existing tab with that engine and the same query exists (case-insensitive), switches directly to that tab.
    - If no existing tab matches, opens a new foreground tab loaded with that search engine's URL for the query.
    - If the user clicks the currently active engine, does nothing.
    - Global default search engine setting remains unchanged.
    - Synced `_uiState.tabs` in `handlePageStarted` and `handlePageFinished` to prevent stale tab URLs.
  - Updated [`SearchOverlay.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/overlays/SearchOverlay.kt) to support elevation on `SearchEngineSwitcherRow` and `SearchEngineChip`, utilizing `Surface(onClick = ...)` to prevent outer clip shadow truncation.
  - Integrated `SearchEngineSwitcherRow` directly into [`BottomBar.kt:UnifiedBottomBar`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/navigation/BottomBar.kt), floating 8.dp above the dock with smooth enter/exit animations, 3.dp elevation shadow, and automatic synchronization with dock scroll auto-hide.
  - Added unit test suite in [`SearchEngineTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/domain/model/SearchEngineTest.kt) testing URL formatting, matching, query extraction, and edge cases.
  - Added unit tests in [`BrowserViewModelTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/viewmodel/BrowserViewModelTest.kt) testing `searchPageMatch` detection, new tab creation, existing tab switching, case-insensitivity, and no-op behavior.
  - Added visual test `renderBottomBarWithSearchEngineSwitcher` in [`BottomBarPreviewTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/navigation/BottomBarPreviewTest.kt) and verified rendered bitmap (`app/build/dock_search_switcher_preview.png`).
  - Full test suite `:app:testDebugUnitTest` completely green (33 actionable tasks executed/up-to-date, all tests passing).
- **Current State:**
  - Feature fully implemented and tested on `main`.
- **Key Architectural Decisions:**
  - Quick engine switcher only activates on search result pages where a valid query exists in the URL (`searchPageMatch != null`), keeping the dock clean on search homepages and general web browsing.
  - Tab matching uses `SearchEngine.findMatchingEngineAndQuery(tab.url)` and case-insensitive comparison, prioritizing the most recently active tab if multiple exist.
  - Switching search engine via the quick switcher does not alter the global user search engine preference in `SettingsRepository`.

### Session: Search Overlay Redesign (Engine Selector Dropdown & Circular Swipe Cycling)

- **Completed:**
  - Redesigned search screen layout in [`SearchOverlay.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/overlays/SearchOverlay.kt) based on user specification and reference screenshot:
    - **Separate Selector Button & Input:** Input field placed on the left (`weight(1f)`) and standalone engine selector button placed on the right (for comfortable right-thumb reach).
    - **SearchEngineSelectorButton:** 68×52.dp `CircleShape` pill button displaying current engine badge and animated rotation arrow (`KeyboardArrowDown` rotating 180° when open).
    - **SearchEngineDropdownCard:** Floating menu card (184.dp wide, `RoundedCornerShape(20.dp)`, `10.dp` shadow elevation, `cardBorder`) anchored above the selector button, listing search engines (Google, DuckDuckGo, Bing) with active engine highlight pill and checkmark.
    - **Gesture Behaviors:**
      - Swiping horizontally on the selector button *always* cycles engines circularly in both directions (left/right) with tactile haptic feedback.
      - Swiping horizontally on the search input cycles engines circularly *only when query is empty* (`query.isEmpty()`). When text is present, standard cursor and text selection behaviors take full precedence.
    - **Input Field:** Single-line input with leading magnifying glass icon, microphone icon, clear button, and localized placeholder `"Search with <name>"` (`R.string.search_with_engine` in both English and Russian).
    - **Cleaned Layout:** Removed the old horizontal engine chips row from the search overlay column layout.
  - Added localized string `search_with_engine` to [`strings.xml`](file:///Users/ilya/CODING/browser/app/src/main/res/values/strings.xml) and Russian translation `Поиск в %1$s` to [`values-ru/strings.xml`](file:///Users/ilya/CODING/browser/app/src/main/res/values-ru/strings.xml).
  - Enhanced preview and unit testing in [`SearchOverlayPreviewTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/overlays/SearchOverlayPreviewTest.kt):
    - `renderSearchEngineSelectorAndDropdownPreview` (renders `search_selector_dropdown_preview.png`).
    - `renderSearchOverlayWithDropdownOpenPreview` (renders `search_overlay_dropdown_open_preview.png`).
    - `testCircularEngineCyclingMath` (tests circular modulo navigation across engines in both directions).
  - Visually inspected rendered PNGs across Light and Dark themes via `view_file`.
  - Full test suite `:app:testDebugUnitTest` completely green.
- **Current State:**
  - Branch: `main`
  - All unit and visual preview tests passing.
- **Key Architectural Decisions:**
  - The dropdown menu is layered as Layer 3 directly inside the root full-screen `Box`, positioned above the bottom-right selector button. This prevents parent layout shift and ensures clicks are never clipped by nested layout boundaries.
  - Circular engine indexing uses Kotlin's `(index + direction).mod(size)` for safe negative modulo wrapping.

### Session: Fix Search Engine Swipe Cycling Stale Closure & Infinite Loop

- **Completed:**
  - Resolved bug where horizontal swipe gestures on the search engine button and empty search input were restricted to bouncing between only two search engines instead of looping through all available engines (Google, DuckDuckGo, Bing).
  - Root cause:
    - `Modifier.pointerInput(density)` had `density` as its sole key, which never changes during the screen lifecycle. As a result, the gesture coroutine never restarted upon recomposition and permanently held references to the initial `onSwipeLeft` / `onSwipeRight` closures, constantly calculating relative to the initial engine.
    - Outer ViewModel updates across successive rapid swipes could introduce race conditions.
  - Fix:
    - Made `searchEngineButtonGestures` and `horizontalSwipeCycler` `@Composable Modifier` extension functions utilizing `rememberUpdatedState` for `onClick`, `onSwipeLeft`, and `onSwipeRight`.
    - Added immediate synchronous Compose state `var currentEngine by remember(uiState.settings.searchEngine) { mutableStateOf(uiState.settings.searchEngine) }` in `SearchOverlay.kt`, instantaneously advancing the active engine on each swipe and eliminating gesture races.
    - Updated gesture math to subtract/add incremental threshold steps (`totalDragX += thresholdPx` / `totalDragX -= thresholdPx`), enabling both discrete flick swipes and continuous smooth dragging through all engines.
    - Added unit test `testSequentialSwipeCyclesAllThreeEnginesContinuously` in [`SearchOverlayPreviewTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/overlays/SearchOverlayPreviewTest.kt) testing sequential multiple-swipe looping in both directions.
  - All unit tests and Robolectric preview tests pass (`:app:testDebugUnitTest`).
- **Current State:**
  - Branch: `main`
  - All tests passing; swipe cycling verified to loop smoothly and continuously across Google, DuckDuckGo, and Bing in both directions.

### Session: Search Engine Logo Carousel, Fling Momentum & Increased Scroll Distance

- **Completed:**
  - **Increased Scroll Distance:** Increased deliberate scroll distance required to switch search engines from 28-36dp to 64.dp (`itemSpacingDp = 64.dp`), preventing hyper-sensitive jumps while providing plenty of space for real-time visual feedback.
  - **Logo Carousel with Soft Edge-Fade:**
    - Implemented a continuous circular ribbon carousel in [`SearchOverlay.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/overlays/SearchOverlay.kt) inside `SearchEngineSelectorButton` (68×52.dp `CircleShape`).
    - During scrolling, the dropdown indicator arrow smoothly fades out (`arrowAlpha` -> 0f), the active logo transitions to dead center (`restingShiftPx * arrowAlpha`), and adjacent search engine logos slide into view from left and right.
    - Soft horizontal edge-fade masks (`Brush.horizontalGradient` with `BlendMode.DstIn` on an offscreen graphics layer) fade out entering/exiting badges across 14.dp at the left and right boundaries, keeping the outer capsule surface, shadow, and rim border crisp and intact.
    - At rest (`isScrolling == false`), edge-fade smoothly turns off and the badge settles with the dropdown arrow.
  - **Immersive Fling Momentum & Inertia:**
    - Implemented natural physics-based fling momentum using platform `splineBasedDecay<Float>(density)` predicting travel distance from gesture velocity (`VelocityTracker`).
    - Clamped maximum fling travel to 6 slots (2 full loops), transitioning to a smooth spring animation (`spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)`) that carries the fling's velocity, smoothly decelerates, and snaps to the nearest engine slot.
    - Synchronized subtle haptic feedback ticks (`HapticFeedbackType.TextHandleMove`) on every engine slot crossed during both manual dragging and inertial fling coasting.
  - **Synchronized Trackpad Dragging on Empty Search Input:**
    - Swiping on the empty search input field (`query.isEmpty()`) acts as a wide touch trackpad that drives the selector button's carousel in real time.
    - When text is typed in the input field, the gesture yields full priority to native cursor manipulation and text editing.
    - Clicking the selector button without dragging toggles the dropdown menu.
  - **Tests & Visual Previews:**
    - Added visual preview test `renderSearchEngineCarouselScrollingPreview` in [`SearchOverlayPreviewTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/overlays/SearchOverlayPreviewTest.kt) rendering `search_carousel_scrolling_preview.png`.
    - Added unit test `testCarouselDragSnapAndHapticFeedback` testing drag start, slot crossing haptics, and zero-velocity spring snap.
    - Added unit test `testCarouselFlingMomentumInertia` testing moderate and strong velocity fling decay and multiple-revolution coasting.
    - Fixed pre-existing `width` variable shadowing typo in `BottomBarPreviewTest.kt`.
    - Verified full test suite (`:app:testDebugUnitTest`) passes 100% green (620+ tests).
- **Current State:**
  - Branch: `main`
  - All unit and visual preview tests passing green.
- **Key Architectural Decisions:**
  - `SearchEngineCarouselState` provides a single source of truth for carousel drag offset, fling decay, spring snapping, and engine commitment, allowing both `SearchEngineSelectorButton` and `HeroSearchInputField` to drive the animation simultaneously.
  - Using `graphicsLayer { translationX = xPos }` instead of layout `offset` ensures all carousel badge translations occur entirely on the RenderNode / GPU draw pass with zero layout remeasurement.
  - Edge fade is applied via `Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent { drawContent(); drawRect(..., blendMode = BlendMode.DstIn) }` on the inner badge container, preserving the outer `Surface` pill's shadow and rim border.
### Session: Fix Search Input Fast-Typing Selection Race & Bottom Text Clipping

- **Completed:**
  - **Fixed Fast-Typing Text Selection Bug:**
    - Diagnosed the race condition where fast typing caused incoming asynchronous ViewModel `searchQuery` updates to trigger `LaunchedEffect(query) { if (query != textFieldValue.text) ... }`, resetting `textFieldValue` with stale text and selecting all text (`TextRange(0, length)`), causing subsequent keystrokes to overwrite and erase previously typed characters.
    - Hoisted `TextFieldValue` to [`SearchOverlay.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/overlays/SearchOverlay.kt) as the single source of truth for the overlay session.
    - Configured initial selection to auto-select all text ONLY on initial open when a prefilled URL exists (standard browser address bar behavior).
    - Removed `LaunchedEffect(query)` state-echo loop; updated `textFieldValue` directly on user input (`onValueChange`), history item insertion, voice recognition, and clear actions.
    - History item insertion now sets the cursor at the end (`TextRange(queryText.length)`), without selecting all text.
    - Tapping Clear (X) resets the query to empty, keeps focus in the input, and keeps the soft keyboard open.
  - **Fixed Bottom Text Clipping:**
    - Replaced Material 3 `OutlinedTextField` with `BasicTextField` using `decorationBox` inside `HeroSearchInputField`.
    - Eliminated Material 3 `OutlinedTextField`'s 56.dp min-height constraint and internal outline label padding that was pushing text descenders against the container clip bounds.
    - Applied `Modifier.weight(1f).fillMaxHeight()` and `Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart)` with `LocalSereneTypography.current.bodyMd` (14sp / 20sp line height) inside the 52.dp capsule, guaranteeing perfect vertical centering and 32.dp of vertical headroom without clipping glyph descenders ('g', 'j', 'p', 'q', 'y', '_').
  - **Testing & Visual Verification:**
    - Created [`SearchOverlayInputTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/overlays/SearchOverlayInputTest.kt) covering fast typing event dispatch, prefilled URL rendering, Clear button behavior, exact query submission, and history item insertion.
    - Added `renderSearchOverlayPrefilledTextPreview` in [`SearchOverlayPreviewTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/overlays/SearchOverlayPreviewTest.kt) to visually render prefilled text with descenders in both Light and Dark themes (`search_overlay_prefilled_preview.png`). Verified 0px clipping and pristine alignment.
    - Verified entire test suite passes 100% green (`:app:testDebugUnitTest`).
- **Current State:**
  - Branch: `fix-search-input`
  - All unit and visual preview tests passing green.
- **Key Architectural Decisions:**
  - `TextFieldValue` state is hoisted to `SearchOverlay` so that all input modifications (IME typing, history suggestions, voice recognition, clear) originate from the single source of truth without asynchronous state echo loops.
  - Custom capsule surfaces and search bars use `BasicTextField` with explicit vertical centering rather than Material 3 `OutlinedTextField` to avoid 56.dp container height constraints and outline label offset issues.

### Session: Industrial Standard Download Engine & Bug Fixes

- **Completed:**
  - **Fixed MP3 .bin Extension & Filename Parsing:**
    - Created [`DownloadFileNameResolver.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/engine/download/DownloadFileNameResolver.kt) supporting RFC 6266 / RFC 5987 UTF-8 `filename*=` Content-Disposition headers.
    - Preserved valid media extensions (`.mp3`, `.m4a`, `.flac`, `.wav`, `.mp4`, `.pdf`, `.zip`, `.apk`, etc.) regardless of server returning generic `application/octet-stream` or `binary/octet-stream` MIME types.
    - Preserved international and Cyrillic filenames (e.g. `Любимая песня.mp3`) instead of stripping non-ASCII characters with overly aggressive regexes.
    - Added automatic non-colliding duplicate file resolution `(1)`, `(2)` matching standard browser behavior.
  - **Branded Hori Notifications & Foreground Service:**
    - Replaced Android system `DownloadManager` with [`HoriDownloadService.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/engine/download/HoriDownloadService.kt) running as a foreground service with `dataSync` type.
    - Notifications are posted under the Hori branding with custom notification icon, live progress bar, speed, formatted progress text, and action buttons (`Pause`, `Resume`, `Cancel`, `Retry`, `Open`).
    - Added `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />`, `FOREGROUND_SERVICE_DATA_SYNC`, and `POST_NOTIFICATIONS` in `AndroidManifest.xml`.
  - **Auto-Resume & Background Resilience:**
    - Implemented streaming with HTTP `Range: bytes=X-` partial download resumption in [`HoriDownloadManager.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/engine/download/HoriDownloadManager.kt) using `.crdownload` temporary files.
    - Downloads survive when Hori is swiped away from Recents or unloaded from memory; sticky foreground service and database persistence ensure uninterrupted downloads.
    - Propagated WebView cookies, User-Agent, and Referer headers to eliminate 403 Forbidden anti-hotlink failures.
  - **Real-Time Reactive Progress & UI Refactor:**
    - Replaced static `"Downloading..."` with dynamic formatted size `"14.2 MB / 45.0 MB (31%)"`, falling back to `"14.2 MB"` if total size is unknown.
    - Streamed live progress via `StateFlow` to [`DownloadsScreen.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/screens/DownloadsScreen.kt) with smooth progress bar updates.
    - Implemented Pause, Resume, Cancel, Retry, and Delete controls on each download card.
    - Deleting a download deletes both the database record and the physical file from disk.
    - Completed downloads are indexed into Android `MediaScannerConnection` so they immediately appear in system file managers and media players.
  - **Data Layer & Room Migration:**
    - Bumped `AppDatabase` to version 5 with updated `DownloadEntity` (`status`, `bytesDownloaded`, `totalBytes`, `errorMessage`, `etag`).
    - Added `DownloadStatus` enum and progress/status DAO queries in `DownloadDao`.
  - **Testing & Visual Verification:**
    - Created unit tests in [`DownloadFileNameResolverTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/engine/download/DownloadFileNameResolverTest.kt), [`DownloadDaoTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/data/local/DownloadDaoTest.kt), and [`DownloadRepositoryTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/data/repository/DownloadRepositoryTest.kt).
    - Expanded [`BrowserViewModelDownloadsTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/viewmodel/BrowserViewModelDownloadsTest.kt) with download events and physical file deletion tests.
    - Added `renderActiveDownloadsScreenPreview` in [`DownloadsScreenPreviewTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/screens/DownloadsScreenPreviewTest.kt) and visually verified `build/downloads_active_preview.png`.
    - All unit and visual tests pass 100% green (`:app:testDebugUnitTest`).
- **Current State:**
  - Branch: `fix-search-input`
  - All unit and Robolectric tests passing green.
- **Key Architectural Decisions:**
  - Modern browsers must run their own foreground download service with persistent notifications rather than delegating to Android system `DownloadManager`, which lacks custom branding, fails on complex redirects/headers, doesn't support blob/data URLs natively, and provides no reactive progress streams.
  - Incomplete downloads write to `.crdownload` temporary files and resume using HTTP `Range: bytes=X-` and `If-Range` headers.
  - File names must be resolved with RFC 5987 / RFC 6266 UTF-8 decoders, preserving original media extensions even if the HTTP server returns `application/octet-stream`.

### Session: Fix ForegroundServiceDidNotStartInTimeException, Runtime POST_NOTIFICATIONS & GitHub Redirects

- **Completed:**
  - **Eliminated Foreground Service Crash Loop:**
    - Modified [`HoriDownloadService.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/engine/download/HoriDownloadService.kt) to call `ensureForegroundStarted()` immediately at entry of `onStartCommand()`, satisfying Android's OS contract for `startForegroundService()` and eliminating `ForegroundServiceDidNotStartInTimeException`.
    - Changed service return to `START_NOT_STICKY` to prevent infinite restart crashes.
    - Updated [`HoriDownloadManager.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/engine/download/HoriDownloadManager.kt) so `deleteDownload()` and `cancelDownload()` only interact with the service if the download was actually running, and use `startService()` for control signals.
  - **Added Runtime Notification Permission (Android 13+):**
    - Implemented `ActivityResultContracts.RequestPermission()` for `android.permission.POST_NOTIFICATIONS` in [`MainActivity.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/MainActivity.kt) on API 33+ so users can grant notification permissions and see status bar download progress.
  - **Robust GitHub & Redirect Downloads:**
    - Fixed off-thread WebView access in `WebViewPool.handleDownload` (captured `refererUrl` safely on main thread).
    - Equipped `OkHttpClient` with an Android `CookieManager`-backed `CookieJar` so redirected domains (e.g. `github.com` -> `objects.githubusercontent.com`) automatically share cookies.
    - Added fallback to mobile Chrome `User-Agent` and standard browser headers (`Accept`, `Accept-Language`, `Connection`) to prevent CDN blocks.
    - Added dynamic refinement of destination filename if server specifies a `Content-Disposition` header upon redirect.
  - **Database Migration Bump:**
    - Bumped `AppDatabase` version to 6 so `fallbackToDestructiveMigration()` cleans up old/corrupted table states.
  - Verified with full test suite `:app:testDebugUnitTest` (all tests passing) and built debug APK (`:app:assembleDebug`).
- **Current State:**
  - Branch: `fix-search-input`
  - All tests passing, debug APK built and ready to install.

### Session: Dock Favicon Lifecycle & Cache-Busting Fix

- **Completed:**
  - Diagnosed and resolved all dock favicon display bugs: favicons not loading on fresh navigation, requiring reload or tab switch to appear, and old website favicons lingering after navigating to a new site.
  - **Early Client Attachment in WebViewPool:** Added `onWebViewCreated` callback to `WebViewPool` and wired `JestkApp` to immediately attach `JestkWebChromeClient` and `JestkWebViewClient` upon `WebView` creation. This eliminates the race condition where `loadUrl` began before `WebChromeClient` was attached in Compose `AndroidView.factory`.
  - **Dual Favicon Extraction:** Extended `JestkWebViewClient` with `onFaviconReceived` extracting `favicon` from `onPageStarted(favicon)` and `onPageFinished(view?.favicon)` alongside `JestkWebChromeClient.onReceivedIcon`.
  - **Timestamped Favicon Files:** Updated `FaviconManager.saveFavicon` to save timestamped files (`favicon_${tabId}_${timestamp}.png`) and delete older tab files, preventing Coil's in-memory bitmap cache from serving stale images from identical file paths. Added `FaviconManager.deleteTabFavicons(context, tabId)`.
  - **Domain-Aware Reset:** Updated `BrowserViewModel.loadUrl` and `handlePageStarted` with `isSameDomain(oldUrl, newUrl)` check. Navigating to a new domain immediately resets `tab.faviconUrl = null` in both `BrowserUiState` and Room DB (`TabDao.updateFavicon(id, null)`). Intra-domain navigation preserves existing favicons to avoid flicker.
  - **Database & Event Nullability:** Made `TabDao.updateFavicon`, `TabRepository.updateFavicon`, and `BrowserUiEvent.UpdateFaviconUrl` accept nullable `faviconUrl: String?` to support favicon clearing.
  - **Dock Substrate Pattern:** Refactored `ActiveTabPill.kt`, `TabStripRow.kt`, and `TabOverviewGrid.kt` with `isFaviconSuccess` state pattern. Substrate letter initial is displayed during loading or on error, and hidden once the favicon image successfully decodes, preventing visual artifacts through transparent PNG favicons.
  - **Tests & Verification:**
    - Added unit tests in `FaviconManagerTest` (`saveFavicon_createsTimestampedFileAndCleansUpOlderTabFiles`, `deleteTabFavicons_removesAllFaviconsForTab`).
    - Added unit tests in `BrowserViewModelTest` (`loadUrl_toDifferentDomain_resetsFaviconToNull`, `loadUrl_toSameDomain_preservesFavicon`, `onPageStarted_toDifferentDomain_resetsFaviconToNull`, `onPageStarted_toSameDomain_preservesFavicon`).
    - Full unit and visual test suite (`:app:testDebugUnitTest`) executed and 100% green (33 actionable tasks, 0 failures).
- **Current State:**
  - Branch: `fix-favicon`
  - All unit and visual preview tests passing green.
- **Key Architectural Decisions:**
  - Attach WebKit clients (`WebChromeClient`, `WebViewClient`) synchronously at the moment of `WebView` instantiation (`onWebViewCreated`) rather than waiting for Compose `AndroidView.factory` layout pass.
  - Invalidate Coil bitmap cache by producing timestamped URI paths (`favicon_${tabId}_${timestamp}.png`) rather than overwriting static files in-place.
  - Immediately clear favicon state on cross-domain navigation while preserving it for intra-domain page changes.

### Session: Custom Search Providers, Dynamic Reordering, and Shortcut Search Execution

- **Completed:**
  - **Dynamic Search Engine Architecture:**
    - Converted `SearchEngine` from fixed `enum` to an extensible data class with companion constants (`DUCKDUCKGO`, `GOOGLE`, `BING`, `DEFAULT_ENGINES`) and `@JvmField` / `@JvmStatic` annotations for Java interop.
    - Added Room persistence via `SearchEngineEntity`, `SearchEngineDao`, and `SearchEngineRepositoryImpl` (Room DB bumped to version 5 with automatic default engine seeding).
    - Repository and Room layer support full CRUD, order position queries, and case-insensitive shortcut lookup.
  - **Real-Time Shortcut Search Execution:**
    - Integrated real-time shortcut detection in `HeroSearchInputField`: typing `<shortcut> ` (e.g. `g `, `d `, `b `, or custom shortcuts like `y `) immediately switches the active search engine badge/carousel and clears the prefix from the input field.
    - Added fallback shortcut detection on query submission in `BrowserViewModel.loadUrl` for pasted queries (e.g. `g android architecture` directly dispatches to Google search and sets the active engine).
  - **Custom Search Provider Modal (`AddEditSearchEngineDialog`):**
    - Built modal with 3 inputs: Title, Shortcut (optional), and URL Template (prefilled with `https://`).
    - Added inline `%s` check, shortcut uniqueness validation against existing engines, and informational helper card: `Write the search url and replace the query with %s.\n\nFor example: https://youtube.com/search?q=%s.`.
    - Integrated immediate favicon preview using `FaviconManager.toImageModel()` and automatic favicon resolution from domain.
    - Wired dialog into `JestkApp.kt`, `SearchOverlay.kt`, and `SettingsScreen.kt`.
  - **Interactive Search Engine Dropdown (`SearchEngineDropdownCard`):**
    - Added bottom flex row with Edit (pencil) and Add (+) buttons.
    - Edit mode reveals a drag handle (`=`) and edit pencil on the left, shortcut badge and delete trash can on the right, and transforms the bottom row into a full-width "Done" button.
    - Supported reordering with haptic feedback, inline editing, and deletion (with safety guard preventing deleting the last engine).
  - **Settings Integration:**
    - Updated `SettingsScreen.kt` Search Engine dropdown to render dynamic providers with shortcuts.
    - Added "Add Search Engine" action row in the settings group card.
  - **Testing & Visual Verification:**
    - Added comprehensive unit tests in `SearchEngineDaoTest.kt`, `SearchEngineRepositoryTest.kt`, `SearchProviderViewModelTest.kt`, and `SearchEngineTest.kt` covering CRUD, reordering, shortcut dispatch, and fallback behavior.
    - Created `AddEditSearchEngineDialogPreviewTest.kt` producing visual PNG previews for Light/Dark themes and Normal/Edit modes (`add_edit_search_engine_dialog_preview.png`, `search_engine_dropdown_card_preview.png`).
    - Executed full test suite (`:app:testDebugUnitTest`) with 690+ tests passing 100% green.
- **Current State:**
  - Branch: `search-providers`
  - All unit, DAO, repository, ViewModel, and Robolectric visual preview tests passing green.
- **Key Architectural Decisions:**
  - Converting `SearchEngine` to an open data class with `@JvmField` and `@JvmStatic` companion members maintained 100% backward compatibility with all legacy Java and Kotlin test suites while unlocking database-backed custom providers.
  - Safely extract host from search URL templates containing `%s` without failing RFC 2396 URI parsing (`searchUrlTemplate.replace("%s", "placeholder")`).
  - Resolve dynamic search engines against `state.searchEngines` inside `BrowserViewModel`'s settings collector so custom providers persist across app restarts and updates.

### Session: Fix Pre-Installed Search Engine Favicon Display

- **Completed:**
  - Diagnosed and fixed the bug where pre-installed search engines (Google, DuckDuckGo, Bing) did not display their favicons/SVGs, rendering only fallback initial letter glyphs ("D", "G", "B").
  - **Asset URI Resolution in FaviconManager:**
    - Root cause: `FaviconManager.toImageModel()` stripped `"file://"` and returned `java.io.File("/android_asset/...")` for built-in engines. APK assets do not exist on the Linux filesystem root, causing Coil's `FileFetcher` to fail with `FileNotFoundException`.
    - Fix: Updated `FaviconManager.toImageModel()` to intercept `file:///android_asset/` and `/android_asset/` and return `Uri.parse(urlOrPath)` directly. This routes the request to Coil's `AssetUriFetcher` which reads from APK asset resources via `context.assets.open(...)`.
  - **Immediate Rendering in SearchEngineBadge:**
    - Replaced `.crossfade(true)` with `.crossfade(false)` in `SearchEngineBadge`'s `ImageRequest.Builder` to prevent transparent initial frames and animation flicker.
  - **Tests & Verification:**
    - Added assertions to `FaviconManagerTest.toImageModel_handlesNullBlankLocalAndRemotePaths` verifying that asset URLs return `android.net.Uri` instances.
    - Updated `SearchOverlayPreviewTest` and `AddEditSearchEngineDialogPreviewTest` to idle the looper and ensure asynchronous image decodes complete before preview capture.
    - Verified generated visual PNG artifacts (`search_badges_preview.png`, `search_engine_dropdown_card_preview.png`, `search_overlay_dropdown_open_preview.png`, `search_overlay_preview.png`), confirming that DuckDuckGo, Google, and Bing SVG favicons render crisply across light and dark themes.
    - Full test suite (`:app:testDebugUnitTest`) executed with all 690+ tests passing 100% green.
- **Current State:**
  - Branch: `search-providers`
  - Pre-installed search engines and custom search providers both render favicons properly.
- **Key Architectural Decisions:**
  - Always map Android asset paths (`file:///android_asset/...`) to `android.net.Uri` rather than `java.io.File` so image loaders utilize `AssetUriFetcher` rather than `FileFetcher`.
  - Disable crossfade transitions on small icon/badge composables to ensure instantaneous rendering without visual delay.

### Session: Scroll Animation to Search Engine on Shortcut

- **Completed:**
  - Added smooth animated scrolling to the selected search engine in `SearchEngineCarouselState` when triggered via shortcut search (e.g. typing `g `, `d `, `b `, or custom shortcuts like `y `) and dropdown selection.
  - **Shortest Circular Distance Algorithm:**
    - Evaluates circular distance in `SearchEngineCarouselState.animateToEngine`: calculates shortest path (`(targetIndex - currentIndex).mod(size)`) in forward or backward directions so the carousel takes the most natural, shortest scroll path.
  - **Organic Spring Physics & Haptics:**
    - Drives the carousel transition using a spring animation (`dampingRatio = 0.85f`, `stiffness = Spring.StiffnessMediumLow`).
    - Fires tactile haptic ticks (`HapticFeedbackType.TextHandleMove`) for each slot traversed during the animation.
    - Synchronously sets `isFlinging = true` and updates settings so queries submitted immediately during the animation execute with the chosen engine without delay.
  - **Tests & Verification:**
    - Added unit tests in `SearchOverlayPreviewTest` (`testAnimateToEngineForwardAndBackward`, `testAnimateToEngineWithCustomEngines`) verifying forward, backward, wrap-around circular motion, haptic emission, and custom engine traversal.
    - Full project test suite (`:app:testDebugUnitTest`) executed with 100% pass rate.
### Session: Real Search Engine Drag & Drop with Fluid Animations & Sibling Displacement

- **Completed:**
  - Implemented real, physics-driven vertical drag & drop reordering for search engines in `SearchEngineDropdownCard` Edit Mode.
  - **Dynamic Gap Opening ("Освобождение места под ним"):**
    - Built [`SearchEngineReorderState`](file:///Users/ilya/CODING/browser/.worktrees/refactor/fast-switching-search/app/src/main/java/com/jestk/browser/ui/overlays/SearchOverlay.kt): calculates continuous slot position during drag and drives sibling items up or down with spring displacement (`Spring.DampingRatioMediumBouncy`), dynamically holding an empty slot open under the floating item.
  - **Tactile Floating Drag Layer:**
    - The active item lifts with scale (`1.04x`), elevation shadow (`8.dp`), brand highlight rim border, and `zIndex = 10f`, continuously following finger movement with bounds clamping.
  - **Universal Row Drag Gesture with Child Click Protection:**
    - Dragging can be initiated anywhere on the card row using `PointerEventPass.Initial` past `touchSlop`.
    - Tap actions on the edit pencil and delete trash buttons remain responsive and independent (touch slop cancellation prevents accidental edits during drag).
  - **Bouncy Snap Drop & Immediate Persistence:**
    - On release, the dragged item smoothly snaps into the target slot using `Animatable.animateTo` with bouncy spring physics and landing haptics (`HapticFeedbackType.TextHandleMove`).
    - Immediately commits reordered list to Room database (`SearchEngineRepository.updateOrderPositions`) on drop.
  - **Tests & Visual Previews:**
    - Created comprehensive unit test suite in [`SearchEngineReorderStateTest.kt`](file:///Users/ilya/CODING/browser/.worktrees/refactor/fast-switching-search/app/src/test/java/com/jestk/browser/ui/overlays/SearchEngineReorderStateTest.kt) covering downward drag, upward drag, sibling offset calculations, bounds clamping, drop animations, and cancel recovery (all 6 tests passing).
    - Added `renderSearchEngineDropdownCardDragPreview` in [`AddEditSearchEngineDialogPreviewTest.kt`](file:///Users/ilya/CODING/browser/.worktrees/refactor/fast-switching-search/app/src/test/java/com/jestk/browser/ui/overlays/AddEditSearchEngineDialogPreviewTest.kt) rendering the active mid-flight drag and sibling gap displacement in both Light and Dark modes to `build/search_engine_dropdown_drag_preview.png`.
    - Full test suite (`:app:testDebugUnitTest`) executed 100% green.
    - Updated TickTick task "драг эн дроп" to `test` column.
- **Current State:**
  - Branch: `refactor/fast-switching-search`
  - Reordering search engines in the dropdown features fluid spring animations, tactile drag feedback, and dynamic slot displacement.
- **Key Architectural Decisions:**
  - Use `PointerEventPass.Initial` to detect and consume vertical drag gestures before child clickables (pencil, delete) process them, allowing the whole row to be draggable while preserving button tap clicks.
  - Sibling offsets and the dragged item's drop offset are driven by spring physics while keeping the item's identity stable (`key(engine.id)`), ensuring seamless transitions with zero layout flicker upon list commitment.


