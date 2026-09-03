# Jestk Browser for Android — Subsystem Specification Report
## R3. Multi-WebView Pool & Lifecycle | R4. Reliable Page Snapshot Engine | R6. Native Ad Blocker, Media/PiP & Back Navigation

**Author:** Spec Miner 3 (Android Systems & WebView Architecture)  
**Date:** 2026-09-02T19:15:00Z  
**Target Platform:** Android (Min SDK 26 / Target SDK 35), Kotlin 1.9+, Jetpack Compose, Material 3, Android System WebView, Room DB.

---

## 1. Executive Summary & Subsystem Map

This specification covers three core runtime subsystems of **Jestk Browser for Android**:
1. **R3: Multi-WebView Pool & Lifecycle Engine** — High-performance in-memory caching of live `android.webkit.WebView` instances per tab ID, safe detachment/reattachment across Compose `AndroidView` nodes, and non-blocking background tab execution preserving HTML5 audio/video playback and JS timers.
2. **R4: Reliable Page Snapshot Engine** — Hardware-synchronized visual frame capture using `WebView.postVisualStateCallback` (and `PixelCopy` fallback) to eliminate blank/white cards in tab overviews, paired with disk caching and Room DB metadata binding for instant cold-boot loading.
3. **R6: Web Platform Features & Systems**:
   - **R6.1: Native Ad & Tracker Blocker** — In-memory compiled blocklist (`HashSet<String>`) loaded from assets, executing $O(k)$ subdomain matching in `WebViewClient.shouldInterceptRequest` with zero-byte dummy streams.
   - **R6.2: Video & Media Systems** — HTML5 fullscreen custom view lifecycle (`WebChromeClient.onShowCustomView`), draggable in-app video mini-player, and Android system PiP (`enterPictureInPictureMode`).
   - **R6.3: Back Navigation Hierarchy** — 5-tier back dispatcher resolving fullscreen custom views, modal overlays, WebView history, multi-tab closure, and application finish.

```
+---------------------------------------------------------------------------------------------------+
|                                         Jestk Browser Core                                        |
+------------------------------------+----------------------------------+---------------------------+
| R3: Multi-WebView Pool             | R4: Snapshot Engine              | R6: Platform & Media      |
+------------------------------------+----------------------------------+---------------------------+
| - ConcurrentHashMap<Long, WebView> | - postVisualStateCallback sync   | - Fast HashSet AdBlock    |
| - Safe ViewGroup.removeView()      | - Off-screen canvas capture      | - Subdomain matcher       |
| - Uninhibited background timers    | - JPEG/WEBP disk cache           | - WebChromeClient Fullscr |
| - State retention (DOM/Scroll/Nav) | - Room DB snapshotPath sync      | - System PiP & MiniPlayer |
| - Safe context wrapping            | - Instant cold-boot hydration    | - 5-Tier BackHandler      |
+------------------------------------+----------------------------------+---------------------------+
```

---

## 2. R3: Multi-WebView Pool & Lifecycle Management

### 2.1 Problem Statement & Architectural Objectives
Jetpack Compose manages UI declaratively by re-invoking composable functions. Standard `AndroidView` creation instantiates fresh Android Views or binds existing ones. In a multi-tab web browser:
1. Creating a new `WebView` on every tab switch drops DOM state, form inputs, scroll position, and back/forward navigation history, causing unacceptable latency and network re-fetching.
2. Re-attaching an existing `WebView` that already has a parent `ViewGroup` without explicit detachment throws `java.lang.IllegalStateException: The specified child already has a parent. You must call removeView() on the child's parent first.`
3. Standard Android lifecycle patterns call `webView.onPause()` or `webView.pauseTimers()` on background tabs. Calling `pauseTimers()` is process-global and pauses all JavaScript timers across every tab, killing background audio/video streams (e.g. YouTube music, podcasts, radio) and WebSockets.

### 2.2 WebViewPool Architecture & Data Structures
The `WebViewPool` is a singleton/scoped manager that maintains live `WebView` instances keyed by `tabId: Long`.

```kotlin
interface IWebViewPool {
    fun getOrCreate(tabId: Long, context: Context): WebView
    fun get(tabId: Long): WebView?
    fun detachFromParent(webView: WebView): WebView
    fun removeAndDestroy(tabId: Long)
    fun clearAll()
    fun getAllWebViews(): Collection<WebView>
}
```

