package com.jestk.browser.ui.overlays

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.jestk.browser.R
import com.jestk.browser.ui.components.DockPromptAction
import com.jestk.browser.ui.components.DockPromptCard
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoDestructive

@Composable
fun WorkspaceDeleteConfirmPrompt(
    workspaceName: String,
    isDark: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val typography = LocalProstoTypography.current
    DockPromptCard(
        title = stringResource(R.string.workspace_delete_confirm_title),
        message = stringResource(R.string.workspace_delete_confirm_message),
        isDark = isDark,
        onDismiss = onDismiss,
        actions = listOf(
            DockPromptAction(
                stringResource(R.string.workspace_delete_confirm), onConfirm,
                primary = true, color = ProstoDestructive, testTag = "workspace_delete_confirm_button",
            ),
            DockPromptAction(
                stringResource(R.string.workspace_delete_cancel), onDismiss,
                testTag = "workspace_delete_cancel_button",
            ),
        ),
        content = if (workspaceName.isBlank()) null else {
            {
                Text(
                    text = workspaceName,
                    style = typography.labelMd,
                    color = if (isDark) DarkOnSurfaceVariant else ProstoDarkText,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}
