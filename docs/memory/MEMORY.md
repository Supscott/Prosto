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
