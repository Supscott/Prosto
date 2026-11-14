package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.covers
import com.jestk.browser.util.UrlSecurityUtils

/** The site a page URL belongs to for the allowlist (its registrable domain); null for non-web pages. */
internal fun allowlistSiteOf(url: String): String? {
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return null
    return UrlSecurityUtils.getBaseDomain(UrlSecurityUtils.extractHost(url)).takeIf { it.isNotEmpty() }
}

/** The site of the page in the active tab, or null on Home and other non-web pages. */
val BrowserUiState.currentAllowlistSite: String?
    get() = if (isHome) null else allowlistSiteOf(currentUrl.ifEmpty { activeTab?.url.orEmpty() })

/** Whether blocking is off for the current page in the active workspace (by its own or an all-workspaces entry). */
val BrowserUiState.isCurrentSiteAllowlisted: Boolean
    get() = currentAllowlistSite?.let { siteAllowlist.covers(it, activeWorkspaceId) } == true
