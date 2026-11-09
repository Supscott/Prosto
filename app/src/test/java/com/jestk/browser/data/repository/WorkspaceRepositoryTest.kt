package com.jestk.browser.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.domain.repository.WorkspaceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class WorkspaceRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: WorkspaceRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        repository = WorkspaceRepositoryImpl(db.workspaceDao(), testDispatcher)
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        db.close()
    }

    @Test
    fun getAllWorkspaces_returnsWorkspaces_includingDefaults() = runTest(testDispatcher) {
        val workspaces = repository.getAllWorkspaces().first()
        assertThat(workspaces).isNotEmpty()
        val ids = workspaces.map { it.id }
        assertThat(ids).contains(Workspace.DEFAULT_PERSONAL_ID)
        assertThat(ids).contains(Workspace.DEFAULT_WORK_ID)
    }

    @Test
    fun insertAndGetWorkspace_returnsInsertedWorkspace() = runTest(testDispatcher) {
        val custom = Workspace(
            id = "ws-custom",
            name = "Project X",
            color = 0xFF8B5CF6.toInt(),
            position = 2,
            profileName = "ws-custom"
        )
        repository.insertWorkspace(custom)

        val retrieved = repository.getWorkspaceById("ws-custom")
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.name).isEqualTo("Project X")
        assertThat(retrieved?.color).isEqualTo(0xFF8B5CF6.toInt())
        assertThat(retrieved?.position).isEqualTo(2)

        val all = repository.getAllWorkspaces().first()
        assertThat(all.map { it.id }).contains("ws-custom")
    }

    @Test
    fun updateWorkspace_updatesPersistedValues() = runTest(testDispatcher) {
        val ws = Workspace(id = "ws-up", name = "Initial", color = 1, position = 3)
        repository.insertWorkspace(ws)

        repository.updateWorkspace(ws.copy(name = "Updated", color = 2))

        val retrieved = repository.getWorkspaceById("ws-up")
        assertThat(retrieved?.name).isEqualTo("Updated")
        assertThat(retrieved?.color).isEqualTo(2)
    }

    @Test
    fun deleteWorkspace_removesFromRepository() = runTest(testDispatcher) {
        val ws = Workspace(id = "ws-del", name = "To Delete", color = 1, position = 4)
        repository.insertWorkspace(ws)
        assertThat(repository.getWorkspaceById("ws-del")).isNotNull()

        repository.deleteWorkspace("ws-del")
        assertThat(repository.getWorkspaceById("ws-del")).isNull()
    }

    @Test
    fun updateWorkspacePositions_reordersCorrectly() = runTest(testDispatcher) {
        val ws1 = Workspace(id = "ws-1", name = "One", color = 1, position = 0)
        val ws2 = Workspace(id = "ws-2", name = "Two", color = 2, position = 1)
        repository.insertWorkspace(ws1)
        repository.insertWorkspace(ws2)

        repository.updateWorkspacePositions(listOf(ws2, ws1))

        val reloaded1 = repository.getWorkspaceById("ws-1")
        val reloaded2 = repository.getWorkspaceById("ws-2")

        assertThat(reloaded2?.position).isEqualTo(0)
        assertThat(reloaded1?.position).isEqualTo(1)
    }

    @Test
    fun ensureDefaultWorkspaces_seedsDefaultsWhenTableEmpty() = runTest(testDispatcher) {
        // Clear all workspaces first
        val all = repository.getAllWorkspacesSync()
        all.forEach { repository.deleteWorkspace(it.id) }

        assertThat(db.workspaceDao().getWorkspaceCount()).isEqualTo(0)

        repository.ensureDefaultWorkspaces()

        val restored = repository.getAllWorkspacesSync()
        assertThat(restored.map { it.id }).containsExactly(
            Workspace.DEFAULT_PERSONAL_ID,
            Workspace.DEFAULT_WORK_ID
        ).inOrder()
    }
}
