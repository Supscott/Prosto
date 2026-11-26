package com.jestk.browser.ui.viewmodel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.SettingsRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.Workspace
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

/** The menu's "Add to / Remove from favorites" item: the toggle event and the "is favorite" check behind its label. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PageFavoriteToggleTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var favoriteRepo: FavoriteRepositoryImpl
    private lateinit var viewModel: BrowserViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        db = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        favoriteRepo = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher)
        viewModel =
            BrowserViewModel(
                tabRepository = TabRepositoryImpl(db.tabDao(), testDispatcher),
                historyRepository = HistoryRepositoryImpl(db.historyDao(), testDispatcher),
                favoriteRepository = favoriteRepo,
                settingsRepository = SettingsRepositoryImpl(db.settingDao(), testDispatcher),
                ioDispatcher = testDispatcher,
            )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    private val state get() = viewModel.uiState.value

    @Test
    fun toggleOnHomeDoesNothing() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            viewModel.onEvent(ToggleCurrentPageFavorite)
            advanceUntilIdle()

            assertThat(state.favorites).isEmpty()
            assertThat(state.favoriteNotice).isNull()
        }

    @Test
    fun toggleAddsThePageThenRemovesIt() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://kotlinlang.org"))
            advanceUntilIdle()
            assertThat(state.currentPageFavorite).isNull()

            viewModel.onEvent(ToggleCurrentPageFavorite)
            advanceUntilIdle()

            assertThat(state.favorites.map { it.url }).containsExactly("https://kotlinlang.org")
            assertThat(state.currentPageFavorite).isNotNull()
            assertThat(state.favoriteNotice?.added).isTrue()
            val addedId = state.favoriteNotice!!.id

            viewModel.onEvent(ToggleCurrentPageFavorite)
            advanceUntilIdle()

            assertThat(state.favorites).isEmpty()
            assertThat(state.currentPageFavorite).isNull()
            assertThat(state.favoriteNotice?.added).isFalse()
            assertThat(state.favoriteNotice!!.id).isNotEqualTo(addedId)
        }

    @Test
    fun dismissClearsTheNotice() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://kotlinlang.org"))
            advanceUntilIdle()
            viewModel.onEvent(ToggleCurrentPageFavorite)
            advanceUntilIdle()
            assertThat(state.favoriteNotice).isNotNull()

            viewModel.onEvent(DismissFavoriteNotice)

            assertThat(state.favoriteNotice).isNull()
        }

    @Test
    fun favoriteAddedWithoutSchemeIsRecognisedOnItsPage() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.onEvent(BrowserUiEvent.AddFavorite("Kotlin", "  kotlinlang.org "))
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://kotlinlang.org"))
            advanceUntilIdle()

            assertThat(state.currentPageFavorite?.title).isEqualTo("Kotlin")

            viewModel.onEvent(ToggleCurrentPageFavorite)
            advanceUntilIdle()

            assertThat(state.favorites).isEmpty()
        }

    @Test
    fun normalizationMatchesWhatAddFavoriteStores() {
        assertThat(normalizeFavoriteUrl("  example.com/a ")).isEqualTo("https://example.com/a")
        assertThat(normalizeFavoriteUrl("http://example.com")).isEqualTo("http://example.com")
        assertThat(normalizeFavoriteUrl("https://example.com")).isEqualTo("https://example.com")
    }

    @Test
    fun favoriteOfAnotherWorkspaceDoesNotCount() {
        val page = "https://kotlinlang.org"
        val tab = Tab(id = "t", url = page, title = "Kotlin", isActive = true)
        val base =
            BrowserUiState(
                currentUrl = page,
                tabs = listOf(tab),
                activeTabId = "t",
                activeWorkspaceId = Workspace.DEFAULT_PERSONAL_ID,
            )

        val elsewhere = FavoriteItem(id = 1, title = "Kotlin", url = page, workspaceId = Workspace.DEFAULT_WORK_ID)
        assertThat(base.copy(favorites = listOf(elsewhere)).currentPageFavorite).isNull()

        val here = elsewhere.copy(id = 2, workspaceId = Workspace.DEFAULT_PERSONAL_ID)
        assertThat(base.copy(favorites = listOf(elsewhere, here)).currentPageFavorite).isEqualTo(here)
    }

    @Test
    fun internalPagesCannotBeFavorited() {
        val tab = Tab(id = "t", url = "about:blank", title = "", isActive = true)
        listOf("about:blank", "chrome://version", "file:///sdcard/a.html", "").forEach { url ->
            val state = BrowserUiState(currentUrl = url, tabs = listOf(tab), activeTabId = "t")
            assertThat(state.favoritablePageUrl).isNull()
        }
        val web = BrowserUiState(currentUrl = "http://example.com/x", tabs = listOf(tab), activeTabId = "t")
        assertThat(web.favoritablePageUrl).isEqualTo("http://example.com/x")
    }
}
