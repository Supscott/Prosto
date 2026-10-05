package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.repository.HistoryRepository
import com.jestk.browser.domain.repository.SearchEngineRepository
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.engine.FaviconManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Coordinator for managing search engines, search history queries, and suggestions pagination.
 */
internal class SearchCoordinator(
    private val historyRepository: HistoryRepository,
    private val searchEngineRepository: SearchEngineRepository?,
    private val settingsRepository: SettingsRepository,
    private val getCurrentState: () -> BrowserUiState,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val onSelectTab: (String) -> Unit,
    private val onOpenNewTab: (String) -> Unit,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private var historyJob: Job? = null

    companion object {
        private const val HISTORY_PAGE_SIZE = 20
    }

    fun observeDatabaseFlows() {
        scope.launch(ioDispatcher) {
            settingsRepository.getSettings().collectLatest { settings ->
                updateUiState { state ->
                    val resolvedEngine = state.searchEngines.firstOrNull {
                        it.id.equals(settings.searchEngine.id, ignoreCase = true) ||
                            it.name.equals(settings.searchEngine.id, ignoreCase = true) ||
                            it.displayName.equals(settings.searchEngine.displayName, ignoreCase = true)
                    } ?: settings.searchEngine
                    state.copy(settings = settings.copy(searchEngine = resolvedEngine))
                }
            }
        }

        if (searchEngineRepository != null) {
            scope.launch(ioDispatcher) {
                searchEngineRepository.getSearchEngines().collectLatest { engines ->
                    updateUiState { state ->
                        val currentSelected = state.settings.searchEngine
                        val updatedSelected = engines.firstOrNull { it.id == currentSelected.id }
                            ?: engines.firstOrNull()
                            ?: currentSelected
                        state.copy(
                            searchEngines = engines,
                            settings = state.settings.copy(searchEngine = updatedSelected),
                        )
                    }
                }
            }
        }
    }

    fun updateHistorySuggestions(query: String) {
        historyJob?.cancel()
        historyJob = scope.launch(ioDispatcher) {
            val isExpanded = getCurrentState().isSearchHistoryExpanded
            if (isExpanded) {
                updateUiState { it.copy(isLoadingMoreHistory = false) }
                val initialPage = if (query.isBlank()) {
                    historyRepository.getPagedHistory(limit = HISTORY_PAGE_SIZE, offset = 0)
                } else {
                    historyRepository.searchHistoryPaged(query = query, limit = HISTORY_PAGE_SIZE, offset = 0)
                }
                updateUiState {
                    it.copy(
                        searchHistorySuggestions = initialPage,
                        hasMoreHistory = initialPage.size >= HISTORY_PAGE_SIZE,
                        isLoadingMoreHistory = false,
                    )
                }
            } else {
                val historyFlow = if (query.isBlank()) {
                    historyRepository.getRecentHistory(10)
                } else {
                    historyRepository.searchHistory(query)
                }
                historyFlow.collectLatest { results ->
                    updateUiState {
                        it.copy(
                            searchHistorySuggestions = results,
                            hasMoreHistory = true,
                            isLoadingMoreHistory = false,
                        )
                    }
                }
            }
        }
    }

    fun loadMoreSearchHistory() {
        val current = getCurrentState()
        if (!current.isSearchHistoryExpanded) return
        if (current.isLoadingMoreHistory) return
        if (!current.hasMoreHistory) return

        updateUiState { it.copy(isLoadingMoreHistory = true) }
        scope.launch(ioDispatcher) {
            try {
                val currentSuggestions = getCurrentState().searchHistorySuggestions
                val query = getCurrentState().searchQuery
                val offset = currentSuggestions.size
                val nextPage = if (query.isBlank()) {
                    historyRepository.getPagedHistory(limit = HISTORY_PAGE_SIZE, offset = offset)
                } else {
                    historyRepository.searchHistoryPaged(query = query, limit = HISTORY_PAGE_SIZE, offset = offset)
                }

                updateUiState { state ->
                    val combined = (state.searchHistorySuggestions + nextPage).distinctBy { it.id }
                    state.copy(
                        searchHistorySuggestions = combined,
                        isLoadingMoreHistory = false,
                        hasMoreHistory = nextPage.size >= HISTORY_PAGE_SIZE,
                    )
                }
            } catch (_: Exception) {
                updateUiState { it.copy(isLoadingMoreHistory = false) }
            }
        }
    }

    fun deleteHistoryItem(id: Long) {
        updateUiState { state ->
            state.copy(
                searchHistorySuggestions = state.searchHistorySuggestions.filterNot { it.id == id },
            )
        }
        scope.launch(ioDispatcher) {
            historyRepository.deleteHistory(id)
        }
    }

    fun clearAllHistory(onClearRecentTabs: suspend () -> Unit) {
        updateUiState {
            it.copy(
                recentTabs = emptyList(),
                searchHistorySuggestions = emptyList(),
                isLoadingMoreHistory = false,
                hasMoreHistory = false,
            )
        }
        scope.launch(ioDispatcher) {
            historyRepository.clearHistory()
            onClearRecentTabs()
        }
    }

    fun switchSearchEngineForQuery(targetEngine: SearchEngine, explicitQuery: String?) {
        val current = getCurrentState()
        val currentCandidate = current.currentUrl.ifEmpty { current.activeTab?.url.orEmpty() }
        val currentMatch = SearchEngine.findMatchingEngineAndQuery(currentCandidate, current.searchEngines)
        val query = explicitQuery ?: currentMatch?.second ?: return

        if (currentMatch?.first?.id == targetEngine.id) return

        val activeId = current.activeTabId
        val matchingTab = current.tabs
            .filter { tab ->
                if (tab.id == activeId) return@filter false
                val tabMatch = SearchEngine.findMatchingEngineAndQuery(tab.url, current.searchEngines)
                tabMatch != null &&
                    tabMatch.first.id == targetEngine.id &&
                    tabMatch.second.equals(query, ignoreCase = true)
            }
            .maxByOrNull { it.lastActiveTimestamp }

        if (matchingTab != null) {
            onSelectTab(matchingTab.id)
        } else {
            val targetUrl = targetEngine.buildSearchUrl(query)
            onOpenNewTab(targetUrl)
        }
    }

    fun saveSearchEngine(
        id: String?,
        title: String,
        shortcut: String?,
        urlTemplate: String,
    ) {
        val trimmedTitle = title.trim()
        val trimmedShortcut = shortcut?.trim()?.takeIf { it.isNotBlank() }
        val trimmedUrl = urlTemplate.trim()

        val normalizedUrl = when {
            trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://") -> trimmedUrl
            else -> "https://$trimmedUrl"
        }

        val finalUrl = if (normalizedUrl.contains("%s")) {
            normalizedUrl
        } else {
            "$normalizedUrl%s"
        }

        val currentEngines = getCurrentState().searchEngines
        val existing = id?.let { engineId -> currentEngines.firstOrNull { it.id == engineId } }

        val iconUrl = if (existing != null && existing.isBuiltIn && existing.searchUrlTemplate == finalUrl) {
            existing.iconUrl
        } else {
            FaviconManager.extractFallbackFaviconUrl(finalUrl)
                ?: existing?.iconUrl
                ?: "file:///android_asset/icons/google.svg"
        }

        val engineToSave = if (existing != null) {
            existing.copy(
                displayName = trimmedTitle,
                searchUrlTemplate = finalUrl,
                shortcut = trimmedShortcut,
                iconUrl = iconUrl,
            )
        } else {
            SearchEngine(
                id = java.util.UUID.randomUUID().toString(),
                displayName = trimmedTitle,
                searchUrlTemplate = finalUrl,
                iconUrl = iconUrl,
                shortcut = trimmedShortcut,
                orderPosition = currentEngines.size,
                isBuiltIn = false,
            )
        }

        val updatedList = if (existing != null) {
            currentEngines.map { if (it.id == engineToSave.id) engineToSave else it }
        } else {
            currentEngines + engineToSave
        }

        updateUiState { state ->
            val updatedSettings = if (state.settings.searchEngine.id == engineToSave.id) {
                state.settings.copy(searchEngine = engineToSave)
            } else {
                state.settings
            }
            state.copy(
                searchEngines = updatedList,
                settings = updatedSettings,
                showAddEditSearchEngineDialog = false,
                editingSearchEngine = null,
            )
        }

        scope.launch(ioDispatcher) {
            searchEngineRepository?.saveSearchEngine(engineToSave)
            if (getCurrentState().settings.searchEngine.id == engineToSave.id) {
                settingsRepository.setSearchEngine(engineToSave)
            }
        }
    }

    fun deleteSearchEngine(id: String) {
        val currentEngines = getCurrentState().searchEngines
        if (currentEngines.size <= 1) return

        val remainingEngines = currentEngines.filterNot { it.id == id }
        val wasSelected = getCurrentState().settings.searchEngine.id == id
        val fallbackEngine = if (wasSelected) remainingEngines.first() else getCurrentState().settings.searchEngine

        updateUiState { state ->
            state.copy(
                searchEngines = remainingEngines,
                settings = state.settings.copy(searchEngine = fallbackEngine),
            )
        }

        scope.launch(ioDispatcher) {
            searchEngineRepository?.deleteSearchEngine(id)
            if (wasSelected) {
                settingsRepository.setSearchEngine(fallbackEngine)
            }
        }
    }

    fun reorderSearchEngines(ordered: List<SearchEngine>) {
        val updated = ordered.mapIndexed { index, engine -> engine.copy(orderPosition = index) }
        updateUiState { it.copy(searchEngines = updated) }
        scope.launch(ioDispatcher) {
            searchEngineRepository?.reorderSearchEngines(updated)
        }
    }

    fun setSearchEngine(engine: SearchEngine) {
        updateUiState { it.copy(settings = it.settings.copy(searchEngine = engine)) }
        scope.launch(ioDispatcher) {
            settingsRepository.setSearchEngine(engine)
        }
    }
}
