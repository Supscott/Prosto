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
        updateUiState { it.copy(favoriteNotice = FavoriteNotice((it.favoriteNotice?.id ?: 0L) + 1, added)) }
    }

    fun dismissNotice() {
        updateUiState { it.copy(favoriteNotice = null) }
    }
}
