package com.jestk.browser.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.repository.TabRepository
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
class TabRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TabRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        repository = TabRepositoryImpl(db.tabDao(), testDispatcher)
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        db.close()
    }

    @Test
    fun createTab_insertsTabAndAssignsPosition() = runTest(testDispatcher) {
        val tab1 = repository.createTab(url = "https://jestk.com", title = "Jestk")
        val tab2 = repository.createTab(url = "https://github.com", title = "GitHub")

        assertThat(tab1.position).isEqualTo(0)
        assertThat(tab2.position).isEqualTo(1)

        val tabs = repository.getAllTabs().first()
        assertThat(tabs).hasSize(2)
        assertThat(tabs[0].url).isEqualTo("https://jestk.com")
        assertThat(tabs[1].url).isEqualTo("https://github.com")
    }

    @Test
    fun getRecentTabs_returnsOrderedByLastActiveTimestamp() = runTest(testDispatcher) {
        val tab1 = repository.createTab(url = "https://site1.com", title = "Site 1")
        val tab2 = repository.createTab(url = "https://site2.com", title = "Site 2")
        val tab3 = repository.createTab(url = "https://site3.com", title = "Site 3")

        repository.updateTab(tab1.copy(lastActiveTimestamp = 1000L))
        repository.updateTab(tab2.copy(lastActiveTimestamp = 3000L))
        repository.updateTab(tab3.copy(lastActiveTimestamp = 2000L))

        val recent = repository.getRecentTabs(limit = 2).first()
        assertThat(recent.map { it.id }).containsExactly(tab2.id, tab3.id).inOrder()
    }

    @Test
    fun setActiveTab_updatesActiveStatus() = runTest(testDispatcher) {
        val tab1 = repository.createTab(url = "https://site1.com", title = "Site 1")
        val tab2 = repository.createTab(url = "https://site2.com", title = "Site 2")

        repository.setActiveTab(tab2.id)

        val retrieved1 = repository.getTabById(tab1.id)
        val retrieved2 = repository.getTabById(tab2.id)

        assertThat(retrieved1?.isActive).isFalse()
        assertThat(retrieved2?.isActive).isTrue()
    }

    @Test
    fun deleteTab_removesTabAndReordersRemaining() = runTest(testDispatcher) {
        val tab0 = repository.createTab(url = "https://site0.com", title = "Site 0")
        val tab1 = repository.createTab(url = "https://site1.com", title = "Site 1")
        val tab2 = repository.createTab(url = "https://site2.com", title = "Site 2")

        repository.deleteTab(tab1.id)

        val remaining = repository.getAllTabs().first()
        assertThat(remaining.map { it.id }).containsExactly(tab0.id, tab2.id).inOrder()
        assertThat(remaining[0].position).isEqualTo(0)
        assertThat(remaining[1].position).isEqualTo(1)
    }

    @Test
    fun updateSnapshot_persistsSnapshotPath() = runTest(testDispatcher) {
        val tab = repository.createTab(url = "https://jestk.com", title = "Jestk")
        repository.updateSnapshot(tab.id, "/snapshots/tab_test.jpg")

        val updated = repository.getTabById(tab.id)
        assertThat(updated?.snapshotPath).isEqualTo("/snapshots/tab_test.jpg")
    }

    @Test
    fun reorderTabs_updatesPositionsCorrectly() = runTest(testDispatcher) {
        val tabA = repository.createTab(url = "https://a.com", title = "A")
        val tabB = repository.createTab(url = "https://b.com", title = "B")
        val tabC = repository.createTab(url = "https://c.com", title = "C")

        repository.reorderTabs(listOf(tabC, tabA, tabB))

        val reordered = repository.getAllTabs().first()
        assertThat(reordered.map { it.id }).containsExactly(tabC.id, tabA.id, tabB.id).inOrder()
        assertThat(reordered[0].position).isEqualTo(0)
        assertThat(reordered[1].position).isEqualTo(1)
        assertThat(reordered[2].position).isEqualTo(2)
    }
}
