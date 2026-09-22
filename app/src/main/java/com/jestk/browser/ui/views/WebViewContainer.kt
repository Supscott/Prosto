package com.jestk.browser.ui.views

import android.annotation.SuppressLint
import android.content.MutableContextWrapper
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.jestk.browser.domain.model.OverlayType
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.jestk.browser.engine.NestedWebView
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.JestkWebChromeClient
import com.jestk.browser.engine.JestkWebViewClient
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.engine.findActivity
import com.jestk.browser.ui.MainActivity
import com.jestk.browser.ui.theme.ActiveTabBorderBrush
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WebViewContainer(
    tabId: String,
    webViewPool: WebViewPool,
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val currentUiState by rememberUpdatedState(uiState)
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentTab = remember(tabId, uiState.tabs) {
        uiState.tabs.find { it.id == tabId }
    }
    val targetUrl = currentTab?.url ?: (if (uiState.activeTabId == tabId) uiState.currentUrl else "")

    val scope = rememberCoroutineScope()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()

    val isImeVisible = WindowInsets.isImeVisible
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val targetBottomPadding = when {
        uiState.isFullscreenCustomView -> 0.dp
        isImeVisible -> 0.dp
        !uiState.isBottomBarVisible -> 0.dp
        else -> 62.dp + navBarBottom
    }
    val bottomPadding by animateDpAsState(
        targetValue = targetBottomPadding,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "WebViewBottomPadding"
    )

    val setupWebViewLongClick: (WebView) -> Unit = remember {
        { wv ->
            wv.isLongClickable = true
            var fallbackTouchX = 0f
            var fallbackTouchY = 0f
            var fallbackRawX = 0f
            var fallbackRawY = 0f

            @SuppressLint("ClickableViewAccessibility")
            wv.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                    fallbackTouchX = event.x
                    fallbackTouchY = event.y
                    fallbackRawX = event.rawX
                    fallbackRawY = event.rawY
                }
                false
            }

            wv.setOnLongClickListener { v ->
                val targetWebView = v as? WebView ?: return@setOnLongClickListener false
                val hitTest = targetWebView.hitTestResult ?: return@setOnLongClickListener false
                val nested = targetWebView as? NestedWebView

                val location = IntArray(2)
                targetWebView.getLocationOnScreen(location)
                val relX = nested?.lastTouchX?.takeIf { it > 0f } ?: fallbackTouchX
                val relY = nested?.lastTouchY?.takeIf { it > 0f } ?: fallbackTouchY
                val anchorX = if (relX > 0f) location[0] + relX else fallbackRawX.takeIf { it > 0f }
                val anchorY = if (relY > 0f) location[1] + relY else fallbackRawY.takeIf { it > 0f }

                when (hitTest.type) {
                    WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                        val url = hitTest.extra
                        if (!url.isNullOrBlank()) {
                            currentOnEvent(BrowserUiEvent.ShowLinkContextMenu(url = url, anchorX = anchorX, anchorY = anchorY))
                            true
                        } else {
                            val handler = object : Handler(Looper.getMainLooper()) {
                                override fun handleMessage(msg: Message) {
                                    val nodeUrl = msg.data?.getString("url")
                                    if (!nodeUrl.isNullOrBlank()) {
                                        currentOnEvent(BrowserUiEvent.ShowLinkContextMenu(url = nodeUrl, anchorX = anchorX, anchorY = anchorY))
                                    }
                                }
                            }
                            targetWebView.requestFocusNodeHref(handler.obtainMessage())
                            true
                        }
                    }
                    WebView.HitTestResult.IMAGE_TYPE -> {
                        val imageUrl = hitTest.extra
                        if (!imageUrl.isNullOrBlank()) {
                            currentOnEvent(BrowserUiEvent.ShowImageContextMenu(imageUrl = imageUrl, anchorX = anchorX, anchorY = anchorY))
                            true
                        } else false
                    }
                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                        val fallbackUrl = hitTest.extra
                        val handler = object : Handler(Looper.getMainLooper()) {
                            override fun handleMessage(msg: Message) {
                                val imageUrl = msg.data?.getString("src")
                                val nodeUrl = msg.data?.getString("url")
                                val targetLink = if (!nodeUrl.isNullOrBlank()) nodeUrl else fallbackUrl
                                if (!imageUrl.isNullOrBlank()) {
                                    currentOnEvent(
                                        BrowserUiEvent.ShowImageContextMenu(
                                            imageUrl = imageUrl,
                                            linkUrl = targetLink,
                                            anchorX = anchorX,
                                            anchorY = anchorY
                                        )
                                    )
                                } else if (!targetLink.isNullOrBlank()) {
                                    currentOnEvent(BrowserUiEvent.ShowLinkContextMenu(url = targetLink, anchorX = anchorX, anchorY = anchorY))
                                }
                            }
                        }
                        targetWebView.requestFocusNodeHref(handler.obtainMessage())
                        true
                    }
                    1 -> { // Deprecated HitTestResult.ANCHOR_TYPE
                        val url = hitTest.extra
                        if (!url.isNullOrBlank()) {
                            currentOnEvent(BrowserUiEvent.ShowLinkContextMenu(url = url, anchorX = anchorX, anchorY = anchorY))
                            true
                        } else false
                    }
                    6 -> { // Deprecated HitTestResult.IMAGE_ANCHOR_TYPE
                        val fallbackUrl = hitTest.extra
                        val handler = object : Handler(Looper.getMainLooper()) {
                            override fun handleMessage(msg: Message) {
                                val imageUrl = msg.data?.getString("src")
                                val nodeUrl = msg.data?.getString("url")
                                val targetLink = if (!nodeUrl.isNullOrBlank()) nodeUrl else fallbackUrl
                                if (!imageUrl.isNullOrBlank()) {
                                    currentOnEvent(
                                        BrowserUiEvent.ShowImageContextMenu(
                                            imageUrl = imageUrl,
                                            linkUrl = targetLink,
                                            anchorX = anchorX,
                                            anchorY = anchorY
                                        )
                                    )
                                } else if (!targetLink.isNullOrBlank()) {
                                    currentOnEvent(BrowserUiEvent.ShowLinkContextMenu(url = targetLink, anchorX = anchorX, anchorY = anchorY))
                                }
                            }
                        }
                        targetWebView.requestFocusNodeHref(handler.obtainMessage())
                        true
                    }
                    else -> false
                }
            }
        }
    }

    val resolvedThemeColor = uiState.activeThemeColor ?: currentTab?.themeColor
    val isHome = uiState.activeOverlay == OverlayType.HOME ||
            uiState.currentUrl.isEmpty() ||
            uiState.currentUrl == "about:blank"
    val defaultBg = MaterialTheme.colorScheme.background
    val targetSiteBg = if (isHome) defaultBg else (resolvedThemeColor?.let { Color(it) } ?: defaultBg)
    val animatedSiteBg by animateColorAsState(
        targetValue = targetSiteBg,
        animationSpec = tween(durationMillis = 350),
        label = "WebViewContainerSiteBg"
    )

    val contentModifier = if (uiState.isFullscreenCustomView) {
        Modifier.fillMaxSize()
    } else {
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = bottomPadding)
            .imePadding()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedSiteBg)
    ) {
        Box(
            modifier = contentModifier
        ) {
            key(tabId) {
            AndroidView<SwipeRefreshLayout>(
                factory = { context ->
                    webViewPool.isAdBlockerEnabled = { currentUiState.settings.isAdBlockerEnabled }
                    val activity = context.findActivity() ?: context
                    val webView = webViewPool.getOrCreateWebView(tabId, activity)
                    // Enforce proper parent detachment when recycling WebViews in Compose
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    (webView.context as? MutableContextWrapper)?.baseContext = activity
                    webView.isFocusable = true
                    webView.isFocusableInTouchMode = true
                    setupWebViewLongClick(webView)

                    webView.setOnScrollChangeListener { v, _, scrollY, _, oldScrollY ->
                        val dy = scrollY - oldScrollY
                        val viewportHeight = if (v.height > 0) v.height else v.resources.displayMetrics.heightPixels
                        val crossedHScreen = (oldScrollY > viewportHeight && scrollY <= viewportHeight) ||
                            (oldScrollY <= viewportHeight && scrollY > viewportHeight)
                        if (kotlin.math.abs(dy) > 8 || scrollY <= 0 || crossedHScreen) {
                            currentOnEvent(
                                BrowserUiEvent.OnWebScroll(
                                    deltaY = dy.toFloat(),
                                    scrollY = scrollY,
                                    viewportHeight = viewportHeight,
                                )
                            )
                        }
                    }

                    webView.webViewClient = JestkWebViewClient(
                        tabId = tabId,
                        isAdBlockerEnabled = { currentUiState.settings.isAdBlockerEnabled },
                        onPageStarted = { id, url ->
                            currentOnEvent(BrowserUiEvent.OnPageStarted(id, url))
                        },
                        onPageFinished = { id, url, title ->
                            currentOnEvent(BrowserUiEvent.OnPageFinished(id, url, title))
                        },
                        onNavigationStateChanged = { id, canBack, canForward ->
                            currentOnEvent(BrowserUiEvent.OnNavigationStateChanged(id, canBack, canForward))
                        },
                        onSnapshotReady = { id, path -> currentOnEvent(BrowserUiEvent.OnSnapshotCaptured(id, path)) },
                        onThemeColorDetected = { id, color ->
                            currentOnEvent(BrowserUiEvent.OnThemeColorReceived(id, color))
                        },
                        isDarkTheme = { isDark }
                    )

                    webView.webChromeClient = JestkWebChromeClient(
                        tabId = tabId,
                        webView = webView,
                        onProgressChanged = { id, progress -> currentOnEvent(BrowserUiEvent.OnProgressChanged(id, progress)) },
                        onTitleReceived = { _, _ -> },
                        onFullscreenCustomViewChanged = { view, isFullscreen, orientation ->
                            currentOnEvent(BrowserUiEvent.SetFullscreenCustomView(view, isFullscreen, orientation))
                        },
                        onVideoPlayingStateChanged = { isPlaying ->
                            currentOnEvent(BrowserUiEvent.SetVideoPlaying(isPlaying))
                        },
                        onFaviconReceived = { id, icon ->
                            scope.launch {
                                val path = FaviconManager.saveFavicon(context, id, icon)
                                if (path != null) {
                                    currentOnEvent(BrowserUiEvent.UpdateFaviconUrl(id, path))
                                }
                            }
                        },
                        onShowFileChooser = { callback, params ->
                            val activity = context.findActivity() as? MainActivity
                                ?: webView.context.findActivity() as? MainActivity
                            activity?.openFileChooser(callback, params) ?: false
                        }
                    )

                    val nestedWebView = webView as? NestedWebView
                    val nestedLoadedUrl = nestedWebView?.loadedUrl
                    val isUnloaded = nestedLoadedUrl.isNullOrEmpty() || nestedLoadedUrl == "about:blank"
                    if (targetUrl.isNotBlank() && targetUrl != "about:blank") {
                        if (isUnloaded && (webView.url.isNullOrEmpty() || webView.url == "about:blank")) {
                            webView.loadUrl(targetUrl)
                        } else if (currentTab?.themeColor == null) {
                            (webView.webViewClient as? JestkWebViewClient)?.extractThemeColor(webView)
                        }
                    }

                    val brandColors = intArrayOf(
                        primaryColor,
                        android.graphics.Color.parseColor("#7BA7D7"),
                        android.graphics.Color.parseColor("#F6B99D"),
                        android.graphics.Color.parseColor("#D391A5")
                    )

                    JestkSwipeRefreshLayout(context).apply {
                        setColorSchemeColors(*brandColors)
                        setProgressBackgroundColorSchemeColor(surfaceColor)
                        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                        setOnRefreshListener {
                            currentOnEvent(BrowserUiEvent.Reload)
                        }
                        setOnChildScrollUpCallback { _, _ ->
                            val currentChild = (getChildAt(0) as? android.webkit.WebView) ?: webView
                            val nested = currentChild as? NestedWebView
                            val isBlocked = currentUiState.isLoading ||
                                    currentUiState.isVideoPlaying ||
                                    currentUiState.isFullscreenCustomView ||
                                    currentUiState.fullscreenCustomView != null

                            if (isBlocked) {
                                true
                            } else if (nested != null && !nested.isPullToRefreshEligible()) {
                                true
                            } else {
                                currentChild.canScrollVertically(-1) || currentChild.scrollY > 0
                            }
                        }
                        addView(
                            webView,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                },
                update = { swipeRefresh ->
                    webViewPool.isAdBlockerEnabled = { uiState.settings.isAdBlockerEnabled }
                    val isBlocked = uiState.isVideoPlaying ||
                            uiState.isFullscreenCustomView ||
                            uiState.fullscreenCustomView != null

                    swipeRefresh.isRefreshing = uiState.isLoading
                    swipeRefresh.isEnabled = !isBlocked

                    val brandColors = intArrayOf(
                        primaryColor,
                        android.graphics.Color.parseColor("#7BA7D7"),
                        android.graphics.Color.parseColor("#F6B99D"),
                        android.graphics.Color.parseColor("#D391A5")
                    )
                    swipeRefresh.setColorSchemeColors(*brandColors)
                    swipeRefresh.setProgressBackgroundColorSchemeColor(surfaceColor)

                    val activity = swipeRefresh.context.findActivity() ?: swipeRefresh.context
                    val currentWebView = (swipeRefresh.getChildAt(0) as? android.webkit.WebView)
                        ?: webViewPool.getOrCreateWebView(tabId, activity)
                    (currentWebView.context as? MutableContextWrapper)?.baseContext = activity
                    currentWebView.isFocusable = true
                    currentWebView.isFocusableInTouchMode = true
                    setupWebViewLongClick(currentWebView)

                    if (currentWebView.parent !== swipeRefresh) {
                        (currentWebView.parent as? ViewGroup)?.removeView(currentWebView)
                        swipeRefresh.addView(
                            currentWebView,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }

                    val currentNested = currentWebView as? NestedWebView
                    val currentLoadedUrl = currentNested?.loadedUrl
                    val isCurrentUnloaded = currentLoadedUrl.isNullOrEmpty() || currentLoadedUrl == "about:blank"
                    if (targetUrl.isNotBlank() && targetUrl != "about:blank") {
                        if (isCurrentUnloaded && (currentWebView.url.isNullOrEmpty() || currentWebView.url == "about:blank")) {
                            currentWebView.loadUrl(targetUrl)
                        }
                    }
                },
                onReset = { swipeRefresh ->
                    swipeRefresh.removeAllViews()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Web Loading Progress Bar with brand gradient and 0.3s smooth fade-out
        val animatedProgress by animateFloatAsState(
            targetValue = uiState.loadingProgress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
            label = "WebLoadingProgress"
        )

        AnimatedVisibility(
            visible = uiState.isLoading,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = animatedProgress)
                        .height(3.dp)
                        .background(ActiveTabBorderBrush)
                )
            }
        }
        }
    }
}
