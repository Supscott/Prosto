package com.jestk.browser.ui.overlays

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jestk.browser.R
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.ui.components.DockPromptAction
import com.jestk.browser.ui.components.DockPromptCard
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.ConfirmDuplicateDownload
import com.jestk.browser.ui.viewmodel.DismissDuplicateDownload
import com.jestk.browser.util.DownloadFileOpener

/** Asks what to do with a file that was already downloaded: open it, download it again or cancel. */
@Composable
internal fun DuplicateDownloadPrompt(
    fileName: String,
    isDark: Boolean,
    onOpen: () -> Unit,
    onDownloadAgain: () -> Unit,
    onCancel: () -> Unit,
) {
    DockPromptCard(
        title = stringResource(R.string.duplicate_download_title),
        message = fileName,
        isDark = isDark,
        onDismiss = onCancel,
        actions = listOf(
            DockPromptAction(stringResource(R.string.download_action_open), onOpen, primary = true),
            DockPromptAction(stringResource(R.string.duplicate_download_again), onDownloadAgain),
            DockPromptAction(stringResource(R.string.cancel), onCancel),
        ),
    )
}

/** Shows [DuplicateDownloadPrompt] while the state holds a prompt; Open launches the existing file. */
@Composable
internal fun DuplicateDownloadHost(uiState: BrowserUiState, isDark: Boolean, onEvent: (BrowserUiEvent) -> Unit) {
    val existing: DownloadItem = uiState.duplicateDownloadPrompt ?: return
    val context = LocalContext.current
    DuplicateDownloadPrompt(
        fileName = existing.fileName,
        isDark = isDark,
        onOpen = {
            DownloadFileOpener.openDownloadedFile(context, existing)
            onEvent(DismissDuplicateDownload)
        },
        onDownloadAgain = { onEvent(ConfirmDuplicateDownload) },
        onCancel = { onEvent(DismissDuplicateDownload) },
    )
}
