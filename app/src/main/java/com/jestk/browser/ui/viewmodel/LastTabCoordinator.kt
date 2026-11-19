package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.Tab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Opens a Home tab when the app comes back after a long time away and offers a "Last tab" pill
 * that returns to the tab the user left. See [ResumeNewTabPolicy] for when this applies.
 *
 * The switch is synchronous so the first frame after the Activity starts already shows the new
 * tab: with tabs in memory it happens inside [onReturnedToForeground]; after process death it is
 * folded into the session restore through [installRestoreHook].
 */
internal class LastTabCoordinator(
    private val policy: ResumeNewTabPolicy,
    private val state: StateFlow<BrowserUiState>,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val now: () -> Long,
    private val openHomeTab: () -> String,
    private val installRestoreHook: (((Tab) -> Boolean)?) -> Unit,
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
        fun shouldOpen(activeTabIsHome: Boolean) =
            policy.shouldOpenNewTab(
                stamp = stamp,
                nowMs = now(),
                hasTabs = true,
                activeTabIsHome = activeTabIsHome,
                returnedWithLink = returnedWithLink,
                mediaActiveNow = mediaActiveNow,
            )

        val loaded = state.value
        if (loaded.activeTabId == null) {
            // After process death the tabs load after the Activity starts: decide when they do.
            installRestoreHook { restored ->
                installRestoreHook(null)
                shouldOpen(restored.isHomeTab)
            }
            return
        }
        val previous = loaded.activeTab ?: return
        if (loaded.tabs.isEmpty() || !shouldOpen(previous.isHomeTab)) return
        val newTabId = openHomeTab()
        if (newTabId == previous.id) return
        updateUiState { it.copy(lastTabPrompt = LastTabPrompt(previous.id, newTabId)) }
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
            now: () -> Long,
            policy: ResumeNewTabPolicy = ResumeNewTabPolicy(),
        ) = LastTabCoordinator(
            policy = policy,
            state = state,
            updateUiState = { transform -> state.update(transform) },
            now = now,
            openHomeTab = { tabs.opening.openHomeTabNow() },
            installRestoreHook = { tabs.sessionSync.resumeOnRestore = it },
            selectTab = { tabs.activation.selectTab(it) },
            closeTab = { tabs.closing.closeTab(it) },
            clearUndoSnackbar = { tabs.closing.clearRecentlyClosedTab() },
        )
    }
}
