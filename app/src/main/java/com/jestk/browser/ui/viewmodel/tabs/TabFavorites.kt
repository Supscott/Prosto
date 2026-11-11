package com.jestk.browser.ui.viewmodel.tabs

import com.jestk.browser.engine.FaviconManager
import kotlinx.coroutines.launch

internal class TabFavorites(
    ctx: TabCoordinatorContext,
) {
    private val favoriteRepository = ctx.favoriteRepository
    private val getCurrentState = ctx.getCurrentState
    private val scope = ctx.scope
    private val ioDispatcher = ctx.ioDispatcher

    fun addCurrentToFavorites() {
        val current = getCurrentState()
        if (current.currentUrl.isNotEmpty() && !current.currentUrl.startsWith("about:")) {
            val activeTab = current.tabs.find { it.id == current.activeTabId }
            val tabFavicon = activeTab?.faviconUrl
            val targetWorkspace = activeTab?.workspaceId ?: current.activeWorkspaceId
            scope.launch(ioDispatcher) {
                val persistentIconPath = if (!tabFavicon.isNullOrEmpty() && (tabFavicon.startsWith("/") || tabFavicon.startsWith("file:"))) {
                    FaviconManager.persistFaviconForFavorite(tabFavicon)
                } else null

                val iconUrl = persistentIconPath
                    ?: (if (!tabFavicon.isNullOrEmpty() && tabFavicon.startsWith("http")) tabFavicon else null)
                    ?: FaviconManager.extractFallbackFaviconUrl(current.currentUrl)

                favoriteRepository.addFavorite(
                    title = current.pageTitle.ifEmpty { current.currentUrl },
                    url = current.currentUrl,
                    iconUrl = iconUrl,
                    workspaceId = targetWorkspace,
                )
            }
        }
    }

    fun addFavorite(title: String, url: String, workspaceId: String? = null) {
        scope.launch(ioDispatcher) {
            val trimmed = url.trim()
            val normalizedUrl = when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                else -> "https://$trimmed"
            }
            val derivedTitle = title.trim().ifBlank {
                normalizedUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')
            }
            val icon = FaviconManager.extractFallbackFaviconUrl(normalizedUrl)
            val targetWorkspace = workspaceId ?: getCurrentState().activeWorkspaceId
            favoriteRepository.addFavorite(derivedTitle, normalizedUrl, icon, targetWorkspace)
        }
    }

    fun deleteFavorite(id: Long) {
        scope.launch(ioDispatcher) {
            favoriteRepository.deleteFavorite(id)
        }
    }

    fun moveFavoriteToWorkspace(id: Long, targetWorkspaceId: String) {
        scope.launch(ioDispatcher) {
            favoriteRepository.moveFavoriteToWorkspace(id, targetWorkspaceId)
        }
    }
}