#### Pool Storage & Session Wrapper
```kotlin
data class TabWebViewSession(
    val tabId: Long,
    val webView: WebView,
    val customWebChromeClient: JestkWebChromeClient,
    val customWebViewClient: JestkWebViewClient,
    var lastActiveTimestamp: Long = System.currentTimeMillis()
)

class WebViewPool(
    private val adBlockerEngine: AdBlockerEngine,
    private val snapshotEngine: SnapshotEngine
) : IWebViewPool {
    private val pool = ConcurrentHashMap<Long, TabWebViewSession>()

    override fun getOrCreate(tabId: Long, context: Context): WebView {
        val session = pool.computeIfAbsent(tabId) { id ->
            val webView = createConfiguredWebView(id, context)
            TabWebViewSession(
                tabId = id,
                webView = webView,
                customWebChromeClient = JestkWebChromeClient(id),
                customWebViewClient = JestkWebViewClient(id, adBlockerEngine, snapshotEngine)
            )
        }
        session.lastActiveTimestamp = System.currentTimeMillis()
        return detachFromParent(session.webView)
    }

    override fun get(tabId: Long): WebView? = pool[tabId]?.webView

    override fun detachFromParent(webView: WebView): WebView {
        (webView.parent as? ViewGroup)?.removeView(webView)
        return webView
    }

    override fun removeAndDestroy(tabId: Long) {
        pool.remove(tabId)?.let { session ->
            val webView = session.webView
            detachFromParent(webView)
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = null
            webView.clearHistory()
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.destroy()
        }
    }

    override fun clearAll() {
        pool.keys.toList().forEach { removeAndDestroy(it) }
    }

    override fun getAllWebViews(): Collection<WebView> = pool.values.map { it.webView }

    private fun createConfiguredWebView(tabId: Long, context: Context): WebView {
        return WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable = true
            isFocusableInTouchMode = true
            
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false // Uninterrupted HTML5 media
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT
                allowFileAccess = false
                allowContentAccess = true
                userAgentString = userAgentString // Standard or customized
            }
        }
    }
}
```

### 2.3 Safe Detach / Attach in Compose `AndroidView`
When the active tab changes in Compose, `AndroidView` executes the `factory` on initial composition or updates via keying:

```kotlin
@Composable
fun BrowserWebViewContainer(
    tabId: Long,
    webViewPool: IWebViewPool,
    modifier: Modifier = Modifier
) {
    key(tabId) {
        AndroidView(
            factory = { context ->
                val webView = webViewPool.getOrCreate(tabId, context)
                // Guaranteed detachment before returning to Compose ViewGroup parent
                webViewPool.detachFromParent(webView)
                webView
            },
            update = { webView ->
                // Ensure parent detachment if updated across recomposition scopes
                webViewPool.detachFromParent(webView)
            },
            onRelease = { webView ->
                // Detach view when removed from composition tree without destroying it
                webViewPool.detachFromParent(webView)
            },
            modifier = modifier.fillMaxSize()
        )
    }
}
```

### 2.4 Background Tab Execution & Media Stream Preservation
1. **No `onPause()` on Background Tabs**: Calling `onPause()` pauses HTML5 audio/video elements and WebRTC streams in WebView. Inactive tabs MUST NOT have `onPause()` invoked.
2. **No `pauseTimers()` on Background Tabs**: `WebView.pauseTimers()` is a static, process-wide call that suspends `setTimeout`, `setInterval`, and animation frames across *all* WebViews in the app. It must NEVER be invoked during tab switching or backgrounding.
3. **Application Level Backgrounding**: When the entire application goes to the background (user leaves app), background audio continues playing via HTML5 audio session unless explicitly paused by user or system audio focus loss.

---

## 3. R4: Reliable Page Snapshot Engine

### 3.1 Problem Statement: Asynchronous Rasterization vs Blank Snapshots
In modern Chromium-based Android System WebView:
- Layout and DOM construction occur on the Main Thread.
- Layer compositing and Skia/Ganesh rasterization occur asynchronously on the RenderThread/GPU.
- `WebViewClient.onPageFinished` signals that the DOM tree and network resources have completed loading, but the GPU raster compositor may not have produced or presented the first visual frame.
- Capturing the `WebView` canvas immediately upon `onPageFinished` or tab navigation triggers results in blank white thumbnails, missing images, or black frames in tab overviews.

### 3.2 `VisualStateCallback` Synchronization Algorithm
Android Marshmallow (API 23+) provides `WebView.postVisualStateCallback(requestId: Long, callback: VisualStateCallback)`. The callback `onComplete(requestId: Long)` is guaranteed by the OS WebView framework to fire ONLY when all DOM changes up to the request have been composited and rendered into the visual surface.

```
[Navigation / onPageFinished]
             │
             ▼
   [Generate Request ID]
             │
             ▼
[postVisualStateCallback(id)] ──(Async Blink Frame Composition)──┐
             │                                                    │
             ▼                                                    ▼
   (Wait for Render)                                   [VisualStateCallback]
                                                                  │
                                                                  ▼
                                                      [Frame Ready on Canvas]
                                                                  │
                                                                  ▼
                                                      [Execute Canvas Draw]
                                                                  │
                                                                  ▼
                                                      [Downscale to 480px]
                                                                  │
                                                                  ▼
                                                      [Compress to Disk Cache]
                                                                  │
                                                                  ▼
                                                      [Update Room DB Path]
                                                                  │
                                                                  ▼
                                                      [Notify UI via Flow]
```

