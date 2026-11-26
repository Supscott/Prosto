package com.jestk.browser.ui.viewmodel.events

import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.FavoriteNotice
import com.jestk.browser.ui.viewmodel.TabCoordinator
import com.jestk.browser.ui.viewmodel.currentPageFavorite
import com.jestk.browser.ui.viewmodel.favoritablePageUrl

/** The menu's "Add to / Remove from favorites" item and the pill that confirms it. */
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

    fun toggle() {
        val state = getCurrentState()
        val saved = state.currentPageFavorite
        val added =
            when {
                saved != null -> {
                    tabCoordinator.favorites.deleteFavorite(saved.id)
                    false
                }
                state.favoritablePageUrl != null -> {
                    tabCoordinator.favorites.addCurrentToFavorites()
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
