package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.Tab

/**
 * The URL a favorite is stored under: trimmed, and `https://` is assumed when the scheme is
 * missing. Adding and the "is this page a favorite" check must both go through it.
 */
internal fun normalizeFavoriteUrl(url: String): String {
    val trimmed = url.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
}

/** The tab with [tabId] among all tabs, or null. */
internal fun BrowserUiState.tabById(tabId: String): Tab? = allTabs.find { it.id == tabId } ?: tabs.find { it.id == tabId }

/** The page URL of [tab]: the live URL for the active tab, the stored one for the others. */
internal fun BrowserUiState.pageUrlOf(tab: Tab): String =
    if (tab.id == activeTabId) currentUrl.ifEmpty { tab.url } else tab.url

/** The URL of the page in [tab] when it is an http(s) page; null on Home and internal pages. */
fun BrowserUiState.favoritablePageUrl(tab: Tab): String? {
    if (tab.id == activeTabId && isHome) return null
    val url = pageUrlOf(tab).trim()
    val isWebPage = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    return url.takeIf { isWebPage }
}

/** The favorite of the tab's workspace that holds the tab's page, or null when it is not saved. */
fun BrowserUiState.favoriteOf(tab: Tab): FavoriteItem? {
    val target = favoritablePageUrl(tab)?.let(::normalizeFavoriteUrl) ?: return null
    return favorites.firstOrNull { it.workspaceId == tab.workspaceId && normalizeFavoriteUrl(it.url) == target }
}

/** The short confirmation shown after the menu item saved or removed the page; [id] restarts its timer. */
data class FavoriteNotice(
    val id: Long,
    val added: Boolean,
)
