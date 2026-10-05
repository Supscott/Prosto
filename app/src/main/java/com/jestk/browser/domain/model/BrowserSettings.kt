package com.jestk.browser.domain.model

data class BrowserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val isAdBlockerEnabled: Boolean = true
) {
    val adBlockerEnabled: Boolean
        get() = isAdBlockerEnabled
}
