package com.jestk.browser.ui

import android.content.pm.ActivityInfo
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
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
import com.jestk.browser.domain.model.isVisible
import com.jestk.browser.engine.JestkWebChromeClient
import com.jestk.browser.engine.JestkWebViewClient
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.ui.navigation.UnifiedBottomBar
import com.jestk.browser.ui.overlays.AddEditSearchEngineDialog
import com.jestk.browser.ui.overlays.AddFavoriteDialog
import com.jestk.browser.ui.overlays.CleanVideoPlayerOverlay
import com.jestk.browser.ui.overlays.FeedbackBottomSheet
import com.jestk.browser.ui.overlays.FolderEditBottomSheet
import com.jestk.browser.ui.overlays.FullscreenVideoLayer
import com.jestk.browser.ui.overlays.BlockedAdNavigationBanner
import com.jestk.browser.ui.overlays.InsecureHttpWarningDialog
import com.jestk.browser.ui.overlays.ExternalAppNotificationPill
import com.jestk.browser.ui.overlays.ImageContextMenuModal
import com.jestk.browser.ui.overlays.LinkContextMenuModal
import com.jestk.browser.ui.overlays.SearchOverlay
import com.jestk.browser.ui.overlays.TabOverviewGrid
import com.jestk.browser.ui.overlays.UpdateNotificationCard
import com.jestk.browser.ui.overlays.WORKSPACE_CARD_CORNER_RADIUS_DP
import com.jestk.browser.ui.overlays.WorkspacesOverviewScreen
import com.jestk.browser.ui.overlays.rememberWorkspaceSwitcherState
import com.jestk.browser.ui.overlays.workspaceSwitcherLiveContent
import com.jestk.browser.ui.screens.DownloadsScreen
import com.jestk.browser.ui.screens.SettingsScreen
import com.jestk.browser.ui.pip.FloatingVideoMiniPlayer
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DynamicBarColors
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserViewModel
import com.jestk.browser.ui.viewmodel.ExternalAppDismissReason
import com.jestk.browser.ui.viewmodel.NavigationCommand
import com.jestk.browser.ui.views.HomeScreen
import com.jestk.browser.ui.views.WebViewClientDeps
import com.jestk.browser.ui.views.WebViewContainer
import com.jestk.browser.ui.views.bindWebViewClients
import com.jestk.browser.ui.views.openCleanPlayerIfIntercepted
import com.jestk.browser.util.DownloadFileOpener
import com.jestk.browser.util.YouTubeUrlHelper
import com.jestk.browser.util.findActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
    val currentIsDark by androidx.compose.runtime.rememberUpdatedState(isDark)
    val clientDeps = remember(webViewPool, viewModel) {
        WebViewClientDeps(
            webViewPool = webViewPool,
            uiState = { viewModel.uiState.value },
            onEvent = viewModel::onEvent,
            isDark = { currentIsDark },
            shouldInterceptForCleanPlayer = viewModel::shouldInterceptForCleanPlayer,
            scope = coroutineScope,
        )
    }

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
        webViewPool.isAdBlockerEnabled = { viewModel.uiState.value.settings.isAdBlockerEnabled }
        webViewPool.isCleanPlayerEnabled = { viewModel.uiState.value.settings.isCleanYouTubePlayerEnabled }
        webViewPool.onCleanPlayerRequested = { url -> openCleanPlayerIfIntercepted(url, clientDeps) }
        webViewPool.onWebViewCreated = { tabId, wv ->
            if (wv.webChromeClient == null) {
                bindWebViewClients(wv, tabId, clientDeps)
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
            val currentTab = viewModel.uiState.value.allTabs.find { it.id == activeId } ?: viewModel.uiState.value.tabs.find { it.id == activeId }
            val activeWorkspaceId = currentTab?.workspaceId ?: viewModel.uiState.value.activeWorkspaceId
            val profileName = viewModel.uiState.value.workspaces.find { it.id == activeWorkspaceId }?.profileName ?: activeWorkspaceId
            when (command) {
                is NavigationCommand.LoadUrl -> {
                    val webView = webViewPool.getWebView(activeId) ?: webViewPool.getOrCreateWebView(activeId, context, profileName = profileName)
                    com.jestk.browser.engine.JestkWebViewClient.loadUrlOrHandleExternal(webView, command.url)
                }

                is NavigationCommand.GoBack -> {
                    webViewPool.getWebView(activeId)?.goBack()
                }

                is NavigationCommand.GoForward -> {
                    webViewPool.getWebView(activeId)?.goForward()
                }

                is NavigationCommand.Reload -> {
                    val webView = webViewPool.getWebView(activeId) ?: webViewPool.getOrCreateWebView(activeId, context, profileName = profileName)
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

    val activeWorkspaceColor = uiState.activeWorkspace?.color?.let { Color(it) }

    JestkBrowserTheme(
        themeMode = uiState.settings.themeMode,
        workspaceColor = activeWorkspaceColor,
    ) {
        val isDark = LocalIsDarkTheme.current
        LaunchedEffect(isDark) {
            webViewPool.updateThemeMode(isDark)
        }

        val currentTab = uiState.tabs.find { it.id == uiState.activeTabId }
        val siteTopColor = uiState.activeTopThemeColor ?: currentTab?.topThemeColor
        val siteBottomColor = uiState.activeBottomThemeColor ?: currentTab?.bottomThemeColor
        val showHomeScreen = uiState.isHome
        val isHomeOrNeutral = showHomeScreen || uiState.isSettingsVisible || uiState.isDownloadsVisible

        val resolvedColors = DynamicBarColors.resolve(
            isDark = isDark,
            isHomeOrNeutralScreen = isHomeOrNeutral,
            topThemeColor = siteTopColor,
            bottomThemeColor = siteBottomColor,
        )

        val animatedStatusBarBg by animateColorAsState(
            targetValue = resolvedColors.statusBarColor,
            animationSpec = tween(durationMillis = 350),
            label = "JestkAppStatusBarBgColor",
        )
        val animatedBottomBarBg by animateColorAsState(
            targetValue = resolvedColors.underDockColor,
            animationSpec = tween(durationMillis = 350),
            label = "JestkAppBottomBarBgColor",
        )

        val view = LocalView.current
        val isLightStatus = animatedStatusBarBg.luminance() > 0.5f
        val isLightNav = animatedBottomBarBg.luminance() > 0.5f
        if (!view.isInEditMode) {
            val isFullscreen = uiState.isFullscreenCustomView
            SideEffect {
                val activity = view.context.findActivity()
                val window = activity?.window
                if (window != null) {
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = isLightStatus
                    insetsController.isAppearanceLightNavigationBars = isLightNav

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                    @Suppress("DEPRECATION")
                    window.statusBarColor = animatedStatusBarBg.toArgb()
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = animatedBottomBarBg.toArgb()
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
                } else {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
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
                }
            }
        }

        val workspaceSwitcher = rememberWorkspaceSwitcherState()
        LaunchedEffect(uiState.isWorkspacesOverviewVisible) {
            // Any close path (switch, back, VM-driven) leaves the live app untransformed.
            if (!uiState.isWorkspacesOverviewVisible && !workspaceSwitcher.isDragging) {
                workspaceSwitcher.reset()
            }
        }
        val switcherCornerRadiusPx = with(LocalDensity.current) { WORKSPACE_CARD_CORNER_RADIUS_DP.dp.toPx() }
        val switcherShadowPx = with(LocalDensity.current) { 8.dp.toPx() }

        val handleUiEvent: (BrowserUiEvent) -> Unit = viewModel::onEvent

        val appBackgroundBrush =
            remember(animatedStatusBarBg, animatedBottomBarBg) {
                Brush.verticalGradient(
                    0.0f to animatedStatusBarBg,
                    0.5f to animatedStatusBarBg,
                    0.5f to animatedBottomBarBg,
                    1.0f to animatedBottomBarBg,
                )
            }

        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(appBackgroundBrush)
                    .onSizeChanged { workspaceSwitcher.rootSize = it },
        ) {
            // Workspace switcher backdrop: revealed around the live app as it shrinks into a card.
            if (uiState.isWorkspacesOverviewVisible) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = workspaceSwitcher.progress.value.coerceIn(0f, 1f) }
                            .background(if (isDark) DarkBackground else ProstoCanvasFloor),
                )
            }

            // Main Viewport: WebView Container and Home Screen (NO BLUR).
            // It is the active workspace card itself while the workspace switcher is open.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .workspaceSwitcherLiveContent(
                            state = workspaceSwitcher,
                            cornerRadiusPx = switcherCornerRadiusPx,
                            shadowElevationPx = switcherShadowPx,
                        )
                        // Paints the status bar strip inside the card when the app is shrunk.
                        .background(appBackgroundBrush),
            ) {
                uiState.activeTabId?.let { activeId ->
                    WebViewContainer(
                        tabId = activeId,
                        webViewPool = webViewPool,
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        shouldInterceptForCleanPlayer = viewModel::shouldInterceptForCleanPlayer,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (showHomeScreen) {
                    HomeScreen(
                        uiState = uiState,
                        onEvent = handleUiEvent,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {},
                    )
                }
            }

            // Outside dim scrim to close expanded menu or edit popup when tapping on webpage
            if (uiState.isMenuExpanded || uiState.isSearchEngineSwitcherEditExpanded) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (uiState.isMenuExpanded) viewModel.onEvent(BrowserUiEvent.SetMenuExpanded(false))
                                if (uiState.isSearchEngineSwitcherEditExpanded) viewModel.onEvent(BrowserUiEvent.SetSearchEngineSwitcherEditExpanded(false))
                            },
                )
            }

            // Unified Single-Tier Bottom Navigation Bar
            val isBottomBarVisible = uiState.isBottomBarVisible &&
                !uiState.isFullscreenCustomView &&
                !WindowInsets.isImeVisible &&
                !uiState.isSettingsVisible &&
                !uiState.isDownloadsVisible

            val bottomBarSlideOffset by animateDpAsState(
                targetValue = if (isBottomBarVisible) 0.dp else 120.dp,
                animationSpec = tween(durationMillis = if (isBottomBarVisible) 260 else 220, easing = FastOutSlowInEasing),
                label = "BottomBarSlide",
            )
            val bottomBarAlpha by animateFloatAsState(
                targetValue = if (isBottomBarVisible) 1f else 0f,
                animationSpec = tween(durationMillis = if (isBottomBarVisible) 220 else 180, easing = FastOutSlowInEasing),
                label = "BottomBarAlpha",
            )
            val effectiveBottomBarSlide = if (uiState.isFullscreenCustomView) 120.dp else bottomBarSlideOffset
            val effectiveBottomBarAlpha = if (uiState.isFullscreenCustomView) 0f else bottomBarAlpha

            if (effectiveBottomBarAlpha > 0f) {
                UnifiedBottomBar(
                    uiState = uiState,
                    onEvent = handleUiEvent,
                    workspaceSwitcher = workspaceSwitcher,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            translationY = effectiveBottomBarSlide.toPx()
                            // The dock fades out quickly while the screen shrinks into the carousel.
                            val switcherFade = (1f - workspaceSwitcher.progress.value * 3f).coerceIn(0f, 1f)
                            alpha = effectiveBottomBarAlpha * switcherFade
                        },
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

            // Enter is driven by the switcher progress (the live app shrinks into the carousel);
            // the short exit fade only hides the hand-off after a workspace switch.
            AnimatedVisibility(
                visible = uiState.isWorkspacesOverviewVisible,
                enter = EnterTransition.None,
                exit = fadeOut(tween(180, easing = FastOutSlowInEasing)),
            ) {
                WorkspacesOverviewScreen(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    switcher = workspaceSwitcher,
                    modifier = Modifier.fillMaxSize(),
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

            val cleanVideoId = uiState.cleanPlayerVideoId
            if (cleanVideoId != null) {
                CleanVideoPlayerOverlay(
                    videoId = cleanVideoId,
                    originalUrl = uiState.cleanPlayerOriginalUrl ?: YouTubeUrlHelper.buildOriginalWatchUrl(cleanVideoId),
                    startSeconds = uiState.cleanPlayerStartSeconds,
                    onClose = { viewModel.onEvent(BrowserUiEvent.CloseCleanPlayer) },
                    onOpenOnWeb = { url -> viewModel.onEvent(BrowserUiEvent.OpenCleanPlayerOnWeb(url)) },
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
                    shape = ProstoShapes.Pill,
                    color = if (isDark) DarkSurface else Color.White,
                    border = ProstoBorders.floatingBorder(isDark, workspaceColor = activeWorkspaceColor),
                    shadowElevation = 8.dp,
                    modifier = Modifier.wrapContentSize(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val closedMessage = "$closedText: $tabTitle"
                        Text(
                            text = closedMessage,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = if (isDark) DarkOnSurface else ProstoDarkText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        val undoAccent = activeWorkspaceColor ?: ProstoBrandPrimary
                        Text(
                            text = undoText,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = undoAccent),
                            modifier =
                                Modifier
                                    .clickable {
                                        viewModel.onEvent(BrowserUiEvent.UndoCloseTab)
                                    }.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
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
                val defaultFileName = stringResource(R.string.download_default_filename)
                val fileName = download?.fileName?.ifEmpty { defaultFileName } ?: defaultFileName

                LaunchedEffect(uiState.showDownloadSnackbar, download?.id, download?.timestamp) {
                    if (uiState.showDownloadSnackbar) {
                        kotlinx.coroutines.delay(5000)
                        viewModel.onEvent(BrowserUiEvent.DismissDownloadSnackbar)
                    }
                }

                val downloadAccent = activeWorkspaceColor ?: ProstoBrandPrimary
                Surface(
                    shape = ProstoShapes.Pill,
                    color = if (isDark) DarkSurface else Color.White,
                    border = ProstoBorders.floatingBorder(isDark, workspaceColor = activeWorkspaceColor),
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
                            contentDescription = null,
                            tint = downloadAccent,
                            modifier = Modifier.size(18.dp),
                        )
                        val isCompleted = download?.status == com.jestk.browser.domain.model.DownloadStatus.COMPLETED
                        Text(
                            text = if (isCompleted) {
                                stringResource(R.string.download_completed_notification, fileName)
                            } else {
                                stringResource(R.string.download_in_progress_notification, fileName)
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = if (isDark) DarkOnSurface else ProstoDarkText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = if (isCompleted) {
                                stringResource(R.string.download_action_open)
                            } else {
                                stringResource(R.string.download_action_details)
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = downloadAccent),
                            modifier =
                                Modifier
                                    .clickable {
                                        if (isCompleted) {
                                            download?.let { DownloadFileOpener.openDownloadedFile(context, it) }
                                        } else {
                                            viewModel.onEvent(BrowserUiEvent.OpenDownloads)
                                        }
                                        viewModel.onEvent(BrowserUiEvent.DismissDownloadSnackbar)
                                    }.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier =
                                Modifier
                                    .size(16.dp)
                                    .clickable {
                                        viewModel.onEvent(BrowserUiEvent.DismissDownloadSnackbar)
                                    },
                        )
                    }
                }
            }

            // External App Floating Notification Pill
            AnimatedVisibility(
                visible = uiState.externalAppPrompt != null,
                enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
                exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        bottom = snackbarBaseBottomOffset +
                            (if (uiState.showUndoTabCloseSnackbar && uiState.recentlyClosedTab != null) 56.dp else 0.dp) +
                            (if (uiState.showDownloadSnackbar && uiState.lastDownloadNotification != null) 56.dp else 0.dp),
                        start = 16.dp,
                        end = 16.dp
                    ),
            ) {
                uiState.externalAppPrompt?.let { prompt ->
                    ExternalAppNotificationPill(
                        prompt = prompt,
                        onOpen = {
                            viewModel.onEvent(BrowserUiEvent.OpenExternalApp(prompt))
                            try {
                                val intent = android.content.Intent.parseUri(prompt.intentUri, android.content.Intent.URI_INTENT_SCHEME).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val pkg = prompt.appPackage
                                val launchIntent = if (!pkg.isNullOrBlank()) {
                                    context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                } else null

                                if (launchIntent != null) {
                                    try {
                                        context.startActivity(launchIntent)
                                    } catch (t: Throwable) {
                                        android.util.Log.w("JestkApp", "Failed to launch package via launch intent", t)
                                    }
                                } else if (!prompt.isInstalled && !prompt.appPackage.isNullOrBlank()) {
                                    try {
                                        val webPlay = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://play.google.com/store/apps/details?id=${prompt.appPackage}")
                                        ).apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(webPlay)
                                    } catch (t: Throwable) {
                                        android.util.Log.w("JestkApp", "Failed to launch play store fallback", t)
                                    }
                                } else {
                                    android.util.Log.w("JestkApp", "Failed to open external app", e)
                                }
                            }
                        },
                        onDismiss = {
                            viewModel.onEvent(BrowserUiEvent.DismissExternalAppPrompt(ExternalAppDismissReason.EXPLICIT))
                        },
                        onTimeout = {
                            // A hint the user ignored must not block a later deliberate tap on the
                            // site's own "open in app" control.
                            viewModel.onEvent(
                                BrowserUiEvent.DismissExternalAppPrompt(ExternalAppDismissReason.TIMEOUT)
                            )
                        }
                    )
                }
            }

            // Update Floating Notification Banner
            AnimatedVisibility(
                visible = uiState.updateState.isVisible,
                enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
                exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(
                            bottom = snackbarBaseBottomOffset +
                                (if (uiState.showUndoTabCloseSnackbar && uiState.recentlyClosedTab != null) 56.dp else 0.dp) +
                                (if (uiState.showDownloadSnackbar && uiState.lastDownloadNotification != null) 56.dp else 0.dp),
                            start = 16.dp,
                            end = 16.dp,
                        ),
            ) {
                UpdateNotificationCard(
                    state = uiState.updateState,
                    onRedirect = { viewModel.onEvent(BrowserUiEvent.RedirectToUpdate) },
                    onDismiss = { viewModel.onEvent(BrowserUiEvent.DismissUpdate) },
                    onSkip = { viewModel.onEvent(BrowserUiEvent.SkipUpdate) },
                    isDark = isDark,
                )
            }

            // Blocked Ad Navigation Floating Banner
            val blockedNav = uiState.blockedAdNavigation
            AnimatedVisibility(
                visible = blockedNav != null,
                enter = slideInVertically(
                    initialOffsetY = { it + 200 },
                    animationSpec = spring(
                        dampingRatio = 0.58f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + fadeIn(animationSpec = tween(120)),
                exit = slideOutVertically(
                    targetOffsetY = { it + 200 },
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                ) + fadeOut(animationSpec = tween(150)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        bottom = snackbarBaseBottomOffset +
                            (if (uiState.showUndoTabCloseSnackbar && uiState.recentlyClosedTab != null) 56.dp else 0.dp) +
                            (if (uiState.showDownloadSnackbar && uiState.lastDownloadNotification != null) 56.dp else 0.dp) +
                            8.dp,
                        start = 16.dp,
                        end = 16.dp,
                    ),
            ) {
                if (blockedNav != null) {
                    BlockedAdNavigationBanner(
                        isDark = isDark,
                        url = blockedNav.url,
                        onDismiss = { viewModel.onEvent(BrowserUiEvent.DismissBlockedAdNavigation) },
                        onConfirm = { viewModel.onEvent(BrowserUiEvent.AllowBlockedAdNavigation) },
                    )
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

            // Add / Edit Search Engine Modal Dialog
            if (uiState.showAddEditSearchEngineDialog) {
                AddEditSearchEngineDialog(
                    isDark = isDark,
                    existingEngines = uiState.searchEngines,
                    engineToEdit = uiState.editingSearchEngine,
                    onDismiss = { viewModel.onEvent(BrowserUiEvent.DismissAddSearchEngineDialog) },
                    onSave = { id, title, shortcut, urlTemplate ->
                        viewModel.onEvent(
                            BrowserUiEvent.SaveSearchEngine(
                                id = id,
                                title = title,
                                shortcut = shortcut,
                                urlTemplate = urlTemplate,
                            )
                        )
                    },
                )
            }

            // Insecure HTTP Warning Modal Dialog
            uiState.insecureHttpPrompt?.let { prompt ->
                InsecureHttpWarningDialog(
                    isDark = isDark,
                    url = prompt.url,
                    onDismiss = { viewModel.onEvent(BrowserUiEvent.DismissInsecureHttpWarning) },
                    onConfirm = { viewModel.onEvent(BrowserUiEvent.ConfirmInsecureHttpNavigation) },
                )
            }

            // Feedback Bottom Sheet Modal
            if (uiState.isFeedbackSheetVisible) {
                FeedbackBottomSheet(
                    isDark = isDark,
                    onDismiss = { viewModel.onEvent(BrowserUiEvent.CloseFeedbackSheet) },
                    onFeedbackSubmitted = {
                        viewModel.onEvent(BrowserUiEvent.DismissFeedbackPrompt)
                        viewModel.onEvent(BrowserUiEvent.CloseFeedbackSheet)
                    },
                )
            }

            // Folder Edit Bottom Sheet Modal
            if (uiState.isFolderSheetVisible) {
                val editingFolder = uiState.folderSheetEditingFolderId?.let { id -> uiState.getFolder(id) }
                FolderEditBottomSheet(
                    isDark = isDark,
                    initialTitle = editingFolder?.title ?: "",
                    initialColor = editingFolder?.color ?: TabFolder.PALETTE.first(),
                    isEditing = editingFolder != null,
                    onDismiss = { viewModel.onEvent(BrowserUiEvent.DismissFolderSheet) },
                    onSave = { title, color ->
                        viewModel.onEvent(BrowserUiEvent.SaveFolder(title = title, color = color))
                    },
                )
            }

            // Fullscreen Custom View (HTML5 Video Fullscreen)
            val customView = uiState.fullscreenCustomView
            FullscreenVideoLayer(
                customView = customView,
                isFullscreen = uiState.isFullscreenCustomView,
                onExitFullscreen = { viewModel.onEvent(BrowserUiEvent.ExitFullscreen) },
            )

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
