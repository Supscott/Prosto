package com.jestk.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jestk.browser.data.local.entity.SiteAllowlistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteAllowlistDao {
    @Query("SELECT * FROM site_allowlist ORDER BY host ASC, workspace_scope ASC")
    fun observeAll(): Flow<List<SiteAllowlistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SiteAllowlistEntity)

    @Query("DELETE FROM site_allowlist WHERE host = :host AND workspace_scope = :scope")
    suspend fun delete(host: String, scope: String)

    @Query("SELECT COUNT(*) FROM site_allowlist WHERE host IN (:hosts) AND workspace_scope IN (:scopes)")
    suspend fun countMatching(hosts: List<String>, scopes: List<String>): Int
}