### 3.3 Complete Snapshot Engine Implementation Contract

```kotlin
interface ISnapshotEngine {
    fun captureTabSnapshot(
        tabId: Long,
        webView: WebView,
        onCaptured: ((File?) -> Unit)? = null
    )
    fun getSnapshotFile(tabId: Long): File
    fun deleteSnapshot(tabId: Long)
}

class SnapshotEngine(
    private val context: Context,
    private val tabRepository: TabRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ISnapshotEngine {

    private val snapshotDir = File(context.filesDir, "snapshots").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun getSnapshotFile(tabId: Long): File {
        return File(snapshotDir, "tab_${tabId}.jpg")
    }

    override fun deleteSnapshot(tabId: Long) {
        val file = getSnapshotFile(tabId)
        if (file.exists()) file.delete()
    }

    override fun captureTabSnapshot(
        tabId: Long,
        webView: WebView,
        onCaptured: ((File?) -> Unit)?
    ) {
        val width = webView.width
        val height = webView.height

        if (width <= 0 || height <= 0) {
            onCaptured?.invoke(null)
            return
        }

        val requestId = System.nanoTime()
        webView.postVisualStateCallback(requestId, object : WebView.VisualStateCallback() {
            override fun onComplete(reqId: Long) {
                scope.launch {
                    val snapshotFile = processAndSaveBitmap(tabId, webView, width, height)
                    if (snapshotFile != null) {
                        tabRepository.updateSnapshotPath(tabId, snapshotFile.absolutePath)
                    }
                    onCaptured?.invoke(snapshotFile)
                }
            }
        })
    }

    private suspend fun processAndSaveBitmap(
        tabId: Long,
        webView: WebView,
        srcWidth: Int,
        srcHeight: Int
    ): File? = withContext(ioDispatcher) {
        try {
            // Target thumbnail bounds (e.g. max width 480px to conserve memory)
            val targetWidth = 480
            val targetHeight = (srcHeight * (targetWidth.toFloat() / srcWidth)).toInt().coerceAtLeast(1)

            val fullBitmap = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(fullBitmap)
            
            // Draw WebView to Canvas on Main thread or via synchronized bitmap
            withContext(Dispatchers.Main) {
                webView.draw(canvas)
            }

            val scaledBitmap = Bitmap.createScaledBitmap(fullBitmap, targetWidth, targetHeight, true)
            fullBitmap.recycle()

            val outputFile = getSnapshotFile(tabId)
            val tempFile = File(snapshotDir, "tab_${tabId}_tmp.jpg")

            FileOutputStream(tempFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.flush()
            }
            scaledBitmap.recycle()

            if (tempFile.renameTo(outputFile)) {
                outputFile
            } else {
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
                outputFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
```

### 3.4 Cold Boot Instant Thumbnail Hydration Flow
1. Tab metadata in Room DB stores `snapshotPath: String?`.
2. Upon cold boot:
   - ViewModel loads `List<TabEntity>` from Room DB via `Flow<List<TabEntity>>`.
   - The Visual Tab Grid (All Tabs Overview) and Home Screen Recent Carousel immediately display cached disk bitmaps using Coil/AsyncImage or custom Bitmap decoder pointed at `File(tab.snapshotPath)`.
   - No `WebView` needs to be initialized or rendered for the UI to display rich visual thumbnails instantly.
3. When the user selects a tab or the active tab loads, `captureTabSnapshot` refreshes the image on disk and updates Room DB.

---

## 4. R6.1: Native Ad & Tracker Blocker Subsystem

