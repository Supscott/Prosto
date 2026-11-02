package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.TabFolder
import kotlinx.coroutines.flow.Flow

interface TabFolderRepository {
    fun getAllFolders(): Flow<List<TabFolder>>
    suspend fun getAllFoldersSync(): List<TabFolder>
    suspend fun getFolderById(id: String): TabFolder?
    suspend fun insertFolder(folder: TabFolder)
    suspend fun updateFolder(folder: TabFolder)
    suspend fun deleteFolder(id: String)
    suspend fun updateFolderPositions(folders: List<TabFolder>)
}
