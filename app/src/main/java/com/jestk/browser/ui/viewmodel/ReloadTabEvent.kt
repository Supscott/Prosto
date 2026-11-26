package com.jestk.browser.ui.viewmodel

/**
 * Reloads the page of [tabId], active or not. Unlike [BrowserUiEvent.Reload] it is tied to the tab
 * the user acted on, so it cannot hit another tab when the active one changes meanwhile.
 */
data class ReloadTab(val tabId: String) : PageUiEvent
