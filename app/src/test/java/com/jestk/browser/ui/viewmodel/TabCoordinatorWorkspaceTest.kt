package com.jestk.browser.ui.viewmodel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.local.entity.TabEntity
import com.jestk.browser.data.local.entity.TabFolderEntity
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.RecentTabRepositoryImpl
import com.jestk.browser.data.repository.SettingsRepositoryImpl
import com.jestk.browser.data.repository.TabFolderRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.data.repository.WorkspaceRepositoryImpl
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.engine.FaviconManager
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
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TabCoordinatorWorkspaceTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var db: AppDatabase
    private lateinit var tabRepo: TabRepositoryImpl
    private lateinit var tabFolderRepo: TabFolderRepositoryImpl
    private lateinit var workspaceRepo: WorkspaceRepositoryImpl
    private lateinit var historyRepo: HistoryRepositoryImpl
    private lateinit var favoriteRepo: FavoriteRepositoryImpl
    private lateinit var recentTabRepo: RecentTabRepositoryImpl

    private var uiState = BrowserUiState()
    private val tabThemeColors = ConcurrentHashMap<String, Int>()
    private val tabLoadingProgress = ConcurrentHashMap<String, Int>()
    private val tabMutex = Mutex()
    private val navigationCommands = mutableListOf<NavigationCommand>()

    private lateinit var coordinator: TabCoordinator
    private lateinit var webViewPool: com.jestk.browser.engine.WebViewPool

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        FaviconManager.init(context)
        db = AppDatabase.createInMemory(context)
        tabRepo = TabRepositoryImpl(db.tabDao(), testDispatcher)
        tabFolderRepo = TabFolderRepositoryImpl(db.tabFolderDao(), testDispatcher)
        workspaceRepo = WorkspaceRepositoryImpl(db.workspaceDao(), testDispatcher)
        historyRepo = HistoryRepositoryImpl(db.historyDao(), testDispatcher)
        favoriteRepo = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher)
        recentTabRepo = RecentTabRepositoryImpl(db.recentTabDao(), testDispatcher)
        webViewPool = com.jestk.browser.engine.WebViewPool(context)

        coordinator = TabCoordinator(
            tabRepository = tabRepo,
            recentTabRepository = recentTabRepo,
            favoriteRepository = favoriteRepo,
            historyRepository = historyRepo,
            webViewPool = webViewPool,
            tabOperationMutex = tabMutex,
            tabThemeColors = tabThemeColors,
            getCurrentState = { uiState },
            updateUiState = { transform -> uiState = transform(uiState) },
            emitNavigation = { navigationCommands.add(it) },
            scope = testScope,
            ioDispatcher = testDispatcher,
            tabLoadingProgress = tabLoadingProgress,
            tabFolderRepository = tabFolderRepo,
            workspaceRepository = workspaceRepo,
        )
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        webViewPool.clearAll()
        FaviconManager.deleteAllFavicons()
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun initialState_hasDefaultWorkspacesAndActivePersonalWorkspace() {
        assertThat(uiState.workspaces).hasSize(2)
        assertThat(uiState.activeWorkspaceId).isEqualTo(Workspace.DEFAULT_PERSONAL_ID)
        assertThat(uiState.activeWorkspace?.id).isEqualTo(Workspace.DEFAULT_PERSONAL_ID)
        assertThat(uiState.activeWorkspace?.id).isEqualTo(Workspace.DEFAULT_PERSONAL_ID)
    }

    @Test
    fun observeDatabaseFlows_filtersTabsAndFoldersByActiveWorkspaceId() =
        runTest(testDispatcher) {
            // Seed DB with personal and work tabs & folders
            db.tabDao().insertTab(
                TabEntity(id = "p1", url = "https://personal1.com", title = "P1", position = 0, workspaceId = Workspace.DEFAULT_PERSONAL_ID, isActive = true)
            )
            db.tabDao().insertTab(
                TabEntity(id = "p2", url = "https://personal2.com", title = "P2", position = 1, workspaceId = Workspace.DEFAULT_PERSONAL_ID)
            )
            db.tabDao().insertTab(
                TabEntity(id = "w1", url = "https://work1.com", title = "W1", position = 2, workspaceId = Workspace.DEFAULT_WORK_ID)
            )
            db.tabDao().insertTab(
                TabEntity(id = "w2", url = "https://work2.com", title = "W2", position = 3, workspaceId = Workspace.DEFAULT_WORK_ID)
            )

            db.tabFolderDao().insertFolder(
                TabFolderEntity(id = "f_personal", title = "Personal Folder", color = 1, position = 0, workspaceId = Workspace.DEFAULT_PERSONAL_ID)
            )
            db.tabFolderDao().insertFolder(
                TabFolderEntity(id = "f_work", title = "Work Folder", color = 2, position = 1, workspaceId = Workspace.DEFAULT_WORK_ID)
            )

            coordinator.sessionSync.observeDatabaseFlows()
            advanceUntilIdle()

            // uiState must expose only items belonging to DEFAULT_PERSONAL_ID
            assertThat(uiState.tabs.map { it.id }).containsExactly("p1", "p2")
            assertThat(uiState.folders.map { it.id }).containsExactly("f_personal")
            assertThat(uiState.activeTabId).isEqualTo("p1")
        }

    @Test
    fun switchWorkspace_switchesActiveWorkspaceAndExposesTargetTabs() =
        runTest(testDispatcher) {
            db.tabDao().insertTab(
                TabEntity(id = "p1", url = "https://personal1.com", title = "P1", position = 0, workspaceId = Workspace.DEFAULT_PERSONAL_ID, isActive = true)
            )
            db.tabDao().insertTab(
                TabEntity(id = "w1", url = "https://work1.com", title = "W1", position = 1, workspaceId = Workspace.DEFAULT_WORK_ID, lastActiveTimestamp = 1000L)
            )
            db.tabDao().insertTab(
                TabEntity(id = "w2", url = "https://work2.com", title = "W2", position = 2, workspaceId = Workspace.DEFAULT_WORK_ID, lastActiveTimestamp = 2000L)
            )
            db.tabFolderDao().insertFolder(
                TabFolderEntity(id = "f_personal", title = "Personal Folder", color = 1, position = 0, workspaceId = Workspace.DEFAULT_PERSONAL_ID)
            )
            db.tabFolderDao().insertFolder(
                TabFolderEntity(id = "f_work", title = "Work Folder", color = 2, position = 1, workspaceId = Workspace.DEFAULT_WORK_ID)
            )

            coordinator.sessionSync.observeDatabaseFlows()
            advanceUntilIdle()

            // Switch to work workspace
            coordinator.workspaces.switchWorkspace(Workspace.DEFAULT_WORK_ID)
            advanceUntilIdle()

            assertThat(uiState.activeWorkspaceId).isEqualTo(Workspace.DEFAULT_WORK_ID)
            assertThat(uiState.activeWorkspace?.id).isEqualTo(Workspace.DEFAULT_WORK_ID)
            assertThat(uiState.tabs.map { it.id }).containsExactly("w1", "w2")
            assertThat(uiState.folders.map { it.id }).containsExactly("f_work")
            // Tab w2 had higher lastActiveTimestamp (2000 > 1000) so it should be active
            assertThat(uiState.activeTabId).isEqualTo("w2")
            assertThat(uiState.activeTab?.url).isEqualTo("https://work2.com")
        }

    @Test
    fun switchWorkspace_whenTargetWorkspaceEmpty_createsHomeTab() =
        runTest(testDispatcher) {
            db.tabDao().insertTab(
                TabEntity(id = "p1", url = "https://personal.com", title = "Personal", position = 0, workspaceId = Workspace.DEFAULT_PERSONAL_ID, isActive = true)
            )
            coordinator.sessionSync.observeDatabaseFlows()
            advanceUntilIdle()

            assertThat(uiState.tabs.map { it.id }).containsExactly("p1")

            // Switch to empty Work workspace
            coordinator.workspaces.switchWorkspace(Workspace.DEFAULT_WORK_ID)
            advanceUntilIdle()

            assertThat(uiState.activeWorkspaceId).isEqualTo(Workspace.DEFAULT_WORK_ID)
            assertThat(uiState.tabs).hasSize(1)
            val workTab = uiState.tabs.first()
            assertThat(workTab.workspaceId).isEqualTo(Workspace.DEFAULT_WORK_ID)
            assertThat(workTab.isHomeTab).isTrue()
            assertThat(workTab.url).isEmpty()
            assertThat(uiState.activeTabId).isEqualTo(workTab.id)
        }

    @Test
    fun moveTabToWorkspace_movesTabAndActivatesNextTabInSource() =
        runTest(testDispatcher) {
            db.tabDao().insertTab(
                TabEntity(id = "p1", url = "https://p1.com", title = "Tab 1", position = 0, workspaceId = Workspace.DEFAULT_PERSONAL_ID, isActive = true)
            )
            db.tabDao().insertTab(
                TabEntity(id = "p2", url = "https://p2.com", title = "Tab 2", position = 1, workspaceId = Workspace.DEFAULT_PERSONAL_ID, isActive = false)
            )
            coordinator.sessionSync.observeDatabaseFlows()
            advanceUntilIdle()

            assertThat(uiState.tabs.map { it.id }).containsExactly("p1", "p2")
            assertThat(uiState.activeTabId).isEqualTo("p1")

            // Move active tab p1 to Work workspace
            coordinator.workspaces.moveTabToWorkspace("p1", Workspace.DEFAULT_WORK_ID)
            advanceUntilIdle()

            // p1 removed from personal workspace UI tabs, p2 becomes active
            assertThat(uiState.tabs.map { it.id }).containsExactly("p2")
            assertThat(uiState.activeTabId).isEqualTo("p2")

            // In DB, p1 has workspaceId = DEFAULT_WORK_ID and folderId = null
            val dbTab1 = tabRepo.getTabById("p1")
            assertThat(dbTab1).isNotNull()
            assertThat(dbTab1?.workspaceId).isEqualTo(Workspace.DEFAULT_WORK_ID)
            assertThat(dbTab1?.folderId).isNull()

            // Switch to Work workspace and verify p1 is present
            coordinator.workspaces.switchWorkspace(Workspace.DEFAULT_WORK_ID)
            advanceUntilIdle()

            assertThat(uiState.tabs.map { it.id }).containsExactly("p1")
            assertThat(uiState.activeTabId).isEqualTo("p1")
        }

    @Test
    fun moveTabToWorkspace_whenLastTabMoved_createsHomeTabInSource() =
        runTest(testDispatcher) {
            db.tabDao().insertTab(
                TabEntity(id = "only_tab", url = "https://only.com", title = "Only Tab", position = 0, workspaceId = Workspace.DEFAULT_PERSONAL_ID, isActive = true)
            )
            coordinator.sessionSync.observeDatabaseFlows()
            advanceUntilIdle()

            assertThat(uiState.tabs.map { it.id }).containsExactly("only_tab")

            coordinator.workspaces.moveTabToWorkspace("only_tab", Workspace.DEFAULT_WORK_ID)
            advanceUntilIdle()

            // Source workspace should now have a fresh Home tab
            assertThat(uiState.tabs).hasSize(1)
            val newHomeTab = uiState.tabs.first()
            assertThat(newHomeTab.id).isNotEqualTo("only_tab")
            assertThat(newHomeTab.workspaceId).isEqualTo(Workspace.DEFAULT_PERSONAL_ID)
            assertThat(newHomeTab.isHomeTab).isTrue()
            assertThat(uiState.activeTabId).isEqualTo(newHomeTab.id)
        }

    @Test
    fun history_savingAndQueriesAreScopedToWorkspace() =
        runTest(testDispatcher) {
            // Record visit in Personal workspace
            historyRepo.recordVisit("https://personal-site.com", "Personal Site", Workspace.DEFAULT_PERSONAL_ID)
            // Record visit in Work workspace
            historyRepo.recordVisit("https://work-portal.com", "Work Portal", Workspace.DEFAULT_WORK_ID)

            // Query personal history
            val personalHistory = historyRepo.getPagedHistory(limit = 10, offset = 0, workspaceId = Workspace.DEFAULT_PERSONAL_ID)
            assertThat(personalHistory.map { it.url }).containsExactly("https://personal-site.com")

            // Query work history
            val workHistory = historyRepo.getPagedHistory(limit = 10, offset = 0, workspaceId = Workspace.DEFAULT_WORK_ID)
            assertThat(workHistory.map { it.url }).containsExactly("https://work-portal.com")

            // Search scoped to personal
            val searchPersonal = historyRepo.searchHistoryPaged(query = "site", limit = 10, offset = 0, workspaceId = Workspace.DEFAULT_PERSONAL_ID)
            assertThat(searchPersonal.map { it.url }).containsExactly("https://personal-site.com")

            // Search scoped to work
            val searchWork = historyRepo.searchHistoryPaged(query = "site", limit = 10, offset = 0, workspaceId = Workspace.DEFAULT_WORK_ID)
            assertThat(searchWork).isEmpty()
        }

    @Test
    fun browserViewModel_switchWorkspaceAndMoveTabEvents_coordinateCorrectly() =
        runTest(testDispatcher) {
            val viewModel = BrowserViewModel(
                tabRepository = tabRepo,
                historyRepository = historyRepo,
                favoriteRepository = favoriteRepo,
                settingsRepository = SettingsRepositoryImpl(db.settingDao(), testDispatcher),
                recentTabRepository = recentTabRepo,
                tabFolderRepository = tabFolderRepo,
                workspaceRepository = workspaceRepo,
                ioDispatcher = testDispatcher,
            )
            advanceUntilIdle()

            // After cold boot, personal workspace has 1 home tab
            val personalHomeTab = viewModel.uiState.value.tabs.first()
            assertThat(viewModel.uiState.value.activeWorkspaceId).isEqualTo(Workspace.DEFAULT_PERSONAL_ID)
            assertThat(personalHomeTab.workspaceId).isEqualTo(Workspace.DEFAULT_PERSONAL_ID)

            // Switch to work workspace via ViewModel event
            viewModel.onEvent(BrowserUiEvent.SwitchWorkspace(Workspace.DEFAULT_WORK_ID))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.activeWorkspaceId).isEqualTo(Workspace.DEFAULT_WORK_ID)
            assertThat(viewModel.uiState.value.tabs).hasSize(1)
            val workHomeTab = viewModel.uiState.value.tabs.first()
            assertThat(workHomeTab.workspaceId).isEqualTo(Workspace.DEFAULT_WORK_ID)

            // Move workHomeTab to Personal workspace
            viewModel.onEvent(BrowserUiEvent.MoveTabToWorkspace(workHomeTab.id, Workspace.DEFAULT_PERSONAL_ID))
            advanceUntilIdle()

            // Source workspace (Work) now has a new Home tab because its only tab was moved
            val newWorkTab = viewModel.uiState.value.tabs.first()
            assertThat(newWorkTab.id).isNotEqualTo(workHomeTab.id)

            // Switch to Personal workspace and verify workHomeTab was moved there
            viewModel.switchWorkspace(Workspace.DEFAULT_PERSONAL_ID)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.tabs.map { it.id }).contains(workHomeTab.id)
        }

    @Test
    fun createWorkspace_addsWorkspaceToRepositoryAndFlowUpdatesWorkspaces() =
        runTest(testDispatcher) {
            coordinator.sessionSync.observeDatabaseFlows()
            advanceUntilIdle()

            val initialCount = uiState.workspaces.size
            coordinator.workspaces.createWorkspace("Research", 0xFF10B981.toInt())
            advanceUntilIdle()

            assertThat(uiState.workspaces).hasSize(initialCount + 1)
            val created = uiState.workspaces.find { it.name == "Research" }
            assertThat(created).isNotNull()
            assertThat(created?.color).isEqualTo(0xFF10B981.toInt())
            assertThat(created?.id).startsWith("ws_")
        }

    @Test
    fun updateWorkspace_updatesNameAndColor() =
        runTest(testDispatcher) {
            coordinator.sessionSync.observeDatabaseFlows()
            advanceUntilIdle()

            val personal = uiState.workspaces.first { it.id == Workspace.DEFAULT_PERSONAL_ID }
            val updated = personal.copy(name = "Personal Renamed", color = 0xFFFF6F61.toInt())
            coordinator.workspaces.updateWorkspace(updated)
            advanceUntilIdle()

            val found = uiState.workspaces.find { it.id == Workspace.DEFAULT_PERSONAL_ID }
            assertThat(found?.name).isEqualTo("Personal Renamed")
            assertThat(found?.color).isEqualTo(0xFFFF6F61.toInt())
        }

    @Test
    fun deleteWorkspace_guardsAgainstDeletingLastWorkspace() =
        runTest(testDispatcher) {
            // First delete Work workspace
            coordinator.sessionSync.observeDatabaseFlows()
            advanceUntilIdle()

            coordinator.workspaces.deleteWorkspace(Workspace.DEFAULT_WORK_ID)
            advanceUntilIdle()

            assertThat(uiState.workspaces).hasSize(1)
            val onlyWorkspaceId = uiState.workspaces.first().id

            // Attempt to delete the last remaining workspace
            coordinator.workspaces.deleteWorkspace(onlyWorkspaceId)
            advanceUntilIdle()

            // Must still have 1 workspace
            assertThat(uiState.workspaces).hasSize(1)
            assertThat(uiState.workspaces.first().id).isEqualTo(onlyWorkspaceId)
        }

    @Test
    fun deleteWorkspace_deletesNonActiveWorkspaceAndClosesItsTabs() =
        runTest(testDispatcher) {
            db.tabDao().insertTab(
                TabEntity(id = "p1", url = "https://personal.com", title = "P1", position = 0, workspaceId = Workspace.DEFAULT_PERSONAL_ID, isActive = true)
            )
            db.tabDao().insertTab(
                TabEntity(id = "w1", url = "https://work1.com", title = "W1", position = 1, workspaceId = Workspace.DEFAULT_WORK_ID)
            )
            coordinator.sessionSync.observeDatabaseFlows()
            advanceUntilIdle()

            // Personal is active. Delete Work workspace.
            coordinator.workspaces.deleteWorkspace(Workspace.DEFAULT_WORK_ID)
            advanceUntilIdle()

            // Work workspace deleted
            assertThat(uiState.workspaces.map { it.id }).doesNotContain(Workspace.DEFAULT_WORK_ID)
            // Work tab w1 deleted from DB
            assertThat(tabRepo.getTabById("w1")).isNull()
            // Personal workspace and tab remain
            assertThat(uiState.activeWorkspaceId).isEqualTo(Workspace.DEFAULT_PERSONAL_ID)
            assertThat(uiState.tabs.map { it.id }).containsExactly("p1")
        }

    @Test
    fun deleteWorkspace_deletesActiveWorkspace_switchesToAnotherWorkspaceAndClosesTabs() =
        runTest(testDispatcher) {
            db.tabDao().insertTab(
                TabEntity(id = "p1", url = "https://personal.com", title = "P1", position = 0, workspaceId = Workspace.DEFAULT_PERSONAL_ID, isActive = true)
            )
            db.tabDao().insertTab(
                TabEntity(id = "w1", url = "https://work1.com", title = "W1", position = 1, workspaceId = Workspace.DEFAULT_WORK_ID)
            )
            coordinator.sessionSync.observeDatabaseFlows()
            advanceUntilIdle()

            // Switch to Work workspace
            coordinator.workspaces.switchWorkspace(Workspace.DEFAULT_WORK_ID)
            advanceUntilIdle()
            assertThat(uiState.activeWorkspaceId).isEqualTo(Workspace.DEFAULT_WORK_ID)

            // Delete active Work workspace
            coordinator.workspaces.deleteWorkspace(Workspace.DEFAULT_WORK_ID)
            advanceUntilIdle()

            // Switched to Personal
            assertThat(uiState.activeWorkspaceId).isEqualTo(Workspace.DEFAULT_PERSONAL_ID)
            assertThat(uiState.workspaces.map { it.id }).doesNotContain(Workspace.DEFAULT_WORK_ID)
            assertThat(tabRepo.getTabById("w1")).isNull()
        }

    @Test
    fun browserViewModel_workspaceCrudAndOverviewEvents_coordinateCorrectly() =
        runTest(testDispatcher) {
            val viewModel = BrowserViewModel(
                tabRepository = tabRepo,
                historyRepository = historyRepo,
                favoriteRepository = favoriteRepo,
                settingsRepository = SettingsRepositoryImpl(db.settingDao(), testDispatcher),
                recentTabRepository = recentTabRepo,
                tabFolderRepository = tabFolderRepo,
                workspaceRepository = workspaceRepo,
                ioDispatcher = testDispatcher,
            )
            advanceUntilIdle()

            // Test Overview events
            assertThat(viewModel.uiState.value.isWorkspacesOverviewVisible).isFalse()
            viewModel.onEvent(BrowserUiEvent.OpenWorkspacesOverview)
            assertThat(viewModel.uiState.value.isWorkspacesOverviewVisible).isTrue()
            assertThat(viewModel.uiState.value.activeOverlay).isEqualTo(com.jestk.browser.domain.model.OverlayType.WORKSPACES)

            viewModel.onEvent(BrowserUiEvent.CloseWorkspacesOverview)
            assertThat(viewModel.uiState.value.isWorkspacesOverviewVisible).isFalse()

            // Test CreateWorkspace event
            viewModel.onEvent(BrowserUiEvent.CreateWorkspace("Test Space", 0xFF00BCD4.toInt()))
            advanceUntilIdle()

            val created = viewModel.uiState.value.workspaces.find { it.name == "Test Space" }
            assertThat(created).isNotNull()

            // Test UpdateWorkspace event
            val updated = created!!.copy(name = "Updated Space")
            viewModel.onEvent(BrowserUiEvent.UpdateWorkspace(updated))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.workspaces.find { it.id == created.id }?.name).isEqualTo("Updated Space")

            // Test DeleteWorkspace event
            viewModel.onEvent(BrowserUiEvent.DeleteWorkspace(created.id))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.workspaces.map { it.id }).doesNotContain(created.id)
        }

    @Test
    fun workspaceScoped_recentlyClosedTabs_isolatedPerWorkspace() =
        runTest(testDispatcher) {
            workspaceRepo.ensureDefaultWorkspaces()
            coordinator.sessionSync.observeDatabaseFlows()
            advanceUntilIdle()

            // 1. Create two tabs in Personal
            val p1 = tabRepo.createTab("https://personal1.com", "Personal 1", workspaceId = Workspace.DEFAULT_PERSONAL_ID)
            val p2 = tabRepo.createTab("https://personal2.com", "Personal 2", workspaceId = Workspace.DEFAULT_PERSONAL_ID)
            tabRepo.setActiveTab(p1.id)
            advanceUntilIdle()

            // 2. Close p1 in Personal -> saved as recently closed in Personal
            coordinator.closing.closeTab(p1.id)
            advanceUntilIdle()

            assertThat(uiState.recentlyClosedTab?.id).isEqualTo(p1.id)
            assertThat(uiState.showUndoTabCloseSnackbar).isTrue()

            // 3. Switch to Work -> Work has no recently closed tab yet
            coordinator.workspaces.switchWorkspace(Workspace.DEFAULT_WORK_ID)
            advanceUntilIdle()

            assertThat(uiState.recentlyClosedTab).isNull()
            assertThat(uiState.showUndoTabCloseSnackbar).isFalse()

            // 4. Create and close a tab in Work
            val w1 = tabRepo.createTab("https://work1.com", "Work 1", workspaceId = Workspace.DEFAULT_WORK_ID)
            tabRepo.setActiveTab(w1.id)
            advanceUntilIdle()

            coordinator.closing.closeTab(w1.id)
            advanceUntilIdle()

            assertThat(uiState.recentlyClosedTab?.id).isEqualTo(w1.id)
            assertThat(uiState.showUndoTabCloseSnackbar).isTrue()

            // 5. Switch back to Personal -> Personal's recently closed tab restored
            coordinator.workspaces.switchWorkspace(Workspace.DEFAULT_PERSONAL_ID)
            advanceUntilIdle()

            assertThat(uiState.recentlyClosedTab?.id).isEqualTo(p1.id)
            assertThat(uiState.showUndoTabCloseSnackbar).isTrue()

            // 6. Undo close tab in Personal restores p1
            coordinator.closing.undoCloseTab()
            advanceUntilIdle()

            val restoredTab = uiState.tabs.find { it.url == "https://personal1.com" }
            assertThat(restoredTab).isNotNull()
            assertThat(restoredTab?.workspaceId).isEqualTo(Workspace.DEFAULT_PERSONAL_ID)
            assertThat(uiState.recentlyClosedTab).isNull()
            assertThat(uiState.showUndoTabCloseSnackbar).isFalse()
        }

    @Test
    fun workspaceScoped_favorites_isolatedAndMovable() =
        runTest(testDispatcher) {
            workspaceRepo.ensureDefaultWorkspaces()
            coordinator.sessionSync.observeDatabaseFlows()
            advanceUntilIdle()

            // 1. Add favorite in Personal
            favoriteRepo.addFavorite("Personal Fav", "https://personal-fav.com", workspaceId = Workspace.DEFAULT_PERSONAL_ID)
            advanceUntilIdle()

            assertThat(uiState.favorites.map { it.title }).containsExactly("Personal Fav")

            // 2. Switch to Work -> favorites is empty for Work
            coordinator.workspaces.switchWorkspace(Workspace.DEFAULT_WORK_ID)
            advanceUntilIdle()

            assertThat(uiState.favorites).isEmpty()

            // 3. Add favorite in Work
            favoriteRepo.addFavorite("Work Fav", "https://work-fav.com", workspaceId = Workspace.DEFAULT_WORK_ID)
            advanceUntilIdle()

            assertThat(uiState.favorites.map { it.title }).containsExactly("Work Fav")

            // 4. Switch to Personal -> shows Personal Fav only
            coordinator.workspaces.switchWorkspace(Workspace.DEFAULT_PERSONAL_ID)
            advanceUntilIdle()

            assertThat(uiState.favorites.map { it.title }).containsExactly("Personal Fav")

            // 5. Move Personal Fav to Work
            val fav = uiState.favorites.first()
            coordinator.favorites.moveFavoriteToWorkspace(fav.id, Workspace.DEFAULT_WORK_ID)
            advanceUntilIdle()

            // Now Personal favorites is empty
            assertThat(uiState.favorites).isEmpty()

            // Switch to Work -> contains both Work Fav and Personal Fav
            coordinator.workspaces.switchWorkspace(Workspace.DEFAULT_WORK_ID)
            advanceUntilIdle()

            assertThat(uiState.favorites.map { it.title }).containsExactly("Work Fav", "Personal Fav")
        }

    @Test
    fun coordinator_addFavorite_savesToActiveWorkspace() =
        runTest(testDispatcher) {
            workspaceRepo.ensureDefaultWorkspaces()
            coordinator.sessionSync.observeDatabaseFlows()
            advanceUntilIdle()

            // 1. Switch to Work workspace
            coordinator.workspaces.switchWorkspace(Workspace.DEFAULT_WORK_ID)
            advanceUntilIdle()

            // 2. Call coordinator.addFavorite
            coordinator.favorites.addFavorite("Work Site", "https://work-site.com")
            advanceUntilIdle()

            // Favorite must appear in Work workspace
            assertThat(uiState.favorites.map { it.title }).containsExactly("Work Site")

            // 3. Switch to Personal workspace -> must NOT contain Work Site
            coordinator.workspaces.switchWorkspace(Workspace.DEFAULT_PERSONAL_ID)
            advanceUntilIdle()

            assertThat(uiState.favorites).isEmpty()
        }

    @Test
    fun coordinator_recentTabs_isolatedPerWorkspace() =
        runTest(testDispatcher) {
            workspaceRepo.ensureDefaultWorkspaces()
            coordinator.sessionSync.observeDatabaseFlows()
            advanceUntilIdle()

            // 1. In Personal workspace, visit a site
            val pTab = tabRepo.createTab("https://personal-site.com", "Personal Site", workspaceId = Workspace.DEFAULT_PERSONAL_ID)
            coordinator.pageEvents.handlePageFinished(pTab.id, "https://personal-site.com", "Personal Site")
            advanceUntilIdle()

            assertThat(uiState.recentTabs.map { it.url }).contains("https://personal-site.com")

            // 2. Switch to Work workspace -> must NOT contain personal-site
            coordinator.workspaces.switchWorkspace(Workspace.DEFAULT_WORK_ID)
            advanceUntilIdle()

            assertThat(uiState.recentTabs.map { it.url }).doesNotContain("https://personal-site.com")

            // 3. In Work workspace, visit work-site
            val wTab = tabRepo.createTab("https://work-site.com", "Work Site", workspaceId = Workspace.DEFAULT_WORK_ID)
            coordinator.pageEvents.handlePageFinished(wTab.id, "https://work-site.com", "Work Site")
            advanceUntilIdle()

            assertThat(uiState.recentTabs.map { it.url }).containsExactly("https://work-site.com")

            // 4. Switch back to Personal -> only personal-site
            coordinator.workspaces.switchWorkspace(Workspace.DEFAULT_PERSONAL_ID)
            advanceUntilIdle()

            assertThat(uiState.recentTabs.map { it.url }).containsExactly("https://personal-site.com")
        }

    @Test
    fun switchWorkspace_atomicallyUpdatesTabsWithoutGhostTabs() =
        runTest(testDispatcher) {
            workspaceRepo.ensureDefaultWorkspaces()
            coordinator.sessionSync.observeDatabaseFlows()
            advanceUntilIdle()

            // 1. Create 3 tabs in Personal
            val p1 = tabRepo.createTab("https://personal1.com", "P1", workspaceId = Workspace.DEFAULT_PERSONAL_ID)
            val p2 = tabRepo.createTab("https://personal2.com", "P2", workspaceId = Workspace.DEFAULT_PERSONAL_ID)
            val p3 = tabRepo.createTab("https://personal3.com", "P3", workspaceId = Workspace.DEFAULT_PERSONAL_ID)
            coordinator.activation.selectTab(p1.id)
            advanceUntilIdle()

            assertThat(uiState.tabs.map { it.id }).containsExactly(p1.id, p2.id, p3.id).inOrder()

            // 2. Create 1 tab in Work
            val w1 = tabRepo.createTab("https://work1.com", "W1", workspaceId = Workspace.DEFAULT_WORK_ID)
            advanceUntilIdle()

            // 3. Switch to Work
            coordinator.workspaces.switchWorkspace(Workspace.DEFAULT_WORK_ID)
            advanceUntilIdle()

            // uiState.tabs must contain ONLY w1, zero tabs from Personal
            assertThat(uiState.tabs.map { it.id }).containsExactly(w1.id)
            assertThat(uiState.tabs.any { it.workspaceId != Workspace.DEFAULT_WORK_ID }).isFalse()

            // 4. Switch back to Personal
            coordinator.workspaces.switchWorkspace(Workspace.DEFAULT_PERSONAL_ID)
            advanceUntilIdle()

            assertThat(uiState.tabs.map { it.id }).containsExactly(p1.id, p2.id, p3.id).inOrder()
            assertThat(uiState.tabs.any { it.workspaceId != Workspace.DEFAULT_PERSONAL_ID }).isFalse()
        }
}
