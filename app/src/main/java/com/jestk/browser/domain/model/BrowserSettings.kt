package com.jestk.browser.domain.model

data class BrowserSettings @JvmOverloads constructor(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val isAdBlockerEnabled: Boolean = true,
    val isMenuExpandedOnWeb: Boolean = false
) {
    val adBlockerEnabled: Boolean
        get() = isAdBlockerEnabled
}
