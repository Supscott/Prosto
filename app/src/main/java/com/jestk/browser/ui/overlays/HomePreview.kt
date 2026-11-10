package com.jestk.browser.ui.overlays

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.views.HomeScreen

/**
 * Builds a home-page-scoped copy of [uiState] for [workspaceId]:
 * per-workspace favorites and recent tabs, current-url cleared (no active page),
 * workspace resolved through [BrowserUiState.workspaces].
 *
 * The active workspace returns [uiState] unchanged.
 */
internal fun BrowserUiState.homePreviewState(workspaceId: String): BrowserUiState {
    if (workspaceId == activeWorkspaceId) return this
    val data = workspaceHomeData[workspaceId]
    return copy(
        activeWorkspaceId = workspaceId,
        favorites = data?.favorites ?: emptyList(),
        recentTabs = data?.recentTabs ?: emptyList(),
        currentUrl = "",
    )
}

/**
 * Live render of the real [HomeScreen] with a shrunken density so it fills the slot edge-to-edge —
 * a screenshot-like preview of the workspace's home page.
 *
 * Content is fully inert for pointer events: the caller is expected to wrap this
 * in its own clickable, which consumes input before children.
 */
@Composable
fun HomePreview(
    uiState: BrowserUiState,
    workspaceId: String,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val screenWidthPx = with(LocalDensity.current) { screenWidthDp.toPx() }

    val previewState = remember(
        workspaceId,
        uiState.workspaceHomeData,
        uiState.settings,
        uiState.activeWorkspaceId,
        uiState.currentUrl,
        uiState.isDefaultBrowser,
        uiState.isFeedbackCardVisible,
        uiState.blockedAdsCount,
        uiState.blockedTrackersCount,
        uiState.workspaces,
    ) { uiState.homePreviewState(workspaceId) }

    BoxWithConstraints(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.TopStart,
    ) {
        val baseDensity = LocalDensity.current
        // Render the home page at its natural phone size but with a shrunken density —
        // everything shrinks proportionally like a smaller device: edge-to-edge fill,
        // no distortion, no letterboxing.
        val fitScale = constraints.maxWidth.toFloat() / screenWidthPx
        CompositionLocalProvider(
            LocalDensity provides Density(baseDensity.density * fitScale, baseDensity.fontScale),
        ) {
            Box(
                modifier = Modifier.requiredSize(screenWidthDp, screenHeightDp),
            ) {
                HomeScreen(
                    uiState = previewState,
                    onEvent = {},
                    isPreview = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Inert shield: a pointer-input node on top stops hit-testing from reaching HomeScreen's
        // controls without consuming events, so the caller's tap/long-press still fires.
        Box(Modifier.fillMaxSize().pointerInput(Unit) {})
    }
}
