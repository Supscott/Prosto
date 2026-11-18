package com.jestk.browser.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.jestk.browser.ui.components.tabDisplayTitle
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.DismissLastTabPrompt
import com.jestk.browser.ui.viewmodel.RestoreLastTab

/**
 * "Last tab: <title>" pill shown over the Home tab the app opened after a long absence. A tap goes
 * back to that tab, a swipe dismisses the pill. It stays until the user leaves the new tab: switching
 * tabs or navigating the new tab to a page removes it.
 */
@Composable
internal fun BoxScope.LastTabPillHost(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    isDark: Boolean,
    activeWorkspaceColor: Color?,
    isBottomBarVisible: Boolean,
    bottomOffset: Dp,
) {
    val prompt = uiState.lastTabPrompt
    val previousTab = prompt?.let { p -> uiState.tabs.find { it.id == p.previousTabId } }
    val onNewTab = prompt != null && uiState.activeTabId == prompt.newTabId && uiState.activeTab?.isHomeTab == true
    val stale = prompt != null && (previousTab == null || !onNewTab)
    LaunchedEffect(stale, prompt) {
        if (stale) onEvent(DismissLastTabPrompt)
    }

    // The last title keeps the pill's text while it animates out.
    val title = previousTab?.let { tabDisplayTitle(it) }
    var shownTitle by remember { mutableStateOf("") }
    LaunchedEffect(title) { if (title != null) shownTitle = title }

    val visible = prompt != null && !stale && isBottomBarVisible && uiState.activeOverlay !in OVERLAYS_WITHOUT_CHIP
    val accent = activeWorkspaceColor ?: ProstoBrandPrimary
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
        exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = bottomOffset, start = 16.dp, end = 16.dp),
    ) {
        Surface(
            shape = ProstoShapes.Pill,
            color = if (isDark) DarkSurface else Color.White,
            border = ProstoBorders.floatingBorder(isDark, workspaceColor = activeWorkspaceColor),
            shadowElevation = 8.dp,
            modifier = Modifier
                .wrapContentSize()
                .swipeToDismissPill(prompt?.newTabId, onDismiss = { onEvent(DismissLastTabPrompt) })
                .clickable { onEvent(RestoreLastTab) },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.last_tab_pill, shownTitle),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
