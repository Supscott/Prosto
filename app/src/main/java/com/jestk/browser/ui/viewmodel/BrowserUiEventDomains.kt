package com.jestk.browser.ui.viewmodel

/** Tabs, workspaces, tab folders and the undo-close snackbar. */
sealed interface TabUiEvent : BrowserUiEvent

/** Overlays, menus, the bottom bar and link and image context menus. */
sealed interface OverlayUiEvent : BrowserUiEvent

/** URL loading, search submission, navigation commands and page callbacks. */
sealed interface PageUiEvent : BrowserUiEvent

/** Favorites, search history and search engine management. */
sealed interface LibraryUiEvent : BrowserUiEvent

/** User settings, downloads, app updates, feedback and default browser status. */
sealed interface SettingsUiEvent : BrowserUiEvent

/** External app prompts, insecure HTTP warnings and blocked ad navigation. */
sealed interface SafetyUiEvent : BrowserUiEvent

/** Video playback, fullscreen and the clean YouTube player. */
sealed interface MediaUiEvent : BrowserUiEvent
