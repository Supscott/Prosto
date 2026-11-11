package com.jestk.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.ui.app.AppDialogs
import com.jestk.browser.ui.app.AppNotificationPills
import com.jestk.browser.ui.app.AppOverlays
import com.jestk.browser.ui.app.NavigationCommandEffect
import com.jestk.browser.ui.app.WebViewPoolBindingEffects
import com.jestk.browser.ui.app.rememberSystemBarsEffects
import com.jestk.browser.ui.navigation.UnifiedBottomBar
import com.jestk.browser.ui.overlays.FullscreenVideoLayer
import com.jestk.browser.ui.overlays.WORKSPACE_CARD_CORNER_RADIUS_DP
import com.jestk.browser.ui.overlays.rememberWorkspaceSwitcherState
import com.jestk.browser.ui.overlays.workspaceSwitcherLiveContent
import com.jestk.browser.ui.pip.FloatingVideoMiniPlayer
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserViewModel
import com.jestk.browser.ui.views.HomeScreen
import com.jestk.browser.ui.views.WebViewClientDeps
import com.jestk.browser.ui.views.WebViewContainer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JestkApp(
    viewModel: BrowserViewModel,
    webViewPool: WebViewPool,
    modifier: Modifier = Modifier,
) {
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

    WebViewPoolBindingEffects(uiState, webViewPool, viewModel, clientDeps)

    NavigationCommandEffect(viewModel, webViewPool)

    val activeWorkspaceColor = uiState.activeWorkspace?.color?.let { Color(it) }

    JestkBrowserTheme(
        themeMode = uiState.settings.themeMode,
        workspaceColor = activeWorkspaceColor,
    ) {
        val isDark = LocalIsDarkTheme.current
        LaunchedEffect(isDark) {
            webViewPool.updateThemeMode(isDark)
        }

        val showHomeScreen = uiState.isHomeContentVisible

        val systemBarColors = rememberSystemBarsEffects(uiState, isDark)
        val animatedStatusBarBg = systemBarColors.statusBar
        val animatedBottomBarBg = systemBarColors.bottomBar

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

            AppOverlays(uiState, handleUiEvent, workspaceSwitcher)

            AppNotificationPills(uiState, handleUiEvent, isDark, activeWorkspaceColor, isBottomBarVisible)

            AppDialogs(uiState, handleUiEvent, isDark, webViewPool)

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
