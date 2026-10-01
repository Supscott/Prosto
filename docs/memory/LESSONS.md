# Lessons Learned & Bug Post-Mortems

This document tracks bugs encountered during development, their root causes, and verified fixes to avoid repeating mistakes.

---

## Log Template

```markdown
### [Bug Title / Short Description]
- **Date:** YYYY-MM-DD
- **Symptom:** What went wrong or failed?
- **Root Cause:** Why and how did it occur?
- **Resolution & Prevention:** How was it fixed, and how should future code prevent this?
```

---

## Resolved Bugs & Lessons

### Gradle configuration fails with `java.lang.IllegalArgumentException: 25.0.3`

- **Date:** 2026-09-19
- **Symptom:** Every Gradle sync/build failed during the configuration phase with `FAILURE: Build failed with an exception. * What went wrong: 25.0.3`, thrown from `org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse` during Kotlin DSL script compilation. The Gradle daemon itself started fine.
- **Root Cause:** Android Studio 2026.1 bundles JBR `25.0.3` and `.idea/gradle.xml` uses `gradleJvm = #GRADLE_LOCAL_JAVA_HOME`, which fell back to that JBR. Gradle 8.14.5 embeds a Kotlin/IntelliJ `JavaVersion` parser that cannot parse the Java 25 version string, so the Kotlin DSL compiler aborts before the build is configured. Nothing in the project sources was wrong.
- **Resolution & Prevention:** Pin the Gradle daemon JVM to a JDK that Gradle 8.14.x understands (JDK 23 here) with `org.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-23.jdk/Contents/Home` in `~/.gradle/gradle.properties`. That property wins over the client JVM, so Android Studio (and CLI runs from any shell) fork a JDK 23 daemon. Verified: client = JBR 25, daemon = JDK 23, `./gradlew help` → `BUILD SUCCESSFUL`.
- **Prevention:** When a Gradle failure happens before any task runs and the message is a bare Java version number, suspect the daemon JDK, not the build script. Keep the Gradle daemon on a JDK the wrapper supports (JDK 21 is the safest for AGP 8.7 / Kotlin 2.0.21); do not let the IDE's bundled JBR silently drive the daemon JVM.

### Settings & Downloads sheets never opened

- **Date:** 2026-02-20
- **Symptom:** Tapping Settings or Downloads in the ActionDock / popup menu did nothing — no sheet appeared.
- **Root Cause:** Two parallel pieces of state described the same UI. `OpenSettings`/`OpenDownloads` set `activeScreen = SETTINGS/DOWNLOADS` and reset `activeOverlay = NONE`, but `JestkApp` only rendered `SettingsSheet`/`DownloadsSheet` when `activeOverlay == SETTINGS/DOWNLOADS`. The render condition and the state the events actually wrote had drifted apart (the overlay-based wiring was correct until commit `075850c`; the screen state was added later without updating the renderer).
- **Resolution & Prevention:** Added `BrowserUiState.isSettingsVisible` / `isDownloadsVisible` predicates that accept either `activeScreen` or `activeOverlay`, made `JestkApp` render from those, and asserted them in `BrowserViewModelTest` / `BrowserViewModelDownloadsTest`. Keep a single render predicate derived from state; never duplicate "is this screen open" checks across ViewModel and UI.

### Page content flickers on Android 12+ after opening a site

- **Date:** 2026-09-19
- **Symptom:** Opening any website made the whole page viewport flicker (rapid blank/stale frames). It continued after the page finished loading and stopped only after switching to another tab and back. Android 12+ only.
- **Root Cause:** `JestkApp` applied `Modifier.haze(rootHazeState)` to `WebViewContainer`, i.e. the live WebView was a Haze source. Haze 1.1.1's `HazeNode.draw()` calls `contentLayer.record { drawContent() }` + `drawLayer(...)` whenever `canUseGraphicLayers()` is true, which is `SDK_INT >= 31 && canvas.isHardwareAccelerated`. So from Android 12 (SDK 31 — note: not 32, where the blur itself starts) the WebView is repainted through an offscreen `RenderNode` re-recorded every frame, and Chromium's functor output ends up stale/blank. A tab switch disposes the `HazeNode`, whose `onDetach()` releases `HazeState.contentLayer`, so the next draw records a fresh layer — hence the "fix by switching tabs".
- **Resolution & Prevention:** Do not put a live `WebView` (or any asynchronously repainting `AndroidView`) inside `Modifier.haze`/`hazeSource`. Over web pages the frosted glass uses Haze's scrim fallback; `Theme.liquidFrostedGlass` now passes an explicit `fallbackTint` so that fallback stays semi-transparent (Haze's `boostForFallback` multiplies alpha by `1 + blurRadius/72`, which turned the dock opaque). Guarded by `FrostedGlassFallbackTest`, which compares the tile rendered over a white vs a black background — identical pixels mean the fallback became opaque. When diagnosing a rendering bug, the SDK version threshold is the strongest clue: grep for the only SDK-gated code path before suspecting app logic.

