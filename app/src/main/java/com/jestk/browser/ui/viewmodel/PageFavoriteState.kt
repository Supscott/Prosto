package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.FavoriteItem

/**
 * The URL a favorite is stored under: trimmed, and `https://` is assumed when the scheme is
 * missing. Adding and the "is this page a favorite" check must both go through it.
 */
internal fun normalizeFavoriteUrl(url: String): String {
    val trimmed = url.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
}

/** The URL of the page in the active tab when it is an http(s) page; null on Home and internal pages. */
val BrowserUiState.favoritablePageUrl: String?
    get() {
        if (isHome) return null
        val url = currentUrl.ifEmpty { activeTab?.url.orEmpty() }.trim()
        val isWebPage = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
        return url.takeIf { isWebPage }
    }

/** The favorite of the active workspace that holds the current page, or null when it is not saved. */
val BrowserUiState.currentPageFavorite: FavoriteItem?
    get() {
        val target = favoritablePageUrl?.let(::normalizeFavoriteUrl) ?: return null
        return favorites.firstOrNull { it.workspaceId == activeWorkspaceId && normalizeFavoriteUrl(it.url) == target }
    }

/** The short confirmation shown after the menu item saved or removed the page; [id] restarts its timer. */
data class FavoriteNotice(
    val id: Long,
    val added: Boolean,
)
