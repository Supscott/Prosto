package com.jestk.browser.ui.viewmodel

/** "Download again" in the already-downloaded dialog: enqueues the stored request. */
data object ConfirmDuplicateDownload : SettingsUiEvent

/** The already-downloaded dialog was cancelled, dismissed or answered with Open. */
data object DismissDuplicateDownload : SettingsUiEvent
