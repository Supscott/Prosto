package com.jestk.browser.ui.viewmodel

/** Per-site content-blocking switch (CB-14): the tab menu item, its prompt and the list for settings. */
sealed interface SiteAllowlistUiEvent : SafetyUiEvent

/** "Disable blocking on this site" in a tab's menu: asks which workspaces the switch-off covers for the site of [url]. */
data class OpenDisableBlockingPrompt(val url: String) : SiteAllowlistUiEvent

/** The prompt was confirmed; [allWorkspaces] widens the entry from the active workspace to all of them. */
data class ConfirmDisableBlocking(val allWorkspaces: Boolean) : SiteAllowlistUiEvent

data object DismissDisableBlockingPrompt : SiteAllowlistUiEvent

/** "Enable blocking on this site" in a tab's menu: removes every entry that covers the site of [url]. */
data class EnableBlockingForSite(val url: String) : SiteAllowlistUiEvent

/** Removes one entry; [workspaceId] null is the all-workspaces entry. Used by the settings list. */
data class RemoveSiteAllowlistEntry(val host: String, val workspaceId: String?) : SiteAllowlistUiEvent
