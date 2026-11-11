package com.jestk.browser.ui.viewmodel.events

import com.jestk.browser.domain.repository.RecentTabRepository
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.domain.repository.TabRepository
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.LibraryUiEvent
import com.jestk.browser.ui.viewmodel.SearchCoordinator
import com.jestk.browser.ui.viewmodel.TabCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class LibraryEventHandler(
    private val tabCoordinator: TabCoordinator,
    private val searchCoordinator: SearchCoordinator,
    private val settingsRepository: SettingsRepository,
    private val recentTabRepository: RecentTabRepository?,
    private val tabRepository: TabRepository,
    private val getCurrentState: () -> BrowserUiState,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun handle(event: LibraryUiEvent) {
        when (event) {
            is BrowserUiEvent.ShowAddFavoriteDialog -> {
                updateUiState { it.copy(showAddFavoriteDialog = true) }
            }

            is BrowserUiEvent.DismissAddFavoriteDialog -> {
                updateUiState { it.copy(showAddFavoriteDialog = false) }
            }

            is BrowserUiEvent.AddCurrentTabToFavorites -> {
                tabCoordinator.addCurrentToFavorites()
            }

            is BrowserUiEvent.AddFavorite -> {
                tabCoordinator.addFavorite(event.title, event.url, getCurrentState().activeWorkspaceId)
            }

            is BrowserUiEvent.RemoveFavorite -> {
                tabCoordinator.deleteFavorite(event.id)
            }

            is BrowserUiEvent.MoveFavoriteToWorkspace -> {
                tabCoordinator.moveFavoriteToWorkspace(event.favoriteId, event.targetWorkspaceId)
            }

            is BrowserUiEvent.ToggleFavoritesExpanded -> {
                updateUiState { it.copy(isFavoritesExpanded = !it.isFavoritesExpanded) }
            }

            is BrowserUiEvent.ClearAllHistory -> {
                searchCoordinator.clearAllHistory {
                    if (recentTabRepository != null) {
                        recentTabRepository.clearRecentTabs()
                    } else {
                        tabRepository.clearRecentTabs()
                    }
                }
            }

            is BrowserUiEvent.ToggleSearchHistoryExpanded -> {
                val target = !getCurrentState().isSearchHistoryExpanded
                updateUiState {
                    it.copy(
                        isSearchHistoryExpanded = target,
                        isLoadingMoreHistory = false,
                        hasMoreHistory = true,
                    )
                }
                searchCoordinator.updateHistorySuggestions(getCurrentState().searchQuery)
            }

            is BrowserUiEvent.SetSearchHistoryExpanded -> {
                updateUiState {
                    it.copy(
                        isSearchHistoryExpanded = event.expanded,
                        isLoadingMoreHistory = false,
                        hasMoreHistory = true,
                    )
                }
                searchCoordinator.updateHistorySuggestions(getCurrentState().searchQuery)
            }

            is BrowserUiEvent.LoadMoreSearchHistory -> {
                searchCoordinator.loadMoreSearchHistory()
            }

            is BrowserUiEvent.ShowAddSearchEngineDialog -> {
                updateUiState {
                    it.copy(
                        showAddEditSearchEngineDialog = true,
                        editingSearchEngine = event.engineToEdit,
                    )
                }
            }

            is BrowserUiEvent.DismissAddSearchEngineDialog -> {
                updateUiState {
                    it.copy(
                        showAddEditSearchEngineDialog = false,
                        editingSearchEngine = null,
                    )
                }
            }

            is BrowserUiEvent.SaveSearchEngine -> {
                searchCoordinator.saveSearchEngine(
                    id = event.id,
                    title = event.title,
                    shortcut = event.shortcut,
                    urlTemplate = event.urlTemplate,
                )
            }

            is BrowserUiEvent.DeleteSearchEngine -> {
                searchCoordinator.deleteSearchEngine(event.id)
            }

            is BrowserUiEvent.ReorderSearchEngines -> {
                searchCoordinator.reorderSearchEngines(event.orderedEngines)
            }

            is BrowserUiEvent.ToggleSearchPageEngineVisibility -> {
                val currentHidden = getCurrentState().settings.hiddenSearchPageEngineIds
                val updatedHidden =
                    if (event.isVisible) {
                        currentHidden - event.engineId
                    } else {
                        currentHidden + event.engineId
                    }
                updateUiState { state ->
                    state.copy(settings = state.settings.copy(hiddenSearchPageEngineIds = updatedHidden))
                }
                scope.launch(ioDispatcher) {
                    settingsRepository.setHiddenSearchPageEngineIds(updatedHidden)
                }
            }

            is BrowserUiEvent.SetSearchEngineSwitcherEditExpanded -> {
                updateUiState { it.copy(isSearchEngineSwitcherEditExpanded = event.expanded) }
            }
        }
    }
}
