package com.jestk.browser.ui.overlays

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jestk.browser.ui.screens.SettingsScreen
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState

@Deprecated(
    message = "SettingsSheet has been refactored into a full screen. Use SettingsScreen in com.jestk.browser.ui.screens",
    replaceWith = ReplaceWith(
        expression = "SettingsScreen(uiState, onEvent, modifier)",
        imports = ["com.jestk.browser.ui.screens.SettingsScreen"]
    )
)
@Composable
fun SettingsSheet(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsScreen(
        uiState = uiState,
        onEvent = onEvent,
        modifier = modifier
    )
}
