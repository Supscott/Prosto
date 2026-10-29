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
    private var launchedIntent: Intent? = null

    private var currentTime = 1_000_000L

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeSettingsRepo = FakeSettingsRepository()
        fakeUpdateManager = FakeAppUpdateManager(ApplicationProvider.getApplicationContext())
        launchedIntent = null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createCoordinator(throttleMs: Long = 60 * 60 * 1000L): UpdateCoordinator {
        return UpdateCoordinator(
            appUpdateManager = fakeUpdateManager,
            settingsRepository = fakeSettingsRepo,
            getCurrentState = { uiState },
            updateUiState = { transform -> uiState = transform(uiState) },
            scope = testScope,
            ioDispatcher = testDispatcher,
            clock = { currentTime },
            checkThrottleMs = throttleMs,
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
            releaseNotes = "New features",
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
    fun onAppStart_whenInstalledVersionIsEqualOrNewer_staysNone() = runTest(testDispatcher) {
        fakeUpdateManager.currentVer = "0.5.0"
        fakeUpdateManager.latestRelease = AppUpdate(
            versionName = "0.5.0",
            releaseNotes = "New features",
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
            releaseNotes = "New features",
            releaseUrl = "https://github.com/Supscott/Prosto/releases/tag/v0.5.0",
        )
        fakeSettingsRepo.settings.value = BrowserSettings(isAutoUpdateEnabled = false)

        val coordinator = createCoordinator()
        coordinator.onAppStart()
        advanceUntilIdle()

        assertThat(uiState.updateState).isEqualTo(AppUpdateUiState.None)
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(0)
    }

    @Test
    fun onAppResume_throttlesChecksWithinInterval() = runTest(testDispatcher) {
        fakeUpdateManager.currentVer = "0.4.0"
        fakeUpdateManager.latestRelease = AppUpdate(
            versionName = "0.5.0",
            releaseNotes = "Notes",
            releaseUrl = "https://example.com",
        )

        val throttleMs = 3600_000L // 1 hour
        val coordinator = createCoordinator(throttleMs = throttleMs)

        coordinator.onAppStart()
        advanceUntilIdle()
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(1)

        // Resume after only 10 minutes -> should be throttled
        currentTime += 600_000L
        coordinator.onAppResume()
        advanceUntilIdle()
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(1)

        // Resume after 65 minutes -> interval exceeded, should trigger check
        currentTime += 3300_000L
        coordinator.onAppResume()
        advanceUntilIdle()
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(2)
    }

    @Test
    fun onRedirectRequested_launchesViewIntentAndClearsState() = runTest(testDispatcher) {
        val update = AppUpdate(
            versionName = "0.5.0",
            releaseNotes = "Notes",
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
            releaseNotes = "Notes",
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
            releaseNotes = "Notes",
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
            releaseNotes = "Notes",
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
            releaseNotes = "Notes",
            releaseUrl = "https://example.com",
        )
        uiState = uiState.copy(updateState = AppUpdateUiState.Available(update))

        val coordinator = createCoordinator()
        coordinator.onAutoUpdateDisabled()

        assertThat(uiState.updateState).isEqualTo(AppUpdateUiState.None)
    }

    private class FakeAppUpdateManager(context: Context) : AppUpdateManager(context) {
        var currentVer: String = "0.4.0"
        var latestRelease: AppUpdate? = null
        var fetchCount = 0

        override fun currentVersion(): String = currentVer

        override suspend fun fetchLatestRelease(repo: String): AppUpdate? {
            fetchCount++
            return latestRelease
        }
    }

    private class FakeSettingsRepository : SettingsRepository {
        val settings = MutableStateFlow(BrowserSettings())

        override fun getSettings(): Flow<BrowserSettings> = settings
        override suspend fun getCurrentSettings(): BrowserSettings = settings.value
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setSearchEngine(engine: SearchEngine) {}
        override suspend fun setAdBlockerEnabled(enabled: Boolean) {}
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
        override fun getBlockedAdsCount(): Flow<Long> = MutableStateFlow(0L)
        override fun getBlockedTrackersCount(): Flow<Long> = MutableStateFlow(0L)
        override suspend fun incrementBlockedAds(count: Long) {}
        override suspend fun incrementBlockedTrackers(count: Long) {}
    }
}
