package com.jestk.browser.ui.viewmodel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.RecentTabRepositoryImpl
import com.jestk.browser.data.repository.SearchEngineRepositoryImpl
import com.jestk.browser.data.repository.SettingsRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.repository.SearchEngineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchProviderViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var searchEngineRepo: SearchEngineRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        searchEngineRepo = SearchEngineRepositoryImpl(db.searchEngineDao(), testDispatcher)
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun createViewModel(): BrowserViewModel {
        val tabRepo = TabRepositoryImpl(db.tabDao(), testDispatcher)
        val historyRepo = HistoryRepositoryImpl(db.historyDao(), testDispatcher)
        val favoriteRepo = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher)
        val settingsRepo = SettingsRepositoryImpl(db.settingDao(), testDispatcher)
        val recentTabRepo = RecentTabRepositoryImpl(db.recentTabDao(), testDispatcher)

        return BrowserViewModel(
            tabRepository = tabRepo,
            historyRepository = historyRepo,
            favoriteRepository = favoriteRepo,
            settingsRepository = settingsRepo,
            recentTabRepository = recentTabRepo,
            searchEngineRepository = searchEngineRepo,
            ioDispatcher = testDispatcher,
        )
    }

    @Test
    fun defaultSearchEngines_loadedOnStart() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        val engines = vm.uiState.value.searchEngines
        assertThat(engines).hasSize(6)
        assertThat(engines.map { it.id }).containsExactly(
            "google", "duckduckgo", "brave", "kagi", "ecosia", "startpage"
        ).inOrder()
        assertThat(engines.map { it.shortcut }).containsExactly(
            "g", "d", "b", "k", "e", "s"
        ).inOrder()
    }

    @Test
    fun submitSearch_withGoogleShortcut_loadsGoogleUrl() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        // Submit "g android architecture"
        vm.onEvent(BrowserUiEvent.SubmitSearch("g android architecture"))
        advanceUntilIdle()

        assertThat(vm.uiState.value.currentUrl).isEqualTo("https://www.google.com/search?q=android+architecture")
        assertThat(vm.uiState.value.settings.searchEngine.id).isEqualTo("google")
    }

    @Test
    fun submitSearch_withDuckDuckGoShortcut_loadsDuckDuckGoUrl() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(BrowserUiEvent.SubmitSearch("d kotlin coroutines"))
        advanceUntilIdle()

        assertThat(vm.uiState.value.currentUrl).isEqualTo("https://duckduckgo.com/?q=kotlin+coroutines")
        assertThat(vm.uiState.value.settings.searchEngine.id).isEqualTo("duckduckgo")
    }

    @Test
    fun submitSearch_withBraveShortcut_loadsBraveUrl() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(BrowserUiEvent.SubmitSearch("b compose ui"))
        advanceUntilIdle()

        assertThat(vm.uiState.value.currentUrl).isEqualTo("https://search.brave.com/search?q=compose+ui")
        assertThat(vm.uiState.value.settings.searchEngine.id).isEqualTo("brave")
    }

    @Test
    fun submitSearch_withKagiShortcut_loadsKagiUrl() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(BrowserUiEvent.SubmitSearch("k fast search"))
        advanceUntilIdle()

        assertThat(vm.uiState.value.currentUrl).isEqualTo("https://kagi.com/search?q=fast+search")
        assertThat(vm.uiState.value.settings.searchEngine.id).isEqualTo("kagi")
    }

    @Test
    fun submitSearch_withEcosiaShortcut_loadsEcosiaUrl() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(BrowserUiEvent.SubmitSearch("e tree planting"))
        advanceUntilIdle()

        assertThat(vm.uiState.value.currentUrl).isEqualTo("https://www.ecosia.org/search?q=tree+planting")
        assertThat(vm.uiState.value.settings.searchEngine.id).isEqualTo("ecosia")
    }

    @Test
    fun submitSearch_withStartpageShortcut_loadsStartpageUrl() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(BrowserUiEvent.SubmitSearch("s privacy"))
        advanceUntilIdle()

        assertThat(vm.uiState.value.currentUrl).isEqualTo("https://www.startpage.com/sp/search?query=privacy")
        assertThat(vm.uiState.value.settings.searchEngine.id).isEqualTo("startpage")
    }

    @Test
    fun saveSearchEngine_andSubmitWithCustomShortcut() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        // Add custom YouTube search provider with shortcut 'y'
        vm.onEvent(
            BrowserUiEvent.SaveSearchEngine(
                id = null,
                title = "YouTube",
                shortcut = "y",
                urlTemplate = "https://www.youtube.com/results?search_query=%s"
            )
        )
        advanceUntilIdle()

        val engines = vm.uiState.value.searchEngines
        assertThat(engines).hasSize(7)
        val ytEngine = engines.find { it.displayName == "YouTube" }
        assertThat(ytEngine).isNotNull()
        assertThat(ytEngine?.shortcut).isEqualTo("y")

        // Submit query using 'y' shortcut
        vm.onEvent(BrowserUiEvent.SubmitSearch("y lofi beats"))
        advanceUntilIdle()

        assertThat(vm.uiState.value.currentUrl).isEqualTo("https://www.youtube.com/results?search_query=lofi+beats")
        assertThat(vm.uiState.value.settings.searchEngine.displayName).isEqualTo("YouTube")
    }

    @Test
    fun saveSearchEngine_editExistingEngine() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        // Edit Google's shortcut to 'gg' and title to 'Google Search'
        vm.onEvent(
            BrowserUiEvent.SaveSearchEngine(
                id = "google",
                title = "Google Search",
                shortcut = "gg",
                urlTemplate = "https://www.google.com/search?q=%s"
            )
        )
        advanceUntilIdle()

        val engines = vm.uiState.value.searchEngines
        val google = engines.find { it.id == "google" }
        assertThat(google?.displayName).isEqualTo("Google Search")
        assertThat(google?.shortcut).isEqualTo("gg")

        // Old 'g' won't match, 'gg' will match
        vm.onEvent(BrowserUiEvent.SubmitSearch("gg jetpack"))
        advanceUntilIdle()

        assertThat(vm.uiState.value.currentUrl).isEqualTo("https://www.google.com/search?q=jetpack")
    }

    @Test
    fun deleteSearchEngine_removesFromListAndFallsBackIfActive() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        // Currently active engine is duckduckgo
        assertThat(vm.uiState.value.settings.searchEngine.id).isEqualTo("duckduckgo")

        // Delete duckduckgo
        vm.onEvent(BrowserUiEvent.DeleteSearchEngine("duckduckgo"))
        advanceUntilIdle()

        val engines = vm.uiState.value.searchEngines
        assertThat(engines).hasSize(5)
        assertThat(engines.none { it.id == "duckduckgo" }).isTrue()

        // Active engine should fall back to google (the next first engine)
        assertThat(vm.uiState.value.settings.searchEngine.id).isEqualTo("google")
    }

    @Test
    fun reorderSearchEngines_updatesOrder() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        val initialEngines = vm.uiState.value.searchEngines
        val reversed = initialEngines.reversed()

        vm.onEvent(BrowserUiEvent.ReorderSearchEngines(reversed))
        advanceUntilIdle()

        val updated = vm.uiState.value.searchEngines
        assertThat(updated.map { it.id }).containsExactly(
            "startpage", "ecosia", "kagi", "brave", "duckduckgo", "google"
        ).inOrder()
    }

    @Test
    fun dialogState_showAndDismissTogglesCorrectly() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.showAddEditSearchEngineDialog).isFalse()

        vm.onEvent(BrowserUiEvent.ShowAddSearchEngineDialog())
        assertThat(vm.uiState.value.showAddEditSearchEngineDialog).isTrue()
        assertThat(vm.uiState.value.editingSearchEngine).isNull()

        val google = vm.uiState.value.searchEngines.find { it.id == "google" }
        vm.onEvent(BrowserUiEvent.ShowAddSearchEngineDialog(engineToEdit = google))
        assertThat(vm.uiState.value.showAddEditSearchEngineDialog).isTrue()
        assertThat(vm.uiState.value.editingSearchEngine?.id).isEqualTo("google")

        vm.onEvent(BrowserUiEvent.DismissAddSearchEngineDialog)
        assertThat(vm.uiState.value.showAddEditSearchEngineDialog).isFalse()
        assertThat(vm.uiState.value.editingSearchEngine).isNull()
    }
}
