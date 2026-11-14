package com.jestk.browser.domain.model

/**
 * Destination URL blocked before it was loaded. [isDocument] marks a `$document` rule match whose
 * block page is already showing in the tab, so the second action is "Go back" instead of "Stay".
 */
data class BlockedAdNavigationPrompt(
    val url: String,
    val tabId: String,
    val isDocument: Boolean = false,
)
