package com.jestk.browser.ui.overlays

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoShapes
import kotlinx.coroutines.delay

/** How long the pill stays before it dismisses itself. */
internal const val POPUP_BLOCKED_PILL_TIMEOUT_MS = 6_000L

/**
 * Non-intrusive "Popup blocked" pill with an "Allow for this site" action. [promptKey] restarts
 * the auto-dismiss timer when another popup is blocked while the pill is showing.
 */
@Composable
internal fun PopupBlockedPill(
    isDark: Boolean,
    promptKey: Any,
    accent: Color?,
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(promptKey) {
        delay(POPUP_BLOCKED_PILL_TIMEOUT_MS)
        currentOnDismiss()
    }
    Surface(
        shape = ProstoShapes.Pill,
        color = if (isDark) DarkSurface else Color.White,
        border = ProstoBorders.floatingBorder(isDark, workspaceColor = accent),
        shadowElevation = 8.dp,
        modifier = modifier.wrapContentSize(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.popup_blocked_message),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (isDark) DarkOnSurface else ProstoDarkText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = stringResource(R.string.popup_blocked_allow_site),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = accent ?: ProstoBrandPrimary,
                ),
                modifier = Modifier.clickable(onClick = onAllow).padding(horizontal = 4.dp, vertical = 2.dp),
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp).clickable(onClick = onDismiss),
            )
        }
    }
}
