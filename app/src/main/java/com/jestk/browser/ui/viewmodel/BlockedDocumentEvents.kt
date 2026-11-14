package com.jestk.browser.ui.viewmodel

/** A `$document` rule blocked the page and its block page is showing in [tabId]. */
data class ShowBlockedDocument(
    val url: String,
    val tabId: String,
) : SafetyUiEvent

/** "Go back" on the blocked-document banner: leaves the block page through the tab's history. */
data object GoBackFromBlockedDocument : SafetyUiEvent
