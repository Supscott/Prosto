package com.jestk.browser.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.AppUpdate
import com.jestk.browser.domain.model.AppUpdateUiState
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.engine.update.AppUpdateManager
import com.jestk.browser.engine.update.UpdateFetchResult
import com.jestk.browser.util.UpdateCheckStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UpdateCoordinatorTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private var uiState = BrowserUiState()
    private lateinit var fakeSettingsRepo: FakeSettingsRepository
    private lateinit var fakeUpdateManager: FakeAppUpdateManager
    private lateinit var store: InMemoryUpdateCheckStore
    private var launchedIntent: Intent? = null

    private var currentTime = 100 * 3600_000L

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeSettingsRepo = FakeSettingsRepository()
        store = InMemoryUpdateCheckStore()
        fakeUpdateManager = FakeAppUpdateManager(ApplicationProvider.getApplicationContext(), store)
        launchedIntent = null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createCoordinator(): UpdateCoordinator {
        return UpdateCoordinator(
            appUpdateManager = fakeUpdateManager,
            settingsRepository = fakeSettingsRepo,
            getCurrentState = { uiState },
            updateUiState = { transform -> uiState = transform(uiState) },
            scope = testScope,
            ioDispatcher = testDispatcher,
            clock = { currentTime },
            onLaunchIntent = { intent -> launchedIntent = intent },
        )
    }

    @Test
    fun defaultRepo_pointsToProsto() {
        assertThat(AppUpdateManager.DEFAULT_REPO).isEqualTo("Supscott/Prosto")
    }

    @Test
    fun onAppStart_whenNewerReleaseAvailable_publishesAvailable() = runTest(testDispatcher) {
        fakeUpdateManager.currentVer = "0.4.0"
        fakeUpdateManager.latestRelease = AppUpdate(
            versionName = "0.5.0",
            releaseUrl = "https://github.com/Supscott/Prosto/releases/tag/v0.5.0",
        )
        fakeSettingsRepo.settings.value = BrowserSettings(isAutoUpdateEnabled = true)

        val coordinator = createCoordinator()
        coordinator.onAppStart()
        advanceUntilIdle()

        assertThat(uiState.updateState).isInstanceOf(AppUpdateUiState.Available::class.java)
        val available = uiState.updateState as AppUpdateUiState.Available
        assertThat(available.update.versionName).isEqualTo("0.5.0")
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(1)
    }

    @Test
    fun coldStart_onAppStartAndOnAppResumeTogether_sendOneRequest() = runTest(testDispatcher) {
        fakeUpdateManager.latestRelease = AppUpdate("0.5.0", "https://github.com/Supscott/Prosto/releases/tag/v0.5.0")
        fakeUpdateManager.fetchDelayMs = 500
        fakeSettingsRepo.settings.value = BrowserSettings(isAutoUpdateEnabled = true)

        val coordinator = createCoordinator()
        coordinator.onAppStart()
        coordinator.onAppResume()
        advanceUntilIdle()

        assertThat(fakeUpdateManager.fetchCount).isEqualTo(1)
    }

    @Test
    fun cachedRelease_tagNotNewer_skipsTheApi() = runTest(testDispatcher) {
        fakeUpdateManager.currentVer = "0.4.0"
        store.cachedRelease = AppUpdate("0.5.0", "https://github.com/Supscott/Prosto/releases/tag/v0.5.0")
        fakeUpdateManager.latestTag = "0.5.0"
        fakeSettingsRepo.settings.value = BrowserSettings(isAutoUpdateEnabled = true)

        createCoordinator().checkForUpdate(manual = false)

        assertThat(fakeUpdateManager.tagProbeCount).isEqualTo(1)
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(0)
        assertThat(store.lastSuccessAt).isEqualTo(currentTime)
        assertThat((uiState.updateState as AppUpdateUiState.Available).update.versionName).isEqualTo("0.5.0")
    }

    @Test
    fun cachedRelease_newerTag_asksTheApiForTheRelease() = runTest(testDispatcher) {
        fakeUpdateManager.currentVer = "0.4.0"
        store.cachedRelease = AppUpdate("0.5.0", "u")
        fakeUpdateManager.latestTag = "0.6.0"
        fakeUpdateManager.latestRelease = AppUpdate("0.6.0", "https://github.com/Supscott/Prosto/releases/tag/v0.6.0")
        fakeSettingsRepo.settings.value = BrowserSettings(isAutoUpdateEnabled = true)

        createCoordinator().checkForUpdate(manual = false)

        assertThat(fakeUpdateManager.fetchCount).isEqualTo(1)
        assertThat(store.cachedRelease?.versionName).isEqualTo("0.6.0")
    }

    @Test
    fun onAppStart_whenInstalledVersionIsEqualOrNewer_staysNone() = runTest(testDispatcher) {
        fakeUpdateManager.currentVer = "0.5.0"
        fakeUpdateManager.latestRelease = AppUpdate(
            versionName = "0.5.0",
            releaseUrl = "https://github.com/Supscott/Prosto/releases/tag/v0.5.0",
        )

        val coordinator = createCoordinator()
        coordinator.onAppStart()
        advanceUntilIdle()

        assertThat(uiState.updateState).isEqualTo(AppUpdateUiState.None)
    }

    @Test
    fun onAppStart_whenAutoUpdateDisabled_doesNotCheck() = runTest(testDispatcher) {
        fakeUpdateManager.currentVer = "0.4.0"
        fakeUpdateManager.latestRelease = AppUpdate(
            versionName = "0.5.0",
            releaseUrl = "https://github.com/Supscott/Prosto/releases/tag/v0.5.0",
        )
        fakeSettingsRepo.settings.value = BrowserSettings(isAutoUpdateEnabled = false)

        val coordinator = createCoordinator()
        coordinator.onAppStart()
        advanceUntilIdle()

        assertThat(uiState.updateState).isEqualTo(AppUpdateUiState.None)
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(0)
    }

    private val release05 = AppUpdate("0.5.0", "https://example.com")
    private val hour = 3600_000L

    @Test
    fun onAppResume_throttlesChecksWithinSixHours() = runTest(testDispatcher) {
        fakeUpdateManager.latestRelease = release05
        val coordinator = createCoordinator()

        coordinator.onAppStart()
        advanceUntilIdle()
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(1)

        currentTime += 5 * hour
        coordinator.onAppResume()
        advanceUntilIdle()
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(1)

        currentTime += 2 * hour
        coordinator.onAppResume()
        advanceUntilIdle()
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(2)
    }

    @Test
    fun coldStart_withinSixHoursOfLastSuccess_makesNoRequest() = runTest(testDispatcher) {
        store.lastSuccessAt = currentTime - hour
        fakeUpdateManager.latestRelease = release05

        createCoordinator().onAppStart()
        advanceUntilIdle()

        assertThat(fakeUpdateManager.fetchCount).isEqualTo(0)
    }

    @Test
    fun coldStart_showsCachedUpdateWithoutRequest() = runTest(testDispatcher) {
        store.lastSuccessAt = currentTime - hour
        store.cachedRelease = release05

        createCoordinator().onAppStart()
        advanceUntilIdle()

        assertThat(uiState.updateState).isEqualTo(AppUpdateUiState.Available(release05))
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(0)
    }

    @Test
    fun coldStart_cachedSkippedVersion_staysHidden() = runTest(testDispatcher) {
        store.lastSuccessAt = currentTime - hour
        store.cachedRelease = release05
        fakeSettingsRepo.settings.value = BrowserSettings(skippedUpdateVersion = "0.5.0")

        createCoordinator().onAppStart()
        advanceUntilIdle()

        assertThat(uiState.updateState).isEqualTo(AppUpdateUiState.None)
    }

    @Test
    fun failure_backsOffFiveThenFifteenMinutes() = runTest(testDispatcher) {
        fakeUpdateManager.resultOverride = UpdateFetchResult.Failed(403, null)
        val coordinator = createCoordinator()

        coordinator.checkForUpdate()
        assertThat(store.failureCount).isEqualTo(1)
        assertThat(store.nextAllowedAt).isEqualTo(currentTime + 5 * 60_000L)
        assertThat(store.lastSuccessAt).isEqualTo(0L)

        currentTime += 4 * 60_000L
        coordinator.checkForUpdate()
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(1)

        currentTime += 2 * 60_000L
        coordinator.checkForUpdate()
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(2)
        assertThat(store.failureCount).isEqualTo(2)
        assertThat(store.nextAllowedAt).isEqualTo(currentTime + 15 * 60_000L)
    }

    @Test
    fun failure_backoffIsCappedAtSixHours() = runTest(testDispatcher) {
        fakeUpdateManager.resultOverride = UpdateFetchResult.Failed(500, null)
        store.failureCount = 10
        createCoordinator().checkForUpdate()
        assertThat(store.nextAllowedAt).isEqualTo(currentTime + 6 * hour)
    }

    @Test
    fun failure_retryAtFromHeadersExtendsBackoff() = runTest(testDispatcher) {
        val resetAt = currentTime + 40 * 60_000L
        fakeUpdateManager.resultOverride = UpdateFetchResult.Failed(403, resetAt)
        createCoordinator().checkForUpdate()
        assertThat(store.nextAllowedAt).isEqualTo(resetAt)
    }

    @Test
    fun success_resetsFailureStateAndStoresEtag() = runTest(testDispatcher) {
        store.failureCount = 3
        store.nextAllowedAt = currentTime - 1
        fakeUpdateManager.latestRelease = release05

        createCoordinator().checkForUpdate()

        assertThat(store.failureCount).isEqualTo(0)
        assertThat(store.nextAllowedAt).isEqualTo(0L)
        assertThat(store.lastSuccessAt).isEqualTo(currentTime)
        assertThat(store.etag).isEqualTo("etag-0.5.0")
        assertThat(store.cachedRelease).isEqualTo(release05)
    }

    @Test
    fun notModified_usesCachedReleaseAndCountsAsSuccess() = runTest(testDispatcher) {
        store.cachedRelease = release05
        store.etag = "abc"
        store.failureCount = 1
        fakeUpdateManager.resultOverride = UpdateFetchResult.NotModified

        createCoordinator().checkForUpdate()

        assertThat(fakeUpdateManager.lastEtag).isEqualTo("abc")
        assertThat(store.lastSuccessAt).isEqualTo(currentTime)
        assertThat(store.failureCount).isEqualTo(0)
        assertThat(uiState.updateState).isEqualTo(AppUpdateUiState.Available(release05))
    }

    @Test
    fun autoUpdateEnabled_bypassesIntervalButNotBackoff() = runTest(testDispatcher) {
        fakeUpdateManager.latestRelease = release05
        store.lastSuccessAt = currentTime - hour
        val coordinator = createCoordinator()

        coordinator.onAutoUpdateEnabled()
        advanceUntilIdle()
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(1)

        store.nextAllowedAt = currentTime + 60_000L
        coordinator.onAutoUpdateEnabled()
        coordinator.checkForUpdate(manual = true)
        advanceUntilIdle()
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(1)
    }

    @Test
    fun onRedirectRequested_launchesViewIntentAndClearsState() = runTest(testDispatcher) {
        val update = AppUpdate(
            versionName = "0.5.0",
            releaseUrl = "https://github.com/Supscott/Prosto/releases/tag/v0.5.0",
        )
        uiState = uiState.copy(updateState = AppUpdateUiState.Available(update))

        val coordinator = createCoordinator()
        coordinator.onRedirectRequested()

        assertThat(launchedIntent).isNotNull()
        assertThat(launchedIntent?.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(launchedIntent?.data).isEqualTo(Uri.parse("https://github.com/Supscott/Prosto/releases/tag/v0.5.0"))
        assertThat(launchedIntent?.flags?.and(Intent.FLAG_ACTIVITY_NEW_TASK)).isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertThat(uiState.updateState).isEqualTo(AppUpdateUiState.None)
    }

    @Test
    fun onSkip_persistsSkippedVersionAndClearsState() = runTest(testDispatcher) {
        val update = AppUpdate(
            versionName = "0.5.0",
            releaseUrl = "https://example.com",
        )
        uiState = uiState.copy(updateState = AppUpdateUiState.Available(update))

        val coordinator = createCoordinator()
        coordinator.onSkip()
        advanceUntilIdle()

        assertThat(fakeSettingsRepo.settings.value.skippedUpdateVersion).isEqualTo("0.5.0")
        assertThat(uiState.updateState).isEqualTo(AppUpdateUiState.None)
    }

    @Test
    fun onDismiss_clearsStateWithoutPersistingSkip() = runTest(testDispatcher) {
        val update = AppUpdate(
            versionName = "0.5.0",
            releaseUrl = "https://example.com",
        )
        uiState = uiState.copy(updateState = AppUpdateUiState.Available(update))

        val coordinator = createCoordinator()
        coordinator.onDismiss()

        assertThat(fakeSettingsRepo.settings.value.skippedUpdateVersion).isNull()
        assertThat(uiState.updateState).isEqualTo(AppUpdateUiState.None)
    }

    @Test
    fun checkForUpdate_whenSkipped_doesNotPublishUnlessManual() = runTest(testDispatcher) {
        fakeUpdateManager.currentVer = "0.4.0"
        fakeUpdateManager.latestRelease = AppUpdate(
            versionName = "0.5.0",
            releaseUrl = "https://example.com",
        )
        fakeSettingsRepo.settings.value = BrowserSettings(
            isAutoUpdateEnabled = true,
            skippedUpdateVersion = "0.5.0",
        )

        val coordinator = createCoordinator()
        coordinator.onAppStart()
        advanceUntilIdle()

        assertThat(uiState.updateState).isEqualTo(AppUpdateUiState.None)

        // Manual check ignores skipped version
        coordinator.checkForUpdate(manual = true)
        advanceUntilIdle()
        assertThat(uiState.updateState).isInstanceOf(AppUpdateUiState.Available::class.java)
    }

    @Test
    fun onAutoUpdateDisabled_clearsUpdateState() = runTest(testDispatcher) {
        val update = AppUpdate(
            versionName = "0.5.0",
            releaseUrl = "https://example.com",
        )
        uiState = uiState.copy(updateState = AppUpdateUiState.Available(update))

        val coordinator = createCoordinator()
        coordinator.onAutoUpdateDisabled()

        assertThat(uiState.updateState).isEqualTo(AppUpdateUiState.None)
    }

    private class FakeAppUpdateManager(context: Context, store: UpdateCheckStore) :
        AppUpdateManager(context, store) {
        var currentVer: String = "0.4.0"
        var latestRelease: AppUpdate? = null
        var resultOverride: UpdateFetchResult? = null
        var fetchCount = 0
        var lastEtag: String? = null

        /** Simulates network latency, so concurrent checks overlap as they do on a device. */
        var fetchDelayMs = 0L
        var latestTag: String? = null
        var tagProbeCount = 0

        override suspend fun fetchLatestTagVersion(repo: String): String? {
            tagProbeCount++
            return latestTag
        }

        override fun currentVersion(): String = currentVer

        override suspend fun fetchLatestRelease(repo: String, etag: String?): UpdateFetchResult {
            fetchCount++
            lastEtag = etag
            if (fetchDelayMs > 0) kotlinx.coroutines.delay(fetchDelayMs)
            return resultOverride
                ?: latestRelease?.let { UpdateFetchResult.Fresh(it, "etag-${it.versionName}") }
                ?: UpdateFetchResult.Failed(0, null)
        }
    }

    private class InMemoryUpdateCheckStore : UpdateCheckStore {
        override var lastSuccessAt = 0L
        override var nextAllowedAt = 0L
        override var failureCount = 0
        override var etag: String? = null
        override var cachedRelease: AppUpdate? = null
    }

    private class FakeSettingsRepository : SettingsRepository {
        val settings = MutableStateFlow(BrowserSettings())

        override fun getSettings(): Flow<BrowserSettings> = settings
        override suspend fun getCurrentSettings(): BrowserSettings = settings.value
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setSearchEngine(engine: SearchEngine) {}
        override suspend fun setAdBlockerEnabled(enabled: Boolean) {}
        override suspend fun setAdRedirectProtectionEnabled(enabled: Boolean) {
            settings.value = settings.value.copy(isAdRedirectProtectionEnabled = enabled)
        }
        override suspend fun setAutoConsentEnabled(enabled: Boolean) {
            settings.value = settings.value.copy(autoConsentEnabled = enabled)
        }
        override suspend fun setTrackingProtectionEnabled(enabled: Boolean) {}
        override suspend fun setHiddenSearchPageEngineIds(hiddenIds: Set<String>) {}
        override suspend fun setOpenInAppsEnabled(enabled: Boolean) {}
        override suspend fun setAutoUpdateEnabled(enabled: Boolean) {
            settings.value = settings.value.copy(isAutoUpdateEnabled = enabled)
        }
        override suspend fun setVibrationEnabled(enabled: Boolean) {}
        override suspend fun setGrayUnloadedTabsEnabled(enabled: Boolean) {}
        override suspend fun setSkippedUpdateVersion(version: String?) {
            settings.value = settings.value.copy(skippedUpdateVersion = version)
        }
        override suspend fun setFeedbackPromptState(state: com.jestk.browser.domain.model.FeedbackPromptState) {
            settings.value = settings.value.copy(feedbackPromptState = state)
        }
        override suspend fun snoozeFeedbackPrompt(snoozedUntil: Long) {
            settings.value = settings.value.copy(
                feedbackPromptState = com.jestk.browser.domain.model.FeedbackPromptState.SNOOZED,
                feedbackPromptSnoozedUntil = snoozedUntil,
            )
        }
        override fun getBlockedAdsCount(): Flow<Long> = MutableStateFlow(0L)
        override fun getBlockedTrackersCount(): Flow<Long> = MutableStateFlow(0L)
        override suspend fun incrementBlockedAds(count: Long) {}
        override suspend fun incrementBlockedTrackers(count: Long) {}
    }
}
