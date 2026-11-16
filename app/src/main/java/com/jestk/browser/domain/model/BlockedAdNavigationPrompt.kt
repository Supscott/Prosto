package com.jestk.browser.domain.model

/** Destination URL of a navigation blocked before it was loaded. */
data class BlockedAdNavigationPrompt(
    val url: String,
    val tabId: String,
)