### 4.1 Blocklist Asset Packaging & Format
- File location: `app/src/main/assets/adblock/hosts.txt`
- Contains high-coverage ad/tracker hostnames compiled from standard rulesets (EasyList, AdGuard, Peter Lowe's list, etc.).
- Plain text line format:
  ```
  # Comments start with hash
  0.0.0.0 doubleclick.net
  0.0.0.0 google-analytics.com
  0.0.0.0 adservice.google.com
  0.0.0.0 pagead2.googlesyndication.com
  0.0.0.0 criteo.com
  0.0.0.0 taboola.com
  0.0.0.0 outbrain.com
  0.0.0.0 scorecardresearch.com
  0.0.0.0 ads.pubmatic.com
  0.0.0.0 metrics.data.microsoft.com
  ```

### 4.2 Fast Startup Loader & `HashSet<String>` Storage
- Loading happens asynchronously on `Dispatchers.IO` at app startup or first engine access.
- Fast parsing strips `0.0.0.0 `, `127.0.0.1 `, inline comments, and trims to lowercase.
- Storage: `HashSet<String>` with pre-sized capacity to eliminate rehashing during load.
- $O(1)$ lookup complexity per candidate domain.

```kotlin
class AdBlockerEngine(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val blockedHosts = HashSet<String>(16384)
    private val isReady = AtomicBoolean(false)
    val isEnabled: StateFlow<Boolean> = settingsRepository.isAdBlockerEnabled

    suspend fun initialize() = withContext(ioDispatcher) {
        if (isReady.get()) return@withContext
        try {
            context.assets.open("adblock/hosts.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val host = trimmed
                            .removePrefix("0.0.0.0 ")
                            .removePrefix("127.0.0.1 ")
                            .trim()
                            .lowercase(Locale.ROOT)
                        if (host.isNotEmpty()) {
                            blockedHosts.add(host)
                        }
                    }
                }
            }
            isReady.set(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Matches exact domain or any parent domain hierarchy.
     * e.g., "ad.secure.doubleclick.net" matches "doubleclick.net"
     */
    fun isBlocked(host: String?): Boolean {
        if (host.isNullOrBlank() || !isEnabled.value || !isReady.get()) return false
        val normalizedHost = host.lowercase(Locale.ROOT)

        // Direct exact match: O(1)
        if (blockedHosts.contains(normalizedHost)) return true

        // Subdomain parent walk: O(k) where k = number of subdomains (usually <= 4)
        var dotIndex = normalizedHost.indexOf('.')
        while (dotIndex != -1 && dotIndex < normalizedHost.length - 1) {
            val parentDomain = normalizedHost.substring(dotIndex + 1)
            if (blockedHosts.contains(parentDomain)) {
                return true
            }
            dotIndex = normalizedHost.indexOf('.', dotIndex + 1)
        }
        return false
    }
}
```

### 4.3 Network Interception Contract (`WebViewClient.shouldInterceptRequest`)
Inside `JestkWebViewClient`:

```kotlin
class JestkWebViewClient(
    private val tabId: Long,
    private val adBlockerEngine: AdBlockerEngine,
    private val snapshotEngine: SnapshotEngine
) : WebViewClient() {

    private val emptyResponse: WebResourceResponse by lazy {
        WebResourceResponse(
            "text/plain",
            "UTF-8",
            200,
            "OK",
            mapOf(
                "Access-Control-Allow-Origin" to "*",
                "Cache-Control" to "no-cache"
            ),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        if (request == null) return super.shouldInterceptRequest(view, request)

        val host = request.url?.host
        if (adBlockerEngine.isBlocked(host)) {
            // Intercept and drop ad request with zero-byte 200 OK stream
            return emptyResponse
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view != null && url != null && url != "about:blank") {
            snapshotEngine.captureTabSnapshot(tabId, view)
        }
    }
}
```

---

## 5. R6.2: Video & Media Systems (Fullscreen, Mini-Player, System PiP)

### 5.1 HTML5 Fullscreen Lifecycle (`WebChromeClient`)
HTML5 video elements enter/exit fullscreen via `WebChromeClient.onShowCustomView` and `onHideCustomView`.

```kotlin
class JestkWebChromeClient(
    private val tabId: Long,
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
) : WebChromeClient() {

    var isCustomViewShowing: Boolean = false
        private set

    var onShowCustomViewListener: ((View, CustomViewCallback) -> Unit)? = null
    var onHideCustomViewListener: (() -> Unit)? = null

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        customViewCallback = callback
        isCustomViewShowing = true
        onShowCustomViewListener?.invoke(view, callback)
    }

    override fun onHideCustomView() {
        isCustomViewShowing = false
        onHideCustomViewListener?.invoke()
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    fun dismissCustomView() {
        if (isCustomViewShowing) {
            onHideCustomView()
        }
    }
}
```

#### Fullscreen UI Container Management
When `onShowCustomView(view)` is invoked:
1. `MainActivity` or root Compose container hosts a top-level `FrameLayout` for fullscreen custom views.
2. Standard browser UI (top tab strip, bottom navigation dock, address overlays) is hidden (`visibility = GONE` or Composable state `isFullscreen = true`).
3. Window flags enter immersive sticky fullscreen:
   ```kotlin
   fun setImmersiveFullscreen(window: Window, enable: Boolean) {
       val insetsController = WindowCompat.getInsetsController(window, window.decorView)
       if (enable) {
           insetsController.hide(WindowInsetsCompat.Type.systemBars())
           insetsController.systemBarsBehavior =
               WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
       } else {
           insetsController.show(WindowInsetsCompat.Type.systemBars())
       }
   }
   ```
4. Custom view is added to `fullscreenContainer.addView(view)`.
5. On `onHideCustomView()`: Custom view is removed from container, immersive mode is disabled, and standard UI is restored.

### 5.2 In-App Draggable Video Mini-Player
When switching away from a tab with active media or minimizing the video viewport inside the app:
- A floating Compose overlay displays the mini-player with drag gestures (`pointerInput { detectDragGestures { ... } }`).
- Mini-player controls:
  - Drag handle to reposition anywhere on the viewport bounds.
  - Return / Expand button: switches active tab back to the video tab.
  - Close button: stops video or dismisses mini-player view.

### 5.3 Android System Picture-in-Picture (PiP)
- **Manifest Setup**:
  ```xml
  <activity
      android:name=".MainActivity"
      android:supportsPictureInPicture="true"
      android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"
      android:exported="true">
  ```
- **PiP Trigger on App Backgrounding (`onUserLeaveHint`)**:
  ```kotlin
  override fun onUserLeaveHint() {
      super.onUserLeaveHint()
      val isVideoActive = mediaStateManager.hasActiveVideoPlayback()
      if (isVideoActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          val aspectRatio = Rational(16, 9)
          val params = PictureInPictureParams.Builder()
              .setAspectRatio(aspectRatio)
              .apply {
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                      setAutoEnterEnabled(true)
                      setSeamlessResizeEnabled(true)
                  }
              }
              .build()
          enterPictureInPictureMode(params)
      }
  }

  override fun onPictureInPictureModeChanged(
      isInPictureInPictureMode: Boolean,
      newConfig: Configuration
  ) {
      super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
      browserViewModel.setPictureInPictureMode(isInPictureInPictureMode)
  }
  ```
- In PiP mode: all navigation bars, search modals, and toolbars are hidden; only the video custom view or active webview is rendered.

---

## 6. R6.3: Back Navigation Hierarchy

### 6.1 Priority Evaluation Cascade
The back gesture must follow a strict 5-layer hierarchy:

```
[User Presses Back / Swipes Back]
                │
                ▼
      ┌───────────────────┐
      │ Layer 1: Custom   │──(Yes)──> [webChromeClient.onHideCustomView()]
      │ View Fullscreen?  │
      └───────────────────┘
                │ (No)
                ▼
      ┌───────────────────┐
      │ Layer 2: Modal    │──(Yes)──> [Dismiss Modal Overlay:
      │ Overlays Open?    │            Search / TabGrid / Settings / Menu]
      └───────────────────┘
                │ (No)
                ▼
      ┌───────────────────┐
      │ Layer 3: WebView  │──(Yes)──> [activeWebView.goBack()]
      │ canGoBack()?      │
      └───────────────────┘
                │ (No)
                ▼
      ┌───────────────────┐
      │ Layer 4: Tabs > 1 │──(Yes)──> [Close Active Tab & Switch to Neighbor]
      │ Open in Browser?  │
      └───────────────────┘
                │ (No)
                ▼
      ┌───────────────────┐
      │ Layer 5: Base Exit│─────────> [Activity finish() / System Back]
      └───────────────────┘
```

### 6.2 Compose BackHandler Implementation
```kotlin
@Composable
fun BrowserBackNavigationHandler(
    viewModel: BrowserViewModel,
    activeSession: TabWebViewSession?,
    activity: ComponentActivity
) {
    val uiState by viewModel.uiState.collectAsState()
    val isCustomViewOpen = activeSession?.customWebChromeClient?.isCustomViewShowing == true
    val isOverlayOpen = uiState.isSearchOverlayVisible ||
                        uiState.isTabGridVisible ||
                        uiState.isSettingsVisible ||
                        uiState.isContextMenuVisible
    val canGoBack = activeSession?.webView?.canGoBack() == true
    val hasMultipleTabs = uiState.tabs.size > 1

    BackHandler(enabled = true) {
        when {
            // Layer 1: HTML5 Fullscreen Custom View
            isCustomViewOpen -> {
                activeSession?.customWebChromeClient?.dismissCustomView()
            }
            // Layer 2: Active Overlays
            uiState.isSearchOverlayVisible -> {
                viewModel.dismissSearchOverlay()
            }
            uiState.isTabGridVisible -> {
                viewModel.dismissTabGrid()
            }
            uiState.isSettingsVisible -> {
                viewModel.dismissSettings()
            }
            uiState.isContextMenuVisible -> {
                viewModel.dismissContextMenu()
            }
            // Layer 3: Active WebView History
            canGoBack -> {
                activeSession?.webView?.goBack()
            }
            // Layer 4: Close Active Tab if Multiple Open
            hasMultipleTabs -> {
                viewModel.closeActiveTab()
            }
            // Layer 5: Exit App
            else -> {
                activity.finish()
            }
        }
    }
}
```

---

## 7. Features Discovered Table

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| 1 | R3: WebView Pool | `getOrCreate(tabId, context)` | Retrieves existing cached `WebView` or instantiates and configures a new one with JS/DOM storage. | `tabId: Long`, `Context` | `WebView` instance | Throws `NullPointerException` if context null; handled via safe fallback. | `ORIGINAL_REQUEST.md` § R3, `spec.md` § 1 |
| 2 | R3: WebView Pool | Safe Detachment (`detachFromParent`) | Safely executes `(webView.parent as? ViewGroup)?.removeView(webView)` before recycling/attaching in Compose `AndroidView`. | `WebView` | Detached `WebView` | Idempotent; returns cleanly if `parent == null`. | `ORIGINAL_REQUEST.md` § R3, `spec.md` § 1 |
| 3 | R3: WebView Pool | In-Memory Session Cache | Key-value store mapping `tabId` to `TabWebViewSession` containing WebView, clients, and state. | `tabId: Long` | `TabWebViewSession?` | None; thread-safe `ConcurrentHashMap`. | `ORIGINAL_REQUEST.md` § R3 |
| 4 | R3: Lifecycle | Uninhibited Background Execution | Keeps background tabs alive without calling `onPause()` or `pauseTimers()`, ensuring ongoing HTML5 audio/video and JS timers. | Inactive `tabId` | Maintained audio/video stream & DOM | None; avoids process-wide timer freeze. | `ORIGINAL_REQUEST.md` § R3, Acceptance Criteria |
| 5 | R3: Lifecycle | Tab Destruction & Resource Freeing | Clears clients, loads `about:blank`, detaches parent, calls `destroy()`, and purges from pool upon tab close. | `tabId: Long` | Resources freed | Protects against memory leaks; checks null before destroy. | `ORIGINAL_REQUEST.md` § R3 |
| 6 | R4: Snapshot Engine | `VisualStateCallback` Frame Sync | Calls `WebView.postVisualStateCallback` to synchronize canvas capture with Blink GPU rasterization completion. | `tabId: Long`, `WebView` | Render-ready callback trigger | Fallback to immediate canvas capture if timeout exceeds 1500ms. | `ORIGINAL_REQUEST.md` § R4 |
| 7 | R4: Snapshot Engine | Canvas Bitmap Capture & Downscaling | Captures `WebView.draw(canvas)` into bitmap, downscales to 480px width preserving aspect ratio. | `WebView`, dimensions | Scaled `Bitmap` (JPEG 85%) | Returns null if WebView width/height <= 0; skips allocation. | `ORIGINAL_REQUEST.md` § R4, `spec.md` § 4.3 |
| 8 | R4: Snapshot Engine | Atomic Disk Cache Storage | Atomically writes compressed JPEG snapshot to `files/snapshots/tab_{id}.jpg` via temp file rename. | `tabId: Long`, `Bitmap` | `File` path | Catches `IOException`, logs error, retries or falls back to direct copy. | `ORIGINAL_REQUEST.md` § R4 |
| 9 | R4: Snapshot Engine | Room DB Metadata Synchronization | Persists snapshot absolute file path to `TabEntity.snapshotPath` in Room DB. | `tabId: Long`, `path: String` | DB update confirmation | Room database transaction failure caught via CoroutineExceptionHandler. | `ORIGINAL_REQUEST.md` § R4, `spec.md` § 5.3 |
| 10 | R4: Snapshot Engine | Cold-Boot Instant Hydration | Tab Grid and Home Carousel read cached thumbnail files directly from Room `snapshotPath` without instantiating WebViews. | `TabEntity.snapshotPath` | Instant visual bitmap in UI | If file missing or corrupted, displays placeholder favicon/card. | `ORIGINAL_REQUEST.md` § R4, `spec.md` § 4.1, 4.3 |
| 11 | R6.1: Ad Blocker | Bundled Hostname Ruleset | Packages compiled ad/tracker hostnames in `assets/adblock/hosts.txt`. | Asset stream | Parsed domain lines | Defaults to empty set if asset missing; logs warning. | `ORIGINAL_REQUEST.md` § R6, `spec.md` § 5.1 |
| 12 | R6.1: Ad Blocker | Fast `HashSet<String>` Storage | Loads and normalizes hostnames into memory at startup for $O(1)$ lookup. | Raw domain strings | Sanitized lowercase `HashSet` | Skips malformed lines and comments (`#`). | `ORIGINAL_REQUEST.md` § R6, `spec.md` § 5.1 |
| 13 | R6.1: Ad Blocker | Subdomain Matching Algorithm | Iteratively strips leftmost subdomain segments to detect parent domain matches. | Target `host: String?` | `Boolean` (blocked/allowed) | Returns `false` immediately on null/blank host. | `ORIGINAL_REQUEST.md` § R6, `spec.md` § 5.1 |
| 14 | R6.1: Ad Blocker | Request Interception (`shouldInterceptRequest`) | Returns empty `WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))` for blocked domains. | `WebResourceRequest` | `WebResourceResponse?` | Returns `null` (pass-through) for non-blocked requests. | `ORIGINAL_REQUEST.md` § R6, `spec.md` § 5.1 |
| 15 | R6.1: Ad Blocker | Settings Toggle Integration | Connects `AdBlockerEngine.isEnabled` to Settings `StateFlow<Boolean>` for instant live enabling/disabling. | User toggle in Settings | Updated filter behavior | Instant effect on subsequent requests; no reload required. | `ORIGINAL_REQUEST.md` § R6, `spec.md` § 4.4 |
| 16 | R6.2: Media Systems | HTML5 Fullscreen Show (`onShowCustomView`) | Hides standard UI, sets immersive system bar flags, attaches video view to root fullscreen container. | `View`, `CustomViewCallback` | Fullscreen video view | Safely stores callback; manages orientation lock if needed. | `ORIGINAL_REQUEST.md` § R6, `spec.md` § 5.2 |
| 17 | R6.2: Media Systems | HTML5 Fullscreen Hide (`onHideCustomView`) | Removes custom view, resets system bars, invokes `onCustomViewHidden()`, restores standard UI. | None | Restored browser viewport | Safely handles null callback if already dismissed. | `ORIGINAL_REQUEST.md` § R6, `spec.md` § 5.2 |
| 18 | R6.2: Media Systems | Draggable Video Mini-Player | In-app floating draggable Compose overlay showing active video when switching tabs. | Active media session, drag offsets | Floating picture overlay | Bounded within screen margins; ignores off-screen drag. | `ORIGINAL_REQUEST.md` § R6 |
| 19 | R6.2: Media Systems | Android System PiP Mode | Invokes `enterPictureInPictureMode(PictureInPictureParams)` on `onUserLeaveHint()` if video is active. | `onUserLeaveHint` event | OS-level floating PiP window | Checks `Build.VERSION.SDK_INT >= 26` and device PiP feature support. | `ORIGINAL_REQUEST.md` § R6, `spec.md` § 5.2 |
| 20 | R6.2: Media Systems | PiP Layout Adaptor | Hides all toolbars and overlays during PiP mode, expanding video surface to 100% window bounds. | `isInPictureInPictureMode: Boolean`| Minimalist video viewport | Restores full browser controls on exit. | `spec.md` § 5.2 |
| 21 | R6.3: Navigation | 5-Tier Back Navigation Dispatcher | Evaluates back hierarchy: Custom View -> Overlays -> WebView History -> Tab Close -> Exit. | Back button / gesture | Corresponding action execution | Guaranteed non-blocking fallback to `activity.finish()`. | `ORIGINAL_REQUEST.md` § R6, `spec.md` § 5.3 |

---

## 8. Edge Cases & Boundary Conditions

| # | Feature | Input / Condition | Observed / Required Behavior |
|---|---------|-------------------|------------------------------|
| 1 | R3: WebViewPool | Rapid tab switching between Tab A and Tab B (<50ms). | `prepareForAttachment` safely removes `WebView` from prior `ViewGroup` synchronously; no `IllegalStateException` or blank view. |
| 2 | R3: WebViewPool | Background tab streaming WebRTC / audio while 5 other tabs open. | Background tab continues playing uninterrupted; JS timers fire normally; no audio stuttering or pause. |
| 3 | R3: WebViewPool | Memory pressure / Low memory warning from OS (`onTrimMemory`). | Drop cached snapshots in memory; keep live WebViews intact unless critical OS trim requires tab suspension. |
| 4 | R3: WebViewPool | Active tab destroyed via swipe-to-dismiss in Tab Grid. | `removeAndDestroy(tabId)` called: WebView detached, loaded with `about:blank`, clients cleared, `destroy()` called, snapshot deleted from disk. |
| 5 | R4: Snapshot Engine | `onPageFinished` fires for `about:blank` or blank new tab. | Snapshot engine detects blank state or skips capture; placeholder background drawn; no corrupted/broken image written to disk. |
| 6 | R4: Snapshot Engine | Snapshot capture invoked while WebView width/height is 0 (unmeasured). | Returns early without allocating bitmap; defers capture until layout pass completes with positive dimensions. |
| 7 | R4: Snapshot Engine | `postVisualStateCallback` does not fire (heavy JS lockup or frozen renderer). | Timeout coroutine (1500ms) triggers fallback direct canvas draw or aborts cleanly without blocking UI thread. |
| 8 | R4: Snapshot Engine | Cold boot with previously deleted/corrupted snapshot file on disk. | UI detects missing file via Coil/custom loader, gracefully displays site favicon + title on default card surface. |
| 9 | R6.1: Ad Blocker | Request with complex subdomain `cdn.adservice.sub.doubleclick.net`. | Subdomain walker extracts `sub.doubleclick.net` -> `doubleclick.net`, finds match in `HashSet`, intercepts request. |
| 10 | R6.1: Ad Blocker | URL with null/empty host (e.g. `data:image/png;base64,...` or `blob:...` or `javascript:...`). | `isBlocked(host)` returns `false` immediately; passes through to default WebView handler. |
| 11 | R6.1: Ad Blocker | User toggles Ad Blocker OFF in Settings during active browsing. | `isEnabled` StateFlow emits `false`; subsequent ad requests immediately bypass filter without page reload. |
| 12 | R6.1: Ad Blocker | Blocked asset is a script (`<script src="...">`) or tracking pixel. | Empty `WebResourceResponse("text/plain", "UTF-8", ...)` returned; page finishes loading without script execution errors. |
| 13 | R6.2: Media Systems | Video enters fullscreen, then user presses Android hardware Back button. | Layer 1 back handler intercepts: dismisses fullscreen custom view, restores portrait browser UI; does not navigate back in WebView history. |
| 14 | R6.2: Media Systems | User presses Home button while HTML5 video is playing fullscreen or inline. | `onUserLeaveHint` fires; creates `PictureInPictureParams` (16:9); calls `enterPictureInPictureMode`; video plays in OS PiP window. |
| 15 | R6.2: Media Systems | Device does not support PiP (e.g. low-ram device / disabled by OEM). | `packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)` returns `false`; skips PiP call without crash. |
| 16 | R6.3: Back Navigation | Search Overlay is open with active query; user presses Back. | Layer 2 back handler dismisses Search Overlay, clears keyboard, returns focus to active web page; WebView history untouched. |
| 17 | R6.3: Back Navigation | Only 1 tab open, `canGoBack() == false`, no overlays active; user presses Back. | Layer 5 executes; calls `activity.finish()` or minimizes app to OS home launcher. |
| 18 | R6.3: Back Navigation | 3 tabs open, active tab has `canGoBack() == false`; user presses Back. | Layer 4 executes; closes active tab, deletes its resources, switches focus to adjacent tab in strip. |

---

## 9. Concurrency, Thread Safety & State Flow

1. **WebView Thread Affinity**:
   - `android.webkit.WebView` methods (`loadUrl`, `goBack`, `draw`, `destroy`, `postVisualStateCallback`) MUST be executed strictly on the Android **Main (UI) Thread** (`Dispatchers.Main.immediate`).
2. **AdBlock Rules Parsing & Matching**:
   - Initialization runs on `Dispatchers.IO`.
   - Read queries (`isBlocked`) in `shouldInterceptRequest` execute on WebView background network worker threads (`Chrome_IOThread` / `Thread-Pool`).
   - `HashSet<String>` is populated before `isReady.set(true)`, making subsequent read operations across concurrent threads memory-safe and lock-free.
3. **Snapshot Disk I/O & Bitmap Processing**:
   - Canvas drawing occurs on Main thread; scaling, compression, disk I/O, and Room updates execute on `Dispatchers.IO`.
   - Temporary file rename guarantees atomic snapshot updates on disk, preventing partially written JPEG reads.

---

## 10. Test Verification Strategy & Assertions Matrix

To ensure compliance with the execution plan and testing tracks, the following verification suite must be constructed:

### Tier 1: Unit & Component Tests
- `WebViewPoolTest`:
  - Verify `getOrCreate` returns same instance for same `tabId`.
  - Verify `detachFromParent` removes view from parent `ViewGroup`.
  - Verify `removeAndDestroy` cleans session, stops loading, and releases memory.
- `AdBlockerEngineTest`:
  - Verify exact domain match (e.g., `doubleclick.net` -> blocked).
  - Verify deep subdomain match (e.g., `tracker.east.doubleclick.net` -> blocked).
  - Verify legitimate domain pass-through (e.g., `wikipedia.org`, `jestk.com` -> allowed).
  - Verify toggle off allows all domains.
- `BackNavigationHierarchyTest`:
  - Test CustomView active -> calls `dismissCustomView`.
  - Test SearchOverlay open -> calls `dismissSearchOverlay`.
  - Test `canGoBack == true` -> calls `webView.goBack()`.
  - Test multi-tab -> calls `closeActiveTab()`.
  - Test single tab -> calls `activity.finish()`.

### Tier 2: Robolectric / Instrumentation Tests
- `VisualStateCallbackSnapshotTest`:
  - Verify `postVisualStateCallback` invocation on `onPageFinished`.
  - Verify generated bitmap file exists on disk and has non-zero size.
  - Verify Room database is updated with valid `snapshotPath`.
- `ComposeAndroidViewDetachmentTest`:
  - Recompose tab view rapidly between tab 1 and tab 2; assert zero `IllegalStateException`.
- `PictureInPictureModeTest`:
  - Trigger `onUserLeaveHint` with active video flag; assert `enterPictureInPictureMode` is invoked with valid parameters.
