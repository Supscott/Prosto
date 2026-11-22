package com.jestk.browser.ui.overlays

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jestk.browser.R
import com.jestk.browser.domain.model.DownloadConfirmRequest
import com.jestk.browser.ui.components.DockPromptAction
import com.jestk.browser.ui.components.DockPromptCard
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.ConfirmDownloadPrompt
import com.jestk.browser.ui.viewmodel.DismissDownloadPrompt
import com.jestk.browser.util.FileSizeFormatter

/**
 * Asks before a file the page started is saved. The message is the file name; a second line holds
 * the size and the page's host, each only when known.
 */
@Composable
internal fun DownloadConfirmPrompt(
    fileName: String,
    details: String?,
    isDark: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    DockPromptCard(
        title = stringResource(R.string.download_confirm_title),
        message = if (details.isNullOrEmpty()) fileName else "$fileName\n$details",
        isDark = isDark,
        onDismiss = onCancel,
        actions = listOf(
            DockPromptAction(stringResource(R.string.download_confirm_action), onDownload, primary = true),
            DockPromptAction(stringResource(R.string.cancel), onCancel),
        ),
    )
}

/** Shows [DownloadConfirmPrompt] while the state holds a request. */
@Composable
internal fun DownloadConfirmHost(uiState: BrowserUiState, isDark: Boolean, onEvent: (BrowserUiEvent) -> Unit) {
    val request: DownloadConfirmRequest = uiState.downloadConfirmPrompt ?: return
    val resources = LocalContext.current.resources
    val size = request.sizeBytes?.let { FileSizeFormatter.formatFileSize(resources, it) }
    DownloadConfirmPrompt(
        fileName = request.fileName,
        details = listOfNotNull(size?.takeIf { it.isNotEmpty() }, request.host).joinToString(" · "),
        isDark = isDark,
        onDownload = { onEvent(ConfirmDownloadPrompt) },
        onCancel = { onEvent(DismissDownloadPrompt) },
    )
}
