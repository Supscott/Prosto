package com.jestk.browser.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.dao.WorkspaceDao
import com.jestk.browser.data.local.entity.WorkspaceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class WorkspaceDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var workspaceDao: WorkspaceDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        workspaceDao = db.workspaceDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRetrieveWorkspace_returnsInsertedWorkspace() = runTest {
        val workspace = WorkspaceEntity(
            id = "ws-custom",
            name = "Research",
            color = 0xFF10B981.toInt(),
            position = 2,
            profileName = "ws-custom"
        )
        workspaceDao.insertWorkspace(workspace)

        val retrieved = workspaceDao.getWorkspaceById("ws-custom")
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.id).isEqualTo("ws-custom")
        assertThat(retrieved?.name).isEqualTo("Research")
        assertThat(retrieved?.color).isEqualTo(0xFF10B981.toInt())
        assertThat(retrieved?.position).isEqualTo(2)
        assertThat(retrieved?.profileName).isEqualTo("ws-custom")
    }

    @Test
    fun getAllWorkspaces_ordersByPositionAscending() = runTest {
        val ws1 = WorkspaceEntity(id = "ws-1", name = "First", color = 1, position = 1)
        val ws0 = WorkspaceEntity(id = "ws-0", name = "Zero", color = 0, position = 0)
        val ws2 = WorkspaceEntity(id = "ws-2", name = "Second", color = 2, position = 2)

        workspaceDao.insertWorkspaces(listOf(ws1, ws0, ws2))

        val all = workspaceDao.getAllWorkspaces().first()
        // Default workspaces might be present if seeded, filter to custom or verify ordering
        val customOnly = all.filter { it.id.startsWith("ws-") }
        assertThat(customOnly.map { it.id }).containsExactly("ws-0", "ws-1", "ws-2").inOrder()
    }

    @Test
    fun updateWorkspace_updatesPropertiesCorrectly() = runTest {
        val workspace = WorkspaceEntity(
            id = "ws-to-update",
            name = "Old Name",
            color = 100,
            position = 0
        )
        workspaceDao.insertWorkspace(workspace)

        val updated = workspace.copy(name = "New Name", color = 200)
        workspaceDao.updateWorkspace(updated)

        val retrieved = workspaceDao.getWorkspaceById("ws-to-update")
        assertThat(retrieved?.name).isEqualTo("New Name")
        assertThat(retrieved?.color).isEqualTo(200)
    }

    @Test
    fun deleteWorkspace_removesWorkspace() = runTest {
        val workspace = WorkspaceEntity(id = "ws-delete", name = "Delete Me", color = 1, position = 5)
        workspaceDao.insertWorkspace(workspace)
        assertThat(workspaceDao.getWorkspaceById("ws-delete")).isNotNull()

        workspaceDao.deleteWorkspace("ws-delete")
        assertThat(workspaceDao.getWorkspaceById("ws-delete")).isNull()
    }

    @Test
    fun updateWorkspacePositions_updatesAllPositions() = runTest {
        val ws1 = WorkspaceEntity(id = "ws-a", name = "A", color = 1, position = 0)
        val ws2 = WorkspaceEntity(id = "ws-b", name = "B", color = 2, position = 1)
        workspaceDao.insertWorkspaces(listOf(ws1, ws2))

        // Swap order: B first, A second
        workspaceDao.updateWorkspacePositions(listOf(ws2, ws1))

        val reloadedA = workspaceDao.getWorkspaceById("ws-a")
        val reloadedB = workspaceDao.getWorkspaceById("ws-b")

        assertThat(reloadedB?.position).isEqualTo(0)
        assertThat(reloadedA?.position).isEqualTo(1)
    }
}
