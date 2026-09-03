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

    override fun getFavorites(): Flow<List<FavoriteItem>> =
        favoriteDao.getAllFavoritesFlow()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun getFavoritesLimited(limit: Int): Flow<List<FavoriteItem>> =
        favoriteDao.getFavoritesLimitedFlow(limit)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun addFavorite(title: String, url: String, iconUrl: String?): Long = withContext(ioDispatcher) {
        val existing = favoriteDao.getFavoriteByUrl(url)
        if (existing != null) {
            existing.id
        } else {
            val count = favoriteDao.getFavoritesCount()
            val entity = FavoriteEntity(
                title = title.ifEmpty { url },
                url = url,
                position = count,
                iconUrl = iconUrl,
                createdAt = System.currentTimeMillis()
            )
            favoriteDao.insertFavorite(entity)
        }
    }

    override suspend fun deleteFavorite(id: Long) = withContext(ioDispatcher) {
        favoriteDao.deleteFavoriteById(id)
    }

    override suspend fun removeFavorite(id: Long) = deleteFavorite(id)

    override suspend fun isFavorite(url: String): Boolean = withContext(ioDispatcher) {
        favoriteDao.getFavoriteByUrl(url) != null
    }

    override suspend fun reorderFavorites(orderedFavorites: List<FavoriteItem>) = withContext(ioDispatcher) {
        favoriteDao.reorderFavorites(orderedFavorites.map { it.toEntity() })
    }

    override suspend fun getFavoritesCount(): Int = withContext(ioDispatcher) {
        favoriteDao.getFavoritesCount()
    }
}
