package com.jestk.browser.data.repository

import com.jestk.browser.data.local.dao.TabFolderDao
import com.jestk.browser.data.mapper.toDomain
import com.jestk.browser.data.mapper.toEntity
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.repository.TabFolderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TabFolderRepositoryImpl(
    private val tabFolderDao: TabFolderDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TabFolderRepository {

    override fun getAllFolders(): Flow<List<TabFolder>> =
        tabFolderDao
            .getAllFolders()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun getAllFoldersSync(): List<TabFolder> =
        withContext(ioDispatcher) {
            tabFolderDao.getAllFoldersSync().map { it.toDomain() }
        }

    override suspend fun getFolderById(id: String): TabFolder? =
        withContext(ioDispatcher) {
            tabFolderDao.getFolderById(id)?.toDomain()
        }

    override suspend fun insertFolder(folder: TabFolder) =
        withContext(ioDispatcher) {
            tabFolderDao.insertFolder(folder.toEntity())
        }

    override suspend fun updateFolder(folder: TabFolder) =
        withContext(ioDispatcher) {
            tabFolderDao.updateFolder(folder.toEntity())
        }

    override suspend fun deleteFolder(id: String) =
        withContext(ioDispatcher) {
            tabFolderDao.deleteFolder(id)
        }

    override suspend fun updateFolderPositions(folders: List<TabFolder>) =
        withContext(ioDispatcher) {
            tabFolderDao.updateFolderPositions(folders.map { it.toEntity() })
        }
}
