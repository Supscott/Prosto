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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jestk.browser.ui.overlays.OpenInAppExclusionPill
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.DismissOpenInAppExclusionPrompt
import com.jestk.browser.ui.viewmodel.ExcludeOpenInAppSite

/** Hosts the "don't offer to open in the app" question for the active tab. */
@Composable
internal fun BoxScope.OpenInAppExclusionPillHost(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    isDark: Boolean,
    activeWorkspaceColor: Color?,
    baseBottomOffset: Dp,
) {
    val prompt = uiState.openInAppExclusionPrompt?.takeIf { it.tabId == uiState.activeTabId }
    AnimatedVisibility(
        visible = prompt != null,
        enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
        exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(
                bottom = baseBottomOffset +
                    (if (uiState.showUndoTabCloseSnackbar && uiState.recentlyClosedTab != null) 56.dp else 0.dp) +
                    (if (uiState.showDownloadSnackbar && uiState.lastDownloadNotification != null) 56.dp else 0.dp),
                start = 16.dp,
                end = 16.dp,
            ),
    ) {
        if (prompt != null) {
            OpenInAppExclusionPill(
                domain = prompt.domain,
                isDark = isDark,
                promptKey = prompt,
                accent = activeWorkspaceColor,
                onExclude = { onEvent(ExcludeOpenInAppSite(prompt.domain)) },
                onDismiss = { onEvent(DismissOpenInAppExclusionPrompt(prompt.domain)) },
            )
        }
    }
}
