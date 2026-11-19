package com.jestk.browser.ui.overlays

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
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
import com.jestk.browser.ui.components.swipeToDismissPill
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoShapes
import kotlinx.coroutines.delay

/** How long the question stays before it dismisses itself. */
internal const val OPEN_IN_APP_EXCLUSION_PILL_TIMEOUT_MS = 8_000L

/**
 * "Don't offer to open <domain> in the app?" pill shown after the site's open-in-app pill was
 * swiped away repeatedly. [promptKey] restarts the auto-dismiss timer and the swipe state.
 */
@Composable
internal fun OpenInAppExclusionPill(
    domain: String,
    isDark: Boolean,
    promptKey: Any,
    accent: Color?,
    onExclude: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(promptKey) {
        delay(OPEN_IN_APP_EXCLUSION_PILL_TIMEOUT_MS)
        currentOnDismiss()
    }
    Surface(
        shape = ProstoShapes.Pill,
        color = if (isDark) DarkSurface else Color.White,
        border = ProstoBorders.floatingBorder(isDark, workspaceColor = accent),
        shadowElevation = 8.dp,
        modifier = modifier.wrapContentSize().swipeToDismissPill(promptKey, onDismiss),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.open_in_app_exclusion_message, domain),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (isDark) DarkOnSurface else ProstoDarkText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = stringResource(R.string.open_in_app_exclusion_action),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = accent ?: ProstoBrandPrimary,
                ),
                modifier = Modifier.clickable(onClick = onExclude).padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}
