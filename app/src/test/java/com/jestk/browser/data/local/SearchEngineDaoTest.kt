package com.jestk.browser.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.dao.SearchEngineDao
import com.jestk.browser.data.local.entity.SearchEngineEntity
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
class SearchEngineDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var searchEngineDao: SearchEngineDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        searchEngineDao = db.searchEngineDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndQueryEngines_orderedByPosition() = runTest {
        val engine1 = SearchEngineEntity(
            id = "google",
            displayName = "Google",
            searchUrlTemplate = "https://google.com/search?q=%s",
            iconUrl = "file:///android_asset/icons/google.svg",
            shortcut = "g",
            orderPosition = 1,
            isBuiltIn = true
        )
        val engine0 = SearchEngineEntity(
            id = "duckduckgo",
            displayName = "DuckDuckGo",
            searchUrlTemplate = "https://duckduckgo.com/?q=%s",
            iconUrl = "file:///android_asset/icons/duckduckgo.svg",
            shortcut = "d",
            orderPosition = 0,
            isBuiltIn = true
        )

        searchEngineDao.insert(engine1)
        searchEngineDao.insert(engine0)

        val engines = searchEngineDao.getAllSearchEnginesFlow().first()
        assertThat(engines).hasSize(2)
        assertThat(engines[0].id).isEqualTo("duckduckgo")
        assertThat(engines[1].id).isEqualTo("google")
    }

    @Test
    fun getEngineByShortcut_matchesCorrectEngine() = runTest {
        val engine = SearchEngineEntity(
            id = "youtube",
            displayName = "YouTube",
            searchUrlTemplate = "https://youtube.com/results?search_query=%s",
            iconUrl = "https://youtube.com/favicon.ico",
            shortcut = "y",
            orderPosition = 0,
            isBuiltIn = false
        )
        searchEngineDao.insert(engine)

        val found = searchEngineDao.getSearchEngineByShortcut("y")
        assertThat(found).isNotNull()
        assertThat(found?.displayName).isEqualTo("YouTube")

        val notFound = searchEngineDao.getSearchEngineByShortcut("z")
        assertThat(notFound).isNull()
    }

    @Test
    fun updateAndDeleteEngine() = runTest {
        val engine = SearchEngineEntity(
            id = "custom1",
            displayName = "Custom One",
            searchUrlTemplate = "https://custom.org/search?q=%s",
            iconUrl = "https://custom.org/favicon.ico",
            shortcut = "c",
            orderPosition = 0,
            isBuiltIn = false
        )
        searchEngineDao.insert(engine)

        searchEngineDao.update(
            engine.copy(displayName = "Custom Updated", shortcut = "cu")
        )

        val updated = searchEngineDao.getSearchEngineById("custom1")
        assertThat(updated?.displayName).isEqualTo("Custom Updated")
        assertThat(updated?.shortcut).isEqualTo("cu")

        searchEngineDao.deleteById("custom1")
        val deleted = searchEngineDao.getSearchEngineById("custom1")
        assertThat(deleted).isNull()
    }

    @Test
    fun getCount_andReorder() = runTest {
        assertThat(searchEngineDao.getCount()).isEqualTo(0)

        val e1 = SearchEngineEntity(id = "e1", displayName = "E1", searchUrlTemplate = "https://e1.com?q=%s", iconUrl = "", orderPosition = 0)
        val e2 = SearchEngineEntity(id = "e2", displayName = "E2", searchUrlTemplate = "https://e2.com?q=%s", iconUrl = "", orderPosition = 1)
        searchEngineDao.insert(e1)
        searchEngineDao.insert(e2)

        assertThat(searchEngineDao.getCount()).isEqualTo(2)

        searchEngineDao.reorder(listOf(e2, e1))

        val engines = searchEngineDao.getAllSearchEnginesFlow().first()
        assertThat(engines[0].id).isEqualTo("e2")
        assertThat(engines[1].id).isEqualTo("e1")
    }
}
