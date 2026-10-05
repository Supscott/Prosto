# Lessons Learned & Bug Post-Mortems

This document tracks technical pitfalls, bugs, and verified solutions in Hori to prevent regressions.

---

## 1. Build & Tooling

### Gradle configuration fails with `java.lang.IllegalArgumentException: 25.0.3`
- **Symptom:** Gradle sync/build fails during script compilation before any task runs: `FAILURE: Build failed with an exception. * What went wrong: 25.0.3`.
- **Root Cause:** Android Studio bundles JBR 25, but Gradle 8.14.x cannot parse Java 25 version strings during Kotlin DSL script compilation.
- **Resolution & Prevention:** Pin the Gradle daemon JVM to JDK 23 via `org.gradle.java.home` in `~/.gradle/gradle.properties` (or `gradle/gradle-daemon-jvm.properties` with `toolchainVersion=23`). When a build fails before task execution with a bare version string, suspect the Gradle daemon JVM.

---

## 2. Web Engine & Rendering

### Live WebView inside Compose `GraphicsLayer` causes frame flicker on Android 12+ (SDK 31+)
- **Symptom:** Navigating to websites causes the entire viewport to flicker with blank/stale frames. Stops only after switching tabs and back. Only occurs on Android 12+.
- **Root Cause:** Applying modifiers that record into a `GraphicsLayer` (such as offscreen blur shaders or render nodes) forces Chromium's asynchronous hardware draw functor to re-record through an offscreen `RenderNode` every frame, producing stale frames. Tab switching temporarily disposes the node.
- **Resolution & Prevention:** Never place a live `WebView` inside a `GraphicsLayer`, offscreen render node, or dynamic blur modifier. Use solid surface fallbacks for overlays positioned over web content.

### WebChromeClient late attachment race and Coil static path cache collision for favicons
- **Symptom:** Favicons fail to appear on fresh tab creation (requiring reload/tab switch), or retain the previous website's icon after navigating to a new URL.
- **Root Cause:**
  1. WebViews created without `WebChromeClient` attached dropped `WebChromeClient.onReceivedIcon` during initial `loadUrl`.
  2. Saving tab icons to a static path (`favicon_${tabId}.png`) caused Coil's in-memory bitmap cache to serve stale images despite disk updates.
- **Resolution & Prevention:**
  1. Synchronously attach `JestkWebChromeClient` and `JestkWebViewClient` in `WebViewPool.onWebViewCreated` at the moment of `WebView` instantiation.
  2. Invalidate Coil caches by writing timestamped file paths (`favicon_${tabId}_${timestamp}.png`) and deleting older files for that tab.
  3. Reset `faviconUrl` to `null` on cross-domain navigation (`isSameDomain(oldUrl, newUrl) == false`), but retain it during intra-domain navigation.

### Converting Android asset `file://` URLs to `java.io.File` breaks Coil `AssetUriFetcher`
- **Symptom:** Built-in search engines using `file:///android_asset/icons/...` fail to display SVG favicons, falling back to initial letter glyphs.
- **Root Cause:** Stripping `"file://"` into `java.io.File("/android_asset/...")` routes to Coil's `FileFetcher`. APK assets are packaged inside the APK archive, not on the Linux root filesystem, throwing `FileNotFoundException`.
- **Resolution & Prevention:** In `FaviconManager.toImageModel`, intercept `file:///android_asset/` and `/android_asset/` and return `android.net.Uri.parse(...)` so Coil routes through `AssetUriFetcher` to `context.assets.open(...)`. Also set `.crossfade(false)` on small badge icons to avoid transparent initial frames.

---

## 3. Jetpack Compose UI & Layout

### Android HWUI elevation shadow vanishes due to low-alpha colors or translucent backgrounds
- **Symptom:** Floating dock or cards appear completely flat without drop shadows despite setting `elevation = 10.dp`.
- **Root Cause:**
  1. Android's native HWUI shadow pipeline multiplies system spot shadow alpha by the color alpha passed in `Modifier.shadow(elevation, ambientColor, spotColor)`. Passing low-alpha tints (e.g. 7% alpha) reduces shadow opacity to near zero.
  2. The `RenderNode` outline system scales shadow opacity by the surface background alpha. Translucent backgrounds scale shadows down proportionally.
- **Resolution & Prevention:** Use opaque colors (`Color.Black` or high-alpha tints) in `Modifier.shadow()`. Use opaque background surfaces for elevated cards, and keep clipping modifiers inside the `Surface` so drop shadows cast outside bounds are not clipped.

