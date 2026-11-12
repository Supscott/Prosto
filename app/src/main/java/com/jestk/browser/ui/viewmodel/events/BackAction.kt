package com.jestk.browser.ui.viewmodel.events

import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState

/** What a system Back press does in a given [BrowserUiState]; see [resolveBackPress]. */
internal sealed interface BackAction {
    /** Back closes something that already has its own event. */
    data class Dispatch(val event: BrowserUiEvent) : BackAction

    data object CloseSearchEngineSwitcherEdit : BackAction
    data object CloseAddEditSearchEngineDialog : BackAction
    data object CloseAddFavoriteDialog : BackAction
    data object CloseImageContextMenu : BackAction
    data object CloseScreen : BackAction
    data object CloseLinkContextMenu : BackAction
    data object CollapseMenu : BackAction
    data object CollapseSearchHistory : BackAction
    data object CloseOverlay : BackAction

    /** The page has web history: go back in the WebView. */
    data object GoBack : BackAction

    /** Like Chrome: a link from another app closes on Back and returns to that app. */
    data class ReturnToCallerApp(val tabId: String) : BackAction

    data class CloseTab(val tabId: String) : BackAction

    /** The only tab shows a website without history: turn it back into a clean Home tab. */
    data class ResetToHome(val tabId: String) : BackAction

    /** The only tab is already Home: leave the app. */
    data object ExitApp : BackAction

    data object Nothing : BackAction
}

/**
 * Decides what Back does, innermost layer first: transient UI (sheets, dialogs, menus,
 * overlays), then web history, then the tab itself. Pure: no side effects, no view model.
 */
internal fun resolveBackPress(state: BrowserUiState): BackAction {
    when {
        state.cleanPlayerVideoId != null -> return BackAction.Dispatch(BrowserUiEvent.CloseCleanPlayer)
        state.isFeedbackSheetVisible -> return BackAction.Dispatch(BrowserUiEvent.CloseFeedbackSheet)
        state.isFolderSheetVisible -> return BackAction.Dispatch(BrowserUiEvent.DismissFolderSheet)
        state.insecureHttpPrompt != null -> return BackAction.Dispatch(BrowserUiEvent.DismissInsecureHttpWarning)
        state.blockedAdNavigation != null -> return BackAction.Dispatch(BrowserUiEvent.DismissBlockedAdNavigation)
        state.isSearchEngineSwitcherEditExpanded -> return BackAction.CloseSearchEngineSwitcherEdit
        state.showAddEditSearchEngineDialog -> return BackAction.CloseAddEditSearchEngineDialog
        state.showAddFavoriteDialog -> return BackAction.CloseAddFavoriteDialog
        state.selectedImageUrl != null -> return BackAction.CloseImageContextMenu
        state.activeScreen != ScreenType.NONE -> return BackAction.CloseScreen
        state.linkContextMenuUrl != null -> return BackAction.CloseLinkContextMenu
        state.isFullscreenCustomView -> return BackAction.Dispatch(BrowserUiEvent.ExitFullscreen)
        state.isMenuExpanded -> return BackAction.CollapseMenu
        state.isSearchHistoryExpanded -> return BackAction.CollapseSearchHistory
        state.activeOverlay != OverlayType.NONE && state.activeOverlay != OverlayType.HOME ->
            return BackAction.CloseOverlay
        state.canGoBack -> return BackAction.GoBack
    }

    // Web history is exhausted.
    val activeTabId = state.activeTabId ?: return BackAction.Nothing
    if (activeTabId in state.externalAppTabIds) return BackAction.ReturnToCallerApp(activeTabId)
    if (state.tabs.size > 1) return BackAction.CloseTab(activeTabId)

    val isBlankOrHome = state.currentUrl.isEmpty() ||
        state.currentUrl == "about:blank" ||
        state.activeOverlay == OverlayType.HOME
    return if (isBlankOrHome) BackAction.ExitApp else BackAction.ResetToHome(activeTabId)
}
