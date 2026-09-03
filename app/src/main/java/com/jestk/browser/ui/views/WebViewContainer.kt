package com.jestk.browser.ui.views

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jestk.browser.engine.JestkWebChromeClient
import com.jestk.browser.engine.JestkWebViewClient
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.key
import com.jestk.browser.engine.FaviconManager

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun WebViewContainer(
    tabId: String,
    webViewPool: WebViewPool,
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading,
        onRefresh = { onEvent(BrowserUiEvent.Reload) }
    )

    // Dynamic bottom padding so web content is never obscured by the bottom bars
    val bottomBarPadding by animateDpAsState(
        targetValue = if (uiState.isBottomBarVisible) 128.dp else 62.dp,
        label = "BottomBarPadding"
    )

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = bottomBarPadding)
            .navigationBarsPadding()
            .pullRefresh(pullRefreshState)
            .nestedScroll(nestedScrollConnection)
    ) {
        key(tabId) {
            AndroidView(
                factory = { context ->
                    val webView = webViewPool.getOrCreateWebView(tabId)
                    // Enforce proper parent detachment when recycling WebViews in Compose
                    (webView.parent as? ViewGroup)?.removeView(webView)

                    webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                        val dy = scrollY - oldScrollY
                        if (dy > 12) {
                            onEvent(BrowserUiEvent.OnWebScroll(dy.toFloat()))
                        } else if (dy < -12 || scrollY <= 0) {
                            onEvent(BrowserUiEvent.OnWebScroll(dy.toFloat()))
                        }
                    }

                    webView.webViewClient = JestkWebViewClient(
                        tabId = tabId,
                        isAdBlockerEnabled = { uiState.settings.isAdBlockerEnabled },
                        onPageStarted = { id, url ->
                            onEvent(BrowserUiEvent.OnPageStarted(id, url))
                            FaviconManager.extractFallbackFaviconUrl(url)?.let {
                                onEvent(BrowserUiEvent.UpdateFaviconUrl(id, it))
                            }
                        },
                        onPageFinished = { id, url, title ->
                            onEvent(BrowserUiEvent.OnPageFinished(id, url, title))
                        },
                        onNavigationStateChanged = { id, canBack, canForward ->
                            onEvent(BrowserUiEvent.OnNavigationStateChanged(id, canBack, canForward))
                        },
                        onSnapshotReady = { id, path -> onEvent(BrowserUiEvent.OnSnapshotCaptured(id, path)) }
                    )

                    webView.webChromeClient = JestkWebChromeClient(
                        tabId = tabId,
                        onProgressChanged = { id, progress -> onEvent(BrowserUiEvent.OnProgressChanged(id, progress)) },
                        onTitleReceived = { _, _ -> },
                        onFullscreenCustomViewChanged = { view, isFullscreen ->
                            onEvent(BrowserUiEvent.SetFullscreenCustomView(view, isFullscreen))
                        },
                        onVideoPlayingStateChanged = { isPlaying ->
                            onEvent(BrowserUiEvent.SetVideoPlaying(isPlaying))
                        },
                        onFaviconReceived = { id, icon ->
                            val path = FaviconManager.saveFavicon(context, id, icon)
                            if (path != null) {
                                onEvent(BrowserUiEvent.UpdateFaviconUrl(id, path))
                            }
                        }
                    )

                    webView
                },
                update = { webView ->
                    // Ensure parent detachment safety
                    if (webView.parent != null && webView.parent !is ViewGroup) {
                        (webView.parent as? ViewGroup)?.removeView(webView)
                    }
                },
                onReset = { webView ->
                    (webView.parent as? ViewGroup)?.removeView(webView)
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

        // Pull to refresh indicator
        PullRefreshIndicator(
            refreshing = uiState.isLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
