package com.jestk.browser.ui.viewmodel.events

import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.FavoriteNotice
import com.jestk.browser.ui.viewmodel.TabCoordinator
import com.jestk.browser.ui.viewmodel.favoriteOf
import com.jestk.browser.ui.viewmodel.favoritablePageUrl
import com.jestk.browser.ui.viewmodel.tabById

/** The tab menu's favorite star and the pill that confirms it. */
internal class PageFavoriteToggle(
    private val tabCoordinator: TabCoordinator,
    private val getCurrentState: () -> BrowserUiState,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
) {
    /**
     * Never reused, even after the notice was dismissed: the pill restarts its timer on a new id, and a
     * notice that reuses the id of one still fading out would inherit the old timer.
     */
    private var lastNoticeId = 0L

    fun toggle(tabId: String) {
        val state = getCurrentState()
        val tab = state.tabById(tabId) ?: return
        val saved = state.favoriteOf(tab)
        val added =
            when {
                saved != null -> {
                    tabCoordinator.favorites.deleteFavorite(saved.id)
                    false
                }
                state.favoritablePageUrl(tab) != null -> {
                    tabCoordinator.favorites.addTabToFavorites(tabId)
                    true
                }
                else -> return
            }
        val noticeId = ++lastNoticeId
        updateUiState { it.copy(favoriteNotice = FavoriteNotice(noticeId, added)) }
    }

    fun dismissNotice() {
        updateUiState { it.copy(favoriteNotice = null) }
    }
}
