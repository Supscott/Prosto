package com.jestk.browser.ui.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jestk.browser.R
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.ConfirmDuplicateDownload
import com.jestk.browser.ui.viewmodel.DismissDuplicateDownload
import com.jestk.browser.util.DownloadFileOpener

/** Asks what to do with a file that was already downloaded: open it, download it again or cancel. */
@Composable
internal fun DuplicateDownloadDialog(
    existing: DownloadItem,
    onOpen: () -> Unit,
    onDownloadAgain: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        DuplicateDownloadCard(existing.fileName, onOpen, onDownloadAgain, onCancel)
    }
}

/** Shows [DuplicateDownloadDialog] while the state holds a prompt; Open launches the existing file. */
@Composable
internal fun DuplicateDownloadHost(uiState: BrowserUiState, onEvent: (BrowserUiEvent) -> Unit) {
    val existing = uiState.duplicateDownloadPrompt ?: return
    val context = LocalContext.current
    DuplicateDownloadDialog(
        existing = existing,
        onOpen = {
            DownloadFileOpener.openDownloadedFile(context, existing)
            onEvent(DismissDuplicateDownload)
        },
        onDownloadAgain = { onEvent(ConfirmDuplicateDownload) },
        onCancel = { onEvent(DismissDuplicateDownload) },
    )
}

@Composable
internal fun DuplicateDownloadCard(
    fileName: String,
    onOpen: () -> Unit,
    onDownloadAgain: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 16.dp, bottom = 12.dp)) {
            Text(
                text = stringResource(R.string.duplicate_download_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                TextButton(onClick = onOpen) { Text(stringResource(R.string.download_action_open)) }
                TextButton(onClick = onDownloadAgain) { Text(stringResource(R.string.duplicate_download_again)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}
