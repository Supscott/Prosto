package com.jestk.browser.ui.viewmodel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.TabFolderRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.domain.model.DomainGroupKey
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.model.suggestDomainGroup
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.viewmodel.events.DomainGroupEventHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import java.util.concurrent.ConcurrentHashMap

/** Create and dismiss of the domain folder suggestion, through the real tab and folder code paths. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DomainGroupEventHandlerTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var db: AppDatabase
    private lateinit var tabFolderRepo: TabFolderRepositoryImpl
    private lateinit var coordinator: TabCoordinator
    private lateinit var handler: DomainGroupEventHandler
    private var uiState = BrowserUiState()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        FaviconManager.init(context)
        db = AppDatabase.createInMemory(context)
        tabFolderRepo = TabFolderRepositoryImpl(db.tabFolderDao(), testDispatcher)
        coordinator = TabCoordinator(
            tabRepository = TabRepositoryImpl(db.tabDao(), testDispatcher),
            favoriteRepository = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher),
            historyRepository = HistoryRepositoryImpl(db.historyDao(), testDispatcher),
            webViewPool = null,
            tabOperationMutex = Mutex(),
            tabThemeColors = ConcurrentHashMap(),
            getCurrentState = { uiState },
            updateUiState = { transform -> uiState = transform(uiState) },
            emitNavigation = {},
            scope = testScope,
            ioDispatcher = testDispatcher,
            tabFolderRepository = tabFolderRepo,
        )
        handler = DomainGroupEventHandler(
            tabCoordinator = coordinator,
            getCurrentState = { uiState },
            updateUiState = { transform -> uiState = transform(uiState) },
            scope = testScope,
        )
    }

    @After
    fun teardown() {
        FaviconManager.deleteAllFavicons()
        Dispatchers.resetMain()
        db.close()
    }

    private suspend fun TestScope.openTabs(vararg urls: String) {
        urls.forEach {
            coordinator.opening.openNewTab(it)
            advanceUntilIdle()
        }
    }

    @Test
    fun create_putsAllTabsOfTheSiteIntoOneNamedFolder() =
        runTest(testDispatcher) {
            openTabs("https://youtube.com/a", "https://other.org/x", "https://music.youtube.com/b", "https://www.youtube.com/c")

            handler.handle(CreateDomainGroup("youtube.com"))
            advanceUntilIdle()

            val folder = uiState.folders.single()
            assertThat(folder.title).isEqualTo("Youtube")
            assertThat(folder.color).isIn(TabFolder.PALETTE)
            assertThat(uiState.getTabsInFolder(folder.id).map { it.url })
                .containsExactly("https://youtube.com/a", "https://music.youtube.com/b", "https://www.youtube.com/c")
            assertThat(uiState.tabs.single { it.url == "https://other.org/x" }.folderId).isNull()
            assertThat(tabFolderRepo.getFolderById(folder.id)?.title).isEqualTo("Youtube")
            assertThat(suggestDomainGroup(uiState.tabs, uiState.activeWorkspaceId)).isNull()
        }

    @Test
    fun create_isIgnoredBelowTheThreshold() =
        runTest(testDispatcher) {
            openTabs("https://a.com/1", "https://a.com/2")

            handler.handle(CreateDomainGroup("a.com"))
            advanceUntilIdle()

            assertThat(uiState.folders).isEmpty()
        }

    @Test
    fun dismiss_hidesTheSuggestionUntilAnotherTabOfTheSiteOpens() =
        runTest(testDispatcher) {
            openTabs("https://a.com/1", "https://a.com/2", "https://a.com/3")
            val suggestion = suggestDomainGroup(uiState.tabs, uiState.activeWorkspaceId)!!

            handler.handle(DismissDomainGroup(suggestion.domain, suggestion.tabIds.size))

            assertThat(uiState.domainGroupDismissals)
                .containsExactly(DomainGroupKey(uiState.activeWorkspaceId, "a.com"), 3)
            assertThat(suggestDomainGroup(uiState.tabs, uiState.activeWorkspaceId, uiState.domainGroupDismissals)).isNull()

            openTabs("https://sub.a.com/4")
            assertThat(
                suggestDomainGroup(uiState.tabs, uiState.activeWorkspaceId, uiState.domainGroupDismissals)?.tabIds,
            ).hasSize(4)
        }
}
