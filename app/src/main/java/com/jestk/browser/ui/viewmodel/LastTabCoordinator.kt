package com.jestk.browser.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val SESSION_WAIT_MS = 5_000L

/**
 * Opens a Home tab when the app comes back after a long time away and offers a "Last tab" pill
 * that returns to the tab the user left. See [ResumeNewTabPolicy] for when this applies.
 */
internal class LastTabCoordinator(
    private val policy: ResumeNewTabPolicy,
    private val state: StateFlow<BrowserUiState>,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val openNewTab: () -> Unit,
    private val selectTab: (String) -> Unit,
    private val closeTab: (String) -> Unit,
    private val clearUndoSnackbar: () -> Unit,
) {
    /** The app returned to the foreground; [stamp] is when it left (null on a first launch). */
    fun onReturnedToForeground(
        stamp: BackgroundStamp?,
        returnedWithLink: Boolean,
        mediaActiveNow: Boolean,
    ) {
        if (stamp == null) return
        scope.launch {
            // After process death the tabs load a moment after the Activity starts.
            val loaded = withTimeoutOrNull(SESSION_WAIT_MS) { state.first { it.activeTabId != null } } ?: return@launch
            val previous = loaded.activeTab ?: return@launch
            val open =
                policy.shouldOpenNewTab(
                    stamp = stamp,
                    nowMs = now(),
                    hasTabs = loaded.tabs.isNotEmpty(),
                    activeTabIsHome = previous.isHomeTab,
                    returnedWithLink = returnedWithLink,
                    mediaActiveNow = mediaActiveNow,
                )
            if (!open) return@launch
            openNewTab()
            val withNewTab =
                withTimeoutOrNull(SESSION_WAIT_MS) {
                    state.first { it.activeTabId != null && it.activeTabId != previous.id }
                } ?: return@launch
            updateUiState { it.copy(lastTabPrompt = LastTabPrompt(previous.id, withNewTab.activeTabId!!)) }
        }
    }

    fun handle(event: LastTabUiEvent) {
        when (event) {
            RestoreLastTab -> restore()
            DismissLastTabPrompt -> clear()
        }
    }

    private fun restore() {
        val prompt = state.value.lastTabPrompt ?: return
        clear()
        val tabs = state.value.tabs
        if (tabs.none { it.id == prompt.previousTabId }) return
        selectTab(prompt.previousTabId)
        if (tabs.any { it.id == prompt.newTabId && it.isHomeTab }) {
            closeTab(prompt.newTabId)
            clearUndoSnackbar()
        }
    }

    private fun clear() {
        updateUiState { it.copy(lastTabPrompt = null) }
    }

    companion object {
        internal fun create(
            state: MutableStateFlow<BrowserUiState>,
            tabs: TabCoordinator,
            scope: CoroutineScope,
            now: () -> Long,
            policy: ResumeNewTabPolicy = ResumeNewTabPolicy(),
        ) = LastTabCoordinator(
            policy = policy,
            state = state,
            updateUiState = { transform -> state.update(transform) },
            scope = scope,
            now = now,
            openNewTab = { tabs.opening.openNewTab("") },
            selectTab = { tabs.activation.selectTab(it) },
            closeTab = { tabs.closing.closeTab(it) },
            clearUndoSnackbar = { tabs.closing.clearRecentlyClosedTab() },
        )
    }
}
