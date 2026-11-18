package com.jestk.browser.ui.overlays

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.DomainGroupSuggestion
import com.jestk.browser.ui.components.swipeToDismissPill
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoShapes

/**
 * Chip above the dock offering to put the open tabs of one site into a folder: how many tabs, the
 * site, a "Create" action. It stays until the user answers or swipes it away.
 */
@Composable
internal fun DomainGroupSuggestionPill(
    suggestion: DomainGroupSuggestion,
    isDark: Boolean,
    accent: Color?,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = accent ?: ProstoBrandPrimary
    val count = suggestion.tabIds.size
    Surface(
        shape = ProstoShapes.Pill,
        color = if (isDark) DarkSurface else Color.White,
        border = ProstoBorders.floatingBorder(isDark, workspaceColor = accent),
        shadowElevation = 8.dp,
        modifier = modifier.wrapContentSize().swipeToDismissPill(suggestion.domain, onDismiss),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = pluralStringResource(R.plurals.domain_group_message, count, count, suggestion.domain),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (isDark) DarkOnSurface else ProstoDarkText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = stringResource(R.string.domain_group_create),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = accentColor),
                modifier = Modifier.clickable(onClick = onCreate).padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}
