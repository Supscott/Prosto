package com.jestk.browser.ui.views

import android.content.MutableContextWrapper
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.LinearProgressIndicator
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
        else -> 60.dp
    }
    val bottomPadding by animateDpAsState(
        targetValue = targetBottomPadding,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "WebViewBottomPadding"
    )

    val contentModifier = if (uiState.isFullscreenCustomView) {
        modifier.fillMaxSize()
    } else {
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = bottomPadding)
            .navigationBarsPadding()
            .imePadding()
            .nestedScroll(nestedScrollConnection)
    }

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
                        }
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
                        }
                    )

                    val nestedWebView = webView as? NestedWebView
                    val nestedLoadedUrl = nestedWebView?.loadedUrl
                    val isUnloaded = nestedLoadedUrl.isNullOrEmpty() || nestedLoadedUrl == "about:blank"
                    if (targetUrl.isNotBlank() && targetUrl != "about:blank") {
                        if (isUnloaded && (webView.url.isNullOrEmpty() || webView.url == "about:blank")) {
                            webView.loadUrl(targetUrl)
                        } else if (currentTab?.themeColor == null) {
                            val js = """
                                (function() {
                                    try {
                                        var meta = document.querySelector('meta[name="theme-color"]');
                                        if (meta && meta.content) return meta.content.trim();
                                        function getBg(el) {
                                            if (!el) return null;
                                            var style = window.getComputedStyle(el);
                                            var bg = style ? style.backgroundColor : null;
                                            if (bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'rgba(0,0,0,0)') return bg.trim();
                                            return null;
                                        }
                                        return getBg(document.body) || getBg(document.documentElement) || '';
                                    } catch(e) {
                                        return '';
                                    }
                                })()
                            """.trimIndent()
                            webView.evaluateJavascript(js) { raw ->
                                val color = JestkWebViewClient.parseCssColor(raw)
                                if (color != null) {
                                    currentOnEvent(BrowserUiEvent.OnThemeColorReceived(tabId, color))
                                }
                            }
                        }
                    }

                    JestkSwipeRefreshLayout(context).apply {
                        setColorSchemeColors(primaryColor)
                        setProgressBackgroundColorSchemeColor(surfaceColor)
                        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                        setOnRefreshListener {
                            currentOnEvent(BrowserUiEvent.Reload)
                        }
                        setOnChildScrollUpCallback { _, _ ->
                            val url = webView.url.orEmpty()
                            val isVideoPage = url.contains("youtube.com/watch") || url.contains("youtu.be/")
                            if (isVideoPage || currentUiState.isVideoPlaying) {
                                true
                            } else {
                                val nested = webView as? NestedWebView
                                if (nested != null && !nested.isPullToRefreshEligible()) {
                                    true
                                } else {
                                    webView.canScrollVertically(-1) || webView.scrollY > 0
                                }
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
                    swipeRefresh.isRefreshing = uiState.isLoading
                    swipeRefresh.setColorSchemeColors(primaryColor)
                    swipeRefresh.setProgressBackgroundColorSchemeColor(surfaceColor)

                    val activity = swipeRefresh.context.findActivity() ?: swipeRefresh.context
                    val currentWebView = (swipeRefresh.getChildAt(0) as? android.webkit.WebView)
                        ?: webViewPool.getOrCreateWebView(tabId, activity)
                    (currentWebView.context as? MutableContextWrapper)?.baseContext = activity
                    currentWebView.isFocusable = true
                    currentWebView.isFocusableInTouchMode = true

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

        // Web Loading Progress Bar at top
        AnimatedVisibility(
            visible = uiState.isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            LinearProgressIndicator(
                progress = { uiState.loadingProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }
    }
}
