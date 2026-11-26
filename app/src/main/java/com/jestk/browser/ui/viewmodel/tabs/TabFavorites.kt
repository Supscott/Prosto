package com.jestk.browser.ui.viewmodel.tabs

import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.viewmodel.favoritablePageUrl
import com.jestk.browser.ui.viewmodel.normalizeFavoriteUrl
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
        addTabToFavorites(getCurrentState().activeTabId ?: return)
    }

    /** Saves the http(s) page of the tab [tabId] (active or not, never incognito) to that tab's workspace. */
    fun addTabToFavorites(tabId: String) {
        val current = getCurrentState()
        val tab = current.tabById(tabId) ?: return
        val url = current.favoritablePageUrl(tab) ?: return
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
