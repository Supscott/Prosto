package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.DomainGroupKey
import com.jestk.browser.domain.model.DomainGroupSuggestion
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.suggestDomainGroup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The only inputs of [suggestDomainGroup]; title, favicon, theme and progress changes leave it equal. */
private data class DomainGroupInput(
    val tabs: List<Tab>,
    val workspaceId: String,
    val dismissals: Map<DomainGroupKey, Int>,
)

private fun BrowserUiState.domainGroupInput() = DomainGroupInput(
    tabs = tabs.map {
        Tab(id = it.id, url = it.url, lastActiveTimestamp = it.lastActiveTimestamp, folderId = it.folderId, workspaceId = it.workspaceId)
    },
    workspaceId = activeWorkspaceId,
    dismissals = domainGroupDismissals,
)

/** The folder suggestion, recomputed only when a tab's site, folder, workspace or activity changes. */
internal fun Flow<BrowserUiState>.domainGroupSuggestions(): Flow<DomainGroupSuggestion?> =
    map { it.domainGroupInput() }
        .distinctUntilChanged()
        .map { suggestDomainGroup(it.tabs, it.workspaceId, it.dismissals) }
        .distinctUntilChanged()

/** Keeps [BrowserUiState.domainGroupSuggestion] current, computing it on [dispatcher] instead of in composition. */
internal fun CoroutineScope.launchDomainGroupSuggestions(
    state: MutableStateFlow<BrowserUiState>,
    dispatcher: CoroutineDispatcher,
) = launch {
    state.domainGroupSuggestions().flowOn(dispatcher).collect { suggestion ->
        state.update { it.copy(domainGroupSuggestion = suggestion) }
    }
}
