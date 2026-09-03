package com.jestk.browser.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.dao.TabDao
import com.jestk.browser.data.local.entity.TabEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class TabDaoStressTest {

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
    fun concurrentSetActiveTab_guaranteesExactlyOneActiveTab() = runTest {
        val tabCount = 50
        val tabs = (0 until tabCount).map { i ->
            TabEntity(
                id = "tab-$i",
                url = "https://example.com/page-$i",
                title = "Tab $i",
                position = i,
                isActive = (i == 0)
            )
        }
        tabDao.insertTabs(tabs)

        // Launch 100 concurrent setActiveTab calls across IO threads
        withContext(Dispatchers.IO) {
            val jobs = (0 until 100).map { step ->
                async {
                    val targetTabId = "tab-${step % tabCount}"
                    tabDao.setActiveTab(targetTabId)
                }
            }
            jobs.awaitAll()
        }

        val allTabs = tabDao.getAllTabs()
        val activeTabs = allTabs.filter { it.isActive }
        assertThat(activeTabs).hasSize(1)

        val activeTabFromDao = tabDao.getActiveTab()
        assertThat(activeTabFromDao).isNotNull()
        assertThat(activeTabFromDao?.id).isEqualTo(activeTabs.first().id)
    }

    @Test
    fun rapidAlternatingSetActiveTab_maintainsConsistency() = runTest {
        val tabA = TabEntity(id = "tab-A", title = "Tab A", isActive = true)
        val tabB = TabEntity(id = "tab-B", title = "Tab B", isActive = false)
        tabDao.insertTabs(listOf(tabA, tabB))

        withContext(Dispatchers.IO) {
            val jobs = (0 until 100).map { i ->
                async {
                    val targetId = if (i % 2 == 0) "tab-A" else "tab-B"
                    tabDao.setActiveTab(targetId)
                }
            }
            jobs.awaitAll()
        }

        val allTabs = tabDao.getAllTabs()
        val activeCount = allTabs.count { it.isActive }
        assertThat(activeCount).isEqualTo(1)
    }

    @Test
    fun largeScaleReordering_500Tabs_maintainsExactPositionOrder() = runTest {
        val count = 500
        val initialTabs = (0 until count).map { i ->
            TabEntity(id = "tab-$i", position = i, title = "Tab $i")
        }
        tabDao.insertTabs(initialTabs)

        assertThat(tabDao.getTabsCount()).isEqualTo(count)

        // Reorder in reverse order
        val reversed = initialTabs.reversed()
        tabDao.reorderTabs(reversed)

        val reorderedFromDb = tabDao.getAllTabs()
        assertThat(reorderedFromDb).hasSize(count)
        for (i in 0 until count) {
            val expectedId = "tab-${count - 1 - i}"
            assertThat(reorderedFromDb[i].id).isEqualTo(expectedId)
            assertThat(reorderedFromDb[i].position).isEqualTo(i)
        }

        // Reorder by IDs
        val shuffledIds = initialTabs.map { it.id }.shuffled()
        tabDao.reorderTabIds(shuffledIds)

        val reorderedByIds = tabDao.getAllTabs()
        assertThat(reorderedByIds.map { it.id }).containsExactlyElementsIn(shuffledIds).inOrder()
        for (i in 0 until count) {
            assertThat(reorderedByIds[i].position).isEqualTo(i)
        }
    }

    @Test
    fun batchInsertAndRecentTabsOrdering_1000Tabs_ordersCorrectly() = runTest {
        val count = 1000
        val tabs = (0 until count).map { i ->
            TabEntity(
                id = "tab-$i",
                title = "Tab $i",
                lastActiveTimestamp = (i * 10).toLong(),
                position = i
            )
        }
        tabDao.insertTabs(tabs)

        assertThat(tabDao.getTabsCount()).isEqualTo(count)
        assertThat(tabDao.getMaxPosition()).isEqualTo(count - 1)

        val recentTabs = tabDao.getRecentTabs(limit = 10)
        assertThat(recentTabs).hasSize(10)
        // Highest timestamps should come first (tab-999 down to tab-990)
        val expectedRecentIds = (count - 1 downTo count - 10).map { "tab-$it" }
        assertThat(recentTabs.map { it.id }).containsExactlyElementsIn(expectedRecentIds).inOrder()
    }

    @Test
    fun extremeInputs_sqlInjectionAndUnicode_handledSafely() = runTest {
        val weirdId = "tab-'; DROP TABLE tabs; -- \uD83D\uDE80\uD83D\uDD25"
        val weirdUrl = "https://jestk.com/path?q=test' OR 1=1;--&emoji=\uD83C\uDF08&rtl=\u0645\u0631\u062D\u0628\u0627"
        val weirdTitle = "Title with 'quotes', \"double quotes\", \n newlines \r\t and emojis \uD83D\uDCBB"
        val hugeSnapshotPath = "/data/user/0/com.jestk.browser/files/snapshots/" + "a".repeat(2000) + ".jpg"

        val tab = TabEntity(
            id = weirdId,
            url = weirdUrl,
            title = weirdTitle,
            snapshotPath = hugeSnapshotPath,
            position = 0,
            isActive = true
        )

        tabDao.insertTab(tab)

        val retrieved = tabDao.getTabById(weirdId)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.id).isEqualTo(weirdId)
        assertThat(retrieved?.url).isEqualTo(weirdUrl)
        assertThat(retrieved?.title).isEqualTo(weirdTitle)
        assertThat(retrieved?.snapshotPath).isEqualTo(hugeSnapshotPath)

        // Ensure table was not dropped and queries still work
        tabDao.setActiveTab(weirdId)
        val active = tabDao.getActiveTab()
        assertThat(active?.id).isEqualTo(weirdId)

        tabDao.deleteTabById(weirdId)
        assertThat(tabDao.getTabById(weirdId)).isNull()
    }

    @Test
    fun boundaryOperations_emptyTable_behavesGracefully() = runTest {
        assertThat(tabDao.getTabsCount()).isEqualTo(0)
        assertThat(tabDao.getAllTabs()).isEmpty()
        assertThat(tabDao.getActiveTab()).isNull()
        assertThat(tabDao.getMaxPosition()).isNull()
        assertThat(tabDao.getTabById("non-existent")).isNull()
        assertThat(tabDao.getRecentTabs(10)).isEmpty()

        // Empty list reordering
        tabDao.reorderTabs(emptyList())
        tabDao.reorderTabIds(emptyList())

        // Delete non-existent
        tabDao.deleteTabById("non-existent")
        tabDao.deleteAllTabs()

        // setActive on non-existent tab
        tabDao.setActiveTab("non-existent")
        assertThat(tabDao.getActiveTab()).isNull()
    }

    @Test
    fun concurrentReadAndWriteFlow_emitsConsistentSnapshots() = runTest {
        val tabCount = 20
        val tabs = (0 until tabCount).map { i ->
            TabEntity(id = "tab-$i", position = i, title = "Tab $i")
        }
        tabDao.insertTabs(tabs)

        withContext(Dispatchers.IO) {
            val writerJobs = (0 until 30).map { step ->
                async {
                    tabDao.updateTabDetails(
                        id = "tab-${step % tabCount}",
                        url = "https://updated.com/$step",
                        title = "Updated $step",
                        canGoBack = true,
                        canGoForward = false
                    )
                    tabDao.updateSnapshotPath("tab-${step % tabCount}", "/snapshot/$step.jpg")
                }
            }

            val readerJobs = (0 until 30).map {
                async {
                    val currentTabs = tabDao.getAllTabs()
                    assertThat(currentTabs).hasSize(tabCount)
                }
            }

            (writerJobs + readerJobs).awaitAll()
        }

        val finalTabs = tabDao.getAllTabs()
        assertThat(finalTabs).hasSize(tabCount)
    }
}
