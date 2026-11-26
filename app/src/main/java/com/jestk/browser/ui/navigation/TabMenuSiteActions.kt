package com.jestk.browser.ui.navigation

import com.jestk.browser.domain.model.Tab
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.EnableBlockingForSite
import com.jestk.browser.ui.viewmodel.OpenDisableBlockingPrompt
import com.jestk.browser.ui.viewmodel.ToggleTabFavorite
import com.jestk.browser.ui.viewmodel.favoriteOf
import com.jestk.browser.ui.viewmodel.favoritablePageUrl
import com.jestk.browser.ui.viewmodel.isSiteAllowlisted
import com.jestk.browser.ui.viewmodel.webPageUrl

/**
 * The state and callbacks behind the favorite star and the blocking row of a tab's long-tap menu.
 * A null callback hides its item: both are for http(s) pages only, and the star is never offered on incognito tabs.
 */
data class TabMenuSiteActions(
    val isFavorite: Boolean = false,
    val onToggleFavorite: (() -> Unit)? = null,
    val isSiteAllowlisted: Boolean = false,
    val onToggleBlocking: (() -> Unit)? = null,
) {
    companion object {
        val None = TabMenuSiteActions()
    }
}

/** The menu actions for [tab], acting on that tab (and its workspace) whether or not it is the active one. */
fun tabMenuSiteActions(
    tab: Tab,
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
): TabMenuSiteActions {
    val url = uiState.webPageUrl(tab) ?: return TabMenuSiteActions.None
    val allowlisted = uiState.isSiteAllowlisted(url, tab.workspaceId)
    val canFavorite = uiState.favoritablePageUrl(tab) != null
    return TabMenuSiteActions(
        isFavorite = canFavorite && uiState.favoriteOf(tab) != null,
        onToggleFavorite = if (canFavorite) ({ onEvent(ToggleTabFavorite(tab.id)) }) else null,
        isSiteAllowlisted = allowlisted,
        onToggleBlocking = {
            onEvent(
                if (allowlisted) EnableBlockingForSite(url, tab.workspaceId) else OpenDisableBlockingPrompt(url, tab.workspaceId),
            )
        },
    )
}
