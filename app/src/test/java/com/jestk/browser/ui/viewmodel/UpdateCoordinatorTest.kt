package com.jestk.browser.ui.viewmodel

import android.content.Context
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UpdateCoordinatorTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private var uiState = BrowserUiState()
    private lateinit var fakeSettingsRepo: FakeSettingsRepository
    private lateinit var fakeUpdateManager: FakeAppUpdateManager

    private var currentTime = 1_000_000L

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeSettingsRepo = FakeSettingsRepository()
        fakeUpdateManager = FakeAppUpdateManager(ApplicationProvider.getApplicationContext())
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
        )
    }

    @Test
    fun defaultRepo_pointsToProsto() {
        assertThat(AppUpdateManager.DEFAULT_REPO).isEqualTo("Supscott/Prosto")
    }

    @Test
    fun onAppStart_whenNewerReleaseAvailableAndAutoUpdateOff_publishesAvailable() = runTest(testDispatcher) {
        fakeUpdateManager.currentVer = "0.4.0"
        fakeUpdateManager.latestRelease = AppUpdate(
            versionName = "0.5.0",
            releaseNotes = "New features",
            apkUrl = "https://example.com/0.5.0.apk",
            releaseUrl = "https://github.com/Supscott/Prosto/releases/tag/v0.5.0",
        )
        fakeSettingsRepo.settings.value = BrowserSettings(isAutoUpdateEnabled = false)

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
            apkUrl = "https://example.com/0.5.0.apk",
            releaseUrl = "https://github.com/Supscott/Prosto/releases/tag/v0.5.0",
        )

        val coordinator = createCoordinator()
        coordinator.onAppStart()
        advanceUntilIdle()

        assertThat(uiState.updateState).isEqualTo(AppUpdateUiState.None)
    }

    @Test
    fun onAppResume_throttlesChecksWithinInterval() = runTest(testDispatcher) {
        fakeUpdateManager.currentVer = "0.4.0"
        fakeUpdateManager.latestRelease = AppUpdate(
            versionName = "0.5.0",
            releaseNotes = "Notes",
            apkUrl = "https://example.com/0.5.0.apk",
            releaseUrl = "https://example.com",
        )

        val throttleMs = 3600_000L // 1 hour
        val coordinator = createCoordinator(throttleMs = throttleMs)

        coordinator.onAppStart()
        advanceUntilIdle()
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(1)

        // Resume after only 10 minutes -> should be throttled, no new fetch
        currentTime += 600_000L
        coordinator.onAppResume()
        advanceUntilIdle()
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(1)

        // Resume after full hour (50 more minutes) -> should trigger fetch
        currentTime += 3000_000L
        coordinator.onAppResume()
        advanceUntilIdle()
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(2)
    }

    @Test
    fun onDownloadRequested_startsDownloadAndTransitionsToReadyToInstall() = runTest(testDispatcher) {
        val update = AppUpdate(
            versionName = "0.5.0",
            releaseNotes = "Notes",
            apkUrl = "https://example.com/0.5.0.apk",
            releaseUrl = "https://example.com",
        )
        fakeUpdateManager.downloadResult = File("/tmp/test.apk")
        val coordinator = createCoordinator()

        uiState = uiState.copy(updateState = AppUpdateUiState.Available(update))

        coordinator.onDownloadRequested()
        advanceUntilIdle()

        assertThat(fakeUpdateManager.downloadedUpdate).isEqualTo(update)
        assertThat(uiState.updateState).isInstanceOf(AppUpdateUiState.ReadyToInstall::class.java)
        assertThat((uiState.updateState as AppUpdateUiState.ReadyToInstall).versionName).isEqualTo("0.5.0")
    }

    @Test
    fun onSkip_persistsSkippedVersionAndClearsUpdateState() = runTest(testDispatcher) {
        val update = AppUpdate(
            versionName = "0.5.0",
            releaseNotes = "Notes",
            apkUrl = "https://example.com/0.5.0.apk",
            releaseUrl = "https://example.com",
        )
        val coordinator = createCoordinator()
        uiState = uiState.copy(updateState = AppUpdateUiState.Available(update))

        coordinator.onSkip()
        advanceUntilIdle()

        assertThat(uiState.updateState).isEqualTo(AppUpdateUiState.None)
        assertThat(fakeSettingsRepo.settings.value.skippedUpdateVersion).isEqualTo("0.5.0")
    }

    @Test
    fun onAppStart_whenAutoUpdateEnabled_triggersDownloadAutomatically() = runTest(testDispatcher) {
        val update = AppUpdate(
            versionName = "0.5.0",
            releaseNotes = "Notes",
            apkUrl = "https://example.com/0.5.0.apk",
            releaseUrl = "https://example.com",
        )
        fakeUpdateManager.currentVer = "0.4.0"
        fakeUpdateManager.latestRelease = update
        fakeUpdateManager.downloadResult = File("/tmp/prosto.apk")
        fakeSettingsRepo.settings.value = BrowserSettings(isAutoUpdateEnabled = true)

        val coordinator = createCoordinator()
        coordinator.onAppStart()
        advanceUntilIdle()

        assertThat(fakeUpdateManager.downloadedUpdate).isEqualTo(update)
        assertThat(uiState.updateState).isInstanceOf(AppUpdateUiState.ReadyToInstall::class.java)
        assertThat((uiState.updateState as AppUpdateUiState.ReadyToInstall).versionName).isEqualTo("0.5.0")
    }

    @Test
    fun onInstallRequested_whenReadyAndPermissionMissing_requestsPermission() = runTest(testDispatcher) {
        fakeUpdateManager.canInstall = false
        val coordinator = createCoordinator()
        uiState = uiState.copy(updateState = AppUpdateUiState.ReadyToInstall("0.5.0"))

        coordinator.onInstallRequested()
        advanceUntilIdle()

        assertThat(fakeUpdateManager.requestedPermission).isTrue()
        assertThat(fakeUpdateManager.installedVersion).isNull()
    }

    @Test
    fun onInstallRequested_whenReadyAndPermissionGranted_installsUpdate() = runTest(testDispatcher) {
        fakeUpdateManager.canInstall = true
        val coordinator = createCoordinator()
        uiState = uiState.copy(updateState = AppUpdateUiState.ReadyToInstall("0.5.0"))

        coordinator.onInstallRequested()
        advanceUntilIdle()

        assertThat(fakeUpdateManager.requestedPermission).isFalse()
        assertThat(fakeUpdateManager.installedVersion).isEqualTo("0.5.0")
    }

    @Test
    fun onAutoUpdateEnabled_triggersImmediateCheckAndDownload() = runTest(testDispatcher) {
        val update = AppUpdate(
            versionName = "0.5.0",
            releaseNotes = "Notes",
            apkUrl = "https://example.com/0.5.0.apk",
            releaseUrl = "https://example.com",
        )
        fakeUpdateManager.currentVer = "0.4.0"
        fakeUpdateManager.latestRelease = update
        fakeUpdateManager.downloadResult = File("/tmp/prosto.apk")
        fakeSettingsRepo.settings.value = BrowserSettings(isAutoUpdateEnabled = true)

        val coordinator = createCoordinator()
        coordinator.onAutoUpdateEnabled()
        advanceUntilIdle()

        assertThat(fakeUpdateManager.fetchCount).isEqualTo(1)
        assertThat(fakeUpdateManager.downloadedUpdate).isEqualTo(update)
        assertThat(uiState.updateState).isInstanceOf(AppUpdateUiState.ReadyToInstall::class.java)
    }

    @Test
    fun checkForUpdate_whenPendingUpdateOnDisk_publishesReadyToInstallIfNotInstalled() = runTest(testDispatcher) {
        fakeUpdateManager.currentVer = "0.4.0"
        fakeUpdateManager.pendingVersion = "0.5.0"
        fakeSettingsRepo.settings.value = BrowserSettings(isAutoUpdateEnabled = true)

        val coordinator = createCoordinator()
        coordinator.onAppStart()
        advanceUntilIdle()

        // Should not fetch from network, should use pending update
        assertThat(fakeUpdateManager.fetchCount).isEqualTo(0)
        assertThat(uiState.updateState).isInstanceOf(AppUpdateUiState.ReadyToInstall::class.java)
        assertThat((uiState.updateState as AppUpdateUiState.ReadyToInstall).versionName).isEqualTo("0.5.0")
    }

    @Test
    fun onAutoUpdateDisabled_cancelsActiveDownload() = runTest(testDispatcher) {
        val coordinator = createCoordinator()
        uiState = uiState.copy(updateState = AppUpdateUiState.Downloading("0.5.0", 25))

        coordinator.onAutoUpdateDisabled()
        advanceUntilIdle()

        assertThat(uiState.updateState).isEqualTo(AppUpdateUiState.None)
    }

    private class FakeAppUpdateManager(context: Context) : AppUpdateManager(context) {
        var currentVer: String = "0.4.0"
        var latestRelease: AppUpdate? = null
        var fetchCount = 0
        var downloadedUpdate: AppUpdate? = null
        var downloadResult: File? = null
        var canInstall: Boolean = true
        var requestedPermission: Boolean = false
        var installedVersion: String? = null
        var pendingVersion: String? = null

        override fun currentVersion(): String = currentVer

        override fun canInstallPackages(): Boolean = canInstall

        override fun requestInstallPermission(): Boolean {
            requestedPermission = true
            return true
        }

        override fun installUpdate(versionName: String): Boolean {
            installedVersion = versionName
            return true
        }

        override fun pendingUpdateVersion(currentVersion: String): String? = pendingVersion

        override suspend fun fetchLatestRelease(repo: String): AppUpdate? {
            fetchCount++
            return latestRelease
        }

        override suspend fun download(update: AppUpdate, onProgress: (Int) -> Unit): File? {
            downloadedUpdate = update
            onProgress(50)
            onProgress(100)
            return downloadResult
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
