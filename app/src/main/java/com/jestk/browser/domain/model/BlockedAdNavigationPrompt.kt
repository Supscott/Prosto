package com.jestk.browser.domain.model

/** Destination URL blocked as an ad navigation before it was loaded. */
data class BlockedAdNavigationPrompt(
    val url: String,
    val tabId: String,
)
