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
### Fast typing in Compose TextField triggers stale state echo and full-text selection

- **Date:** 2026-10-01
- **Symptom:** Fast typing into the search input intermittently selected all text, causing subsequent keystrokes to overwrite and erase previously entered text.
- **Root Cause:** `HeroSearchInputField` kept an internal `textFieldValue` state and attempted to synchronize with the parent `query: String` via `LaunchedEffect(query) { if (query != textFieldValue.text) textFieldValue = TextFieldValue(text = query, selection = TextRange(0, query.length)) }`. Because typing dispatches `UpdateSearchQuery` through ViewModel coroutines and StateFlow to parent state asynchronously, fast consecutive keystrokes cause older query emissions from the parent to arrive while the local field has already accepted newer characters (`query != textFieldValue.text` evaluates to true). The effect reset the field with the stale text and forced `TextRange(0, query.length)` selection, which was then immediately replaced by the user's next keystroke.
- **Resolution & Prevention:** Hoist `TextFieldValue` state to the parent overlay (`SearchOverlay`) as a single source of truth. Initialize `selection = TextRange(0, length)` only once on initial composition for prefilled URLs (standard browser address bar behavior). Remove `LaunchedEffect(query)` feedback loops; update `textFieldValue` directly on user input (`onValueChange`), history item insertion, voice recognition, and clear actions. Never reset selection to `TextRange(0, length)` inside an effect observing text state changes.

### Text clipped at bottom inside 52dp capsule using OutlinedTextField

- **Date:** 2026-10-01
- **Symptom:** Text inside the 52dp search input capsule had descenders ('g', 'j', 'p', 'q', 'y', '_') and bottom glyph bounds clipped.
- **Root Cause:** The search pill wrapped Material 3 `OutlinedTextField` inside a container constrained to `52.dp`. Material 3's `OutlinedTextField` enforces a default `minHeight = 56.dp` and incorporates internal outline label content padding (`top = 8.dp`), shifting the baseline downward and pushing descending characters outside the single-line scroll clip bounds.
- **Resolution & Prevention:** For custom pill or capsule surfaces where borders and backgrounds are already drawn by parent containers (e.g. `Surface(shape = CircleShape, border = border)`), use `BasicTextField` with `decorationBox` instead of `OutlinedTextField`. Combine `Modifier.weight(1f).fillMaxHeight()` and `Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart)` to guarantee true vertical centering and unobstructed vertical breathing room without M3 min-height constraints.

### Android URLUtil.guessFileName replaces MP3 and media extensions with .bin

- **Date:** 2026-10-02
- **Symptom:** Downloading MP3 audio files or media files resulted in filenames ending in `.bin` (e.g. `track.bin` instead of `track.mp3`).
- **Root Cause:**
  1. Most web download servers return `Content-Type: application/octet-stream` or `binary/octet-stream` on file downloads.
  2. Android's internal `URLUtil.guessFileName(url, contentDisposition, mimeType)` checks if the extension derived from the filename matches `mimeType`. When the filename is `track.mp3` (`audio/mpeg`) but `mimeType` is `application/octet-stream`, `URLUtil` replaces the file's extension with `MimeTypeMap.getExtensionFromMimeType("application/octet-stream")`, which evaluates to `"bin"`.
  3. Furthermore, `URLUtil.parseContentDisposition` does not support RFC 5987 / RFC 6266 `filename*=UTF-8''...` headers, and raw regex replacements stripped international/Cyrillic characters.
- **Resolution & Prevention:** Implement a dedicated [`DownloadFileNameResolver`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/engine/download/DownloadFileNameResolver.kt):
  1. Parse RFC 5987 / 6266 UTF-8 encoded `filename*=` as well as RFC 2616 quoted/token `filename=`.
  2. If the extracted filename already has a recognized media/file extension (e.g. `.mp3`, `.m4a`, `.flac`, `.pdf`, `.zip`, `.apk`), preserve it! Never allow generic `application/octet-stream` MIME types to overwrite known extensions with `.bin`.
  3. Sanitize only filesystem-forbidden characters (`[\\/:*?"<>|]`), preserving Unicode and Cyrillic script.
  4. Automatically resolve collisions with `filename (1).ext`, `filename (2).ext` instead of appending arbitrary timestamps.

