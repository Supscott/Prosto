package com.jestk.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jestk.browser.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY position ASC, createdAt ASC")
    fun getAllFavoritesFlow(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY position ASC, createdAt ASC")
    suspend fun getAllFavorites(): List<FavoriteEntity>

    @Query("SELECT * FROM favorites ORDER BY position ASC, createdAt ASC LIMIT :limit")
    fun getFavoritesLimitedFlow(limit: Int = 6): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE url = :url LIMIT 1")
    suspend fun getFavoriteByUrl(url: String): FavoriteEntity?

    @Query("SELECT * FROM favorites WHERE id = :id LIMIT 1")
    suspend fun getFavoriteById(id: Long): FavoriteEntity?

    @Query("SELECT COUNT(*) > 0 FROM favorites WHERE url = :url")
    suspend fun isFavorite(url: String): Boolean

    @Query("SELECT COUNT(*) > 0 FROM favorites WHERE url = :url")
    fun isFavoriteFlow(url: String): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun getFavoritesCount(): Int

    @Query("SELECT MAX(position) FROM favorites")
    suspend fun getMaxPosition(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity): Long

    @Update
    suspend fun updateFavorite(favorite: FavoriteEntity)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteFavoriteById(id: Long)

    @Query("DELETE FROM favorites WHERE url = :url")
    suspend fun deleteFavoriteByUrl(url: String)

    @Query("DELETE FROM favorites")
    suspend fun clearAllFavorites()

    @Query("UPDATE favorites SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Query("UPDATE favorites SET iconUrl = :iconUrl WHERE url = :url")
    suspend fun updateFavicon(url: String, iconUrl: String)

    suspend fun reorderFavorites(orderedFavorites: List<FavoriteEntity>) {
        orderedFavorites.forEachIndexed { index, fav ->
            updatePosition(fav.id, index)
        }
    }
}
