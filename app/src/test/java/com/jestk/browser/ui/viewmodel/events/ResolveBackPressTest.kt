package com.jestk.browser.ui.viewmodel.events

import com.google.common.truth.Truth.assertWithMessage
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.HomeUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Test

class ResolveBackPressTest {

    private data class Case(val name: String, val state: BrowserUiState, val expected: BackAction)

    private val oneTab = listOf(Tab(id = "a"))
    private val twoTabs = listOf(Tab(id = "a"), Tab(id = "b"))

    /** A single tab on a website with no web history and nothing open over it. */
    private val onWebsite = BrowserUiState(
        tabs = oneTab,
        activeTabId = "a",
        currentUrl = "https://example.com",
        activeOverlay = OverlayType.NONE,
    )

    private val cases = listOf(
        Case(
            "clean player closes first",
            onWebsite.copy(cleanPlayerVideoId = "v", isMenuExpanded = true, canGoBack = true),
            BackAction.Dispatch(BrowserUiEvent.CloseCleanPlayer),
        ),
        Case(
            "tutorial stories",
            onWebsite.copy(isTutorialVisible = true),
            BackAction.Dispatch(HomeUiEvent.CloseTutorial),
        ),
        Case(
            "feedback sheet",
            onWebsite.copy(isFeedbackSheetVisible = true),
            BackAction.Dispatch(BrowserUiEvent.CloseFeedbackSheet),
        ),
        Case(
            "folder sheet",
            onWebsite.copy(isFolderSheetVisible = true),
            BackAction.Dispatch(BrowserUiEvent.DismissFolderSheet),
        ),
        Case(
            "search engine switcher edit popup",
            onWebsite.copy(isSearchEngineSwitcherEditExpanded = true),
            BackAction.CloseSearchEngineSwitcherEdit,
        ),
        Case(
            "add/edit search engine dialog",
            onWebsite.copy(showAddEditSearchEngineDialog = true),
            BackAction.CloseAddEditSearchEngineDialog,
        ),
        Case(
            "add favorite dialog",
            onWebsite.copy(showAddFavoriteDialog = true),
            BackAction.CloseAddFavoriteDialog,
        ),
        Case(
            "image context menu wins over link context menu",
            onWebsite.copy(selectedImageUrl = "https://example.com/a.png", linkContextMenuUrl = "https://x"),
            BackAction.CloseImageContextMenu,
        ),
        Case(
            "settings or downloads screen",
            onWebsite.copy(activeScreen = ScreenType.entries.first { it != ScreenType.NONE }),
            BackAction.CloseScreen,
        ),
        Case(
            "link context menu",
            onWebsite.copy(linkContextMenuUrl = "https://x"),
            BackAction.CloseLinkContextMenu,
        ),
        Case(
            "fullscreen video wins over the menu",
            onWebsite.copy(isFullscreenCustomView = true, isMenuExpanded = true),
            BackAction.Dispatch(BrowserUiEvent.ExitFullscreen),
        ),
        Case(
            "expanded dock menu",
            onWebsite.copy(isMenuExpanded = true),
            BackAction.CollapseMenu,
        ),
        Case(
            "expanded search history wins over the search overlay",
            onWebsite.copy(isSearchHistoryExpanded = true, activeOverlay = OverlayType.entries.first { it != OverlayType.NONE && it != OverlayType.HOME }),
            BackAction.CollapseSearchHistory,
        ),
        Case(
            "dismissible overlay wins over web history",
            onWebsite.copy(activeOverlay = OverlayType.entries.first { it != OverlayType.NONE && it != OverlayType.HOME }, canGoBack = true),
            BackAction.CloseOverlay,
        ),
        Case(
            "web history",
            onWebsite.copy(canGoBack = true),
            BackAction.GoBack,
        ),
        Case(
            "link from another app returns to that app even with other tabs",
            onWebsite.copy(tabs = twoTabs, externalAppTabIds = setOf("a")),
            BackAction.ReturnToCallerApp("a"),
        ),
        Case(
            "several tabs: the active one closes",
            onWebsite.copy(tabs = twoTabs),
            BackAction.CloseTab("a"),
        ),
        Case(
            "only tab on a website becomes Home",
            onWebsite,
            BackAction.ResetToHome("a"),
        ),
        Case(
            "only tab already Home exits",
            onWebsite.copy(activeOverlay = OverlayType.HOME),
            BackAction.ExitApp,
        ),
        Case(
            "only tab blank exits",
            onWebsite.copy(currentUrl = "about:blank"),
            BackAction.ExitApp,
        ),
        Case(
            "no active tab does nothing",
            onWebsite.copy(activeTabId = null, tabs = twoTabs),
            BackAction.Nothing,
        ),
    )

    @Test
    fun resolveBackPress_followsTheLayerOrder() {
        for (case in cases) {
            assertWithMessage(case.name).that(resolveBackPress(case.state)).isEqualTo(case.expected)
        }
    }
}
