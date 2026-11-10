package com.jestk.browser.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.jestk.browser.engine.NestedWebView
import com.jestk.browser.engine.JestkWebViewClient
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.util.LocalVibrationFeedback
import com.jestk.browser.util.findActivity
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DynamicBarColors
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.ProstoAccent
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WebViewContainer(
    tabId: String,
    webViewPool: WebViewPool,
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    shouldInterceptForCleanPlayer: (String) -> Boolean = { false },
) {
    val isDark = LocalIsDarkTheme.current
    val vibration = LocalVibrationFeedback.current
    val currentUiState by rememberUpdatedState(uiState)
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentTab = remember(tabId, uiState.tabs) {
        uiState.tabs.find { it.id == tabId }
    }
    val targetUrl = currentTab?.url ?: (if (uiState.activeTabId == tabId) uiState.currentUrl else "")

    val scope = rememberCoroutineScope()
    val primaryColor = ProstoBrandPrimary.toArgb()
    val surfaceColor = (if (isDark) DarkSurface else Color.White).toArgb()

    val isImeVisible = WindowInsets.isImeVisible
    val isFullscreen = uiState.isFullscreenCustomView
    val targetBottomPadding = when {
        isFullscreen -> 0.dp
        isImeVisible -> 0.dp
        !uiState.isBottomBarVisible -> 0.dp
        else -> 62.dp
    }
    val animatedBottomPadding by animateDpAsState(
        targetValue = targetBottomPadding,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "WebViewBottomPadding"
    )
    val bottomPadding = if (isFullscreen) 0.dp else animatedBottomPadding

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

    val context = LocalContext.current
    val currentActiveTabId by rememberUpdatedState(tabId)

    val currentIsDark by rememberUpdatedState(isDark)
    val currentShouldInterceptForCleanPlayer by rememberUpdatedState(shouldInterceptForCleanPlayer)
    val clientDeps = remember(webViewPool) {
        WebViewClientDeps(
            webViewPool = webViewPool,
            uiState = { currentUiState },
            onEvent = { currentOnEvent(it) },
            isDark = { currentIsDark },
            shouldInterceptForCleanPlayer = { currentShouldInterceptForCleanPlayer(it) },
            scope = scope,
        )
    }

    val configureWebView: (WebView, String, Context) -> Unit = remember {
        { wv, forTabId, act ->
            (wv.context as? MutableContextWrapper)?.baseContext = act
            wv.isFocusable = true
            wv.isFocusableInTouchMode = true
            setupWebViewLongClick(wv)

            wv.setOnScrollChangeListener { v, _, scrollY, _, oldScrollY ->
                val dy = scrollY - oldScrollY
                val viewportHeight = if (v.height > 0) v.height else v.resources.displayMetrics.heightPixels
                val scrollThreshold = viewportHeight / 2
                val crossedThreshold = (oldScrollY > scrollThreshold && scrollY <= scrollThreshold) ||
                    (oldScrollY <= scrollThreshold && scrollY > scrollThreshold)
                if (kotlin.math.abs(dy) > 8 || scrollY <= 0 || crossedThreshold) {
                    currentOnEvent(
                        BrowserUiEvent.OnWebScroll(
                            deltaY = dy.toFloat(),
                            scrollY = scrollY,
                            viewportHeight = viewportHeight,
                        )
                    )
                }
            }

            if (wv.tag != forTabId || wv.webChromeClient == null) {
                bindWebViewClients(wv, forTabId, clientDeps)
            }
        }
    }

    val resolvedTopColor = uiState.activeTopThemeColor ?: currentTab?.topThemeColor
    val resolvedBottomColor = uiState.activeBottomThemeColor ?: currentTab?.bottomThemeColor
    val isHome = currentUiState.isHome
    val isHomeOrNeutral = isHome || uiState.isSettingsVisible || uiState.isDownloadsVisible

    val resolvedColors = DynamicBarColors.resolve(
        isDark = isDark,
        isHomeOrNeutralScreen = isHomeOrNeutral,
        topThemeColor = resolvedTopColor,
        bottomThemeColor = resolvedBottomColor,
    )

    val animatedTopBg by animateColorAsState(
        targetValue = resolvedColors.statusBarColor,
        animationSpec = tween(durationMillis = 350),
        label = "WebViewContainerTopBg"
    )
    val animatedBottomBg by animateColorAsState(
        targetValue = resolvedColors.underDockColor,
        animationSpec = tween(durationMillis = 350),
        label = "WebViewContainerBottomBg"
    )

    val contentModifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .padding(bottom = bottomPadding)
        .navigationBarsPadding()
        .imePadding()

    val siteBackgroundBrush = remember(animatedTopBg, animatedBottomBg) {
        Brush.verticalGradient(
            0.0f to animatedTopBg,
            0.5f to animatedTopBg,
            0.5f to animatedBottomBg,
            1.0f to animatedBottomBg,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(siteBackgroundBrush)
    ) {
        Box(
            modifier = contentModifier
        ) {
            AndroidView<SwipeRefreshLayout>(
                factory = { ctx ->
                    webViewPool.isAdBlockerEnabled = { currentUiState.settings.isAdBlockerEnabled }
                    webViewPool.isCleanPlayerEnabled = { currentUiState.settings.isCleanYouTubePlayerEnabled }
                    val act = ctx.findActivity() ?: ctx
                    val initialVisibility = if (isHome) View.INVISIBLE else View.VISIBLE
                    val tabsContainer = FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        visibility = initialVisibility
                    }

                    val brandColors = intArrayOf(
                        primaryColor,
                        ProstoAccent.toArgb(),
                        android.graphics.Color.parseColor("#93C5FD"),
                        android.graphics.Color.parseColor("#38BDF8")
                    )

                    val swipeRefresh = JestkSwipeRefreshLayout(ctx).apply {
                        setColorSchemeColors(*brandColors)
                        setProgressBackgroundColorSchemeColor(surfaceColor)
                        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                        setOnRefreshListener {
                            currentOnEvent(BrowserUiEvent.Reload)
                        }
                        onRefreshTriggerReached = {
                            vibration.confirm()
                        }
                        setOnChildScrollUpCallback { _, _ ->
                            val activeWv = webViewPool.getWebView(currentActiveTabId)
                                ?: (tabsContainer.getChildAt(tabsContainer.childCount - 1) as? WebView)
                            val nested = activeWv as? NestedWebView
                            val isBlocked = currentUiState.isLoading ||
                                    currentUiState.isVideoPlaying ||
                                    currentUiState.isFullscreenCustomView ||
                                    currentUiState.fullscreenCustomView != null

                            if (isBlocked) {
                                true
                            } else if (nested != null && !nested.isPullToRefreshEligible()) {
                                true
                            } else {
                                activeWv?.canScrollVertically(-1) == true || (activeWv?.scrollY ?: 0) > 0
                            }
                        }
                        addView(
                            tabsContainer,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }

                    val initialWorkspaceId = currentTab?.workspaceId ?: currentUiState.activeWorkspaceId
                    val initialProfileName = currentUiState.workspaces.find { it.id == initialWorkspaceId }?.profileName ?: initialWorkspaceId
                    val initialWebView = webViewPool.getOrCreateWebView(tabId, act, profileName = initialProfileName)
                    (initialWebView.parent as? ViewGroup)?.removeView(initialWebView)
                    configureWebView(initialWebView, tabId, act)
                    tabsContainer.addView(
                        initialWebView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    initialWebView.visibility = initialVisibility
                    initialWebView.safeResume()

                    val nestedWebView = initialWebView as? NestedWebView
                    val nestedLoadedUrl = nestedWebView?.loadedUrl
                    val isUnloaded = nestedLoadedUrl.isNullOrEmpty() || nestedLoadedUrl == "about:blank"
                    if (targetUrl.isNotBlank() && targetUrl != "about:blank") {
                        if (isUnloaded && (initialWebView.url.isNullOrEmpty() || initialWebView.url == "about:blank")) {
                            initialWebView.loadUrl(targetUrl)
                        } else if (currentTab?.themeColor == null) {
                            (initialWebView.webViewClient as? JestkWebViewClient)?.extractThemeColor(initialWebView)
                        }
                    }

                    swipeRefresh
                },
                update = { swipeRefresh ->
                    webViewPool.isAdBlockerEnabled = { uiState.settings.isAdBlockerEnabled }
                    webViewPool.isCleanPlayerEnabled = { uiState.settings.isCleanYouTubePlayerEnabled }
                    val isBlocked = uiState.isVideoPlaying ||
                            uiState.isFullscreenCustomView ||
                            uiState.fullscreenCustomView != null

                    swipeRefresh.isRefreshing = uiState.isLoading
                    swipeRefresh.isEnabled = !isBlocked && !isHome

                    val brandColors = intArrayOf(
                        primaryColor,
                        ProstoAccent.toArgb(),
                        android.graphics.Color.parseColor("#93C5FD"),
                        android.graphics.Color.parseColor("#38BDF8")
                    )
                    swipeRefresh.setColorSchemeColors(*brandColors)
                    swipeRefresh.setProgressBackgroundColorSchemeColor(surfaceColor)
                    (swipeRefresh as? JestkSwipeRefreshLayout)?.onRefreshTriggerReached = { vibration.confirm() }

                    val tabsContainer = (swipeRefresh.getChildAt(0) as? FrameLayout) ?: return@AndroidView
                    val targetVisibility = if (isHome) View.INVISIBLE else View.VISIBLE
                    if (tabsContainer.visibility != targetVisibility) {
                        tabsContainer.visibility = targetVisibility
                    }
                    val act = swipeRefresh.context.findActivity() ?: swipeRefresh.context
                    val updateTab = uiState.tabs.find { it.id == tabId }
                    val targetWorkspaceId = updateTab?.workspaceId ?: uiState.activeWorkspaceId
                    val targetProfileName = uiState.workspaces.find { it.id == targetWorkspaceId }?.profileName ?: targetWorkspaceId
                    val targetWebView = webViewPool.getOrCreateWebView(tabId, act, profileName = targetProfileName)

                    if (targetWebView.parent !== tabsContainer) {
                        (targetWebView.parent as? ViewGroup)?.removeView(targetWebView)
                        tabsContainer.addView(
                            targetWebView,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        configureWebView(targetWebView, tabId, act)
                        targetWebView.safeResume()
                    } else {
                        configureWebView(targetWebView, tabId, act)
                    }

                    // Hidden tabs are paused like DuckDuckGo's onTabHidden(); the shown tab resumes.
                    for (i in 0 until tabsContainer.childCount) {
                        val child = tabsContainer.getChildAt(i)
                        if (child === targetWebView) {
                            val childVis = if (isHome) View.INVISIBLE else View.VISIBLE
                            if (child.visibility == View.GONE) {
                                targetWebView.safeResume()
                            }
                            if (child.visibility != childVis) {
                                child.visibility = childVis
                            }
                            child.bringToFront()
                        } else if (child.visibility != View.GONE) {
                            child.visibility = View.GONE
                            (child as? WebView)?.safePause()
                        }
                    }

                    val pooledWebViews = uiState.tabs.mapNotNull { webViewPool.getWebView(it.id) }.toSet()
                    for (i in tabsContainer.childCount - 1 downTo 0) {
                        val child = tabsContainer.getChildAt(i) as? WebView
                        if (child != null && child !== targetWebView && !pooledWebViews.contains(child)) {
                            tabsContainer.removeViewAt(i)
                        }
                    }

                    val currentNested = targetWebView as? NestedWebView
                    val currentLoadedUrl = currentNested?.loadedUrl
                    val isCurrentUnloaded = currentLoadedUrl.isNullOrEmpty() || currentLoadedUrl == "about:blank"
                    if (targetUrl.isNotBlank() && targetUrl != "about:blank") {
                        if (isCurrentUnloaded && (targetWebView.url.isNullOrEmpty() || targetWebView.url == "about:blank")) {
                            targetWebView.loadUrl(targetUrl)
                        }
                    }
                },
                onReset = { swipeRefresh ->
                    (swipeRefresh as? JestkSwipeRefreshLayout)?.onRefreshTriggerReached = null
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun WebView.safePause() {
    try {
        onPause()
    } catch (t: Throwable) {
        android.util.Log.w("WebViewContainer", "Failed to pause WebView", t)
    }
}

private fun WebView.safeResume() {
    try {
        onResume()
    } catch (t: Exception) {
        android.util.Log.w("WebViewContainer", "Failed to resume WebView", t)
    }
}
