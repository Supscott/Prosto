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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.ui.navigation.UnifiedBottomBar
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
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jestk.browser.engine.JestkWebChromeClient
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
                is NavigationCommand.ExitFullscreen -> {
                    val activeId = viewModel.uiState.value.activeTabId
                    val webView = activeId?.let { webViewPool.getWebView(it) }
                    (webView?.webChromeClient as? JestkWebChromeClient)?.onHideCustomView()
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
        val isFullscreen = uiState.isFullscreenCustomView
        SideEffect {
            val activity = view.context.findActivity()
            val window = activity?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = isLightSite
                insetsController.isAppearanceLightNavigationBars = isLightSite
                if (isFullscreen) {
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val attrs = window.attributes
                        attrs.layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        window.attributes = attrs
                    }
                } else {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val attrs = window.attributes
                        attrs.layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                        window.attributes = attrs
                    }
                }
            }
        }
    }

    val isFullscreenVideo = uiState.isFullscreenCustomView
    val fullscreenOrientation = uiState.fullscreenRequestedOrientation
    LaunchedEffect(isFullscreenVideo, fullscreenOrientation) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        if (isFullscreenVideo) {
            val target = when (fullscreenOrientation) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            activity.requestedOrientation = target
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val activity = context.findActivity()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { window ->
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val attrs = window.attributes
                    attrs.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    window.attributes = attrs
                }
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

            // Outside scrim to close expanded menu when tapping on webpage
            if (uiState.isMenuExpanded && !showHomeScreen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            viewModel.onEvent(BrowserUiEvent.SetMenuExpanded(false))
                        }
                )
            }

            // Unified Single-Tier Bottom Navigation Bar
            if (!uiState.isFullscreenCustomView && !WindowInsets.isImeVisible) {
                UnifiedBottomBar(
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
                            customView.apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                keepScreenOn = true
                                isFocusable = true
                                isFocusableInTouchMode = true
                                requestFocus()
                            }
                        },
                        update = { view ->
                            view.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            view.keepScreenOn = true
                            view.isFocusable = true
                            view.isFocusableInTouchMode = true
                            view.requestFocus()
                        },
                        onReset = { view ->
                            (view.parent as? ViewGroup)?.removeView(view)
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
