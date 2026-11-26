package com.jestk.browser.ui.viewmodel

/** The menu's one-tap item: saves the current page to the active workspace's favorites, or removes it when it is saved. */
data object ToggleCurrentPageFavorite : LibraryUiEvent

/** The "Added to / Removed from favorites" pill timed out or was swiped away. */
data object DismissFavoriteNotice : LibraryUiEvent
