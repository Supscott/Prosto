package com.jestk.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jestk.browser.data.local.entity.TabFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TabFolderDao {
    @Query("SELECT * FROM tab_folders ORDER BY position ASC, id ASC")
    fun getAllFolders(): Flow<List<TabFolderEntity>>

    @Query("SELECT * FROM tab_folders ORDER BY position ASC, id ASC")
    suspend fun getAllFoldersSync(): List<TabFolderEntity>

    @Query("SELECT * FROM tab_folders WHERE id = :id LIMIT 1")
    suspend fun getFolderById(id: String): TabFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: TabFolderEntity)

    @Update
    suspend fun updateFolder(folder: TabFolderEntity)

    @Query("DELETE FROM tab_folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Query("UPDATE tab_folders SET position = :position WHERE id = :id")
    suspend fun updateFolderPosition(id: String, position: Int)

    @Transaction
    suspend fun updateFolderPositions(folders: List<TabFolderEntity>) {
        folders.forEachIndexed { index, folder ->
            updateFolderPosition(folder.id, index)
        }
    }
}
