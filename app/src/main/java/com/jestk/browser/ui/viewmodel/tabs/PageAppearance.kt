package com.jestk.browser.ui.viewmodel.tabs

import android.util.Log
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.viewmodel.isSameDomain
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "TabCoordinator"

internal class PageAppearance(
    ctx: TabCoordinatorContext,
) {
    private val tabRepository = ctx.tabRepository
    private val recentTabRepository = ctx.recentTabRepository
    private val favoriteRepository = ctx.favoriteRepository
    private val tabThemeColors = ctx.tabThemeColors
    private val getCurrentState = ctx.getCurrentState
    private val updateUiState = ctx.updateUiState
    private val scope = ctx.scope
    private val ioDispatcher = ctx.ioDispatcher
    private val tabBottomThemeColors = ctx.tabBottomThemeColors

    fun handleSnapshot(tabId: String, path: String) {
        scope.launch(ioDispatcher) {
            tabRepository.updateSnapshot(tabId, path)
            val tab = tabRepository.getTabById(tabId)
            if (tab != null && tab.url.isNotEmpty() && !tab.url.startsWith("about:")) {
                recentTabRepository?.updateSnapshot(tab.url, path, tab.workspaceId)
            }
        }
    }

    fun handleUpdateFavicon(tabId: String, faviconUrl: String?, pageUrl: String? = null) {
        val current = getCurrentState().tabs.find { it.id == tabId } ?: return

        // 1. If pageUrl is specified, verify that it matches current tab's domain
        if (!pageUrl.isNullOrBlank() && current.url.isNotBlank() && current.url != "about:blank") {
            if (!isSameDomain(current.url, pageUrl)) {
                Log.d(TAG, "Ignoring favicon for tab $tabId: pageUrl ($pageUrl) does not match tab url (${current.url})")
                return
            }
        }

        // 2. If faviconUrl points to a domain-specific cache file, verify that it matches current tab's domain
        if (!faviconUrl.isNullOrBlank() && current.url.isNotBlank() && current.url != "about:blank") {
            val fileName = java.io.File(faviconUrl).name
            if (fileName.startsWith("domain_") && fileName.endsWith(".png")) {
                val fileHost = fileName.removePrefix("domain_").removeSuffix(".png")
                val currentHost = FaviconManager.extractHost(current.url)?.let { FaviconManager.sanitizeHost(it) }
                if (currentHost != null && fileHost != currentHost) {
                    Log.d(TAG, "Ignoring favicon for tab $tabId: file domain ($fileHost) does not match current host ($currentHost)")
                    return
                }
            }
        }

        // Pages report their icon repeatedly. The favicon path is stable, so an unchanged value
        // carries no new information and must not trigger another write and state update.
        if (current.faviconUrl == faviconUrl) return
        updateUiState { state ->
            state.copy(
                tabs =
                    state.tabs.map { tab ->
                        if (tab.id == tabId) tab.copy(faviconUrl = faviconUrl) else tab
                    },
            )
        }
        scope.launch(ioDispatcher) {
            tabRepository.updateFavicon(tabId, faviconUrl)
            val tab = tabRepository.getTabById(tabId)
            if (tab != null && tab.url.isNotEmpty() && !tab.url.startsWith("about:") && !faviconUrl.isNullOrEmpty()) {
                val effectiveUrl = pageUrl ?: tab.url
                if (isSameDomain(tab.url, effectiveUrl)) {
                    recentTabRepository?.updateFavicon(tab.url, faviconUrl, tab.workspaceId)
                    FaviconManager.cacheFaviconPathForDomain(tab.url, faviconUrl)
                    if (favoriteRepository.isFavorite(tab.url, tab.workspaceId)) {
                        val persistentIconPath = if (faviconUrl.startsWith("/") || faviconUrl.startsWith("file:")) {
                            FaviconManager.persistFaviconForFavorite(faviconUrl)
                        } else null
                        val iconToUpdate = persistentIconPath ?: faviconUrl
                        favoriteRepository.updateFavicon(tab.url, iconToUpdate)
                    }
                }
            }
        }
    }

    fun handleThemeColor(tabId: String, color: Int?) {
        handleThemeColors(tabId, color, tabBottomThemeColors[tabId])
    }

    fun handleThemeColors(tabId: String, topColor: Int?, bottomColor: Int?) {
        if (topColor != null) {
            tabThemeColors[tabId] = topColor
        } else {
            tabThemeColors.remove(tabId)
        }
        if (bottomColor != null) {
            tabBottomThemeColors[tabId] = bottomColor
        } else if (topColor == null) {
            tabBottomThemeColors.remove(tabId)
        }
        scope.launch(ioDispatcher) {
            tabRepository.updateThemeColor(tabId, topColor)
        }
        val currentBottomColor = tabBottomThemeColors[tabId]
        updateUiState { state ->
            val updatedTabs =
                state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        tab.copy(
                            themeColor = topColor,
                            bottomThemeColor = currentBottomColor ?: tab.bottomThemeColor,
                        )
                    } else tab
                }
            if (state.activeTabId == tabId) {
                state.copy(
                    tabs = updatedTabs,
                    activeThemeColor = topColor,
                    activeBottomThemeColor = currentBottomColor ?: state.activeBottomThemeColor,
                )
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
    }
}
