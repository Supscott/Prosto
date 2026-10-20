package com.jestk.browser.data.repository

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.domain.model.SearchEngine
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
class SearchEngineRepositoryTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var repository: SearchEngineRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        repository = SearchEngineRepositoryImpl(db.searchEngineDao())
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun getSearchEngines_returnsDefaultEnginesWhenEmpty() = runTest {
        val engines = repository.getSearchEngines().first()
        assertThat(engines).hasSize(6)
        assertThat(engines.map { it.id }).containsExactly(
            "google", "duckduckgo", "brave", "kagi", "ecosia", "startpage"
        ).inOrder()
        assertThat(engines[0].shortcut).isEqualTo("g")
        assertThat(engines[1].shortcut).isEqualTo("d")
        assertThat(engines[2].shortcut).isEqualTo("b")
        assertThat(engines[3].shortcut).isEqualTo("k")
        assertThat(engines[4].shortcut).isEqualTo("e")
        assertThat(engines[5].shortcut).isEqualTo("s")
    }

    @Test
    fun saveSearchEngine_insertsNewCustomEngine() = runTest {
        val custom = SearchEngine(
            id = "custom_yt",
            displayName = "YouTube",
            searchUrlTemplate = "https://youtube.com/results?search_query=%s",
            iconUrl = "https://youtube.com/favicon.ico",
            shortcut = "y",
            orderPosition = 6,
            isBuiltIn = false
        )
        repository.saveSearchEngine(custom)

        val engines = repository.getSearchEngines().first()
        assertThat(engines).hasSize(7)
        assertThat(engines.last().displayName).isEqualTo("YouTube")
        assertThat(engines.last().shortcut).isEqualTo("y")

        val found = repository.getSearchEngineByShortcut("y")
        assertThat(found).isNotNull()
        assertThat(found?.id).isEqualTo("custom_yt")
    }

    @Test
    fun deleteSearchEngine_removesEngineFromList() = runTest {
        val custom = SearchEngine(
            id = "custom_reddit",
            displayName = "Reddit",
            searchUrlTemplate = "https://reddit.com/search?q=%s",
            iconUrl = "https://reddit.com/favicon.ico",
            shortcut = "r",
            orderPosition = 6,
            isBuiltIn = false
        )
        repository.saveSearchEngine(custom)
        assertThat(repository.getSearchEngines().first()).hasSize(7)

        repository.deleteSearchEngine("custom_reddit")
        val updated = repository.getSearchEngines().first()
        assertThat(updated).hasSize(6)
        val hasDeleted = updated.any { it.id == "custom_reddit" }
        assertThat(hasDeleted).isFalse()
    }

    @Test
    fun reorderSearchEngines_updatesOrderPositions() = runTest {
        val initial = repository.getAllSearchEngines()
        assertThat(initial[0].id).isEqualTo("google")
        assertThat(initial[1].id).isEqualTo("duckduckgo")

        // Invert order
        val reversed = initial.reversed()
        repository.reorderSearchEngines(reversed)

        val reordered = repository.getSearchEngines().first()
        assertThat(reordered[0].id).isEqualTo("startpage")
        assertThat(reordered[1].id).isEqualTo("ecosia")
        assertThat(reordered[2].id).isEqualTo("kagi")
        assertThat(reordered[3].id).isEqualTo("brave")
        assertThat(reordered[4].id).isEqualTo("duckduckgo")
        assertThat(reordered[5].id).isEqualTo("google")
    }
}
