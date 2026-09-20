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
