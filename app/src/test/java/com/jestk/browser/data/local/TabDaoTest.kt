package com.jestk.browser.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.dao.TabDao
import com.jestk.browser.data.local.entity.TabEntity
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
class TabDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var tabDao: TabDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        tabDao = db.tabDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRetrieveTab_returnsInsertedTab() = runTest {
        val tab = TabEntity(
            id = "tab-1",
            url = "https://jestk.com",
            title = "Jestk",
            position = 0,
            isActive = true
        )
        tabDao.insertTab(tab)

        val retrieved = tabDao.getTabById("tab-1")
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.id).isEqualTo("tab-1")
        assertThat(retrieved?.url).isEqualTo("https://jestk.com")
        assertThat(retrieved?.title).isEqualTo("Jestk")
        assertThat(retrieved?.isActive).isTrue()
    }

    @Test
    fun getAllTabsFlow_returnsTabsInPositionOrder() = runTest {
        val tab2 = TabEntity(id = "tab-2", position = 2, title = "Tab 2")
        val tab0 = TabEntity(id = "tab-0", position = 0, title = "Tab 0")
        val tab1 = TabEntity(id = "tab-1", position = 1, title = "Tab 1")

        tabDao.insertTabs(listOf(tab2, tab0, tab1))

        val tabs = tabDao.getAllTabsFlow().first()
        assertThat(tabs.map { it.id }).containsExactly("tab-0", "tab-1", "tab-2").inOrder()
    }

    @Test
    fun setActiveTab_clearsPreviousActiveFlagsAndMarksNewActive() = runTest {
        val tab1 = TabEntity(id = "tab-1", isActive = true)
        val tab2 = TabEntity(id = "tab-2", isActive = false)
        val tab3 = TabEntity(id = "tab-3", isActive = false)

        tabDao.insertTabs(listOf(tab1, tab2, tab3))

        tabDao.setActiveTab("tab-2")

        val activeTab = tabDao.getActiveTab()
        assertThat(activeTab?.id).isEqualTo("tab-2")

        val allTabs = tabDao.getAllTabs()
        val activeCount = allTabs.count { it.isActive }
        assertThat(activeCount).isEqualTo(1)
        assertThat(allTabs.find { it.id == "tab-1" }?.isActive).isFalse()
        assertThat(allTabs.find { it.id == "tab-2" }?.isActive).isTrue()
        assertThat(allTabs.find { it.id == "tab-3" }?.isActive).isFalse()
    }

    @Test
    fun reorderTabs_updatesPositionsSequentially() = runTest {
        val tabA = TabEntity(id = "tab-a", position = 0)
        val tabB = TabEntity(id = "tab-b", position = 1)
        val tabC = TabEntity(id = "tab-c", position = 2)

        tabDao.insertTabs(listOf(tabA, tabB, tabC))

        tabDao.reorderTabs(listOf(tabC, tabA, tabB))

        val reordered = tabDao.getAllTabs()
        assertThat(reordered.map { it.id }).containsExactly("tab-c", "tab-a", "tab-b").inOrder()
        assertThat(reordered.find { it.id == "tab-c" }?.position).isEqualTo(0)
        assertThat(reordered.find { it.id == "tab-a" }?.position).isEqualTo(1)
        assertThat(reordered.find { it.id == "tab-b" }?.position).isEqualTo(2)
    }

    @Test
    fun getRecentTabsFlow_ordersByLastActiveTimestampDescending() = runTest {
        val tab1 = TabEntity(id = "tab-1", lastActiveTimestamp = 1000L)
        val tab2 = TabEntity(id = "tab-2", lastActiveTimestamp = 3000L)
        val tab3 = TabEntity(id = "tab-3", lastActiveTimestamp = 2000L)

        tabDao.insertTabs(listOf(tab1, tab2, tab3))

        val recent = tabDao.getRecentTabsFlow(limit = 2).first()
        assertThat(recent.map { it.id }).containsExactly("tab-2", "tab-3").inOrder()
    }

    @Test
    fun updateSnapshotPath_updatesTargetTabSnapshotOnly() = runTest {
        val tab = TabEntity(id = "tab-1", snapshotPath = null)
        tabDao.insertTab(tab)

        tabDao.updateSnapshotPath("tab-1", "/cache/snapshots/tab-1.jpg")

        val updated = tabDao.getTabById("tab-1")
        assertThat(updated?.snapshotPath).isEqualTo("/cache/snapshots/tab-1.jpg")
    }

    @Test
    fun deleteTabById_removesSpecificTab() = runTest {
        val tab1 = TabEntity(id = "tab-1")
        val tab2 = TabEntity(id = "tab-2")
        tabDao.insertTabs(listOf(tab1, tab2))

        tabDao.deleteTabById("tab-1")

        val remaining = tabDao.getAllTabs()
        assertThat(remaining.map { it.id }).containsExactly("tab-2")
    }
}
