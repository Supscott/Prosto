package com.jestk.browser.ui.viewmodel.events

import com.jestk.browser.domain.model.covers
import com.jestk.browser.domain.repository.SiteAllowlistRepository
import com.jestk.browser.engine.adblock.SiteAllowlist
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.ConfirmDisableBlocking
import com.jestk.browser.ui.viewmodel.DismissDisableBlockingPrompt
import com.jestk.browser.ui.viewmodel.EnableBlockingForSite
import com.jestk.browser.ui.viewmodel.NavigationCommand
import com.jestk.browser.ui.viewmodel.OpenDisableBlockingPrompt
import com.jestk.browser.ui.viewmodel.RemoveSiteAllowlistEntry
import com.jestk.browser.ui.viewmodel.SiteAllowlistUiEvent
import com.jestk.browser.ui.viewmodel.allowlistSiteOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The per-site content-blocking switch: the menu item and its prompt, the "Allow for this site"
 * pill action and the list for the settings screen. Entries are keyed by workspace id (PLAN Q8).
 * After a change the in-memory [SiteAllowlist] snapshot is refreshed before the page reloads, so
 * the reload already sees it.
 */
internal class SiteAllowlistEventHandler(
    private val repository: SiteAllowlistRepository?,
    private val getCurrentState: () -> BrowserUiState,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val emitNavigation: (NavigationCommand) -> Unit,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val snapshot: SiteAllowlist = SiteAllowlist.shared,
) {
    /** Mirrors the persisted entries into [BrowserUiState.siteAllowlist]. */
    fun observe() {
        val repo = repository ?: return
        scope.launch(ioDispatcher) {
            repo.observe().collect { entries -> updateUiState { it.copy(siteAllowlist = entries) } }
        }
    }

    fun handle(event: SiteAllowlistUiEvent) {
        when (event) {
            is OpenDisableBlockingPrompt -> {
                val site = allowlistSiteOf(event.url) ?: return
                updateUiState { it.copy(siteBlockingPromptHost = site, siteBlockingPromptWorkspaceId = event.workspaceId) }
            }

            is ConfirmDisableBlocking -> {
                val state = getCurrentState()
                val site = state.siteBlockingPromptHost ?: return
                updateUiState { it.copy(siteBlockingPromptHost = null, siteBlockingPromptWorkspaceId = null) }
                // The workspace of the tab that opened the prompt, not the active one.
                val workspaceId = if (event.allWorkspaces) null else state.siteBlockingPromptWorkspaceId ?: state.activeWorkspaceId
                change(reload = true) { it.add(site, workspaceId) }
            }

            is DismissDisableBlockingPrompt ->
                updateUiState { it.copy(siteBlockingPromptHost = null, siteBlockingPromptWorkspaceId = null) }

            is EnableBlockingForSite -> {
                val site = allowlistSiteOf(event.url) ?: return
                change(reload = true) { repo ->
                    repo.observe().first()
                        .filter { it.covers(site, event.workspaceId) }
                        .forEach { repo.remove(it.host, it.workspaceId) }
                }
            }

            is RemoveSiteAllowlistEntry -> change(reload = false) { it.remove(event.host, event.workspaceId) }
        }
    }

    /** The pill's "Allow for this site": blocking goes off for the opener's site in the active workspace. */
    fun allowFromPopup(openerUrl: String) {
        val site = allowlistSiteOf(openerUrl) ?: return
        val workspaceId = getCurrentState().activeWorkspaceId
        change(reload = false) { it.add(site, workspaceId) }
    }

    /**
     * With [reload], every open tab whose blocking state the change flipped reloads (active or not).
     * The entries and the tab list are read when the user acts, so a tab switch while the write runs cannot skew them.
     */
    private fun change(reload: Boolean, block: suspend (SiteAllowlistRepository) -> Unit) {
        val repo = repository ?: return
        val stateAtClick = getCurrentState()
        scope.launch(ioDispatcher) {
            block(repo)
            val after = repo.observe().first()
            snapshot.update(after)
            if (reload) {
                tabsAffectedByAllowlistChange(stateAtClick, stateAtClick.siteAllowlist, after)
                    .forEach { emitNavigation(NavigationCommand.ReloadTab(it)) }
            }
        }
    }
}
