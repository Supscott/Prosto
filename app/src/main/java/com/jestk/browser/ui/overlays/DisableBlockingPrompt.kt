package com.jestk.browser.ui.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.ui.components.DockPromptAction
import com.jestk.browser.ui.components.DockPromptCard
import com.jestk.browser.ui.components.ProstoSwitchRow
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoDarkText

/**
 * Asks before content blocking is switched off for [host]. The entry covers the active
 * workspace unless "Apply to all workspaces" is on.
 */
@Composable
fun DisableBlockingPrompt(
    isDark: Boolean,
    host: String,
    onDismiss: () -> Unit,
    onConfirm: (allWorkspaces: Boolean) -> Unit,
) {
    var allWorkspaces by remember { mutableStateOf(false) }
    val textColor = if (isDark) DarkOnSurface else ProstoDarkText
    val typography = LocalProstoTypography.current

    DockPromptCard(
        title = stringResource(R.string.disable_blocking_title),
        message = stringResource(R.string.disable_blocking_message, host),
        isDark = isDark,
        onDismiss = onDismiss,
        actions = listOf(
            DockPromptAction(stringResource(R.string.disable_blocking_confirm), { onConfirm(allWorkspaces) }, primary = true),
            DockPromptAction(stringResource(R.string.cancel), onDismiss),
        ),
        content = {
            ProstoSwitchRow(
                checked = allWorkspaces,
                onCheckedChange = { allWorkspaces = it },
                contentPadding = PaddingValues(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.disable_blocking_all_workspaces),
                    style = typography.bodyMd,
                    color = textColor,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    )
}
