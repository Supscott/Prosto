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
        handleBackNavigation()
    }

    private fun handleBackNavigation() {
        val current = getCurrentState()
        if (current.cleanPlayerVideoId != null) {
            dispatch(BrowserUiEvent.CloseCleanPlayer)
            return
        }
        if (current.isFeedbackSheetVisible) {
            dispatch(BrowserUiEvent.CloseFeedbackSheet)
            return
        }
        if (current.isFolderSheetVisible) {
            dispatch(BrowserUiEvent.DismissFolderSheet)
            return
        }
        if (current.insecureHttpPrompt != null) {
            dispatch(BrowserUiEvent.DismissInsecureHttpWarning)
            return
        }
        if (current.blockedAdNavigation != null) {
            dispatch(BrowserUiEvent.DismissBlockedAdNavigation)
            return
        }
        if (current.isSearchEngineSwitcherEditExpanded) {
            updateUiState { it.copy(isSearchEngineSwitcherEditExpanded = false) }
            return
        }
        if (current.showAddEditSearchEngineDialog) {
            updateUiState { it.copy(showAddEditSearchEngineDialog = false, editingSearchEngine = null) }
            return
        }
        if (current.showAddFavoriteDialog) {
            updateUiState { it.copy(showAddFavoriteDialog = false) }
            return
        }
        if (current.selectedImageUrl != null) {
            updateUiState {
                it.copy(
                    selectedImageUrl = null,
                    imageContextMenuLinkUrl = null,
                    contextMenuAnchor = null,
                )
            }
            return
        }
        if (current.activeScreen != ScreenType.NONE) {
            updateUiState { it.copy(activeScreen = ScreenType.NONE) }
            return
        }
        if (current.linkContextMenuUrl != null) {
            updateUiState {
                it.copy(
                    linkContextMenuUrl = null,
                    contextMenuAnchor = null,
                )
            }
            return
        }
        if (current.isFullscreenCustomView) {
            dispatch(BrowserUiEvent.ExitFullscreen)
            return
        }
        if (current.isMenuExpanded) {
            updateUiState { it.copy(isMenuExpanded = false) }
            return
        }
        if (current.isSearchHistoryExpanded) {
            updateUiState {
                it.copy(
                    isSearchHistoryExpanded = false,
                    isLoadingMoreHistory = false,
                    hasMoreHistory = true,
                )
            }
            searchCoordinator.updateHistorySuggestions(current.searchQuery)
            return
        }
        val isDismissibleOverlay = current.activeOverlay != OverlayType.NONE && current.activeOverlay != OverlayType.HOME
        if (isDismissibleOverlay) {
            updateUiState {
                it.copy(
                    activeOverlay = OverlayType.NONE,
                    isSearchHistoryExpanded = false,
                    isLoadingMoreHistory = false,
                    hasMoreHistory = true,
                )
            }
            return
        }

        if (current.canGoBack) {
            emitNavigation(NavigationCommand.GoBack)
            return
        }

        // Web history is exhausted!
        val activeTabId = current.activeTabId
        if (activeTabId != null && activeTabId in current.externalAppTabIds) {
            // Like Chrome: a link from another app closes on Back and hands the user back to that
            // app, instead of closing the user's other tabs one by one.
            tabCoordinator.closeTab(activeTabId)
            emitNavigation(NavigationCommand.ReturnToCallerApp)
            return
        }
        if (current.tabs.size > 1) {
            current.activeTabId?.let { tabCoordinator.closeTab(it) }
        } else {
            val isCurrentTabBlankOrHome =
                current.currentUrl.isEmpty() ||
                    current.currentUrl == "about:blank" ||
                    current.activeOverlay == OverlayType.HOME

            if (!isCurrentTabBlankOrHome) {
                // If it was on a website, close website and return to clean Home tab
                current.activeTabId?.let { tabId ->
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
                        tabRepository.updateTabDetails(tabId, "", "", canGoBack = false, canGoForward = false)
                    }
                }
            } else {
                // History exhausted and already on clean Home tab: exit app cleanly without crashing
                emitNavigation(NavigationCommand.ExitApp)
            }
        }
    }
}
