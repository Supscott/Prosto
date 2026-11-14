package com.jestk.browser.ui.viewmodel

/** A popup or popunder opened by [openerUrl] was blocked; the "Popup blocked" pill shows. */
data class ShowPopupBlocked(val openerUrl: String) : SafetyUiEvent

/** "Allow for this site" on the pill: popups of the opener's host are no longer dropped this session. */
data object AllowPopupsForSite : SafetyUiEvent

/** The pill timed out or was dismissed. */
data object DismissPopupBlocked : SafetyUiEvent