### System DownloadManager delegates notifications and fails on memory unload

- **Date:** 2026-10-02
- **Symptom:** Downloads showed "Download Manager" / "Диспетчер загрузок" in status bar notifications rather than "Hori"; downloads aborted when Hori was swiped away from Recents or cleared from memory; download progress did not update in real-time in the UI without re-opening the screen.
- **Root Cause:**
  1. Android's `DownloadManager` runs under system UID `com.android.providers.downloads`, which displays "Download Manager" in notification headers on Android 8.0+.
  2. `DownloadManager` lacks referer headers from WebView tabs, causing hotlink-protected media downloads to fail with 403 Forbidden.
  3. Hori had no Foreground Service or sticky background process, and in-memory download tracking was lost on process death.
  4. `DownloadsScreen` used a one-shot `LaunchedEffect` that queried the system cursor once on composition without polling or listening to updates.
- **Resolution & Prevention:**
  1. Replace system `DownloadManager` with an in-app [`HoriDownloadService`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/engine/download/HoriDownloadService.kt) (`android:foregroundServiceType="dataSync"`) and [`HoriDownloadManager`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/engine/download/HoriDownloadManager.kt).
  2. Post notifications directly under Hori's notification channel with Hori's app title, icon, and custom actions (`Pause`, `Resume`, `Cancel`, `Retry`, `Open`).
  3. Stream downloads to `.crdownload` temporary files with HTTP `Range: bytes=X-` and `If-Range` resume support so interrupted downloads seamlessly continue.
  4. Stream real-time progress via Kotlin `StateFlow<Map<Long, DownloadProgressInfo>>` updating every 150ms to `DownloadsScreen`, displaying dynamic `"14.2 MB / 45.0 MB (31%)"` and real-time progress bars.
  5. Index completed files into Android `MediaScannerConnection` so they immediately appear in system media players and file managers.

### ForegroundServiceDidNotStartInTimeException, Missing Runtime POST_NOTIFICATIONS & Redirect Downloads

- **Date:** 2026-10-02
- **Symptom:**
  1. Tapping "Delete" on downloads caused `ForegroundServiceDidNotStartInTimeException` and crashed the app into a restart loop ("app won't open").
  2. Status bar notification for downloading files was never shown.
  3. GitHub downloads showed a "Downloading..." toast but failed to download.
- **Root Cause:**
  1. **Foreground Service Crash:** `startForegroundService` was invoked for `ACTION_CANCEL` when deleting an item. Because the item was not actively downloading, the service immediately called `checkFinishService() -> stopSelf()` without having called `startForeground()`. On Android 8.0+, calling `stopSelf()` before `startForeground()` causes the system to throw `ForegroundServiceDidNotStartInTimeException`. Furthermore, returning `START_STICKY` caused Android to restart the service on every app boot, creating an unrecoverable crash loop.
  2. **Missing Runtime Permission:** On Android 13+ (API 33+), `Manifest.permission.POST_NOTIFICATIONS` is a runtime permission. Declaring it only in `AndroidManifest.xml` without runtime user prompt caused Android to silently drop all posted notifications.
  3. **Redirect & User-Agent on GitHub:** GitHub download URLs (such as releases or archive zips) redirect from `github.com` to `objects.githubusercontent.com` / `codeload.github.com`. Cross-domain redirects require cookies from `CookieManager` to be retained across matching domains, standard browser `User-Agent` headers (servers reject `okhttp/*`), and refinements from server-provided `Content-Disposition`. Also, Room database schema mismatch occurred when `downloads` table columns changed without bumping the database version.
