package com.jestk.browser.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.dao.RecentTabDao
import com.jestk.browser.data.local.dao.TabDao
import com.jestk.browser.data.local.entity.RecentTabEntity
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
class RecentTabDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var recentTabDao: RecentTabDao
    private lateinit var tabDao: TabDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        recentTabDao = db.recentTabDao()
        tabDao = db.tabDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun upsertRecentTab_retrievesOrderedByTimestampDesc() = runTest {
        val r1 = RecentTabEntity(
            url = "https://example.com/1",
            title = "One",
            lastVisitedTimestamp = 1000L
        )
        val r2 = RecentTabEntity(
            url = "https://example.com/2",
            title = "Two",
            lastVisitedTimestamp = 2000L
        )
        recentTabDao.upsertRecentTab(r1)
        recentTabDao.upsertRecentTab(r2)

        val list = recentTabDao.getRecentTabsFlow(10).first()
        assertThat(list).hasSize(2)
        assertThat(list[0].url).isEqualTo("https://example.com/2")
        assertThat(list[1].url).isEqualTo("https://example.com/1")
    }

    @Test
    fun closingTab_doesNotDeleteRecentTab() = runTest {
        val tab = TabEntity(
            id = "tab-1",
            url = "https://example.com",
            title = "Example",
            position = 0,
            isActive = true
        )
        tabDao.insertTab(tab)

        val recent = RecentTabEntity(
            url = "https://example.com",
            title = "Example",
            snapshotPath = "/path/to/snap.jpg",
            faviconUrl = "https://example.com/fav.png",
            lastVisitedTimestamp = 5000L
        )
        recentTabDao.upsertRecentTab(recent)

        // Close the tab
        tabDao.deleteTabById("tab-1")

        assertThat(tabDao.getTabById("tab-1")).isNull()
        val recents = recentTabDao.getRecentTabs(10)
        assertThat(recents).hasSize(1)
        assertThat(recents[0].url).isEqualTo("https://example.com")
        assertThat(recents[0].snapshotPath).isEqualTo("/path/to/snap.jpg")
    }

    @Test
    fun clearRecentTabs_emptiesTable() = runTest {
        recentTabDao.upsertRecentTab(RecentTabEntity(url = "https://a.com", title = "A"))
        recentTabDao.clearRecentTabs()
        assertThat(recentTabDao.getRecentTabs(10)).isEmpty()
    }
}
