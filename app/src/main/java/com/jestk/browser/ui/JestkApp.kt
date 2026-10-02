package com.jestk.browser.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jestk.browser.R
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.JestkWebChromeClient
import com.jestk.browser.engine.JestkWebViewClient
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.ui.navigation.UnifiedBottomBar
import com.jestk.browser.ui.overlays.AddFavoriteDialog
import com.jestk.browser.ui.overlays.ImageContextMenuModal
import com.jestk.browser.ui.overlays.LinkContextMenuModal
import com.jestk.browser.ui.overlays.SearchOverlay
import com.jestk.browser.ui.overlays.TabOverviewGrid
import com.jestk.browser.ui.screens.DownloadsScreen
import com.jestk.browser.ui.screens.SettingsScreen
import com.jestk.browser.ui.pip.FloatingVideoMiniPlayer
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.SereneBorders
import com.jestk.browser.ui.theme.SereneBrandPrimary
import com.jestk.browser.ui.theme.SereneCanvasFloor
import com.jestk.browser.ui.theme.SereneDarkText
import com.jestk.browser.ui.theme.SereneShapes
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserViewModel
import com.jestk.browser.ui.viewmodel.DismissDownloadSnackbar
import com.jestk.browser.ui.viewmodel.NavigationCommand
import com.jestk.browser.ui.views.HomeScreen
import com.jestk.browser.ui.views.WebViewContainer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = com.jestk.browser.ui.theme.isAppDarkTheme(uiState.settings.themeMode)

    // Handle back button press hierarchy
    BackHandler {
        viewModel.onEvent(BrowserUiEvent.HandleBackPress)
    }

    // Automatically destroy closed WebViews when tabs are closed
    val currentTabIds =
        androidx.compose.runtime.remember(uiState.tabs) {
            uiState.tabs.map { it.id }.toSet()
        }
    LaunchedEffect(currentTabIds) {
        if (currentTabIds.isNotEmpty()) {
            webViewPool.retainTabs(currentTabIds)
        }
    }

    // Connect WebViewPool download listener and web client initializer to ViewModel
    LaunchedEffect(webViewPool, viewModel) {
        webViewPool.onDownloadStarted = { item ->
            viewModel.onDownloadStarted(item)
        }
        webViewPool.onWebViewCreated = { tabId, wv ->
            if (wv.webChromeClient == null) {
                val handleFavicon: (String, android.graphics.Bitmap) -> Unit = { id, icon ->
                    coroutineScope.launch {
                        val path = FaviconManager.saveFavicon(context, id, icon)
                        if (path != null) {
                            viewModel.onEvent(BrowserUiEvent.UpdateFaviconUrl(id, path))
                        }
                    }
                }
                wv.webViewClient = JestkWebViewClient(
                    tabId = tabId,
                    isAdBlockerEnabled = { viewModel.uiState.value.settings.isAdBlockerEnabled },
                    onPageStarted = { id, url -> viewModel.onEvent(BrowserUiEvent.OnPageStarted(id, url)) },
                    onPageFinished = { id, url, title -> viewModel.onEvent(BrowserUiEvent.OnPageFinished(id, url, title)) },
                    onNavigationStateChanged = { id, canBack, canForward -> viewModel.onEvent(BrowserUiEvent.OnNavigationStateChanged(id, canBack, canForward)) },
                    onSnapshotReady = { id, path -> viewModel.onEvent(BrowserUiEvent.OnSnapshotCaptured(id, path)) },
                    onThemeColorDetected = { id, color -> viewModel.onEvent(BrowserUiEvent.OnThemeColorReceived(id, color)) },
                    isDarkTheme = { isDark },
                    onFaviconReceived = handleFavicon
                )
                wv.webChromeClient = JestkWebChromeClient(
                    tabId = tabId,
                    webView = wv,
                    onProgressChanged = { id, progress -> viewModel.onEvent(BrowserUiEvent.OnProgressChanged(id, progress)) },
                    onTitleReceived = { _, _ -> },
                    onFullscreenCustomViewChanged = { view, isFullscreen, orientation ->
                        viewModel.onEvent(BrowserUiEvent.SetFullscreenCustomView(view, isFullscreen, orientation))
                    },
                    onVideoPlayingStateChanged = { isPlaying ->
                        viewModel.onEvent(BrowserUiEvent.SetVideoPlaying(isPlaying))
                    },
                    onFaviconReceived = handleFavicon
                )
            }
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
                    val webView = webViewPool.getWebView(activeId) ?: webViewPool.getOrCreateWebView(activeId, context)
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
                    try {
                        webView?.evaluateJavascript(
                            "(function(){ if (document.exitFullscreen) document.exitFullscreen(); else if (document.webkitExitFullscreen) document.webkitExitFullscreen(); })()",
                            null,
                        )
                    } catch (_: Exception) {
                    }
                }

                is NavigationCommand.ExitApp -> {
                    Unit
                }
            }
        }
    }

    JestkBrowserTheme(
        themeMode = uiState.settings.themeMode,
    ) {
        val isDark = LocalIsDarkTheme.current
        val currentContext = LocalContext.current
        LaunchedEffect(isDark) {
            webViewPool.updateThemeMode(isDark, currentContext)
        }

        val currentTab = uiState.tabs.find { it.id == uiState.activeTabId }
        val siteColor = uiState.activeThemeColor ?: currentTab?.themeColor
        val showHomeScreen =
            uiState.activeOverlay == OverlayType.HOME ||
                uiState.currentUrl.isEmpty() ||
                uiState.currentUrl == "about:blank"

        val defaultBg = if (isDark) DarkBackground else SereneCanvasFloor
        val targetAppBg =
            if (showHomeScreen || siteColor == null || uiState.isSettingsVisible || uiState.isDownloadsVisible) {
                defaultBg
            } else {
                Color(siteColor)
            }
        val animatedAppBg by animateColorAsState(
            targetValue = targetAppBg,
            animationSpec = tween(durationMillis = 350),
            label = "JestkAppRootBgColor",
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

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                        window.isStatusBarContrastEnforced = false
                    }
                    @Suppress("DEPRECATION")
                    window.statusBarColor = animatedAppBg.toArgb()
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = animatedAppBg.toArgb()
                }
            }

            LaunchedEffect(isFullscreen) {
                val activity = view.context.findActivity() ?: return@LaunchedEffect
                val window = activity.window ?: return@LaunchedEffect
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
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

        val isFullscreenVideo = uiState.isFullscreenCustomView
        val requestedOrientation = uiState.fullscreenRequestedOrientation
        LaunchedEffect(isFullscreenVideo, requestedOrientation) {
            val activity = context.findActivity() ?: return@LaunchedEffect
            if (isFullscreenVideo) {
                if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                    activity.requestedOrientation = requestedOrientation
                }
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

        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(animatedAppBg),
        ) {
            // Main Viewport: WebView Container or Home Screen (NO BLUR)
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                if (showHomeScreen) {
                    HomeScreen(
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    uiState.activeTabId?.let { activeId ->
                        key(activeId) {
                            WebViewContainer(
                                tabId = activeId,
                                webViewPool = webViewPool,
                                uiState = uiState,
                                onEvent = viewModel::onEvent,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            // Outside dim scrim to close expanded menu when tapping on webpage
            if (uiState.isMenuExpanded) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                viewModel.onEvent(BrowserUiEvent.SetMenuExpanded(false))
                            },
                )
            }

            // Unified Single-Tier Bottom Navigation Bar
            val isBottomBarVisible = uiState.isBottomBarVisible &&
                !uiState.isFullscreenCustomView &&
                !WindowInsets.isImeVisible &&
                !uiState.isSettingsVisible &&
                !uiState.isDownloadsVisible

            AnimatedVisibility(
                visible = isBottomBarVisible,
                enter =
                    fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
                exit =
                    fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                        slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                UnifiedBottomBar(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                )
            }

            // Overlays with smooth animated transitions
            AnimatedVisibility(
                visible = uiState.activeOverlay == OverlayType.SEARCH,
                enter =
                    fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(250, easing = FastOutSlowInEasing)) { it / 3 },
                exit =
                    fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                        slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it / 3 },
            ) {
                SearchOverlay(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                )
            }

            AnimatedVisibility(
                visible = uiState.activeOverlay == OverlayType.TAB_GRID,
                enter =
                    fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(250, easing = FastOutSlowInEasing)) { it / 4 },
                exit =
                    fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                        slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it / 4 },
            ) {
                TabOverviewGrid(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                )
            }

            // Dedicated full screens with smooth animated transitions
            AnimatedVisibility(
                visible = uiState.isSettingsVisible,
                enter =
                    fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(250, easing = FastOutSlowInEasing)) { it / 4 },
                exit =
                    fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                        slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it / 4 },
            ) {
                SettingsScreen(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AnimatedVisibility(
                visible = uiState.isDownloadsVisible,
                enter =
                    fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(250, easing = FastOutSlowInEasing)) { it / 4 },
                exit =
                    fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                        slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it / 4 },
            ) {
                DownloadsScreen(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            val snackbarBaseBottomOffset by animateDpAsState(
                targetValue = if (isBottomBarVisible) 76.dp else 16.dp,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "SnackbarBaseBottomOffset",
            )

            // Undo Closed Tab Floating Notification Pill
            AnimatedVisibility(
                visible = uiState.showUndoTabCloseSnackbar && uiState.recentlyClosedTab != null,
                enter =
                    fadeIn(tween(220, easing = FastOutSlowInEasing)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
                exit =
                    fadeOut(tween(180, easing = FastOutSlowInEasing)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = snackbarBaseBottomOffset, start = 16.dp, end = 16.dp),
            ) {
                val tab = uiState.recentlyClosedTab
                val tabTitle = tab?.title?.takeIf { it.isNotBlank() && it != "Home" && it != "New Tab" } ?: "Tab"
                val undoText = stringResource(R.string.undo)
                val closedText = stringResource(R.string.tab_closed)

                LaunchedEffect(uiState.showUndoTabCloseSnackbar, tab?.id) {
                    if (uiState.showUndoTabCloseSnackbar) {
                        kotlinx.coroutines.delay(4500)
                        viewModel.onEvent(BrowserUiEvent.DismissUndoTabCloseSnackbar)
                    }
                }

                Surface(
                    shape = SereneShapes.Pill,
                    color = if (isDark) DarkSurface else Color.White,
                    border = SereneBorders.floatingBorder(isDark),
                    shadowElevation = 8.dp,
                    modifier = Modifier.wrapContentSize(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "$closedText: $tabTitle",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = if (isDark) DarkOnSurface else SereneDarkText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = undoText,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = SereneBrandPrimary),
                            modifier =
                                Modifier
                                    .clickable {
                                        viewModel.onEvent(BrowserUiEvent.UndoCloseTab)
                                    }.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier =
                                Modifier
                                    .size(16.dp)
                                    .clickable {
                                        viewModel.onEvent(BrowserUiEvent.DismissUndoTabCloseSnackbar)
                                    },
                        )
                    }
                }
            }

            // Download Floating Notification Pill
            AnimatedVisibility(
                visible = uiState.showDownloadSnackbar && uiState.lastDownloadNotification != null,
                enter =
                    fadeIn(tween(220, easing = FastOutSlowInEasing)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
                exit =
                    fadeOut(tween(180, easing = FastOutSlowInEasing)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(
                            bottom = if (uiState.showUndoTabCloseSnackbar && uiState.recentlyClosedTab != null) {
                                snackbarBaseBottomOffset + 56.dp
                            } else {
                                snackbarBaseBottomOffset
                            },
                            start = 16.dp,
                            end = 16.dp,
                        ),
            ) {
                val download = uiState.lastDownloadNotification
                val fileName = download?.fileName?.ifEmpty { "file" } ?: "file"

                LaunchedEffect(uiState.showDownloadSnackbar, download?.id, download?.timestamp) {
                    if (uiState.showDownloadSnackbar) {
                        kotlinx.coroutines.delay(5000)
                        viewModel.onEvent(DismissDownloadSnackbar)
                    }
                }

                Surface(
                    shape = SereneShapes.Pill,
                    color = if (isDark) DarkSurface else Color.White,
                    border = SereneBorders.floatingBorder(isDark),
                    shadowElevation = 8.dp,
                    modifier = Modifier.wrapContentSize(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = SereneBrandPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        val isCompleted = download?.status == com.jestk.browser.domain.model.DownloadStatus.COMPLETED
                        Text(
                            text = if (isCompleted) "Downloaded $fileName" else "Downloading $fileName",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = if (isDark) DarkOnSurface else SereneDarkText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = if (isCompleted) "Open" else "Details",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = SereneBrandPrimary),
                            modifier =
                                Modifier
                                    .clickable {
                                        viewModel.onEvent(BrowserUiEvent.OpenDownloads)
                                        viewModel.onEvent(DismissDownloadSnackbar)
                                    }.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier =
                                Modifier
                                    .size(16.dp)
                                    .clickable {
                                        viewModel.onEvent(DismissDownloadSnackbar)
                                    },
                        )
                    }
                }
            }

            // Link Context Menu Floating Modal
            uiState.linkContextMenuUrl?.let { linkUrl ->
                LinkContextMenuModal(
                    url = linkUrl,
                    anchor = uiState.contextMenuAnchor,
                    onEvent = viewModel::onEvent,
                )
            }

            // Image Context Menu Floating Modal
            uiState.selectedImageUrl?.let { imageUrl ->
                ImageContextMenuModal(
                    imageUrl = imageUrl,
                    linkUrl = uiState.imageContextMenuLinkUrl,
                    anchor = uiState.contextMenuAnchor,
                    onEvent = viewModel::onEvent,
                    onDownloadImage = { url ->
                        webViewPool.handleDownload(
                            url = url,
                            userAgent = null,
                            contentDisposition = null,
                            mimeType = "image/*",
                            contentLength = 0L,
                            context = context,
                        )
                    },
                )
            }

            // Add Favorite Modal Dialog
            if (uiState.showAddFavoriteDialog) {
                AddFavoriteDialog(
                    isDark = isDark,
                    onDismiss = { viewModel.onEvent(BrowserUiEvent.DismissAddFavoriteDialog) },
                    onConfirm = { title, url ->
                        viewModel.onEvent(BrowserUiEvent.AddFavorite(title, url))
                        viewModel.onEvent(BrowserUiEvent.DismissAddFavoriteDialog)
                    },
                )
            }

            // Fullscreen Custom View (HTML5 Video Fullscreen)
            val customView = uiState.fullscreenCustomView as? android.view.View
            if (uiState.isFullscreenCustomView && customView != null) {
                val density = LocalDensity.current
                val swipeThresholdPx = remember(density) { with(density) { 70.dp.toPx() } }
                val velocityThresholdPx = remember(density) { with(density) { 1000.dp.toPx() } }
                val minDragDistancePx = remember(density) { with(density) { 20.dp.toPx() } }

                LaunchedEffect(customView) {
                    val activeId = uiState.activeTabId
                    val webView = activeId?.let { webViewPool.getWebView(it) }
                    (webView?.webChromeClient as? JestkWebChromeClient)?.resumeVideoPlayback()
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                                    val startY = down.position.y
                                    val startX = down.position.x
                                    var totalDeltaY = 0f
                                    var totalDeltaX = 0f
                                    var hasTriggered = false
                                    val velocityTracker = VelocityTracker()
                                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                                    while (true) {
                                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                        val change =
                                            event.changes.firstOrNull { it.id == down.id }
                                                ?: event.changes.firstOrNull()
                                                ?: break

                                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                                        if (change.changedToUp() || !change.pressed) {
                                            if (!hasTriggered && totalDeltaY > minDragDistancePx &&
                                                totalDeltaY > kotlin.math.abs(totalDeltaX) * 1.2f
                                            ) {
                                                val velocityY = velocityTracker.calculateVelocity().y
                                                if (totalDeltaY > swipeThresholdPx || velocityY > velocityThresholdPx) {
                                                    hasTriggered = true
                                                    viewModel.onEvent(BrowserUiEvent.ExitFullscreen)
                                                }
                                            }
                                            break
                                        }

                                        val currentPos = change.position
                                        totalDeltaY = currentPos.y - startY
                                        totalDeltaX = currentPos.x - startX

                                        if (!hasTriggered && totalDeltaY > swipeThresholdPx &&
                                            totalDeltaY > kotlin.math.abs(totalDeltaX) * 1.2f
                                        ) {
                                            hasTriggered = true
                                            change.consume()
                                            viewModel.onEvent(BrowserUiEvent.ExitFullscreen)
                                            break
                                        }
                                    }
                                }
                            },
                ) {
                    AndroidView<android.view.View>(
                        factory = {
                            (customView.parent as? ViewGroup)?.removeView(customView)
                            customView.apply {
                                layoutParams =
                                    ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                keepScreenOn = true
                                if (this is ViewGroup) {
                                    descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                                }
                                if (!hasFocus()) {
                                    requestFocus()
                                }
                            }
                        },
                        update = { view ->
                            view.layoutParams =
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                            view.keepScreenOn = true
                        },
                        onReset = { view ->
                            (view.parent as? ViewGroup)?.removeView(view)
                        },
                        modifier = Modifier.fillMaxSize(),
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
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
    }
}
