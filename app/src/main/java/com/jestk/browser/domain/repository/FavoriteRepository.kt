package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.FavoriteItem
import kotlinx.coroutines.flow.Flow

interface FavoriteRepository {
    fun getFavorites(): Flow<List<FavoriteItem>>
    fun getFavoritesLimited(limit: Int = 6): Flow<List<FavoriteItem>>
    suspend fun addFavorite(title: String, url: String, iconUrl: String? = null): Long
    suspend fun deleteFavorite(id: Long)
    suspend fun removeFavorite(id: Long)
    suspend fun isFavorite(url: String): Boolean
    suspend fun reorderFavorites(orderedFavorites: List<FavoriteItem>)
    suspend fun getFavoritesCount(): Int
}
