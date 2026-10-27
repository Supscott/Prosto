package com.jestk.browser.ui.viewmodel

import android.util.Log
import com.jestk.browser.domain.model.AppUpdate
import com.jestk.browser.domain.model.AppUpdateUiState
import com.jestk.browser.domain.model.compareVersions
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.engine.update.AppUpdateManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "UpdateCoordinator"
private const val DEFAULT_THROTTLE_MS = 60 * 60 * 1000L // 1 hour

/**
 * Owns the self-update flow: detect a GitHub release, download it and hand it to
 * the system installer. The app is never killed while the user browses - an update
 * that was downloaded in the background is installed on the next cold start.
 */
class UpdateCoordinator(
    private val appUpdateManager: AppUpdateManager?,
    private val settingsRepository: SettingsRepository,
    private val getCurrentState: () -> BrowserUiState,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val checkThrottleMs: Long = DEFAULT_THROTTLE_MS,
) {
    private var lastCheckTimestamp: Long = 0L
    private var downloadJob: Job? = null

    fun onAppStart() {
        Log.d(TAG, "onAppStart: scheduling update check")
        lastCheckTimestamp = clock()
        scope.launch(ioDispatcher) { checkForUpdate() }
    }

    fun onAppResume() {
        val now = clock()
        val elapsed = now - lastCheckTimestamp
        if (elapsed in 0 until checkThrottleMs) {
            Log.d(TAG, "onAppResume: throttled (elapsed ${elapsed / 1000}s < ${checkThrottleMs / 1000}s)")
            return
        }
        Log.d(TAG, "onAppResume: checking for updates after ${elapsed / 1000}s")
        lastCheckTimestamp = now
        scope.launch(ioDispatcher) { checkForUpdate() }
    }

    fun onAutoUpdateEnabled() {
        Log.d(TAG, "onAutoUpdateEnabled: immediate check")
        lastCheckTimestamp = clock()
        scope.launch(ioDispatcher) { checkForUpdate() }
    }

    fun onAutoUpdateDisabled() {
        Log.d(TAG, "onAutoUpdateDisabled: cancelling pending downloads")
        downloadJob?.cancel()
        downloadJob = null
        if (getCurrentState().updateState is AppUpdateUiState.Downloading) {
            publish(AppUpdateUiState.None)
        }
    }

    suspend fun checkForUpdate() {
        val manager = appUpdateManager ?: return
        val settings = settingsRepository.getCurrentSettings()
        val currentVersion = manager.currentVersion()
        Log.d(TAG, "checkForUpdate: currentVer=$currentVersion, autoUpdate=${settings.isAutoUpdateEnabled}")

        val pending = manager.pendingUpdateVersion()
        val release = manager.fetchLatestRelease()

        if (release != null && compareVersions(release.versionName, currentVersion) > 0) {
            if (pending != null && compareVersions(pending, release.versionName) >= 0) {
                Log.d(TAG, "Pending update APK $pending matches latest release ${release.versionName}")
                publish(AppUpdateUiState.ReadyToInstall(pending))
                return
            }
            if (release.versionName == settings.skippedUpdateVersion) {
                Log.d(TAG, "Version ${release.versionName} was skipped by user")
            } else {
                if (settings.isAutoUpdateEnabled) {
                    Log.d(TAG, "Auto-update enabled, initiating download for ${release.versionName}")
                    download(release)
                } else {
                    Log.d(TAG, "Publishing update available for ${release.versionName}")
                    publish(AppUpdateUiState.Available(release))
                }
                return
            }
        }

        if (pending != null) {
            Log.d(TAG, "Pending update APK found on disk: $pending")
            publish(AppUpdateUiState.ReadyToInstall(pending))
            return
        }

        if (release != null) {
            Log.d(TAG, "Current version is up to date ($currentVersion >= ${release.versionName})")
        }
    }

    fun onDownloadRequested() {
        val state = getCurrentState().updateState
        if (state is AppUpdateUiState.Available) {
            Log.d(TAG, "onDownloadRequested: starting download for ${state.update.versionName}")
            download(state.update)
        }
    }

    fun onInstallRequested() {
        val manager = appUpdateManager ?: return
        when (val state = getCurrentState().updateState) {
            is AppUpdateUiState.Available -> {
                onDownloadRequested()
            }

            is AppUpdateUiState.ReadyToInstall -> {
                if (!manager.canInstallPackages()) {
                    Log.d(TAG, "onInstallRequested: requesting install unknown apps permission")
                    manager.requestInstallPermission()
                    return
                }
                Log.d(TAG, "onInstallRequested: launching installer for ${state.versionName}")
                manager.installUpdate(state.versionName)
            }

            else -> Unit
        }
    }

    fun onSkip() {
        downloadJob?.cancel()
        downloadJob = null
        val version =
            when (val state = getCurrentState().updateState) {
                is AppUpdateUiState.Available -> state.update.versionName
                is AppUpdateUiState.Downloading -> state.versionName
                is AppUpdateUiState.ReadyToInstall -> state.versionName
                AppUpdateUiState.None -> null
            } ?: return
        appUpdateManager?.deletePendingUpdate(version)
        scope.launch(ioDispatcher) { settingsRepository.setSkippedUpdateVersion(version) }
        publish(AppUpdateUiState.None)
    }

    private fun download(update: AppUpdate) {
        val manager = appUpdateManager ?: return
        publish(AppUpdateUiState.Downloading(update.versionName, 0))
        downloadJob?.cancel()
        downloadJob =
            scope.launch(ioDispatcher) {
                var lastProgress = -1
                val file =
                    manager.download(update) { progress ->
                        if (progress != lastProgress) {
                            lastProgress = progress
                            publish(AppUpdateUiState.Downloading(update.versionName, progress))
                        }
                    }
                if (file == null) {
                    Log.w(TAG, "Download failed for ${update.versionName}")
                    publish(AppUpdateUiState.Available(update))
                    return@launch
                }
                publish(AppUpdateUiState.ReadyToInstall(update.versionName))
                manager.notifyUpdateReady(update.versionName)
            }
    }

    private fun publish(state: AppUpdateUiState) {
        updateUiState { it.copy(updateState = state) }
    }
}