### Misapplying backdrop blur over solid background paddings

- **Date:** 2026-09-19
- **Symptom:** The floating bottom dock looked muddy, grey, and plasticky. Text in the active tab pill had poor contrast, and the dock appeared over-engineered and murky.
- **Root Cause:** In `WebViewContainer.kt`, `Modifier.padding(bottom = 62.dp)` ensured the WebView ended above the bottom bar. The dock was actually positioned over `animatedSiteBg` (a flat, single-color background) with an additional 62dp bottom gradient. Applying `liquidFrostedGlass` with Haze over a flat solid color cannot produce any optical blur effect; instead, it resulted in compounding semi-transparent layers (dock fallback tint + center pill background) with opacities exceeding 0.90, making the dock look like murky plastic.
- **Resolution & Prevention:** Do not apply backdrop blur or `Haze` to UI components that sit over solid/empty color bars. Instead, use clean, tactile design: solid/opaque `Surface`, subtle specular highlights (`LiquidBorders.specularBorder`), and elevation drop shadows. Reserve backdrop blur strictly for elements that hover over rich, multi-colored graphic content (such as modal overlays and the Home Screen pastel gradient canvas).

### Missing modal backdrop blur over WebViews

- **Date:** 2026-09-20
- **Symptom:** Opening the action popup menu, settings sheet, or modals over a web page had zero backdrop blur, rendering as flat opaque/translucent cards without optical depth.
- **Root Cause:** After removing `WebViewContainer` as a Haze source to prevent the Android 12+ GraphicsLayer flicker bug, Haze had zero sources whenever a web page was displayed. Any overlay attempting to use `Modifier.hazeChild()` fell back to flat tinted scrim with no blur.
- **Resolution & Prevention:** For modal overlays, bottom sheets, and popup menus, do not attempt to register the live WebView into Haze. Instead, apply Compose's native hardware `Modifier.blur(backdropBlurRadius)` to the entire main viewport (`HomeScreen` / `WebViewContainer`) in `JestkApp.kt`. Animate `backdropBlurRadius` dynamically based on `isModalActive` (from `0.dp` to `20.dp` on open, back to `0.dp` on dismiss). This provides deep, GPU-accelerated backdrop blur across both web pages and Compose screens with zero flicker and zero overhead during normal browsing.

### RoleManager.createRequestRoleIntent does not show UI when launched with startActivity()

- **Date:** 2026-09-20
- **Symptom:** Tapping "Set as Default" on the Home Screen or in the Settings Sheet did nothing — no system role picker dialog or default apps settings opened, and no exception was logged in `try / catch`.
- **Root Cause:** On Android Q+ (API 29+), `RoleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)` directs to system `RequestRoleActivity`. `RequestRoleActivity` inspects `getCallingPackage()` to identify and verify the app requesting the role. When started with `context.startActivity(intent)`, `getCallingPackage()` is `null`, causing `RequestRoleActivity` to immediately call `finish()` without displaying anything and without throwing an exception.
- **Resolution & Prevention:** Never invoke role request intents with `context.startActivity()`. Always launch via `rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult())` (or `Activity.startActivityForResult()`) to ensure `getCallingPackage()` is populated. Additionally, implement multi-tier fallbacks in `DefaultBrowserHelper.openDefaultBrowserSettings`: `RoleManager` -> `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS` -> `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` -> `Settings.ACTION_SETTINGS`.

### Favicons not displayed in favorites and tab components

