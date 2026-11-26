package com.jestk.browser.ui.overlays

import androidx.compose.runtime.Composable
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.ResolveSitePermissionPrompt

/** Shows [SitePermissionConfirmPrompt] while the state holds a camera / microphone request. */
@Composable
internal fun SitePermissionHost(uiState: BrowserUiState, isDark: Boolean, onEvent: (BrowserUiEvent) -> Unit) {
    val prompt = uiState.sitePermissionPrompt ?: return
    SitePermissionConfirmPrompt(
        host = prompt.host,
        resources = prompt.resources,
        canRemember = prompt.canRemember,
        isDark = isDark,
        onChoice = { choice -> onEvent(ResolveSitePermissionPrompt(choice)) },
    )
}
