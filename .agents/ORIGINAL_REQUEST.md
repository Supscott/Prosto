# Original User Request

## Initial Request — 2026-09-02T19:08:51Z

Build **Jestk Browser for Android**, a modern, high-performance browser using Kotlin, Jetpack Compose, Material 3, Android System WebView, and Room Database based on `spec.md`, the bottom navigation mockup, and strict WebView lifecycle/snapshot requirements.

Working directory: `/Users/ilya/CODING/browser`
Integrity mode: development

## Requirements

### R1. Project Scaffolding & Architecture
- Complete Android Gradle project (`settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`) targeting Android 15 (SDK 35) with Min SDK 26, JVM 17.
- Clean MVVM architecture with unidirectional `StateFlow` and lightweight manual DI / ViewModelProvider.
- Room database for persistent Tabs, History, Favorites, and Settings.

### R2. Dual-Tier Bottom Navigation Bar
- Grounded bottom dock matching the reference mockup layout using standard Material icons: Home, Search, All Tabs (Grid), and Settings.
- Tier 1 horizontal tab strip (`LazyRow`): Active tab pill (50% screen width) showing favicon and truncated title; Inactive tabs as circular favicon buttons; Trailing `+` button.
- Interaction: Single tap active tab opens Search Overlay pre-filled with current URL; swipe left/right on active tab switches tabs; long press inactive tab opens popup menu (`Close Tab`, `Copy URL`, `Add to Favorites`).
- Smooth auto-hide dynamic translation on WebView scroll down; reveal on scroll up.

### R3. Multi-WebView Pool & Lifecycle Management
- In-memory `WebViewPool` storing live `WebView` instances per tab for instant switching and preserved scroll state.
- Prevent memory leaks and Compose parent collisions: initialize views safely with application context and execute `(webView.parent as? ViewGroup)?.removeView(webView)` before recycling/attaching in `AndroidView`.
- Keep background media playback alive: do not call `onPause()` or `pauseTimers()` on backgrounded tabs.

### R4. Reliable Page Snapshot Capture
- Capture WebView canvas thumbnails upon navigation and page load completion.
- Must use `VisualStateCallback` (`postVisualStateCallback`) or `PixelCopy` prior to canvas drawing to guarantee Blink has finished rendering frames, preventing blank/white cards in the tab grid.
- Cache snapshot images to app storage and link paths in Room DB for instant cold-boot loading.

### R5. Views & Overlays
- **Search Overlay:** Slide-in modal positioned at 0.55 bias, soft keyboard autofocus with explicit `SoftwareKeyboardController.show()` call delayed via `LaunchedEffect(Unit)`, upward-scrolling fading history stack, DuckDuckGo default.
- **All Tabs Overview (Visual Grid):** 3-column grid displaying live bitmap snapshot cards with footer (favicon, title, close button) and horizontal swipe-to-dismiss tab closing.
- **Home Screen:** 6-card recent tabs horizontal carousel at the top; favorites grid starting clean with empty-state bookmark prompt, expanding if > 6 items.
- **Settings Sheet:** Theme mode selector (System, Light, Dark), Search engine selector, Ad Blocker toggle, GitHub/Jestk.com links.

### R6. Native Ad & Tracker Blocker and Video Systems
- Bundle optimized ad/tracker ruleset in app assets loaded into a fast `HashSet` at startup with subdomain matching in `WebViewClient.shouldInterceptRequest`.
- HTML5 fullscreen handling via `WebChromeClient.onShowCustomView` and `onHideCustomView`.
- In-app draggable video mini-player when switching tabs while video is active, and system-level PiP (`enterPictureInPictureMode`) on app backgrounding.
- Back button handling: dismiss overlays -> `webView.goBack()` -> close active tab.

## Acceptance Criteria

### Compilation & Build
- [ ] `./gradlew assembleDebug` compiles cleanly with zero errors.
- [ ] No unresolved symbols, manifest errors, or Compose compiler incompatibilities.

### Architecture & Lifecycle
- [ ] `WebViewPool` correctly detaches WebViews from previous parent `ViewGroup` before attaching in Compose `AndroidView`.
- [ ] Background tabs do not pause timers or stop ongoing HTML5 audio/video streams.
- [ ] Cold boot restores previously open tabs, active tab index, and cached thumbnail snapshots from Room DB.

### UI & Interaction
- [ ] Bottom bar matches the reference dock structure with standard icons and 50% width active pill tab.
- [ ] Tapping active pill opens Search Overlay with current URL pre-filled and soft keyboard auto-focused.
- [ ] All Tabs Overview displays 3 columns with real-time snapshots and supports horizontal swipe-to-dismiss.
- [ ] Dynamic auto-hide smoothly translates bottom bar out of view on downward scroll and restores on upward scroll.

### Ad Blocker & Media
- [ ] Ad blocker intercepts ad/tracker domains with an empty `WebResourceResponse` when enabled, and passes through when toggled off in Settings.
- [ ] System PiP is triggered on user leave when video is playing.
