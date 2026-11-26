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
import com.jestk.browser.ui.viewmodel.currentAllowlistSite
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
                updateUiState { it.copy(siteBlockingPromptHost = site) }
            }

            is ConfirmDisableBlocking -> {
                val state = getCurrentState()
                val site = state.siteBlockingPromptHost ?: return
                updateUiState { it.copy(siteBlockingPromptHost = null) }
                val workspaceId = if (event.allWorkspaces) null else state.activeWorkspaceId
                change(reload = state.currentAllowlistSite == site) { it.add(site, workspaceId) }
            }

            is DismissDisableBlockingPrompt -> updateUiState { it.copy(siteBlockingPromptHost = null) }

            is EnableBlockingForSite -> {
                val state = getCurrentState()
                val site = allowlistSiteOf(event.url) ?: return
                // The tab menu acts on any tab; only the active tab can be reloaded, the others pick it up on their next load.
                change(reload = state.currentAllowlistSite == site) { repo ->
                    repo.observe().first()
                        .filter { it.covers(site, state.activeWorkspaceId) }
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

    private fun change(reload: Boolean, block: suspend (SiteAllowlistRepository) -> Unit) {
        val repo = repository ?: return
        scope.launch(ioDispatcher) {
            block(repo)
            snapshot.update(repo.observe().first())
            if (reload) emitNavigation(NavigationCommand.Reload)
        }
    }
}
