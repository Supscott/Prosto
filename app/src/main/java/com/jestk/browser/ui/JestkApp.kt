package com.jestk.browser.ui

import android.app.Activity
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.ui.navigation.DualTierBottomBar
import com.jestk.browser.ui.overlays.SearchOverlay
import com.jestk.browser.ui.overlays.SettingsSheet
import com.jestk.browser.ui.overlays.TabOverviewGrid
import com.jestk.browser.ui.pip.FloatingVideoMiniPlayer
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserViewModel
import com.jestk.browser.ui.viewmodel.NavigationCommand
import com.jestk.browser.ui.views.HomeScreen
import com.jestk.browser.ui.views.WebViewContainer
import kotlinx.coroutines.flow.collectLatest

private fun android.content.Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JestkApp(
    viewModel: BrowserViewModel,
    webViewPool: WebViewPool,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Handle back button press hierarchy
    BackHandler {
        viewModel.onEvent(BrowserUiEvent.HandleBackPress)
    }

    // Automatically destroy closed WebViews when tabs are closed
    val currentTabIds = androidx.compose.runtime.remember(uiState.tabs) {
        uiState.tabs.map { it.id }.toSet()
    }
    LaunchedEffect(currentTabIds) {
        if (currentTabIds.isNotEmpty()) {
            webViewPool.retainTabs(currentTabIds)
        }
    }

    // Handle navigation commands targeting WebView & Application
    LaunchedEffect(viewModel) {
        viewModel.navigationCommands.collectLatest { command ->
            if (command is NavigationCommand.ExitApp) {
                context.findActivity()?.finish()
                return@collectLatest
            }
            val activeId = viewModel.uiState.value.activeTabId ?: return@collectLatest
            when (command) {
                is NavigationCommand.LoadUrl -> {
                    val webView = webViewPool.getOrCreateWebView(activeId, context)
                    webView.loadUrl(command.url)
                }
                is NavigationCommand.GoBack -> {
                    webViewPool.getWebView(activeId)?.goBack()
                }
                is NavigationCommand.GoForward -> {
                    webViewPool.getWebView(activeId)?.goForward()
                }
                is NavigationCommand.Reload -> {
                    val webView = webViewPool.getWebView(activeId) ?: webViewPool.getOrCreateWebView(activeId, context)
                    if (webView.url.isNullOrEmpty() || webView.url == "about:blank") {
                        val url = viewModel.uiState.value.currentUrl
                        if (url.isNotEmpty() && url != "about:blank") {
                            webView.loadUrl(url)
                        }
                    } else {
                        webView.reload()
                    }
                }
                is NavigationCommand.StopLoading -> {
                    webViewPool.getWebView(activeId)?.stopLoading()
                }
                is NavigationCommand.ExitApp -> Unit
            }
        }
    }

    val currentTab = uiState.tabs.find { it.id == uiState.activeTabId }
    val siteColor = uiState.activeThemeColor ?: currentTab?.themeColor
    val showHomeScreen = uiState.activeOverlay == OverlayType.HOME ||
            uiState.currentUrl.isEmpty() ||
            uiState.currentUrl == "about:blank"

    val defaultBg = MaterialTheme.colorScheme.background
    val targetAppBg = if (showHomeScreen || siteColor == null) defaultBg else Color(siteColor)
    val animatedAppBg by animateColorAsState(
        targetValue = targetAppBg,
        animationSpec = tween(durationMillis = 350),
        label = "JestkAppRootBgColor"
    )

    val view = LocalView.current
    val isLightSite = animatedAppBg.luminance() > 0.5f
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = isLightSite
                insetsController.isAppearanceLightNavigationBars = isLightSite
            }
        }
    }

    JestkBrowserTheme(themeMode = uiState.settings.themeMode) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(animatedAppBg)
        ) {
            // Main Viewport: WebView Container or Home Screen

            if (showHomeScreen) {
                HomeScreen(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                uiState.activeTabId?.let { activeId ->
                    key(activeId) {
                        WebViewContainer(
                            tabId = activeId,
                            webViewPool = webViewPool,
                            uiState = uiState,
                            onEvent = viewModel::onEvent,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Dual-Tier Bottom Navigation Bar (Tier 1 Tab Strip + Tier 2 Action Dock)
            if (!uiState.isFullscreenCustomView && !WindowInsets.isImeVisible) {
                DualTierBottomBar(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // Overlays
            when (uiState.activeOverlay) {
                OverlayType.SEARCH -> {
                    SearchOverlay(
                        uiState = uiState,
                        onEvent = viewModel::onEvent
                    )
                }
                OverlayType.TAB_GRID -> {
                    TabOverviewGrid(
                        uiState = uiState,
                        onEvent = viewModel::onEvent
                    )
                }
                OverlayType.SETTINGS -> {
                    SettingsSheet(
                        uiState = uiState,
                        onEvent = viewModel::onEvent
                    )
                }
                else -> Unit
            }

            // Fullscreen Custom View (HTML5 Video Fullscreen)
            val customView = uiState.fullscreenCustomView as? android.view.View
            if (uiState.isFullscreenCustomView && customView != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AndroidView<android.view.View>(
                        factory = {
                            (customView.parent as? ViewGroup)?.removeView(customView)
                            customView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // In-app Floating Draggable Video Mini-Player (when video plays in background)
            if (uiState.isVideoPlaying && customView != null && !uiState.isFullscreenCustomView) {
                FloatingVideoMiniPlayer(
                    customView = customView,
                    onClose = {
                        viewModel.onEvent(BrowserUiEvent.SetVideoPlaying(false))
                        viewModel.onEvent(BrowserUiEvent.SetFullscreenCustomView(null, false))
                    },
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
    }
}
