package com.jestk.browser.ui.views

import android.content.MutableContextWrapper
import android.os.Handler
import android.os.Looper
import android.os.Message
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
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

    // NestedScrollConnection to report scroll delta for dynamic auto-hide of bottom bar
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) {
                    onEvent(BrowserUiEvent.OnWebScroll(-available.y))
                }
                return Offset.Zero
            }
        }
    }

    val isImeVisible = WindowInsets.isImeVisible
    val targetBottomPadding = when {
        uiState.isFullscreenCustomView -> 0.dp
        isImeVisible -> 0.dp
        else -> 62.dp
    }
    val bottomPadding by animateDpAsState(
        targetValue = targetBottomPadding,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "WebViewBottomPadding"
    )

    val setupWebViewLongClick: (WebView) -> Unit = remember {
        { wv ->
            wv.isLongClickable = true
            wv.setOnLongClickListener { v ->
                val targetWebView = v as? WebView ?: return@setOnLongClickListener false
                val hitTest = targetWebView.hitTestResult ?: return@setOnLongClickListener false
                when (hitTest.type) {
                    WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                        val url = hitTest.extra
                        if (!url.isNullOrBlank()) {
                            currentOnEvent(BrowserUiEvent.ShowLinkContextMenu(url))
                            true
                        } else {
                            val handler = object : Handler(Looper.getMainLooper()) {
                                override fun handleMessage(msg: Message) {
                                    val nodeUrl = msg.data?.getString("url")
                                    if (!nodeUrl.isNullOrBlank()) {
                                        currentOnEvent(BrowserUiEvent.ShowLinkContextMenu(nodeUrl))
                                    }
                                }
                            }
                            targetWebView.requestFocusNodeHref(handler.obtainMessage())
                            true
                        }
                    }
                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                        val fallbackUrl = hitTest.extra
                        val handler = object : Handler(Looper.getMainLooper()) {
                            override fun handleMessage(msg: Message) {
                                val nodeUrl = msg.data?.getString("url")
                                val target = if (!nodeUrl.isNullOrBlank()) nodeUrl else fallbackUrl
                                if (!target.isNullOrBlank()) {
                                    currentOnEvent(BrowserUiEvent.ShowLinkContextMenu(target))
                                }
                            }
                        }
                        targetWebView.requestFocusNodeHref(handler.obtainMessage())
                        true
                    }
                    1 -> { // Deprecated HitTestResult.ANCHOR_TYPE
                        val url = hitTest.extra
                        if (!url.isNullOrBlank()) {
                            currentOnEvent(BrowserUiEvent.ShowLinkContextMenu(url))
                            true
                        } else false
                    }
                    6 -> { // Deprecated HitTestResult.IMAGE_ANCHOR_TYPE
                        val fallbackUrl = hitTest.extra
                        val handler = object : Handler(Looper.getMainLooper()) {
                            override fun handleMessage(msg: Message) {
                                val nodeUrl = msg.data?.getString("url")
                                val target = if (!nodeUrl.isNullOrBlank()) nodeUrl else fallbackUrl
                                if (!target.isNullOrBlank()) {
                                    currentOnEvent(BrowserUiEvent.ShowLinkContextMenu(target))
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
            .navigationBarsPadding()
            .imePadding()
            .nestedScroll(nestedScrollConnection)
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
                    val activity = context.findActivity() ?: context
                    val webView = webViewPool.getOrCreateWebView(tabId, activity)
                    // Enforce proper parent detachment when recycling WebViews in Compose
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    (webView.context as? MutableContextWrapper)?.baseContext = activity
                    webView.isFocusable = true
                    webView.isFocusableInTouchMode = true
                    setupWebViewLongClick(webView)

                    webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                        val dy = scrollY - oldScrollY
                        if (dy > 12) {
                            currentOnEvent(BrowserUiEvent.OnWebScroll(dy.toFloat()))
                        } else if (dy < -12 || scrollY <= 0) {
                            currentOnEvent(BrowserUiEvent.OnWebScroll(dy.toFloat()))
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
