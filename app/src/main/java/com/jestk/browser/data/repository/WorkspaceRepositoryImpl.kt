package com.jestk.browser.data.repository

import com.jestk.browser.data.local.dao.WorkspaceDao
import com.jestk.browser.data.mapper.toDomain
import com.jestk.browser.data.mapper.toEntity
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.domain.repository.WorkspaceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class WorkspaceRepositoryImpl(
    private val workspaceDao: WorkspaceDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WorkspaceRepository {

    override fun getAllWorkspaces(): Flow<List<Workspace>> =
        workspaceDao
            .getAllWorkspaces()
            .map { entities ->
                if (entities.isEmpty()) {
                    Workspace.DEFAULT_WORKSPACES
                } else {
                    entities.map { it.toDomain() }
                }
            }
            .flowOn(ioDispatcher)

    override suspend fun getAllWorkspacesSync(): List<Workspace> =
        withContext(ioDispatcher) {
            val entities = workspaceDao.getAllWorkspacesSync()
            if (entities.isEmpty()) {
                Workspace.DEFAULT_WORKSPACES
            } else {
                entities.map { it.toDomain() }
            }
        }

    override suspend fun getWorkspaceById(id: String): Workspace? =
        withContext(ioDispatcher) {
            workspaceDao.getWorkspaceById(id)?.toDomain()
                ?: Workspace.DEFAULT_WORKSPACES.firstOrNull { it.id == id }
        }

    override suspend fun insertWorkspace(workspace: Workspace) =
        withContext(ioDispatcher) {
            workspaceDao.insertWorkspace(workspace.toEntity())
        }

    override suspend fun updateWorkspace(workspace: Workspace) =
        withContext(ioDispatcher) {
            workspaceDao.updateWorkspace(workspace.toEntity())
        }

    override suspend fun deleteWorkspace(id: String) =
        withContext(ioDispatcher) {
            workspaceDao.deleteWorkspace(id)
        }

    override suspend fun updateWorkspacePositions(workspaces: List<Workspace>) =
        withContext(ioDispatcher) {
            workspaceDao.updateWorkspacePositions(workspaces.map { it.toEntity() })
        }

    override suspend fun ensureDefaultWorkspaces() =
        withContext(ioDispatcher) {
            if (workspaceDao.getWorkspaceCount() == 0) {
                workspaceDao.insertWorkspaces(Workspace.DEFAULT_WORKSPACES.map { it.toEntity() })
            }
        }
}
