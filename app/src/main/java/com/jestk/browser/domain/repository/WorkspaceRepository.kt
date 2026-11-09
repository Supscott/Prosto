package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.Workspace
import kotlinx.coroutines.flow.Flow

interface WorkspaceRepository {
    fun getAllWorkspaces(): Flow<List<Workspace>>
    suspend fun getAllWorkspacesSync(): List<Workspace>
    suspend fun getWorkspaceById(id: String): Workspace?
    suspend fun insertWorkspace(workspace: Workspace)
    suspend fun updateWorkspace(workspace: Workspace)
    suspend fun deleteWorkspace(id: String)
    suspend fun updateWorkspacePositions(workspaces: List<Workspace>)
    suspend fun ensureDefaultWorkspaces()
}
