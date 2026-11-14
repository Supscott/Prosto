package com.jestk.browser.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.domain.model.Workspace
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SiteAllowlistRepositoryTest {
    private lateinit var db: AppDatabase
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: SiteAllowlistRepositoryImpl

    @Before
    fun setUp() {
        db = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        repository = SiteAllowlistRepositoryImpl(db.siteAllowlistDao(), dispatcher, clock = { 42L })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addNormalizesTheHostAndObserveReturnsIt() = runTest(dispatcher) {
        repository.add(" WWW.Example.COM. ", Workspace.DEFAULT_WORK_ID)

        val entries = repository.observe().first()

        assertThat(entries).hasSize(1)
        assertThat(entries[0].host).isEqualTo("example.com")
        assertThat(entries[0].workspaceId).isEqualTo(Workspace.DEFAULT_WORK_ID)
        assertThat(entries[0].createdAt).isEqualTo(42L)
    }

    @Test
    fun allWorkspacesIsNullAndSeparateFromAWorkspaceEntry() = runTest(dispatcher) {
        repository.add("example.com", null)
        repository.add("example.com", "w1")
        repository.add("example.com", "w1")

        assertThat(repository.observe().first().map { it.workspaceId }).containsExactly(null, "w1")
    }

    @Test
    fun blankHostIsIgnored() = runTest(dispatcher) {
        repository.add("  ", null)

        assertThat(repository.observe().first()).isEmpty()
    }

    @Test
    fun isAllowlistedHonoursScopeAndSubdomains() = runTest(dispatcher) {
        repository.add("example.com", "w1")
        repository.add("shared.test", null)

        assertThat(repository.isAllowlisted("www.example.com", "w1")).isTrue()
        assertThat(repository.isAllowlisted("a.b.example.com", "w1")).isTrue()
        assertThat(repository.isAllowlisted("example.com", "w2")).isFalse()
        assertThat(repository.isAllowlisted("example.com", null)).isFalse()
        assertThat(repository.isAllowlisted("shared.test", "w2")).isTrue()
        assertThat(repository.isAllowlisted("shared.test", null)).isTrue()
        assertThat(repository.isAllowlisted("other.test", "w1")).isFalse()
        assertThat(repository.isAllowlisted("", "w1")).isFalse()
    }

    @Test
    fun removeDeletesOnlyTheGivenScope() = runTest(dispatcher) {
        repository.add("example.com", "w1")
        repository.add("example.com", null)

        repository.remove("www.example.com", "w1")

        assertThat(repository.observe().first().map { it.workspaceId }).containsExactly(null)
        repository.remove("example.com", null)
        assertThat(repository.observe().first()).isEmpty()
    }

    @Test
    fun deletingAWorkspaceDeletesItsEntriesOnly() = runTest(dispatcher) {
        val workspaces = WorkspaceRepositoryImpl(db.workspaceDao(), dispatcher)
        workspaces.insertWorkspace(Workspace(id = "w1", name = "One", color = 0))
        repository.add("a.test", "w1")
        repository.add("b.test", "w1")
        repository.add("c.test", Workspace.DEFAULT_PERSONAL_ID)
        repository.add("d.test", null)

        workspaces.deleteWorkspace("w1")

        assertThat(repository.observe().first().map { it.host }).containsExactly("c.test", "d.test")
    }

    @Test
    fun observeIsOrderedByHostThenScope() = runTest(dispatcher) {
        repository.add("b.test", "w1")
        repository.add("a.test", "w2")
        repository.add("a.test", null)

        assertThat(repository.observe().first().map { it.host to it.workspaceId })
            .containsExactly("a.test" to null, "a.test" to "w2", "b.test" to "w1").inOrder()
    }
}
