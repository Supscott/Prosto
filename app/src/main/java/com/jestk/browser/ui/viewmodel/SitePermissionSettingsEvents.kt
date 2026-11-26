package com.jestk.browser.ui.viewmodel

/** Settings → Site permissions: forget the camera / microphone grants of one origin. */
data class RemoveSitePermissionSite(val origin: String) : SitePermissionUiEvent

/** Settings → Site permissions: forget every camera / microphone grant. */
data object RemoveAllSitePermissionSites : SitePermissionUiEvent
