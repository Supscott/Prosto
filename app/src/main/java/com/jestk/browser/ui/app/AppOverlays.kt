package com.jestk.browser.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.ui.overlays.CleanVideoPlayerOverlay
import com.jestk.browser.ui.overlays.SearchOverlay
import com.jestk.browser.ui.overlays.TabOverviewGrid
import com.jestk.browser.ui.overlays.WorkspaceSwitcherState
import com.jestk.browser.ui.overlays.WorkspacesOverviewScreen
import com.jestk.browser.ui.screens.DownloadsScreen
import com.jestk.browser.ui.screens.SettingsScreen
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.util.YouTubeUrlHelper

@Composable
internal fun AppOverlays(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    workspaceSwitcher: WorkspaceSwitcherState,
) {
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
            onEvent = onEvent,
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
            onEvent = onEvent,
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
            onEvent = onEvent,
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
            onEvent = onEvent,
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
            onEvent = onEvent,
            modifier = Modifier.fillMaxSize(),
        )
    }

    val cleanVideoId = uiState.cleanPlayerVideoId
    if (cleanVideoId != null) {
        CleanVideoPlayerOverlay(
            videoId = cleanVideoId,
            originalUrl = uiState.cleanPlayerOriginalUrl ?: YouTubeUrlHelper.buildOriginalWatchUrl(cleanVideoId),
            startSeconds = uiState.cleanPlayerStartSeconds,
            onClose = { onEvent(BrowserUiEvent.CloseCleanPlayer) },
            onOpenOnWeb = { url -> onEvent(BrowserUiEvent.OpenCleanPlayerOnWeb(url)) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
