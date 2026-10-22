package com.jestk.browser.domain.model

data class BrowserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val isAdBlockerEnabled: Boolean = true,
    val hiddenSearchPageEngineIds: Set<String> = emptySet(),
    val isOpenInAppsEnabled: Boolean = true,
    val isAutoUpdateEnabled: Boolean = true,
    val isVibrationEnabled: Boolean = true,
    val skippedUpdateVersion: String? = null,
) {
    val adBlockerEnabled: Boolean
        get() = isAdBlockerEnabled
}
