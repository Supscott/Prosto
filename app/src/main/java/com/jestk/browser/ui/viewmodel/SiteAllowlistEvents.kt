package com.jestk.browser.ui.viewmodel

/** Per-site content-blocking switch (CB-14): the browser menu item, its prompt and the list for settings. */
sealed interface SiteAllowlistUiEvent : SafetyUiEvent

/** "Disable blocking on this site" in the browser menu: asks which workspaces the switch-off covers. */
data object OpenDisableBlockingPrompt : SiteAllowlistUiEvent

/** The prompt was confirmed; [allWorkspaces] widens the entry from the active workspace to all of them. */
data class ConfirmDisableBlocking(val allWorkspaces: Boolean) : SiteAllowlistUiEvent

data object DismissDisableBlockingPrompt : SiteAllowlistUiEvent

/** "Enable blocking on this site": removes every entry that covers the current page. */
data object EnableBlockingForCurrentSite : SiteAllowlistUiEvent

/** Removes one entry; [workspaceId] null is the all-workspaces entry. Used by the settings list. */
data class RemoveSiteAllowlistEntry(val host: String, val workspaceId: String?) : SiteAllowlistUiEvent
