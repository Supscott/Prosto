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
    @Query("SELECT * FROM search_engines ORDER BY orderPosition ASC")
    fun getAllSearchEnginesFlow(): Flow<List<SearchEngineEntity>>

    @Query("SELECT * FROM search_engines ORDER BY orderPosition ASC")
    suspend fun getAllSearchEngines(): List<SearchEngineEntity>

    @Query("SELECT * FROM search_engines WHERE id = :id LIMIT 1")
    suspend fun getSearchEngineById(id: String): SearchEngineEntity?

    @Query("SELECT * FROM search_engines WHERE LOWER(shortcut) = LOWER(:shortcut) LIMIT 1")
    suspend fun getSearchEngineByShortcut(shortcut: String): SearchEngineEntity?

    @Query("SELECT COUNT(*) FROM search_engines")
    suspend fun getCount(): Int

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

    @Query("UPDATE search_engines SET orderPosition = :position WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int)

    suspend fun reorder(orderedEngines: List<SearchEngineEntity>) {
        orderedEngines.forEachIndexed { index, engine ->
            updatePosition(engine.id, index)
        }
    }
}
