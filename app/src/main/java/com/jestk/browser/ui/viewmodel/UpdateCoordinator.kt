package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.AppUpdate
import com.jestk.browser.domain.model.AppUpdateUiState
import com.jestk.browser.domain.model.compareVersions
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.engine.update.AppUpdateManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
) {
    fun onAppStart() {
        scope.launch(ioDispatcher) { checkForUpdate() }
    }

    fun onAutoUpdateEnabled() {
        scope.launch(ioDispatcher) { checkForUpdate() }
    }

    suspend fun checkForUpdate() {
        val manager = appUpdateManager ?: return
        val settings = settingsRepository.getCurrentSettings()

        // An APK downloaded in a previous session is waiting to be installed.
        val pending = manager.pendingUpdateVersion()
        if (pending != null) {
            val installed =
                settings.isAutoUpdateEnabled && manager.canInstallPackages() && manager.installUpdate(pending)
            if (!installed) publish(AppUpdateUiState.ReadyToInstall(pending))
            return
        }

        val release = manager.fetchLatestRelease() ?: return
        if (compareVersions(release.versionName, manager.currentVersion()) <= 0) return
        if (release.versionName == settings.skippedUpdateVersion) return

        if (settings.isAutoUpdateEnabled) {
            download(release, installWhenDone = false)
        } else {
            publish(AppUpdateUiState.Available(release))
        }
    }

    fun onInstallRequested() {
        val manager = appUpdateManager ?: return
        when (val state = getCurrentState().updateState) {
            is AppUpdateUiState.Available -> {
                if (!manager.canInstallPackages()) {
                    manager.requestInstallPermission()
                    return
                }
                download(state.update, installWhenDone = true)
            }

            is AppUpdateUiState.ReadyToInstall -> {
                if (!manager.canInstallPackages()) {
                    manager.requestInstallPermission()
                    return
                }
                manager.installUpdate(state.versionName)
            }

            else -> {
                Unit
            }
        }
    }

    fun onInstallLater() {
        publish(AppUpdateUiState.None)
    }

    fun onSkip() {
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

    private fun download(
        update: AppUpdate,
        installWhenDone: Boolean,
    ) {
        val manager = appUpdateManager ?: return
        publish(AppUpdateUiState.Downloading(update.versionName, 0))
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
                publish(AppUpdateUiState.Available(update))
                return@launch
            }
            publish(AppUpdateUiState.ReadyToInstall(update.versionName))
            manager.notifyUpdateReady(update.versionName)
            if (installWhenDone) manager.installUpdate(update.versionName)
        }
    }

    private fun publish(state: AppUpdateUiState) {
        updateUiState { it.copy(updateState = state) }
    }
}
