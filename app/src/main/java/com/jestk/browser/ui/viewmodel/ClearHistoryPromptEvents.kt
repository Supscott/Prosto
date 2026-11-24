package com.jestk.browser.ui.viewmodel

/** "Clear History" in the Search screen: asks for confirmation, nothing is deleted yet. */
data object RequestClearHistory : LibraryUiEvent

/** The "Clear all history?" card was cancelled or dismissed: the history stays. */
data object DismissClearHistoryPrompt : LibraryUiEvent
