package com.jestk.browser.ui.viewmodel

/** "Go back" on a block page: leaves it through the tab's history, or closes a tab with nothing behind it. */
data class GoBackFromBlockedDocument(val tabId: String) : SafetyUiEvent

/** "Proceed anyway" on a block page: allows [url]'s host in [tabId] only, then loads the page. */
data class AllowBlockedDocument(val tabId: String, val url: String) : SafetyUiEvent
