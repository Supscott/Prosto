package com.jestk.browser.ui.viewmodel

/** Per-site content-blocking switch (CB-14): the browser menu item, its dialog and the list for settings. */
sealed interface SiteAllowlistUiEvent : SafetyUiEvent

/** "Disable blocking on this site" in the browser menu: asks which workspaces the switch-off covers. */
data object OpenDisableBlockingDialog : SiteAllowlistUiEvent

/** The dialog was confirmed; [allWorkspaces] widens the entry from the active workspace to all of them. */
data class ConfirmDisableBlocking(val allWorkspaces: Boolean) : SiteAllowlistUiEvent

data object DismissDisableBlockingDialog : SiteAllowlistUiEvent

/** "Enable blocking on this site": removes every entry that covers the current page. */
data object EnableBlockingForCurrentSite : SiteAllowlistUiEvent

/** Removes one entry; [workspaceId] null is the all-workspaces entry. Used by the settings list. */
data class RemoveSiteAllowlistEntry(val host: String, val workspaceId: String?) : SiteAllowlistUiEvent
