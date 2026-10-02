package com.jestk.browser.ui.overlays

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jestk.browser.ui.screens.DownloadsScreen
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState

@Deprecated(
    message = "DownloadsSheet has been refactored into a full screen. Use DownloadsScreen in com.jestk.browser.ui.screens",
    replaceWith = ReplaceWith(
        expression = "DownloadsScreen(uiState, onEvent, modifier)",
        imports = ["com.jestk.browser.ui.screens.DownloadsScreen"]
    )
)
@Composable
fun DownloadsSheet(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    DownloadsScreen(
        uiState = uiState,
        onEvent = onEvent,
        modifier = modifier
    )
}
