package com.jestk.browser.ui.viewmodel.tabs

import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.viewmodel.normalizeFavoriteUrl
import com.jestk.browser.ui.viewmodel.pageUrlOf
import com.jestk.browser.ui.viewmodel.tabById
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
            save(
                url = current.currentUrl,
                title = current.pageTitle,
                tabFavicon = activeTab?.faviconUrl,
                targetWorkspace = activeTab?.workspaceId ?: current.activeWorkspaceId,
            )
        }
    }

    /** Saves the page of the tab [tabId] (active or not) to that tab's workspace. */
    fun addTabToFavorites(tabId: String) {
        val current = getCurrentState()
        val tab = current.tabById(tabId) ?: return
        val url = current.pageUrlOf(tab)
        if (url.isEmpty() || url.startsWith("about:")) return
        val title = if (tab.id == current.activeTabId) current.pageTitle else tab.title
        save(url = url, title = title, tabFavicon = tab.faviconUrl, targetWorkspace = tab.workspaceId)
    }

    private fun save(url: String, title: String, tabFavicon: String?, targetWorkspace: String) {
        scope.launch(ioDispatcher) {
            val persistentIconPath = if (!tabFavicon.isNullOrEmpty() && (tabFavicon.startsWith("/") || tabFavicon.startsWith("file:"))) {
                FaviconManager.persistFaviconForFavorite(tabFavicon)
            } else null

            val iconUrl = persistentIconPath
                ?: (if (!tabFavicon.isNullOrEmpty() && tabFavicon.startsWith("http")) tabFavicon else null)
                ?: FaviconManager.extractFallbackFaviconUrl(url)

            favoriteRepository.addFavorite(
                title = title.ifEmpty { url },
                url = url,
                iconUrl = iconUrl,
                workspaceId = targetWorkspace,
            )
        }
    }

    fun addFavorite(title: String, url: String, workspaceId: String? = null) {
        scope.launch(ioDispatcher) {
            val normalizedUrl = normalizeFavoriteUrl(url)
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
