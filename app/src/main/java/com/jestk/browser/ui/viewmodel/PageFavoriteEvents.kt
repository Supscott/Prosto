package com.jestk.browser.ui.viewmodel

/** The star in a tab's menu: saves that tab's page to its workspace's favorites, or removes it when it is saved. */
data class ToggleTabFavorite(val tabId: String) : LibraryUiEvent

/** The "Added to / Removed from favorites" pill timed out or was swiped away. */
data object DismissFavoriteNotice : LibraryUiEvent
