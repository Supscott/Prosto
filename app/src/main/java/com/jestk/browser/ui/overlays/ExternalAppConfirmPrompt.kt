package com.jestk.browser.ui.overlays

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jestk.browser.R
import com.jestk.browser.ui.components.DockPromptAction
import com.jestk.browser.ui.components.DockPromptCard

/**
 * Confirmation shown when the user deliberately taps a site's own "Open app" control.
 *
 * Actions: [onOpen] leaves the browser for the native app, [onCloseTab] closes the tab that asked
 * for the handoff, [onCancel] (also back / outside tap) keeps the page as it is.
 */
@Composable
fun ExternalAppConfirmPrompt(
    isDark: Boolean,
    onOpen: () -> Unit,
    onCloseTab: () -> Unit,
    onCancel: () -> Unit,
) {
    DockPromptCard(
        title = stringResource(R.string.external_app_confirm_title),
        message = stringResource(R.string.external_app_confirm_message),
        isDark = isDark,
        onDismiss = onCancel,
        actions = listOf(
            DockPromptAction(
                stringResource(R.string.external_app_confirm_open), onOpen,
                primary = true, testTag = "external_app_confirm_open",
            ),
            DockPromptAction(
                stringResource(R.string.external_app_confirm_close_tab), onCloseTab,
                testTag = "external_app_confirm_close_tab",
            ),
            DockPromptAction(
                stringResource(R.string.external_app_confirm_cancel), onCancel,
                testTag = "external_app_confirm_cancel",
            ),
        ),
    )
}
