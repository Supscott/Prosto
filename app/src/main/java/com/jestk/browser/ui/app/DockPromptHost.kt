package com.jestk.browser.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.jestk.browser.ui.launchExternalApp
import com.jestk.browser.ui.overlays.AddEditSearchEngineDialog
import com.jestk.browser.ui.overlays.AddFavoriteDialog
import com.jestk.browser.ui.overlays.DisableBlockingDialog
import com.jestk.browser.ui.overlays.DuplicateDownloadHost
import com.jestk.browser.ui.overlays.ExternalAppConfirmDialog
import com.jestk.browser.ui.overlays.InsecureHttpWarningDialog
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.ConfirmDisableBlocking
import com.jestk.browser.ui.viewmodel.DismissDisableBlockingDialog
import com.jestk.browser.ui.viewmodel.ExternalAppDismissReason

/** True while one of the confirmations hosted by [DockPromptHost] is on screen. */
internal fun hasDockPrompt(uiState: BrowserUiState): Boolean =
    uiState.showAddFavoriteDialog ||
        uiState.showAddEditSearchEngineDialog ||
        uiState.insecureHttpPrompt != null ||
        uiState.siteBlockingPromptHost != null ||
        uiState.duplicateDownloadPrompt != null ||
        uiState.externalAppPrompt?.requiresConfirmation == true

/**
 * The single slot for app-level confirmations: one prompt card above the dock at a time, in this
 * priority order. Drawn above the dock, the page and the notification pills (which are hidden
 * while a prompt is shown).
 */
@Composable
internal fun DockPromptHost(uiState: BrowserUiState, onEvent: (BrowserUiEvent) -> Unit, isDark: Boolean) {
    val context = LocalContext.current
    val insecure = uiState.insecureHttpPrompt
    val blockingHost = uiState.siteBlockingPromptHost
    val external = uiState.externalAppPrompt?.takeIf { it.requiresConfirmation }
    when {
        // Forms come first so a prompt arriving meanwhile does not tear down what the user typed.
        uiState.showAddFavoriteDialog -> AddFavoriteDialog(
            isDark = isDark,
            onDismiss = { onEvent(BrowserUiEvent.DismissAddFavoriteDialog) },
            onConfirm = { title, url ->
                onEvent(BrowserUiEvent.AddFavorite(title, url))
                onEvent(BrowserUiEvent.DismissAddFavoriteDialog)
            },
        )
        uiState.showAddEditSearchEngineDialog -> AddEditSearchEngineDialog(
            isDark = isDark,
            existingEngines = uiState.searchEngines,
            engineToEdit = uiState.editingSearchEngine,
            onDismiss = { onEvent(BrowserUiEvent.DismissAddSearchEngineDialog) },
            onSave = { id, title, shortcut, urlTemplate ->
                onEvent(BrowserUiEvent.SaveSearchEngine(id = id, title = title, shortcut = shortcut, urlTemplate = urlTemplate))
            },
        )
        insecure != null -> InsecureHttpWarningDialog(
            isDark = isDark,
            url = insecure.url,
            onDismiss = { onEvent(BrowserUiEvent.DismissInsecureHttpWarning) },
            onConfirm = { onEvent(BrowserUiEvent.ConfirmInsecureHttpNavigation) },
        )
        blockingHost != null -> DisableBlockingDialog(
            isDark = isDark,
            host = blockingHost,
            onDismiss = { onEvent(DismissDisableBlockingDialog) },
            onConfirm = { allWorkspaces -> onEvent(ConfirmDisableBlocking(allWorkspaces)) },
        )
        uiState.duplicateDownloadPrompt != null -> DuplicateDownloadHost(uiState, isDark, onEvent)
        // Cancel and Close tab dismiss transiently so a later tap is offered again.
        external != null -> ExternalAppConfirmDialog(
            isDark = isDark,
            onOpen = {
                onEvent(BrowserUiEvent.OpenExternalApp(external))
                context.launchExternalApp(external)
            },
            onCloseTab = {
                onEvent(BrowserUiEvent.DismissExternalAppPrompt(ExternalAppDismissReason.TRANSIENT))
                onEvent(BrowserUiEvent.CloseTab(external.tabId))
            },
            onCancel = {
                onEvent(BrowserUiEvent.DismissExternalAppPrompt(ExternalAppDismissReason.TRANSIENT))
                // A held-back web link loads in the browser after all.
                external.webFallbackUrl?.let { onEvent(BrowserUiEvent.LoadUrl(it)) }
            },
        )
    }
}
