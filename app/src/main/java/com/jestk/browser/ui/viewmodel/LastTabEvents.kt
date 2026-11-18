package com.jestk.browser.ui.viewmodel

/** Events of the "Last tab" pill shown after the app opened a new Home tab on return. */
sealed interface LastTabUiEvent : TabUiEvent

/** The pill was tapped: go back to the previous tab and drop the untouched Home tab. */
data object RestoreLastTab : LastTabUiEvent

/** The pill was swiped away or became stale: hide it and keep the new tab. */
data object DismissLastTabPrompt : LastTabUiEvent

/** The tab to return to and the Home tab the app opened in its place. */
data class LastTabPrompt(
    val previousTabId: String,
    val newTabId: String,
)