### Text descenders clipped inside 52dp capsule using `OutlinedTextField`
- **Symptom:** Text inside the search input pill has descenders ('g', 'j', 'p', 'q', 'y', '_') clipped.
- **Root Cause:** Material 3 `OutlinedTextField` enforces a minimum height of `56.dp` and incorporates internal outline label padding (`8.dp` top), shifting the text baseline downward and clipping against parent constraints.
- **Resolution & Prevention:** For custom-height capsules and pills where borders are drawn by the parent container, use `BasicTextField` with a custom `decorationBox` centered vertically via `Alignment.CenterStart`.

### `AnimatedVisibility` DSL overload ambiguity and layering floating popups
- **Symptom:** Compilation error: `'fun ColumnScope.AnimatedVisibility(...) cannot be called in this context with an implicit receiver'`, or floating menus push siblings upward.
- **Root Cause:** Calling `AnimatedVisibility` inside a `Box` nested in a `Column` triggers `@DslMarker` receiver ambiguity. Also, measuring a dropdown within the content column causes layout shifts and touch clipping if negative offsets are used.
- **Resolution & Prevention:** Render floating menus in a separate full-screen layer directly inside the root `Box` (`Modifier.fillMaxSize()`), aligned to `BottomEnd` with matching window insets.

---

## 4. Jetpack Compose Gestures & Animation

### `awaitEachGesture` early return before suspending causes 100% CPU lockup (ANR)
- **Symptom:** Releasing a dragged item freezes the app with 100% CPU utilization, triggering an ANR.
- **Root Cause:** `awaitEachGesture` wraps an internal `while (currentCoroutineContext().isActive)` loop. If the lambda exits conditionally (e.g. `if (isDropping) return@awaitEachGesture`) *before* calling a suspending pointer method, the loop spins millions of times per second on the main looper thread.
- **Resolution & Prevention:** The very first statement in `awaitEachGesture` must ALWAYS be a suspending call (e.g. `awaitFirstDown()`). If the gesture should be ignored while another animation runs, suspend on `awaitPointerEvent` until pointers are released before returning.

### `PointerEventPass.Initial` for draggable rows containing clickable child buttons
- **Symptom:** Draggable list items with child buttons (edit, delete) either block child clicks or have drag gestures intercepted by child clickables.
- **Root Cause:** By default, child clickables consume pointer events on `PointerEventPass.Main` before parent gesture handlers receive them.
- **Resolution & Prevention:** Intercept motion on `PointerEventPass.Initial`:
  1. Observe pointer delta on `PointerEventPass.Initial` without consuming while movement is below `touchSlop`. Child buttons receive events on `PointerEventPass.Main` normally.
  2. Once displacement exceeds `touchSlop`, consume the pointer change (`change.consume()`) on `PointerEventPass.Initial`. Child clickables see the consumed event and cancel pending clicks.

### Stale closures in `Modifier.pointerInput` gesture callbacks
- **Symptom:** Horizontal swipe gestures lock between two items, or rapid drags reference stale state.
- **Root Cause:** `Modifier.pointerInput(key)` only restarts its gesture coroutine when `key` changes. If callbacks (`onClick`, `onSwipe`) or list states are captured by value, the coroutine executes stale closures from initial composition.
- **Resolution & Prevention:** Wrap all callback parameters in `rememberUpdatedState` inside custom gesture modifiers. For draggable lists, key drag operations on immutable identifiers (`engine.id`), manage the list in an internal `currentList by mutableStateOf(...)`, and dynamically resolve current indices.

### Sibling item jump caused by spring animation after layout slot shift
- **Symptom:** Releasing a dragged item causes neighboring displaced items to visibly jump or flicker upon settling.
- **Root Cause:** Neighboring items are displaced by an animated offset (e.g. `-48f`). When the drop completes and the database list updates, Compose recomposes the item into its new layout slot (already 48px higher). If the animation runs a spring from `-48f` to `0f`, adding `-48f` to the new layout position doubles the displacement (`-96px`).
- **Resolution & Prevention:** Guard displacement with active reorder state (`isDragging || isDropping`). Clamp visual translation to `0f` in the layout phase when inactive, and call `Animatable.snapTo(0f)` immediately upon drop settlement.

### Underdamped spring oscillation with default `0.01f` visibility threshold causes release hang
- **Symptom:** Releasing a dragged card intermittently freezes in mid-air for ~500ms before landing.
- **Root Cause:** Default `visibilityThreshold = 0.01f` causes underdamped springs to spend >500ms decaying imperceptible subpixel oscillations (between +0.2px and -0.1px).
- **Resolution & Prevention:** Set explicit `visibilityThreshold = 0.5f` on spatial pixel `Animatable` instances (`dropAnim`, `siblingOffsetAnim`). Once displacement is under 0.5px, the animation completes immediately. Use snappy specs (`Spring.StiffnessMedium` = 1500f, `Spring.DampingRatioLowBouncy` = 0.75f).

