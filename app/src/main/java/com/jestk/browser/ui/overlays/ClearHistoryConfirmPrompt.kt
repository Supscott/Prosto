package com.jestk.browser.ui.overlays

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jestk.browser.R
import com.jestk.browser.ui.components.DockPromptAction
import com.jestk.browser.ui.components.DockPromptCard
import com.jestk.browser.ui.theme.ProstoDestructive

/** Confirmation before "Clear History" in the Search screen deletes the workspace's history. */
@Composable
fun ClearHistoryConfirmPrompt(
    isDark: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DockPromptCard(
        title = stringResource(R.string.clear_history_confirm_title),
        message = stringResource(R.string.clear_history_confirm_message),
        isDark = isDark,
        onDismiss = onDismiss,
        actions = listOf(
            DockPromptAction(
                stringResource(R.string.clear_history_confirm), onConfirm,
                primary = true, color = ProstoDestructive, testTag = "clear_history_confirm_button",
            ),
            DockPromptAction(
                stringResource(R.string.cancel), onDismiss,
                testTag = "clear_history_cancel_button",
            ),
        ),
    )
}
