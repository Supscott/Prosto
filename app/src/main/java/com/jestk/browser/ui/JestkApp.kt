package com.jestk.browser.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
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

@Composable
fun JestkApp(
    viewModel: BrowserViewModel,
    webViewPool: WebViewPool,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Handle back button press hierarchy
    BackHandler {
        viewModel.onEvent(BrowserUiEvent.HandleBackPress)
    }

    // Handle navigation commands targeting WebView
    LaunchedEffect(viewModel, uiState.activeTabId) {
        viewModel.navigationCommands.collectLatest { command ->
            val activeId = uiState.activeTabId ?: return@collectLatest
            when (command) {
                is NavigationCommand.LoadUrl -> {
                    val webView = webViewPool.getOrCreateWebView(activeId)
                    webView.loadUrl(command.url)
                }
                is NavigationCommand.GoBack -> {
                    webViewPool.getWebView(activeId)?.goBack()
                }
                is NavigationCommand.GoForward -> {
                    webViewPool.getWebView(activeId)?.goForward()
                }
                is NavigationCommand.Reload -> {
                    webViewPool.getWebView(activeId)?.reload()
                }
                is NavigationCommand.StopLoading -> {
                    webViewPool.getWebView(activeId)?.stopLoading()
                }
            }
        }
    }

    JestkBrowserTheme(themeMode = uiState.settings.themeMode) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Main Viewport: WebView Container or Home Screen
            val showHomeScreen = uiState.activeOverlay == OverlayType.HOME ||
                    uiState.currentUrl.isEmpty() ||
                    uiState.currentUrl == "about:blank"

            if (showHomeScreen) {
                HomeScreen(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                uiState.activeTabId?.let { activeId ->
                    WebViewContainer(
                        tabId = activeId,
                        webViewPool = webViewPool,
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Dual-Tier Bottom Navigation Bar (Tier 1 Tab Strip + Tier 2 Action Dock)
            if (!uiState.isFullscreenCustomView) {
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