---

## 5. State Management & Navigation

### Dual state desync for screen visibility
- **Symptom:** Tapping Settings or Downloads does nothing — no screen or sheet appears.
- **Root Cause:** Events updated `activeScreen = SETTINGS`, while the UI checked `activeOverlay == SETTINGS`. The render condition and the event-updated state drifted apart.
- **Resolution & Prevention:** Use single derived predicates in `BrowserUiState` (e.g. `isSettingsVisible`, `isDownloadsVisible`) that encapsulate all valid visibility states, and assert them in unit tests.

### Fast typing in `TextField` triggers stale state echo and full-text selection
- **Symptom:** Fast typing in the search input intermittently selects all text, causing keystrokes to overwrite prior characters.
- **Root Cause:** A `LaunchedEffect(query)` in the field updated local `TextFieldValue(text = query, selection = TextRange(0, query.length))`. Asynchronous emissions through ViewModel StateFlow caused older query states to arrive while newer characters were already typed locally, resetting selection to full range.
- **Resolution & Prevention:** Hoist `TextFieldValue` to the parent composable as a single source of truth. Initialize full-range selection only once on initial composition for prefilled URLs. Never reset selection inside a state-observing effect.

### Kotlin default parameter evaluation trap with mutable state during list reorder
- **Symptom:** Repeated drag-and-drop operations scramble list order upon release.
- **Root Cause:** Functions used default arguments like `list: List<SearchEngine> = currentList`. Kotlin evaluates default argument expressions at the call site *before* entering the function body. When `finishPendingDropImmediately()` mutated `currentList` inside `onDragStart`, the parameter was already bound to the pre-drop list.
- **Resolution & Prevention:** Never use mutable state properties as default parameters (`arg = mutableState`) when the function itself mutates that state. Use `arg: Type? = null` and resolve it inside the body (`val effective = arg ?: mutableState`) *after* prerequisite mutations run.

---

## 6. In-App Downloads & Background Processing

### `ForegroundServiceDidNotStartInTimeException` on Android 8.0+
- **Symptom:** Tapping "Cancel" or "Delete" on downloads crashes the app with `ForegroundServiceDidNotStartInTimeException` into an unrecoverable restart loop.
- **Root Cause:** Calling `context.startForegroundService()` requires the service to call `startForeground()` within 5 seconds. If the item was not downloading, the service immediately called `stopSelf()` without `startForeground()`, triggering the OS exception. Returning `START_STICKY` caused Android to restart the service on boot.
- **Resolution & Prevention:** Call `ensureForegroundStarted()` immediately at entry of `onStartCommand()`, regardless of intent action. Return `START_NOT_STICKY` to prevent restart loops. Use standard `startService()` for control signals (cancel/delete), reserving `startForegroundService()` strictly for initiating active downloads.

### Android `URLUtil.guessFileName` replaces media extensions with `.bin`
- **Symptom:** Downloading media files (e.g. `.mp3`) results in files named `track.bin`.
- **Root Cause:** Web servers often return `Content-Type: application/octet-stream`. `URLUtil.guessFileName` overwrites the file extension with the MIME type extension when they do not match. Additionally, `URLUtil` does not decode RFC 5987 / 6266 `filename*=UTF-8''` headers.
- **Resolution & Prevention:** In `DownloadFileNameResolver`, decode RFC 5987 / 6266 UTF-8 headers. If the extracted filename already has a recognized extension (`.mp3`, `.pdf`, `.apk`, `.zip`), preserve it against generic `application/octet-stream` MIME types.

### Cross-domain redirects fail for authenticated downloads (e.g. GitHub releases)
- **Symptom:** Downloading release files from GitHub shows "Downloading..." but fails with 403 Forbidden.
- **Root Cause:** GitHub download URLs redirect across domains (`github.com` -> `objects.githubusercontent.com`). `OkHttpClient` lacked cookies from Android's `CookieManager`, mobile browser `User-Agent`, and referer headers.
- **Resolution & Prevention:** Configure `OkHttpClient` with a `CookieJar` backed by Android `CookieManager.getInstance()`, supply a standard mobile Chrome `User-Agent`, and forward the tab's `referer` header.

---

## 7. Ad-Blocking Engine