- **Date:** 2026-09-20
- **Symptom:** Favicons were not appearing on the Home Screen favorite cards, search overlay favorites, or tab bars. Cards either showed generic Material icons or blank icons.
- **Root Cause:**
  1. `DefaultPresets` in `HomeScreen.kt` omitted `iconUrl`, defaulting to `null`. On fresh launch or empty favorites, `DefaultPresets` was rendered as-is without favicon URLs.
  2. Coil's `AsyncImage` received raw local filesystem path strings (e.g., `"/data/user/0/.../favicons/favicon_1.png"`). In Coil, `StringMapper` converts string data via `Uri.parse()`, producing a `Uri` with a null scheme. Because Coil's `FileUriFetcher` requires `uri.scheme == "file"`, raw path strings failed fetcher selection and resulted in image load errors.
  3. `BrowserViewModel.addCurrentToFavorites()` had a guard `tabFavicon.startsWith("http")`, which discarded the real favicon downloaded from the website via WebChromeClient because tab favicons are saved as local absolute filesystem paths.
  4. Tab favicon cache files (`favicon_$tabId.png`) are deleted upon tab closure, which would invalidate any favorite pointing directly to the tab's temporary icon file.
  5. Receiving a favicon in `handleUpdateFavicon` updated tabs and recent tabs, but never updated the favorite stored for that URL.
- **Resolution & Prevention:**
  1. Add default fallback favicon URLs (`FaviconManager.extractFallbackFaviconUrl(...)`) to all default presets in `DefaultPresets` and dynamically in `HomeScreen.kt:itemsToDisplay`.
  2. Introduce `FaviconManager.toImageModel()`: if the path starts with `/` or `file://`, map it to `java.io.File`; otherwise return the URL string. Use this across all Compose `AsyncImage` callers (`FavoriteSquircleCard`, `SearchFavoriteSquircleCard`, `ActiveTabPill`, `TabStripRow`, `TabOverviewGrid`).
  3. Implement `FaviconManager.persistFaviconForFavorite()` to copy tab favicons to persistent favorite icon files (`fav_*.png`), which survive tab closure.
  4. Add `updateFavicon` to `FavoriteDao` and `FavoriteRepository`, and call it in `BrowserViewModel.handleUpdateFavicon` to keep favorites synchronized with loaded icons.

### Favorites 'Add' button opened Search overlay instead of Add Favorite modal

- **Date:** 2026-09-20
- **Symptom:** Tapping the "+" Add button in the Favorites section on the Home Screen opened the Search overlay instead of opening a modal window to add a website.
- **Root Cause:** In `HomeScreen.kt:FavoritesSection`, `onAddClick` had been stubbed with `onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null, openInNewTab = true))`. No dedicated `AddFavoriteDialog` or modal state existed in the codebase.
- **Resolution & Prevention:**
  1. Created `AddFavoriteDialog` and `AddFavoriteDialogContent` conforming to the Liquid Pastel Sunset design system (`LiquidShapes.Capsule` 24.dp corner radius, specular rim border, name and URL text inputs, clipboard quick-paste, and 22.dp pill action buttons).
  2. Added `showAddFavoriteDialog: Boolean` to `BrowserUiState`, with `ShowAddFavoriteDialog` and `DismissAddFavoriteDialog` events in `BrowserUiEvent`.
  3. Included `showAddFavoriteDialog` in `JestkApp.kt`'s `isModalActive` so the underlying viewport automatically receives the 20.dp hardware-accelerated backdrop blur.
  4. Handled back press in `BrowserViewModel.handleBackNavigation()` to dismiss the dialog first.
  5. Extracted the dialog card layout to `AddFavoriteDialogContent` so it can be previewed in Compose Previews and verified via Robolectric Native Graphics without requiring an active Android `Window`.

### Returning generic empty text/plain for blocked requests causes infinite spinners and residual frames

- **Date:** 2026-09-21
- **Symptom:** Websites with blocked ad networks displayed empty placeholder blocks, broken image icons, or infinite loading spinners that hung site content and video players.
- **Root Cause:** 
  1. Returning HTTP 200 OK `text/plain` with 0 bytes for `<script>` or API requests means external SDKs never emit network errors, yet fail to declare expected globals (`window.adsbygoogle`, `window.googletag`). Host scripts waiting on callbacks or promises hang forever.
  2. Pure network request interception only prevents remote assets from loading; the host document's DOM still contains fixed-dimension layout containers (`style="min-height: 250px"`).
  3. Image tags receiving `text/plain` 0-byte responses trigger broken-image fallback indicators.
