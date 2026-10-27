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
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.InsecureNavigationTarget
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.repository.FavoriteRepository
import com.jestk.browser.domain.repository.HistoryRepository
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.domain.repository.TabRepository
import com.jestk.browser.util.UrlSecurityUtils
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
class InsecureHttpWarningTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var tabRepo: TabRepository
    private lateinit var historyRepo: HistoryRepository
    private lateinit var favoriteRepo: FavoriteRepository
    private lateinit var settingsRepo: SettingsRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        tabRepo = TabRepositoryImpl(db.tabDao(), testDispatcher)
        historyRepo = HistoryRepositoryImpl(db.historyDao(), testDispatcher)
        favoriteRepo = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher)
        settingsRepo = SettingsRepositoryImpl(db.settingDao(), testDispatcher)
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun createViewModel(): BrowserViewModel {
        return BrowserViewModel(
            tabRepository = tabRepo,
            historyRepository = historyRepo,
            favoriteRepository = favoriteRepo,
            settingsRepository = settingsRepo,
            ioDispatcher = testDispatcher,
        )
    }

    @Test
    fun urlSecurityUtils_extractDisplayHost_preservesCustomPorts() {
        assertThat(UrlSecurityUtils.extractDisplayHost("http://localhost:8080/path")).isEqualTo("localhost:8080")
        assertThat(UrlSecurityUtils.extractDisplayHost("http://example.com:80/path")).isEqualTo("example.com")
        assertThat(UrlSecurityUtils.extractDisplayHost("http://192.168.1.1:3000/")).isEqualTo("192.168.1.1:3000")
        assertThat(UrlSecurityUtils.extractDisplayHost("http://mysite.org/")).isEqualTo("mysite.org")
    }

    @Test
    fun loadUrl_withInsecureHttpUrl_showsWarningAndDoesNotNavigateImmediately() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(BrowserUiEvent.LoadUrl("http://insecure-site.org"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.insecureHttpPrompt).isNotNull()
        assertThat(state.insecureHttpPrompt?.url).isEqualTo("http://insecure-site.org")
        assertThat(state.insecureHttpPrompt?.target).isEqualTo(InsecureNavigationTarget.ACTIVE_TAB)
        // Page should not have navigated yet
        assertThat(state.currentUrl).isEmpty()
    }

    @Test
    fun confirmInsecureHttpNavigation_loadsUrlAndWhitelistsHostForSession() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(BrowserUiEvent.LoadUrl("http://insecure-site.org/login"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.insecureHttpPrompt).isNotNull()

        // User confirms navigation
        viewModel.onEvent(BrowserUiEvent.ConfirmInsecureHttpNavigation)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.insecureHttpPrompt).isNull()
        assertThat(state.allowedInsecureHosts).contains("insecure-site.org")
        assertThat(state.currentUrl).isEqualTo("http://insecure-site.org/login")

        // Navigating to the same host again should NOT show warning prompt
        viewModel.onEvent(BrowserUiEvent.LoadUrl("http://insecure-site.org/dashboard"))
        advanceUntilIdle()

        val nextState = viewModel.uiState.value
        assertThat(nextState.insecureHttpPrompt).isNull()
        assertThat(nextState.currentUrl).isEqualTo("http://insecure-site.org/dashboard")
    }

    @Test
    fun confirmInsecureHttpNavigation_withOriginatingTabId_switchesToOriginatingTab() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Create tab 1 and tab 2
        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://tab1.com"))
        advanceUntilIdle()
        val tab1Id = viewModel.uiState.value.activeTabId!!

        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://tab2.com"))
        advanceUntilIdle()
        val tab2Id = viewModel.uiState.value.activeTabId!!
        assertThat(tab2Id).isNotEqualTo(tab1Id)

        // Web client in tab 1 triggered an insecure navigation while tab 2 was active
        viewModel.onEvent(
            BrowserUiEvent.ShowInsecureHttpWarning(
                url = "http://insecure-sublink.org",
                tabId = tab1Id,
                target = InsecureNavigationTarget.ACTIVE_TAB,
            )
        )
        advanceUntilIdle()

        // User confirms
        viewModel.onEvent(BrowserUiEvent.ConfirmInsecureHttpNavigation)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.insecureHttpPrompt).isNull()
        // Must have activated tab1Id
        assertThat(state.activeTabId).isEqualTo(tab1Id)
        assertThat(state.currentUrl).isEqualTo("http://insecure-sublink.org")
    }

    @Test
    fun dismissInsecureHttpWarning_cancelsNavigationAndRestoresOverlay() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(BrowserUiEvent.LoadUrl("http://sketchy-site.net"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.insecureHttpPrompt).isNotNull()

        // User dismisses modal
        viewModel.onEvent(BrowserUiEvent.DismissInsecureHttpWarning)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.insecureHttpPrompt).isNull()
        assertThat(state.allowedInsecureHosts).doesNotContain("sketchy-site.net")
        assertThat(state.currentUrl).isEmpty()
        assertThat(state.activeOverlay).isEqualTo(OverlayType.HOME)
    }

    @Test
    fun openNewTab_withInsecureHttpUrl_showsWarningAndCreatesTabOnConfirm() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val initialTabCount = viewModel.uiState.value.tabs.size

        viewModel.onEvent(BrowserUiEvent.OpenNewTab("http://external-http.com"))
        advanceUntilIdle()

        val prompt = viewModel.uiState.value.insecureHttpPrompt
        assertThat(prompt).isNotNull()
        assertThat(prompt?.url).isEqualTo("http://external-http.com")
        assertThat(prompt?.target).isEqualTo(InsecureNavigationTarget.NEW_TAB)
        assertThat(viewModel.uiState.value.tabs.size).isEqualTo(initialTabCount)

        // Confirm
        viewModel.onEvent(BrowserUiEvent.ConfirmInsecureHttpNavigation)
        advanceUntilIdle()

        val updatedState = viewModel.uiState.value
        assertThat(updatedState.insecureHttpPrompt).isNull()
        assertThat(updatedState.tabs.size).isEqualTo(initialTabCount + 1)
        assertThat(updatedState.currentUrl).isEqualTo("http://external-http.com")
        assertThat(updatedState.allowedInsecureHosts).contains("external-http.com")
    }

    @Test
    fun openTabInBackground_withInsecureHttpUrl_showsWarningAndOpensBackgroundTabOnConfirm() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val initialActiveTabId = viewModel.uiState.value.activeTabId
        val initialTabCount = viewModel.uiState.value.tabs.size

        viewModel.onEvent(BrowserUiEvent.OpenTabInBackground("http://background-site.info"))
        advanceUntilIdle()

        val prompt = viewModel.uiState.value.insecureHttpPrompt
        assertThat(prompt).isNotNull()
        assertThat(prompt?.target).isEqualTo(InsecureNavigationTarget.BACKGROUND_TAB)
        assertThat(viewModel.uiState.value.tabs.size).isEqualTo(initialTabCount)

        viewModel.onEvent(BrowserUiEvent.ConfirmInsecureHttpNavigation)
        advanceUntilIdle()

        val updatedState = viewModel.uiState.value
        assertThat(updatedState.insecureHttpPrompt).isNull()
        assertThat(updatedState.tabs.size).isEqualTo(initialTabCount + 1)
        assertThat(updatedState.activeTabId).isEqualTo(initialActiveTabId)
        assertThat(updatedState.allowedInsecureHosts).contains("background-site.info")
    }

    @Test
    fun backNavigation_dismissesInsecureWarningDialog() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(BrowserUiEvent.LoadUrl("http://untrusted-network.com"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.insecureHttpPrompt).isNotNull()

        // Trigger back action
        viewModel.onEvent(BrowserUiEvent.HandleBackPress)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.insecureHttpPrompt).isNull()
    }

    @Test
    fun previewImageInNewTab_withInsecureHttpUrl_showsWarning() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(BrowserUiEvent.PreviewImageInNewTab("http://image.insecure.org/pic.png"))
        advanceUntilIdle()

        val prompt = viewModel.uiState.value.insecureHttpPrompt
        assertThat(prompt).isNotNull()
        assertThat(prompt?.url).isEqualTo("http://image.insecure.org/pic.png")
        assertThat(prompt?.target).isEqualTo(InsecureNavigationTarget.NEW_TAB)
    }

    @Test
    fun restoreSession_prepopulatesExistingInsecureTabHosts() = runTest(testDispatcher) {
        // Pre-create an insecure tab in DB
        tabRepo.createTab("http://nas.local:8080/dashboard", "NAS")
        advanceUntilIdle()

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Restore session
        viewModel.tabCoordinator.restoreSession(
            initialSettings = BrowserSettings(),
            currentEngines = SearchEngine.DEFAULT_ENGINES,
        )
        advanceUntilIdle()

        // The restored host should be pre-approved so it loads smoothly without unexpected warning
        assertThat(viewModel.uiState.value.allowedInsecureHosts).contains("nas.local")
    }
}
