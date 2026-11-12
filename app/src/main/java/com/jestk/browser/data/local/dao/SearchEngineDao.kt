package com.jestk.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jestk.browser.data.local.entity.SearchEngineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchEngineDao {
    @Query("SELECT * FROM search_engines ORDER BY orderPosition ASC, id ASC")
    fun getAllSearchEnginesFlow(): Flow<List<SearchEngineEntity>>

    @Query("SELECT * FROM search_engines ORDER BY orderPosition ASC, id ASC")
    suspend fun getAllSearchEngines(): List<SearchEngineEntity>

    @Query("SELECT * FROM search_engines WHERE id = :id LIMIT 1")
    suspend fun getSearchEngineById(id: String): SearchEngineEntity?

    @Query("SELECT * FROM search_engines WHERE LOWER(shortcut) = LOWER(:shortcut) LIMIT 1")
    suspend fun getSearchEngineByShortcut(shortcut: String): SearchEngineEntity?

    @Query("SELECT COUNT(*) FROM search_engines")
    suspend fun getCount(): Int

    /** The position after the last engine; unlike the row count it stays unique after a delete. */
    @Query("SELECT COALESCE(MAX(orderPosition), -1) + 1 FROM search_engines")
    suspend fun nextOrderPosition(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(engine: SearchEngineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(engines: List<SearchEngineEntity>)

    @Update
    suspend fun update(engine: SearchEngineEntity)

    @Delete
    suspend fun delete(engine: SearchEngineEntity)

    @Query("DELETE FROM search_engines WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Sets only the icon, so edits and reorders made meanwhile are kept. */
    @Query("UPDATE search_engines SET iconUrl = :iconUrl WHERE id = :id")
    suspend fun updateIconUrl(id: String, iconUrl: String)

    @Query("UPDATE search_engines SET orderPosition = :position WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int)

    suspend fun reorder(orderedEngines: List<SearchEngineEntity>) {
        orderedEngines.forEachIndexed { index, engine ->
            updatePosition(engine.id, index)
        }
    }
}
