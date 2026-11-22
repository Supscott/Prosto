package com.jestk.browser.domain.model

data class BrowserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val isAdBlockerEnabled: Boolean = true,
    val isAdRedirectProtectionEnabled: Boolean = true,
    val autoConsentEnabled: Boolean = true,
    val isCleanYouTubePlayerEnabled: Boolean = false,
    val isTrackingProtectionEnabled: Boolean = true,
    val hiddenSearchPageEngineIds: Set<String> = emptySet(),
    val isOpenInAppsEnabled: Boolean = true,
    /** Sites the user chose never to be offered in an app (passive pill only). */
    val openInAppExcludedSites: Set<String> = emptySet(),
    /** Explicit (swipe) dismissals of the open-in-app pill per site, toward the "don't offer" question. */
    val openInAppDismissCounts: Map<String, Int> = emptyMap(),
    val isAutoUpdateEnabled: Boolean = true,
    val isVibrationEnabled: Boolean = true,
    val skippedUpdateVersion: String? = null,
    val isGrayUnloadedTabsEnabled: Boolean = true,
    val feedbackPromptState: FeedbackPromptState = FeedbackPromptState.VISIBLE,
    val feedbackPromptSnoozedUntil: Long = 0L,
    val isHomePrivacyCardEnabled: Boolean = true,
    val isTutorialCompleted: Boolean = false,
    val isBackgroundPlaybackEnabled: Boolean = false,
    val isAskBeforeDownloadEnabled: Boolean = true,
) {
    val isAutoConsentEnabled: Boolean
        get() = autoConsentEnabled
}
