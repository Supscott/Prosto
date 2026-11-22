package com.jestk.browser.ui.viewmodel

/** "Download" in the "Download file?" card: starts the shown download. */
data object ConfirmDownloadPrompt : SettingsUiEvent

/** The "Download file?" card was cancelled or dismissed: the shown download is dropped. */
data object DismissDownloadPrompt : SettingsUiEvent

/** Settings switch "Ask before downloading". */
data class SetAskBeforeDownloadEnabled(val enabled: Boolean) : SettingsUiEvent
