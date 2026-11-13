package com.jestk.browser.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.ui.overlays.AddEditSearchEngineDialog
import com.jestk.browser.ui.overlays.AddFavoriteDialog
import com.jestk.browser.ui.overlays.FeedbackBottomSheet
import com.jestk.browser.ui.overlays.FolderEditBottomSheet
import com.jestk.browser.ui.overlays.ImageContextMenuModal
import com.jestk.browser.ui.overlays.InsecureHttpWarningDialog
import com.jestk.browser.ui.overlays.LinkContextMenuModal
import com.jestk.browser.ui.overlays.TutorialStoriesScreen
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.HomeUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState

@Composable
internal fun AppDialogs(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    isDark: Boolean,
    webViewPool: WebViewPool,
) {
    val context = LocalContext.current
    // Link Context Menu Floating Modal
    uiState.linkContextMenuUrl?.let { linkUrl ->
        LinkContextMenuModal(
            url = linkUrl,
            anchor = uiState.contextMenuAnchor,
            onEvent = onEvent,
        )
    }

    // Image Context Menu Floating Modal
    uiState.selectedImageUrl?.let { imageUrl ->
        ImageContextMenuModal(
            imageUrl = imageUrl,
            linkUrl = uiState.imageContextMenuLinkUrl,
            anchor = uiState.contextMenuAnchor,
            onEvent = onEvent,
            onDownloadImage = { url ->
                webViewPool.handleDownload(
                    url = url,
                    userAgent = null,
                    contentDisposition = null,
                    mimeType = "image/*",
                    contentLength = 0L,
                    context = context,
                )
            },
        )
    }

    // Add Favorite Modal Dialog
    if (uiState.showAddFavoriteDialog) {
        AddFavoriteDialog(
            isDark = isDark,
            onDismiss = { onEvent(BrowserUiEvent.DismissAddFavoriteDialog) },
            onConfirm = { title, url ->
                onEvent(BrowserUiEvent.AddFavorite(title, url))
                onEvent(BrowserUiEvent.DismissAddFavoriteDialog)
            },
        )
    }

    // Add / Edit Search Engine Modal Dialog
    if (uiState.showAddEditSearchEngineDialog) {
        AddEditSearchEngineDialog(
            isDark = isDark,
            existingEngines = uiState.searchEngines,
            engineToEdit = uiState.editingSearchEngine,
            onDismiss = { onEvent(BrowserUiEvent.DismissAddSearchEngineDialog) },
            onSave = { id, title, shortcut, urlTemplate ->
                onEvent(
                    BrowserUiEvent.SaveSearchEngine(
                        id = id,
                        title = title,
                        shortcut = shortcut,
                        urlTemplate = urlTemplate,
                    )
                )
            },
        )
    }

    // Insecure HTTP Warning Modal Dialog
    uiState.insecureHttpPrompt?.let { prompt ->
        InsecureHttpWarningDialog(
            isDark = isDark,
            url = prompt.url,
            onDismiss = { onEvent(BrowserUiEvent.DismissInsecureHttpWarning) },
            onConfirm = { onEvent(BrowserUiEvent.ConfirmInsecureHttpNavigation) },
        )
    }

    // Feedback Bottom Sheet Modal
    if (uiState.isFeedbackSheetVisible) {
        FeedbackBottomSheet(
            isDark = isDark,
            onDismiss = { onEvent(BrowserUiEvent.CloseFeedbackSheet) },
            onFeedbackSubmitted = {
                onEvent(BrowserUiEvent.DismissFeedbackPrompt)
                onEvent(BrowserUiEvent.CloseFeedbackSheet)
            },
        )
    }

    // Stories-style tutorial
    if (uiState.isTutorialVisible) {
        TutorialStoriesScreen(
            isDark = isDark,
            isDefaultBrowser = uiState.isDefaultBrowser,
            onDefaultBrowserChanged = { onEvent(BrowserUiEvent.UpdateDefaultBrowserStatus(it)) },
            onClose = { onEvent(HomeUiEvent.CloseTutorial) },
            onComplete = { onEvent(HomeUiEvent.CompleteTutorial) },
        )
    }

    // Folder Edit Bottom Sheet Modal
    if (uiState.isFolderSheetVisible) {
        val editingFolder = uiState.folderSheetEditingFolderId?.let { id -> uiState.getFolder(id) }
        FolderEditBottomSheet(
            isDark = isDark,
            initialTitle = editingFolder?.title ?: "",
            initialColor = editingFolder?.color ?: TabFolder.PALETTE.first(),
            isEditing = editingFolder != null,
            onDismiss = { onEvent(BrowserUiEvent.DismissFolderSheet) },
            onSave = { title, color ->
                onEvent(BrowserUiEvent.SaveFolder(title = title, color = color))
            },
        )
    }
}
