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

