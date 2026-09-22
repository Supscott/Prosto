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