- **Resolution & Prevention:**
  1. In [`HoriDownloadService`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/engine/download/HoriDownloadService.kt), call `ensureForegroundStarted()` immediately at entry of `onStartCommand()`, regardless of action. Return `START_NOT_STICKY` to prevent restart loops.
  2. In [`HoriDownloadManager`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/engine/download/HoriDownloadManager.kt), only notify the foreground service for cancel/delete if the job was actually running. For secondary actions, use `startService()` rather than `startForegroundService()`.
  3. Add runtime `POST_NOTIFICATIONS` permission request via `ActivityResultContracts.RequestPermission()` in [`MainActivity`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/MainActivity.kt) on API 33+.
  4. Equip `OkHttpClient` with a `CookieJar` linked directly to Android's `CookieManager`, provide standard mobile Chrome fallback `User-Agent`, standard browser HTTP headers (`Accept`, `Accept-Language`, `Connection`), and dynamically refine destination filename from server `Content-Disposition`.
  5. Bump Room database version to 6 in [`AppDatabase.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/data/local/AppDatabase.kt) so `fallbackToDestructiveMigration()` cleans up old table schemas.

### WebChromeClient late attachment and static path caching collisions for favicons

- **Date:** 2026-10-01
- **Symptom:** Favicons in the floating dock and tab switchers failed to load on initial site opening (requiring a reload or switching tabs), or retained an old website's icon after navigating to a different website in the same tab.
- **Root Cause:**
  1. **Late Client Attachment Race:** `WebViewPool.getOrCreateWebView` created WebViews without `webChromeClient` attached. `WebChromeClient` was only attached later during `AndroidView.factory` composition in `WebViewContainer`. When `openNewTab` or `loadUrl` began loading the page immediately upon tab creation, the page load started with `webChromeClient == null`, dropping `WebChromeClient.onReceivedIcon` entirely. Reloading the page or switching tabs worked because `webChromeClient` was attached by that time.
  2. **Coil In-Memory Cache Collision on Static Paths:** Tab favicons were saved to a static file path `favicon_${tabId}.png`. Navigating to a new site in the same tab overwrote the file on disk, but the file path URI remained identical (`/data/user/0/.../favicons/favicon_${tabId}.png`). Coil's in-memory bitmap cache keyed on the file path and continued serving the cached bitmap of the old site.
  3. **No Reset on Inter-Domain Navigation:** Neither `loadUrl` nor `handlePageStarted` reset `tab.faviconUrl` when navigating to a new domain, and `TabDao.updateTabDetails` omitted `faviconUrl`. Thus, the old favicon URL remained in UI state and SQLite until an icon arrived (or indefinitely if the new site had no favicon).
  4. **Single-Callback Vulnerability:** Favicon reception relied exclusively on `WebChromeClient.onReceivedIcon`, missing fallback opportunities from `WebViewClient.onPageStarted` (which receives `favicon: Bitmap?`) and `WebViewClient.onPageFinished` (`view.favicon`).
- **Resolution & Prevention:**
  1. **Lifecycle Hook in WebViewPool:** Added `onWebViewCreated` callback to `WebViewPool` and attached `JestkWebChromeClient` and `JestkWebViewClient` synchronously inside `JestkApp` at the exact moment a `WebView` instance is instantiated, before any `loadUrl` call can occur.
  2. **Timestamped Cache-Busting File Paths:** Switched `FaviconManager.saveFavicon` to save files with unique timestamped names (`favicon_${tabId}_${timestamp}.png`), while deleting older favicon files for that tab. Any update changes the file path string, immediately busting Coil's memory and disk caches.
  3. **Domain-Aware Favicon Reset:** In `BrowserViewModel.loadUrl` and `handlePageStarted`, check if navigating to a different domain via `isSameDomain(tab.url, newUrl)`. If different, reset `faviconUrl` to `null` in both `BrowserUiState` and Room DB (`TabDao.updateFavicon(id, null)`). If within the same domain, preserve the current favicon to avoid unnecessary flicker.
  4. **Dual Favicon Ingestion:** Added `JestkWebViewClient.onFaviconReceived` extracting icons from `onPageStarted` and `onPageFinished` in addition to `JestkWebChromeClient.onReceivedIcon`.
  5. **Dock Substrate Pattern:** In `ActiveTabPill`, `TabStripRow`, and `TabOverviewGrid`, render the domain letter glyph substrate while loading or on error, but hide the letter once `isFaviconSuccess` is true to prevent transparent favicon PNGs from overlaying the letter.

### java.net.URI throws URISyntaxException on search URL templates with `%s`

- **Date:** 2026-10-02
- **Symptom:** `SearchEngine.matchesUrl` failed for custom search engine templates containing query placeholders like `https://www.youtube.com/results?search_query=%s`.
- **Root Cause:** RFC 2396 URI syntax requires the `%` character in URI query strings to be immediately followed by two hexadecimal digits representing an escaped octet. `%s` is not a valid hex escape sequence, causing `java.net.URI(template)` constructor to throw `URISyntaxException: Malformed escape pair`.
- **Resolution & Prevention:** Before feeding template strings to `java.net.URI`, replace `%s` with an alphanumeric placeholder (e.g. `template.replace("%s", "placeholder")`), with a regex/substring fallback (`substringAfter("://").substringBefore("/").substringBefore("?")`).

### Converting Kotlin enum to data class requires `@JvmField` and `@JvmStatic` for Java interop

- **Date:** 2026-10-02
- **Symptom:** Java test classes (e.g. `ArchitectureAndDatabaseCoverageTest`, `SettingsSheetBoundaryTest`) failed compilation with `error: DUCKDUCKGO has private access in SearchEngine` and `error: cannot find symbol: method values()`.
- **Root Cause:** In Kotlin, `companion object` properties and functions without explicit Jvm annotations compile as private static fields accessed via companion object getters and methods on the `Companion` class. Java callers expecting enum-like public static constants (`SearchEngine.GOOGLE`) and static methods (`SearchEngine.values()`) cannot access them.
- **Resolution & Prevention:** Annotate companion constants with `@JvmField` and companion functions with `@JvmStatic` (and `@JvmOverloads` where default arguments exist). In Java pattern matching switch statements (`switch (engine)`), replace with explicit `if-else` or `.equals()` checks since Java 17 only permits switching on primitives, Strings, and true enums.

### Converting Android asset file:// URLs to java.io.File breaks Coil AssetUriFetcher

- **Date:** 2026-10-02
- **Symptom:** Pre-installed search engines (Google, DuckDuckGo, Bing) did not display their favicons/SVGs in `SearchEngineBadge`, the search engine switcher chips, or the dropdown list, displaying only the fallback initial letter glyphs ("D", "G", "B"). Custom providers (such as YouTube with HTTP favicons) rendered properly.
- **Root Cause:**
  1. Built-in search engines have `iconUrl = "file:///android_asset/icons/duckduckgo.svg"`.
  2. `FaviconManager.toImageModel(urlOrPath)` checked `if (urlOrPath.startsWith("/") || urlOrPath.startsWith("file://"))` and stripped `"file://"` to instantiate `java.io.File("/android_asset/...")`.
  3. APK assets are packaged inside the APK archive and do not exist as standalone files on the Linux root filesystem (`/android_asset/...`). Coil's `FileFetcher` called `File.exists()`, which returned `false` with `FileNotFoundException`.
  4. Coil includes a specialized `AssetUriFetcher` that checks `isAssetUri(uri: Uri)`: `uri.scheme == "file" && uri.pathSegments.firstOrNull() == "android_asset"`. Because `toImageModel` passed a `File` object instead of an `android.net.Uri` (or URL string), Coil routed through `FileFetcher` rather than `AssetUriFetcher`, triggering `onError = { isImageError = true }` and falling back to initial letter avatars.
  5. Furthermore, in Compose previews and unit tests, `crossfade(true)` in `SearchEngineBadge` resulted in zero alpha on initial frame capture.
- **Resolution & Prevention:**
  1. In `FaviconManager.toImageModel`, explicitly intercept `file:///android_asset/` and `/android_asset/` and return `Uri.parse(urlOrPath)` so Coil routes to `AssetUriFetcher` and opens the stream via `context.assets.open(...)`.
  2. In `SearchEngineBadge`, set `.crossfade(false)` on `ImageRequest.Builder` for instant icon display without transparent initial frames or animation delay.
### PointerEventPass.Initial for row-wide drag gestures with child clickable buttons

- **Date:** 2026-10-02
- **Symptom:** In reorderable lists (such as `SearchEngineDropdownCard`), having clickable buttons (e.g. edit pencil, delete trash) inside a draggable row causes touch event conflicts: either the child buttons consume the touch and prevent dragging over them, or putting standard drag gestures on the container blocks button click events.
- **Root Cause:** In Jetpack Compose, gestures on `PointerEventPass.Main` are processed by child clickables before parent containers. If a child consumes down/motion events, parent pointer input receives events with `isConsumed == true`. Conversely, consuming all pointer events on the parent prevents child `clickable` / `IconButton` instances from detecting taps.
- **Resolution & Prevention:** Intercept pointer motion using `awaitPointerEvent(pass = PointerEventPass.Initial)`.
  1. Observe pointer delta on `PointerEventPass.Initial` without consuming if motion is below `touchSlop`. Child buttons receive unconsumed events on `PointerEventPass.Main` and process taps/clicks normally.
  2. Once vertical displacement exceeds `touchSlop`, consume the pointer change (`change.consume()`) on `PointerEventPass.Initial`. Child clickables see that the event has been consumed and immediately cancel their pending click gestures without triggering action callbacks.
  3. All subsequent movements drive the continuous drag smoothly.

### Stale captured index in drag gesture closures corrupts list reorder sequence

- **Date:** 2026-10-02
- **Symptom:** During rapid or frequent reordering of search engines in the dropdown card, items became mixed up or transposed. For instance, tapping item 1 selected item 3, and dragging an item moved an entirely different item in the list.
- **Root Cause:** In Jetpack Compose, `Modifier.pointerInput(key)` only restarts its gesture coroutine when `key` changes. When `pointerInput(engine.id)` captured `index: Int` from `forEachIndexed`, the item's `id` stayed constant across reorder operations. When item 0 moved to index 2, its `pointerInput` lambda retained the stale captured `index = 0`. On subsequent drags, `onDragStart(index)` and `onDragEnd` called `list.removeAt(0)` instead of `list.removeAt(2)`, removing and shifting the wrong item.
- **Resolution & Prevention:** Never capture transient positional indices (`index: Int`) inside long-lived gesture lambdas. Key drag operations on immutable domain identifiers (`engine.id`) and dynamically resolve current list indices (`currentList.indexOfFirst { it.id == engineId }`) at the moment of drag start, during drag displacement, and at drop commitment.

### Sibling item jump caused by spring animation after layout slot shift

- **Date:** 2026-10-02
- **Symptom:** Upon releasing a dragged item, once the drop settled, the neighboring displaced item visibly jumped or flickered before settling into place.
- **Root Cause:** During drag, the neighboring sibling was displaced by an animated offset `targetSiblingOffset = -48f` (`animateFloatAsState`). When the drop finished, `currentList` updated and Compose recomposed: the sibling moved to its new layout slot (which is already 48px higher). Simultaneously, dragging ended so `targetSiblingOffset` reset to `0f`. Because `animateFloatAsState` does not instantly snap to `0f`, it ran a spring animation from `-48f` to `0f`. Adding `-48f` to the new layout position displaced the sibling by a double offset (`-96px`), creating a visible jump.
- **Resolution & Prevention:** Guard visual displacement with active reorder state (`isReorderActive = isDragging || isDropping`). If not active, immediately clamp visual translation to `0f` in the layout phase, and use an explicit `Animatable.snapTo(0f)` when the reorder cycle terminates, ensuring zero frame offset jumps upon layout index updates.

### Early return in `awaitEachGesture` before suspending causes 100% CPU lockup (ANR)

- **Date:** 2026-10-02
- **Symptom:** Releasing an item after dragging caused the app to instantly freeze and crash with an ANR (`98% CPU: com.jestk.browser.debug: 94% user + 4.3% kernel`).
- **Root Cause:** In Jetpack Compose, `awaitEachGesture` runs an internal `while (currentCoroutineContext().isActive)` loop. If the lambda returns without suspending (e.g. `if (reorderState.isDragging || reorderState.isDropping) return@awaitEachGesture` placed before `awaitFirstDown`), the loop runs un-suspended at full CPU speed. When `isDropping` was set to `true` upon releasing the pointer, the gesture ended and `awaitEachGesture` immediately restarted, hit the early return on every iteration, and spun millions of times per second on the Android main looper thread, blocking the UI and choreographer until killed by the OS.
- **Resolution & Prevention:** NEVER place non-suspending conditional exits at the top of `awaitEachGesture`. The very first statement in `awaitEachGesture` must always be a suspending pointer call (such as `awaitFirstDown()`). If the gesture should be ignored while another action is in progress, suspend on pointer events (`awaitPointerEvent`) until all pointers are released before returning.




