package com.jestk.browser.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.ui.components.swipeToDismissPill
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.DismissDownloadFailure
import com.jestk.browser.ui.viewmodel.DownloadFailure

/** How long the pill stays before it dismisses itself. */
internal const val DOWNLOAD_FAILED_PILL_TIMEOUT_MS = 5_000L

/**
 * Pill shown when a download could not be started. Dismisses itself after
 * [DOWNLOAD_FAILED_PILL_TIMEOUT_MS] or by swipe; a newer failure restarts the timer.
 */
@Composable
internal fun BoxScope.DownloadFailedPill(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    isDark: Boolean,
    activeWorkspaceColor: Color?,
    baseBottomOffset: Dp,
) {
    val failure = uiState.downloadFailure
    var lastShown by remember { mutableStateOf<DownloadFailure?>(null) }
    if (failure != null) lastShown = failure
    val shown = failure ?: lastShown
    val currentOnEvent by rememberUpdatedState(onEvent)

    AnimatedVisibility(
        visible = failure != null,
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
        if (shown != null) {
            PillAutoDismissEffect(
                active = true,
                restartKey = shown.id,
                baseTimeoutMs = DOWNLOAD_FAILED_PILL_TIMEOUT_MS,
                containsIcons = true,
                containsControls = false,
                onDismiss = { currentOnEvent(DismissDownloadFailure) },
            )
            val reason = shown.message.ifBlank { shown.fileName.orEmpty() }
            val text = if (reason.isBlank()) {
                stringResource(R.string.downloads_failed)
            } else {
                stringResource(R.string.download_failed_pill, reason)
            }
            Surface(
                shape = ProstoShapes.Pill,
                color = if (isDark) DarkSurface else Color.White,
                border = ProstoBorders.floatingBorder(isDark, workspaceColor = activeWorkspaceColor),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .wrapContentSize()
                    .swipeToDismissPill(shown.id, onDismiss = { currentOnEvent(DismissDownloadFailure) }),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (isDark) DarkOnSurface else ProstoDarkText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}
