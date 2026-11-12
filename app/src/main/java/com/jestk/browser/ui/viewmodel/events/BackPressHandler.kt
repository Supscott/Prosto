package com.jestk.browser.ui.viewmodel.events

import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.domain.repository.TabRepository
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.NavigationCommand
import com.jestk.browser.ui.viewmodel.SearchCoordinator
import com.jestk.browser.ui.viewmodel.TabCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class BackPressHandler(
    private val tabCoordinator: TabCoordinator,
    private val searchCoordinator: SearchCoordinator,
    private val tabRepository: TabRepository,
    private val getCurrentState: () -> BrowserUiState,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val emitNavigation: (NavigationCommand) -> Unit,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val dispatch: (BrowserUiEvent) -> Unit,
) {
    fun handle() {
        when (val action = resolveBackPress(getCurrentState())) {
            is BackAction.Dispatch -> dispatch(action.event)
            BackAction.CloseSearchEngineSwitcherEdit ->
                updateUiState { it.copy(isSearchEngineSwitcherEditExpanded = false) }
            BackAction.CloseAddEditSearchEngineDialog ->
                updateUiState { it.copy(showAddEditSearchEngineDialog = false, editingSearchEngine = null) }
            BackAction.CloseAddFavoriteDialog -> updateUiState { it.copy(showAddFavoriteDialog = false) }
            BackAction.CloseImageContextMenu -> updateUiState {
                it.copy(
                    selectedImageUrl = null,
                    imageContextMenuLinkUrl = null,
                    contextMenuAnchor = null,
                )
            }
            BackAction.CloseScreen -> updateUiState { it.copy(activeScreen = ScreenType.NONE) }
            BackAction.CloseLinkContextMenu -> updateUiState {
                it.copy(
                    linkContextMenuUrl = null,
                    contextMenuAnchor = null,
                )
            }
            BackAction.CollapseMenu -> updateUiState { it.copy(isMenuExpanded = false) }
            BackAction.CollapseSearchHistory -> {
                val query = getCurrentState().searchQuery
                updateUiState {
                    it.copy(
                        isSearchHistoryExpanded = false,
                        isLoadingMoreHistory = false,
                        hasMoreHistory = true,
                    )
                }
                searchCoordinator.updateHistorySuggestions(query)
            }
            BackAction.CloseOverlay -> updateUiState {
                it.copy(
                    activeOverlay = OverlayType.NONE,
                    isSearchHistoryExpanded = false,
                    isLoadingMoreHistory = false,
                    hasMoreHistory = true,
                )
            }
            BackAction.GoBack -> emitNavigation(NavigationCommand.GoBack)
            is BackAction.ReturnToCallerApp -> {
                tabCoordinator.closeTab(action.tabId)
                emitNavigation(NavigationCommand.ReturnToCallerApp)
            }
            is BackAction.CloseTab -> tabCoordinator.closeTab(action.tabId)
            is BackAction.ResetToHome -> {
                updateUiState {
                    it.copy(
                        currentUrl = "",
                        pageTitle = "",
                        canGoBack = false,
                        canGoForward = false,
                        activeOverlay = OverlayType.HOME,
                    )
                }
                scope.launch(ioDispatcher) {
                    tabRepository.updateTabDetails(action.tabId, "", "", canGoBack = false, canGoForward = false)
                }
            }
            BackAction.ExitApp -> emitNavigation(NavigationCommand.ExitApp)
            BackAction.Nothing -> Unit
        }
    }
}
