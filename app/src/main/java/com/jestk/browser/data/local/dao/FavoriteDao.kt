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
    @Query("SELECT * FROM favorites WHERE workspaceId = :workspaceId ORDER BY position ASC, createdAt ASC")
    fun getAllFavoritesFlow(workspaceId: String = "default_personal"): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE workspaceId = :workspaceId ORDER BY position ASC, createdAt ASC")
    suspend fun getAllFavorites(workspaceId: String = "default_personal"): List<FavoriteEntity>

    @Query("SELECT * FROM favorites WHERE workspaceId = :workspaceId ORDER BY position ASC, createdAt ASC LIMIT :limit")
    fun getFavoritesLimitedFlow(limit: Int = 6, workspaceId: String = "default_personal"): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE url = :url AND workspaceId = :workspaceId LIMIT 1")
    suspend fun getFavoriteByUrl(url: String, workspaceId: String = "default_personal"): FavoriteEntity?

    @Query("SELECT * FROM favorites WHERE id = :id LIMIT 1")
    suspend fun getFavoriteById(id: Long): FavoriteEntity?

    @Query("SELECT COUNT(*) > 0 FROM favorites WHERE url = :url AND workspaceId = :workspaceId")
    suspend fun isFavorite(url: String, workspaceId: String = "default_personal"): Boolean

    @Query("SELECT COUNT(*) > 0 FROM favorites WHERE url = :url AND workspaceId = :workspaceId")
    fun isFavoriteFlow(url: String, workspaceId: String = "default_personal"): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM favorites WHERE workspaceId = :workspaceId")
    suspend fun getFavoritesCount(workspaceId: String = "default_personal"): Int

    @Query("SELECT MAX(position) FROM favorites WHERE workspaceId = :workspaceId")
    suspend fun getMaxPosition(workspaceId: String = "default_personal"): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity): Long

    @Update
    suspend fun updateFavorite(favorite: FavoriteEntity)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteFavoriteById(id: Long)

    @Query("DELETE FROM favorites WHERE url = :url AND workspaceId = :workspaceId")
    suspend fun deleteFavoriteByUrl(url: String, workspaceId: String = "default_personal")

    @Query("DELETE FROM favorites WHERE workspaceId = :workspaceId")
    suspend fun clearFavoritesByWorkspace(workspaceId: String)

    @Query("DELETE FROM favorites")
    suspend fun clearAllFavorites()

    @Query("UPDATE favorites SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Query("UPDATE favorites SET workspaceId = :targetWorkspaceId WHERE id = :id")
    suspend fun updateWorkspaceId(id: Long, targetWorkspaceId: String)

    @Query("UPDATE favorites SET iconUrl = :iconUrl WHERE url = :url")
    suspend fun updateFavicon(url: String, iconUrl: String)

    suspend fun reorderFavorites(orderedFavorites: List<FavoriteEntity>) {
        orderedFavorites.forEachIndexed { index, fav ->
            updatePosition(fav.id, index)
        }
    }
}
