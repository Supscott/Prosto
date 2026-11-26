package com.jestk.browser.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.DomainGroupSuggestion
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.ui.overlays.DomainGroupSuggestionPill
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.CreateDomainGroup
import com.jestk.browser.ui.viewmodel.DismissDomainGroup

/** Overlays that cover the page and the dock; the chip belongs to the browsing view only. */
internal val OVERLAYS_WITHOUT_CHIP = setOf(
    OverlayType.SEARCH,
    OverlayType.TAB_GRID,
    OverlayType.SETTINGS,
    OverlayType.DOWNLOADS,
    OverlayType.WORKSPACES,
)

/** A transient pill is on screen; the suggestion waits so that the two never overlap. */
internal fun BrowserUiState.hasTransientPill(): Boolean =
    (showUndoTabCloseSnackbar && recentlyClosedTab != null) ||
        (showDownloadSnackbar && lastDownloadNotification != null) ||
        externalAppPrompt != null ||
        lastTabPrompt != null ||
        popupBlocked != null ||
        downloadFailure != null ||
        favoriteNotice != null ||
        blockedAdNavigation != null ||
        insecureHttpPrompt != null

/**
 * The chip is up only on the browsing view, with no other pill on screen, and only while the open
 * tab is one of the tabs it would group: offering to group sites the user is not looking at reads
 * as noise (PRO-65).
 */
internal fun BrowserUiState.shouldShowDomainGroupChip(isBottomBarVisible: Boolean): Boolean {
    val suggestion = domainGroupSuggestion ?: return false
    return isBottomBarVisible &&
        activeTabId in suggestion.tabIds &&
        // The chip sits where the expanded menu ends and covered its last item (Settings).
        !isMenuExpanded &&
        activeOverlay !in OVERLAYS_WITHOUT_CHIP &&
        !hasTransientPill()
}

/**
 * The "create a folder for these tabs" chip above the dock. Shown only while the dock is visible,
 * no overlay covers the page, no other pill is up and the open tab belongs to the group.
 */
@Composable
internal fun BoxScope.DomainGroupSuggestionHost(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    isDark: Boolean,
    activeWorkspaceColor: Color?,
    isBottomBarVisible: Boolean,
    bottomOffset: Dp,
) {
    val suggestion = uiState.domainGroupSuggestion
    // The last suggestion keeps the chip's text while it animates out.
    var lastSuggestion by remember { mutableStateOf<DomainGroupSuggestion?>(null) }
    LaunchedEffect(suggestion) { if (suggestion != null) lastSuggestion = suggestion }

    val visible = uiState.shouldShowDomainGroupChip(isBottomBarVisible)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
        exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = bottomOffset, start = 16.dp, end = 16.dp),
    ) {
        (suggestion ?: lastSuggestion)?.let { shown ->
            DomainGroupSuggestionPill(
                suggestion = shown,
                isDark = isDark,
                accent = activeWorkspaceColor,
                onCreate = { onEvent(CreateDomainGroup(shown.domain)) },
                onDismiss = { onEvent(DismissDomainGroup(shown.domain, shown.tabIds.size)) },
            )
        }
    }
}
