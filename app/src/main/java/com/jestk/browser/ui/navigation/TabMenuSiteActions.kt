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

/**
 * The state and callbacks behind the favorite star and the blocking row of a tab's long-tap menu.
 * A null callback hides its item: both are for http(s) pages only.
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

/** The menu actions for [tab], acting on that tab whether or not it is the active one. */
fun tabMenuSiteActions(
    tab: Tab,
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
): TabMenuSiteActions {
    val url = uiState.favoritablePageUrl(tab) ?: return TabMenuSiteActions.None
    val allowlisted = uiState.isSiteAllowlisted(url)
    return TabMenuSiteActions(
        isFavorite = uiState.favoriteOf(tab) != null,
        onToggleFavorite = { onEvent(ToggleTabFavorite(tab.id)) },
        isSiteAllowlisted = allowlisted,
        onToggleBlocking = { onEvent(if (allowlisted) EnableBlockingForSite(url) else OpenDisableBlockingPrompt(url)) },
    )
}
