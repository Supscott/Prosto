package com.jestk.browser.ui.viewmodel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.mapper.parseSiteCounts
import com.jestk.browser.data.mapper.parseSiteSet
import com.jestk.browser.data.mapper.serializeSiteCounts
import com.jestk.browser.data.mapper.serializeSiteSet
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.RecentTabRepositoryImpl
import com.jestk.browser.data.repository.SettingsRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.ExternalAppPrompt
import com.jestk.browser.ui.viewmodel.tabs.OpenInAppExclusions
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

/** Per-site "don't offer to open in the app": counting, the question, exclusion and persistence (PRO-64). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OpenInAppExclusionTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var settingsRepo: SettingsRepositoryImpl
    private lateinit var viewModel: BrowserViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        settingsRepo = SettingsRepositoryImpl(db.settingDao(), testDispatcher)
        viewModel = BrowserViewModel(
            tabRepository = TabRepositoryImpl(db.tabDao(), testDispatcher),
            historyRepository = HistoryRepositoryImpl(db.historyDao(), testDispatcher),
            favoriteRepository = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher),
            settingsRepository = settingsRepo,
            recentTabRepository = RecentTabRepositoryImpl(db.recentTabDao(), testDispatcher),
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun prompt(tabId: String, domain: String = "reddit.com", confirm: Boolean = false) =
        ExternalAppPrompt(
            tabId = tabId,
            appName = "Reddit",
            intentUri = "intent://r/android#Intent;scheme=https;package=com.reddit.frontpage;end",
            domain = domain,
            requiresConfirmation = confirm,
        )

    private suspend fun kotlinx.coroutines.test.TestScope.activeTab(): String {
        advanceUntilIdle()
        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://example.com"))
        advanceUntilIdle()
        return viewModel.uiState.value.activeTabId!!
    }

    private fun swipeAway(domain: String, times: Int) =
        repeat(times) { viewModel.onEvent(RecordOpenInAppDismissal(domain)) }

    @Test
    fun threeExplicitDismissals_replaceThePillWithTheQuestion() =
        runTest(testDispatcher) {
            val tabId = activeTab()
            swipeAway("reddit.com", 3)

            viewModel.onEvent(BrowserUiEvent.OnExternalAppOffered(tabId, prompt(tabId, "www.reddit.com"), false))

            assertThat(viewModel.uiState.value.externalAppPrompt).isNull()
            assertThat(viewModel.uiState.value.openInAppExclusionPrompt?.tabId).isEqualTo(tabId)
        }

    @Test
    fun twoExplicitDismissals_stillShowTheNormalPill() =
        runTest(testDispatcher) {
            val tabId = activeTab()
            swipeAway("reddit.com", 2)

            viewModel.onEvent(BrowserUiEvent.OnExternalAppOffered(tabId, prompt(tabId), false))

            assertThat(viewModel.uiState.value.externalAppPrompt).isNotNull()
            assertThat(viewModel.uiState.value.openInAppExclusionPrompt).isNull()
        }

    @Test
    fun timeouts_doNotCount() =
        runTest(testDispatcher) {
            val tabId = activeTab()
            repeat(4) {
                viewModel.onEvent(BrowserUiEvent.OnExternalAppOffered(tabId, prompt(tabId), false))
                viewModel.onEvent(BrowserUiEvent.DismissExternalAppPrompt(ExternalAppDismissReason.TIMEOUT))
            }
            assertThat(viewModel.uiState.value.settings.openInAppDismissCounts).isEmpty()
        }

    @Test
    fun excludeAction_stopsPassivePill_butAUserTapStillAsks_andPersists() =
        runTest(testDispatcher) {
            val tabId = activeTab()
            swipeAway("reddit.com", 3)
            viewModel.onEvent(BrowserUiEvent.OnExternalAppOffered(tabId, prompt(tabId), false))
            viewModel.onEvent(ExcludeOpenInAppSite("reddit.com"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.openInAppExclusionPrompt).isNull()
            assertThat(viewModel.uiState.value.settings.openInAppDismissCounts).isEmpty()
            assertThat(settingsRepo.getCurrentSettings().openInAppExcludedSites).containsExactly("reddit.com")

            viewModel.onEvent(BrowserUiEvent.OnExternalAppOffered(tabId, prompt(tabId, "www.reddit.com"), false))
            assertThat(viewModel.uiState.value.externalAppPrompt).isNull()
            assertThat(viewModel.uiState.value.openInAppExclusionPrompt).isNull()

            viewModel.onEvent(BrowserUiEvent.OnExternalAppOffered(tabId, prompt(tabId, confirm = true), true))
            assertThat(viewModel.uiState.value.externalAppPrompt?.requiresConfirmation).isTrue()
        }

    @Test
    fun dismissingTheQuestion_startsTheCountOver_andRemovingASiteRestoresOffers() =
        runTest(testDispatcher) {
            val tabId = activeTab()
            swipeAway("reddit.com", 3)
            viewModel.onEvent(BrowserUiEvent.OnExternalAppOffered(tabId, prompt(tabId), false))
            viewModel.onEvent(DismissOpenInAppExclusionPrompt("reddit.com"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.openInAppExclusionPrompt).isNull()
            assertThat(settingsRepo.getCurrentSettings().openInAppDismissCounts).isEmpty()

            viewModel.onEvent(ExcludeOpenInAppSite("reddit.com"))
            viewModel.onEvent(IncludeOpenInAppSite("reddit.com"))
            advanceUntilIdle()
            assertThat(settingsRepo.getCurrentSettings().openInAppExcludedSites).isEmpty()
            viewModel.onEvent(BrowserUiEvent.OnExternalAppOffered(tabId, prompt(tabId), false))
            assertThat(viewModel.uiState.value.externalAppPrompt).isNotNull()
        }

    @Test
    fun policy_matchesAliasesAndIgnoresExcludedSites() {
        var settings = BrowserSettings()
        settings = OpenInAppExclusions.recordDismissal(settings, "www.Reddit.com")
        settings = OpenInAppExclusions.recordDismissal(settings, "reddit.com")
        assertThat(settings.openInAppDismissCounts).containsExactly("reddit.com", 2)
        settings = OpenInAppExclusions.exclude(settings, "reddit.com")
        assertThat(OpenInAppExclusions.recordDismissal(settings, "reddit.com")).isEqualTo(settings)
        assertThat(OpenInAppExclusions.isExcluded(settings, "www.reddit.com")).isTrue()
    }

    @Test
    fun codec_roundTripsSitesAndCounts() {
        val sites = setOf("a.com", "b.org")
        assertThat(parseSiteSet(serializeSiteSet(sites))).isEqualTo(sites)
        val counts = mapOf("a.com" to 2, "b.org" to 1)
        assertThat(parseSiteCounts(serializeSiteCounts(counts))).isEqualTo(counts)
        assertThat(parseSiteCounts("bad,x=0,=3,y=z")).isEmpty()
        assertThat(parseSiteSet(null)).isEmpty()
    }
}