### Returning 0-byte `text/plain` for blocked requests causes infinite spinners
- **Symptom:** Blocked ad slots display broken image icons, layout reflow gaps, or infinite loading spinners that freeze host video players.
- **Root Cause:** Returning 0-byte `text/plain` for `<script>` requests prevents expected globals (`window.adsbygoogle`, `window.googletag`) from being defined without throwing network errors, causing host scripts waiting on promises to hang.
- **Resolution & Prevention:** Use content-aware responses in `AdBlockerEngine.createSmartBlockedResponse`: 1×1 transparent PNG for images, benign script stubs for scripts, HTTP 204 No Content for beacons/telemetry, and clean HTML for subframes. Inject cosmetic CSS (`display: none !important;`) and scriptlet surrogates at document start.

### Portal domain ad delivery requires path-based interception
- **Symptom:** Sites like `food.ru` leak ads served via portal domains (`yandex.ru/ads/system/context.js`).
- **Root Cause:** Search portal domains (e.g. `yandex.ru`, `mail.ru`) cannot be blocked at host level without breaking search and mail services.
- **Resolution & Prevention:** Implement path-based URL filtering (`isBlockedPath`) in `AdBlockerEngine.shouldBlock` to selectively neutralize ad scripts on shared domains without affecting main services.

---

## 8. Persistence & Database (Room)

### Room `@Transaction suspend fun` interface default methods cause `assertNotSuspendingTransaction`
- **Symptom:** Unit tests asserting ViewModel operations fail with `IllegalStateException: Cannot access database on a different coroutine context inherited from a suspending transaction` at `RoomDatabase.assertNotSuspendingTransaction`.
- **Root Cause:** Room's `@Transaction` on a Kotlin interface `suspend fun` default body wraps execution in `RoomDatabaseKt.withTransaction`. When child queries invalidate tables, active Room `Flow` observers re-query on the same thread/coroutine context, triggering Room's concurrent transaction assertion.
- **Resolution & Prevention:** Do not annotate Kotlin interface default helper methods with `@Transaction` when looping over atomic queries (e.g. `reorderTabs`). Declare them as plain `suspend fun`. If an atomic transaction is required, invoke `database.withTransaction { ... }` explicitly at the repository layer.

---

## 9. Android OS Integration

### `RoleManager.createRequestRoleIntent` fails silently when launched with `startActivity()`
- **Symptom:** Tapping "Set as Default Browser" does nothing — no dialog appears.
- **Root Cause:** On Android Q+ (API 29+), `RequestRoleActivity` inspects `getCallingPackage()`. When launched via `context.startActivity(intent)`, `getCallingPackage()` is `null`, causing the activity to finish immediately without showing UI.
- **Resolution & Prevention:** Always launch role request intents using `rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult())` (or `Activity.startActivityForResult()`). Provide multi-tier settings fallbacks (`ROLE_BROWSER` -> `ACTION_MANAGE_DEFAULT_APPS_SETTINGS` -> `ACTION_APPLICATION_DETAILS_SETTINGS`).

### `java.net.URI` throws `URISyntaxException` on URL templates with `%s`
- **Symptom:** Custom search engine URL template parsing throws `URISyntaxException: Malformed escape pair`.
- **Root Cause:** RFC 2396 requires `%` in URI query strings to be followed by two hexadecimal digits. `%s` is not a valid hex escape sequence.
- **Resolution & Prevention:** Replace `%s` with an alphanumeric token (`template.replace("%s", "placeholder")`) before instantiating `java.net.URI`.

---

## 10. Unit & Robolectric Testing

### Compose `Animatable` requires `MonotonicFrameClock` in JVM `runTest`
- **Symptom:** Tests calling `Animatable.animateTo` fail with `IllegalStateException: A MonotonicFrameClock is not available in this CoroutineContext`.
- **Root Cause:** Compose animation drivers require a `MonotonicFrameClock` in the coroutine context to drive frame progression, which standard `runTest` does not provide.
- **Resolution & Prevention:** Supply a test `MonotonicFrameClock` implementing `withFrameNanos` advancing time by 16ms:
  ```kotlin
  private val testFrameClock = object : MonotonicFrameClock {
      private var time = 0L
      override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
          time += 16_000_000L
          return onFrame(time)
      }
  }
  ```
  Pass `testFrameClock` to `runTest(testFrameClock) { ... }`.

### Robolectric `ShadowApplication` service intent queue is FIFO and retains earlier intents
- **Symptom:** Asserting secondary actions (e.g. `cancelDownload`) fails with `expected: CANCEL_DOWNLOAD but was: START_DOWNLOAD`.
- **Root Cause:** In Robolectric, `shadowOf(application).nextStartedService` polls from a FIFO queue. Preceding setup steps (e.g. `enqueueDownload`) queued `START_DOWNLOAD`, which remains at the head of the queue.
- **Resolution & Prevention:** In multi-step workflow tests, consume or clear prior service intents (`clearStartedServices()`) after setup steps before asserting subsequent actions.
