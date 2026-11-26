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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
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
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.DismissFavoriteNotice
import com.jestk.browser.ui.viewmodel.FavoriteNotice

/** How long the pill stays before it dismisses itself. */
internal const val FAVORITE_NOTICE_PILL_TIMEOUT_MS = 3_000L

/** Hosts [FavoriteNoticePill] for [BrowserUiState.favoriteNotice], stacked above the other pills. */
@Composable
internal fun BoxScope.FavoriteNoticePillHost(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    isDark: Boolean,
    activeWorkspaceColor: Color?,
    baseBottomOffset: Dp,
) {
    val notice = uiState.favoriteNotice
    var lastShown by remember { mutableStateOf<FavoriteNotice?>(null) }
    if (notice != null) lastShown = notice
    val shown = notice ?: lastShown
    val currentOnEvent by rememberUpdatedState(onEvent)

    AnimatedVisibility(
        visible = notice != null,
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
                baseTimeoutMs = FAVORITE_NOTICE_PILL_TIMEOUT_MS,
                containsIcons = true,
                containsControls = false,
                onDismiss = { currentOnEvent(DismissFavoriteNotice) },
            )
            FavoriteNoticePill(
                added = shown.added,
                isDark = isDark,
                accent = activeWorkspaceColor,
                swipeKey = shown.id,
                onDismiss = { currentOnEvent(DismissFavoriteNotice) },
            )
        }
    }
}

/** "Added to favorites" / "Removed from favorites": a plain confirmation, nothing to undo. */
@Composable
internal fun FavoriteNoticePill(
    added: Boolean,
    isDark: Boolean,
    accent: Color?,
    swipeKey: Any?,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = ProstoShapes.Pill,
        color = if (isDark) DarkSurface else Color.White,
        border = ProstoBorders.floatingBorder(isDark, workspaceColor = accent),
        shadowElevation = 8.dp,
        modifier = Modifier
            .wrapContentSize()
            .swipeToDismissPill(swipeKey, onDismiss = onDismiss),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (added) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = accent ?: ProstoBrandPrimary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(if (added) R.string.favorite_added_pill else R.string.favorite_removed_pill),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (isDark) DarkOnSurface else ProstoDarkText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
