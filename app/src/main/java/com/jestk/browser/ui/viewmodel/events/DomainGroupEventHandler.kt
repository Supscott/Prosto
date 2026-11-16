package com.jestk.browser.ui.viewmodel.events

import com.jestk.browser.domain.model.DOMAIN_GROUP_MIN_TABS
import com.jestk.browser.domain.model.DomainGroupKey
import com.jestk.browser.domain.model.folderTitleForDomain
import com.jestk.browser.domain.model.pickFolderColor
import com.jestk.browser.domain.model.ungroupedTabsByDomain
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.CreateDomainGroup
import com.jestk.browser.ui.viewmodel.DismissDomainGroup
import com.jestk.browser.ui.viewmodel.DomainGroupUiEvent
import com.jestk.browser.ui.viewmodel.TabCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Folder suggestions for sites with several open tabs. Creating goes through the regular folder
 * code path; the dock draws a folder where its first member sits, so the folder lands where the
 * first of the grouped tabs was.
 */
internal class DomainGroupEventHandler(
    private val tabCoordinator: TabCoordinator,
    private val getCurrentState: () -> BrowserUiState,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val scope: CoroutineScope,
) {
    fun handle(event: DomainGroupUiEvent) {
        when (event) {
            is CreateDomainGroup -> create(event.domain)
            is DismissDomainGroup -> updateUiState {
                val key = DomainGroupKey(it.activeWorkspaceId, event.domain)
                it.copy(domainGroupDismissals = it.domainGroupDismissals + (key to event.tabCount))
            }
        }
    }

    private fun create(domain: String) {
        val state = getCurrentState()
        val tabs = ungroupedTabsByDomain(state.tabs, state.activeWorkspaceId)[domain].orEmpty()
        if (tabs.size < DOMAIN_GROUP_MIN_TABS) return
        val color = pickFolderColor(state.folders, state.activeWorkspaceId)
        scope.launch {
            val folder = tabCoordinator.folders.createFolder(
                tabId = tabs.first().id,
                title = folderTitleForDomain(domain),
                color = color,
            )
            tabs.drop(1).forEach { tabCoordinator.folders.addTabToFolder(it.id, folder.id) }
        }
    }
}