- **Resolution & Prevention:**
  1. Use content-aware responses in `AdBlockerEngine.createSmartBlockedResponse`: return 1x1 transparent PNG for images, valid JavaScript with stubs for scripts, HTTP 204 No Content for beacons/telemetry, and clean HTML for subframes.
  2. Decode binary image data using `Base64.decode` to avoid manual hex array alignment bugs.
  3. Inject cosmetic CSS rules (`display: none !important; height: 0 !important;`) and scriptlet surrogates at document start (`WebViewCompat.addDocumentStartJavaScript` with `onPageStarted` fallback) and use a `MutationObserver` DOM sanitizer to collapse orphaned parent containers.

### Ads leak through on Russian sites (food.ru) due to portal-path delivery and unblocked ADFOX

- **Date:** 2026-09-21
- **Symptom:** Opening `https://food.ru/recipes/181400-kak-svarit-ovsianku` still displayed ads, banner containers, and empty placeholder frames.
- **Root Cause:**
  1. Russian sites heavily rely on Yandex Ad Network / ADFOX (`yandex.ru/ads/system/context.js`, `yandex.ru/ads/system/header-bidding.js`, `ads.x5media.ru`, `ad.mail.ru`).
  2. Because `yandex.ru` is a major search portal, its root host cannot be blocked outright. Pure host-based checking in `shouldBlock` completely bypassed scripts served under `yandex.ru/ads/*`.
  3. Russian ad networks were absent from `DEFAULT_HOSTS` and standard English `EasyList`.
  4. Next.js / CSS module class names (`slugPage_bannerContainer__0D6xI`, `slugPage_incontentBanner__eObdd`, `RightsideBannersBlockWrapper`) and ADFOX DOM elements (`div[id^="adfox_"]`) were missing from cosmetic CSS selectors.
  5. The host page hung or rendered ads because ADFOX SDK (`window.Ya.adfoxCode`) was not stubbed.
- **Resolution & Prevention:**
  1. Add path-based matching (`isBlockedPath`) in `shouldBlock` to intercept ad endpoints on portal domains (e.g. `yandex.ru/ads/`, `mail.ru/static/ads`) without affecting primary services like search or mail.
  2. Populate `DEFAULT_HOSTS` and `adblock_hosts.txt` with essential regional ad networks (`adfox.ru`, `ad.mail.ru`, `ads.x5media.ru`, `my.target.com`, etc.).
  3. Include surrogate mocks for `window.Ya.adfoxCode`, `window.Ya.Context`, and `window.x5media`.
  4. Expand cosmetic selectors to cover `[id^="adfox"]`, `[class*="bannerContainer"]`, `[class*="incontentBanner"]`, `[class*="RightsideBanners"]`, `[class*="BannersBlock"]`, and `[class*="adFeedback"]`.

### Context menu full-width bottom sheet vs finger-anchored floating modal

- **Date:** 2026-09-22
- **Symptom:** Long-pressing a link or image at the top or middle of a webpage opened a full-width `ModalBottomSheet` sliding from the bottom of the device, requiring the user to reach far down and losing spatial context with the element being pressed.
- **Root Cause:** Context menus initially reused `ModalBottomSheet` for convenience without tracking finger touch coordinates from `WebView` gestures.
- **Resolution & Prevention:**
  1. Capture touch coordinates in `NestedWebView.onTouchEvent` (`lastTouchX`, `lastTouchY`, `lastTouchRawX`, `lastTouchRawY`) and calculate screen anchor coordinates (`location[0] + lastTouchX`, `location[1] + lastTouchY`) inside `setOnLongClickListener`.
  2. Implement `FloatingContextMenuContainer` that places the modal directly below the finger (`anchor.y + 14.dp`), or flips it above the finger if close to the bottom dock, clamped horizontally to safe margins.
  3. Animate card entrance via scale and alpha originating directly from the touch anchor point (`TransformOrigin`).
  4. Always prefer compact, contextual floating cards near the interaction point over full-screen bottom sheets for tap/long-press actions on web elements.

