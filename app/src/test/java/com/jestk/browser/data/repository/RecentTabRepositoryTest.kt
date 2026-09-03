package com.jestk.browser.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.domain.repository.RecentTabRepository
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
class RecentTabRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RecentTabRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        repository = RecentTabRepositoryImpl(db.recentTabDao(), testDispatcher)
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        db.close()
    }

    @Test
    fun recordRecentTab_andRetrieve_returnsItem() = runTest(testDispatcher) {
        repository.recordRecentTab(
            url = "https://jestk.com",
            title = "Jestk Browser",
            snapshotPath = "/snap.jpg",
            faviconUrl = "https://jestk.com/fav.ico"
        )

        val items = repository.getRecentTabs(10).first()
        assertThat(items).hasSize(1)
        assertThat(items[0].url).isEqualTo("https://jestk.com")
        assertThat(items[0].title).isEqualTo("Jestk Browser")
        assertThat(items[0].snapshotPath).isEqualTo("/snap.jpg")
    }

    @Test
    fun recordRecentTab_ignoresBlankAndAboutBlank() = runTest(testDispatcher) {
        repository.recordRecentTab("", "Empty")
        repository.recordRecentTab("about:blank", "Blank")

        val items = repository.getRecentTabs(10).first()
        assertThat(items).isEmpty()
    }

    @Test
    fun updateSnapshotAndFavicon_updatesExistingRecord() = runTest(testDispatcher) {
        repository.recordRecentTab("https://jestk.com", "Jestk")
        repository.updateSnapshot("https://jestk.com", "/new_snap.jpg")
        repository.updateFavicon("https://jestk.com", "https://jestk.com/icon.png")

        val items = repository.getRecentTabs(10).first()
        assertThat(items).hasSize(1)
        assertThat(items[0].snapshotPath).isEqualTo("/new_snap.jpg")
        assertThat(items[0].faviconUrl).isEqualTo("https://jestk.com/icon.png")
    }
}
