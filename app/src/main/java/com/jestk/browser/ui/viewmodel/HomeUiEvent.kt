package com.jestk.browser.ui.viewmodel

/** Home page preferences and the Stories-style tutorial. */
sealed interface HomeUiEvent : SettingsUiEvent {
    data object OpenTutorial : HomeUiEvent

    data object CloseTutorial : HomeUiEvent

    /** The walkthrough was watched to the end: hide the Home card for good. */
    data object CompleteTutorial : HomeUiEvent

    /** The Home card was dismissed with its close button. */
    data object DismissTutorialCard : HomeUiEvent

    data class SetHomePrivacyCardEnabled(
        val enabled: Boolean,
    ) : HomeUiEvent
}
