package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.ProtectionLevel

/** The content-blocking section of the settings screen. */
sealed interface ContentBlockingUiEvent : BrowserUiEvent

/** Picking Strict also starts the download of its lists. */
data class SetProtectionLevel(val level: ProtectionLevel) : ContentBlockingUiEvent

/** "Update filter lists now" and the retry after a failed download. */
data object UpdateFilterListsNow : ContentBlockingUiEvent