### Purging third-party library dependencies (Haze) across multi-file callers

- **Date:** 2026-09-23
- **Symptom:** Removing `dev.chrisbanes.haze:haze` dependency from `build.gradle.kts` and `LocalHazeState` from `Theme.kt` caused Kotlin compilation (`:app:compileDebugKotlin`) to fail across multiple consumer files with unresolved references to `dev.chrisbanes.haze` and `LocalHazeState`.
- **Root Cause:** In Kotlin multi-file Android projects, removing an external library dependency invalidates package imports in all files referencing types from that library, even if theme functions have backwards-compatible signatures with optional parameters.
- **Resolution & Prevention:** When removing a library dependency, simultaneously remove all unused `import <lib>.*` statements and obsolete parameters/CompositionLocal bindings across dependent files, while providing backward-compatible method overloads/delegates (e.g. `liquidFrostedGlass` delegating to solid `sereneCardSurface` without blur) so calling code compiles cleanly.

### Elevation shadow disappears due to double alpha pre-multiplication and translucent background
- **Date:** 2026-09-24
- **Symptom:** Floating dock and active tab pill appeared completely flat without visible elevation drop shadows, despite `elevation = 10.dp` on the dock and `shadowElevation = 2.dp` on the pill.
- **Root Cause:**
  1. In `Modifier.shadow(elevation, ambientColor, spotColor)`, passing low-alpha colors (e.g. `Color(0x140F172A)` which has 7.8% alpha) caused Android's native HWUI shadow pipeline to multiply system spot shadow alpha (~0.19) by 0.078, reducing spot shadow opacity to ~0.015 (virtually invisible).
  2. In Android's RenderNode outline system, the outline shadow opacity is directly scaled by the background alpha of the View/Surface. An active tab pill with 10% alpha background (`alpha = 0.10f`) scales its shadow down by 90%, causing the shadow to vanish.
  3. Having `Modifier.clip(shape)` on an outer modifier before `Surface` clips any shadow cast outside the layout bounds.
- **Resolution & Prevention:**
  1. Use opaque colors (e.g. `Color.Black` or high-alpha tints) for `spotColor` and `ambientColor` in `Modifier.shadow()`, allowing Android's HWUI engine to apply its calibrated system shadow alpha.
  2. For elevated floating pills inside parent surfaces, use an opaque solid tint (e.g. `Color(0xFFE9F1F7)` instead of `Color(0x1A2271B3)`) so the RenderNode outline retains `alpha = 1.0f` and casts a full, crisp elevation shadow.
  3. Keep `combinedClickable` and corner clipping inside `Surface` (on the inner content `Row`/`Box`) so the outer `Surface` shadow is not clipped.

### AnimatedVisibility DSL overload ambiguity and layering floating menus

- **Date:** 2026-09-30
- **Symptom:**
  1. Kotlin compilation failed with `'fun ColumnScope.AnimatedVisibility(...) cannot be called in this context with an implicit receiver'`.
  2. Placing a floating dropdown menu inside a nested bottom `Box` within a `Column` either pushes other elements up on open (layout shift) or clips touch events if artificially given 0 measured height.
- **Root Cause:**
  1. When `AnimatedVisibility` is called inside a `Box` that is itself nested inside an outer `Column`, the Kotlin compiler sees `ColumnScope.AnimatedVisibility` from the outer receiver scope rather than the top-level overload, triggering `@DslMarker` receiver ambiguity.
  2. In Jetpack Compose, hit-testing traverses from parent down to children. If a parent container has measured height 52.dp and a child is placed via negative offset above it, the parent's hit test rejects touches landing outside its bounds (`y < 0`), failing to dispatch clicks to the child menu items. Furthermore, if the child is measured normally, it increases the parent container's height and shifts sibling layout elements (favorites, history) upwards.
- **Resolution & Prevention:**
  1. Place floating popups/dropdowns as a separate Layer 3 directly inside the root full-screen `Box` (`Modifier.fillMaxSize()`), aligned to `BottomEnd` with matching insets (`statusBarsPadding()`, `navigationBarsPadding()`, `imePadding()`, and `padding(bottom = 60.dp)`).
  2. This guarantees:
     - No `@DslMarker` ambiguity (direct child of `Box`, not inside `ColumnScope`).
     - Zero layout shift: Layer 2 `Column` retains its stable height and siblings do not move when the dropdown animates open or closed.
     - Flawless hit-testing: The root `Box` encloses the entire screen, so touches on the floating card always pass hit-testing and execute click actions.

