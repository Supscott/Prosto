package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.Workspace
import kotlinx.coroutines.flow.Flow

interface FavoriteRepository {
    fun getFavorites(workspaceId: String = Workspace.DEFAULT_PERSONAL_ID): Flow<List<FavoriteItem>>
    fun getFavoritesLimited(limit: Int = 6, workspaceId: String = Workspace.DEFAULT_PERSONAL_ID): Flow<List<FavoriteItem>>
    suspend fun addFavorite(title: String, url: String, iconUrl: String? = null, workspaceId: String = Workspace.DEFAULT_PERSONAL_ID): Long
    suspend fun deleteFavorite(id: Long)
    suspend fun isFavorite(url: String, workspaceId: String = Workspace.DEFAULT_PERSONAL_ID): Boolean
    suspend fun reorderFavorites(orderedFavorites: List<FavoriteItem>)
    suspend fun getFavoritesCount(workspaceId: String = Workspace.DEFAULT_PERSONAL_ID): Int
    suspend fun updateFavicon(url: String, iconUrl: String)
    suspend fun moveFavoriteToWorkspace(id: Long, targetWorkspaceId: String)
}
