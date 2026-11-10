package com.jestk.browser.data.repository

import com.jestk.browser.data.local.dao.FavoriteDao
import com.jestk.browser.data.local.entity.FavoriteEntity
import com.jestk.browser.data.mapper.toDomain
import com.jestk.browser.data.mapper.toEntity
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.repository.FavoriteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FavoriteRepositoryImpl(
    private val favoriteDao: FavoriteDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : FavoriteRepository {

    override fun getFavorites(workspaceId: String): Flow<List<FavoriteItem>> =
        favoriteDao.getAllFavoritesFlow(workspaceId)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun getFavoritesLimited(limit: Int, workspaceId: String): Flow<List<FavoriteItem>> =
        favoriteDao.getFavoritesLimitedFlow(limit, workspaceId)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun addFavorite(
        title: String,
        url: String,
        iconUrl: String?,
        workspaceId: String,
    ): Long = withContext(ioDispatcher) {
        val existing = favoriteDao.getFavoriteByUrl(url, workspaceId)
        if (existing != null) {
            existing.id
        } else {
            val count = favoriteDao.getFavoritesCount(workspaceId)
            val entity = FavoriteEntity(
                title = title.ifEmpty { url },
                url = url,
                position = count,
                iconUrl = iconUrl,
                createdAt = System.currentTimeMillis(),
                workspaceId = workspaceId,
            )
            favoriteDao.insertFavorite(entity)
        }
    }

    override suspend fun deleteFavorite(id: Long) = withContext(ioDispatcher) {
        favoriteDao.deleteFavoriteById(id)
    }

    override suspend fun isFavorite(url: String, workspaceId: String): Boolean = withContext(ioDispatcher) {
        favoriteDao.getFavoriteByUrl(url, workspaceId) != null
    }

    override suspend fun reorderFavorites(orderedFavorites: List<FavoriteItem>) = withContext(ioDispatcher) {
        favoriteDao.reorderFavorites(orderedFavorites.map { it.toEntity() })
    }

    override suspend fun getFavoritesCount(workspaceId: String): Int = withContext(ioDispatcher) {
        favoriteDao.getFavoritesCount(workspaceId)
    }

    override suspend fun updateFavicon(url: String, iconUrl: String) = withContext(ioDispatcher) {
        favoriteDao.updateFavicon(url, iconUrl)
    }

    override suspend fun moveFavoriteToWorkspace(id: Long, targetWorkspaceId: String) = withContext(ioDispatcher) {
        val favorite = favoriteDao.getFavoriteById(id) ?: return@withContext
        val targetCount = favoriteDao.getFavoritesCount(targetWorkspaceId)
        favoriteDao.updateWorkspaceId(id, targetWorkspaceId)
        favoriteDao.updatePosition(id, targetCount)
    }
}