### Stale closure in Modifier.pointerInput causing swipe cycling to lock between two items

- **Date:** 2026-10-01
- **Symptom:** Horizontal swipe gestures on search engine button/input only switched between two search engines and could never loop through all three engines.
- **Root Cause:**
  1. `Modifier.pointerInput(density)` had `density` as its only key. Because `density` never changes during the screen lifecycle, `pointerInput` never restarted its coroutine upon recomposition.
  2. The callbacks `onSwipeLeft` and `onSwipeRight` were captured by value when the coroutine first launched, referencing the initial `uiState.settings.searchEngine` (e.g. `GOOGLE`).
  3. Every subsequent swipe called the stale lambda, which always evaluated `(GOOGLE ± 1)`, trapping the user between only two search engines: `DUCKDUCKGO` and `BING`.
  4. Furthermore, relying asynchronously on outer ViewModel updates across rapid successive swipes can cause race conditions where a fast second swipe reads the uncommitted prior engine.
- **Resolution & Prevention:**
  1. Make custom gesture modifiers `@Composable Modifier` extension functions and wrap all event callbacks (`onClick`, `onSwipeLeft`, `onSwipeRight`) in `rememberUpdatedState`. This allows the active gesture coroutine to remain uninterrupted while always executing the latest recomposition's lambdas.
  2. In `SearchOverlay`, track an immediate synchronous Compose state `var currentEngine by remember(uiState.settings.searchEngine) { mutableStateOf(...) }` that updates instantly on swipe, preventing gesture races.
  3. Use incremental threshold subtraction (`totalDragX += thresholdPx` / `totalDragX -= thresholdPx`) so both discrete swipe flicks and continuous dragging cycle smoothly step-by-step.

### MonotonicFrameClock required for Compose Animatable in JVM runTest

- **Date:** 2026-10-01
- **Symptom:** Unit tests asserting `Animatable.animateTo` (such as carousel fling decay and spring snapping) threw `IllegalStateException: A MonotonicFrameClock is not available in this CoroutineContext` when run inside `kotlinx.coroutines.test.runTest`.
- **Root Cause:** In Jetpack Compose, animation drivers (`Animatable`, `animateDecay`, `animateFloatAsState`) rely on a `MonotonicFrameClock` in the coroutine context to coordinate frame updates. A standard `runTest` context does not provide a Compose frame clock by default.
- **Resolution & Prevention:** Provide a lightweight test `MonotonicFrameClock` implementing `withFrameNanos`:
  ```kotlin
  private val testFrameClock = object : MonotonicFrameClock {
      private var time = 0L
      override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
          time += 16_000_000L
          return onFrame(time)
      }
  }
  ```
  Pass `testFrameClock` directly to `runTest(testFrameClock) { ... }`. When testing Compose animations in JVM tests without an active Compose Rule or Window, always supply `testFrameClock` to enable frame progression.

### Full-revolution modulo wrapping under high-velocity fling momentum

- **Date:** 2026-10-01
- **Symptom:** A strong fling test asserted that the final search engine differed from the starting engine (`selectedEngine != SearchEngine.GOOGLE`), but failed because `selectedEngine` remained `SearchEngine.GOOGLE` despite the fling traversing 6 full slots.
- **Root Cause:** With 3 search engines (`[DUCKDUCKGO, GOOGLE, BING]`), advancing by `targetSlot = 6` results in `(1 + 6).mod(3) == 1` (exactly 2 complete revolutions back to the starting engine). The fling physics correctly calculated velocity decay, clamped to 6 slots, executed all 6 slot transitions with 6 haptic ticks, and settled on slot 6.
- **Resolution & Prevention:** When writing unit tests for circular collections under high momentum, account for modulo periodicity `(index + slots) % size`. Assert both the number of haptic ticks/slots traversed (`hapticTicks >= 5`) and verify intermediate/moderate velocities (`-800 px/sec`) that land on distinct items (`(1 + 2) % 3 == 0`).








