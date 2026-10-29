package com.jestk.browser.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.jestk.browser.domain.model.AppUpdate
import com.jestk.browser.domain.model.AppUpdateUiState
import com.jestk.browser.domain.model.compareVersions
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.engine.update.AppUpdateManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "UpdateCoordinator"
private const val DEFAULT_THROTTLE_MS = 60 * 60 * 1000L // 1 hour

/**
 * Coordinates update discovery using the Notify & Redirect pattern:
 * - Checks GitHub Releases API in the background (cold start, throttled resume, or on demand).
 * - Notifies the user via [AppUpdateUiState.Available] when a newer version exists.
 * - Redirects the user to the official release download URL via external Intent on request.
 * - Does not perform in-app APK downloads or package installation.
 */
class UpdateCoordinator(
    private val appUpdateManager: AppUpdateManager?,
    private val settingsRepository: SettingsRepository,
    private val getCurrentState: () -> BrowserUiState,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = { System.currentTimeMillis() },
    val checkThrottleMs: Long = DEFAULT_THROTTLE_MS,
    var onLaunchIntent: (Intent) -> Unit = {},
) {
    private var lastCheckTimestamp: Long = 0L

    fun onAppStart() {
        Log.d(TAG, "onAppStart: scheduling update check")
        lastCheckTimestamp = clock()
        scope.launch(ioDispatcher) { checkForUpdate(manual = false) }
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
        scope.launch(ioDispatcher) { checkForUpdate(manual = false) }
    }

    fun onAutoUpdateEnabled() {
        Log.d(TAG, "onAutoUpdateEnabled: immediate check")
        lastCheckTimestamp = clock()
        scope.launch(ioDispatcher) { checkForUpdate(manual = false) }
    }

    fun onAutoUpdateDisabled() {
        Log.d(TAG, "onAutoUpdateDisabled: clearing update notification")
        publish(AppUpdateUiState.None)
    }

    suspend fun checkForUpdate(manual: Boolean = false) {
        val manager = appUpdateManager ?: return
        val settings = settingsRepository.getCurrentSettings()
        if (!manual && !settings.isAutoUpdateEnabled) {
            Log.d(TAG, "checkForUpdate: auto-update check disabled in settings")
            return
        }

        val currentVersion = manager.currentVersion()
        Log.d(TAG, "checkForUpdate: currentVer=$currentVersion, manual=$manual")

        val release = manager.fetchLatestRelease()
        if (release != null && compareVersions(release.versionName, currentVersion) > 0) {
            if (!manual && release.versionName == settings.skippedUpdateVersion) {
                Log.d(TAG, "Version ${release.versionName} was skipped by user")
                return
            }
            Log.d(TAG, "Publishing update available for ${release.versionName}")
            publish(AppUpdateUiState.Available(release))
            return
        }

        if (release != null) {
            Log.d(TAG, "Current version is up to date ($currentVersion >= ${release.versionName})")
        }
    }

    fun onRedirectRequested() {
        val state = getCurrentState().updateState
        if (state is AppUpdateUiState.Available) {
            val targetUrl = state.update.releaseUrl.ifBlank { state.update.apkUrl }
            if (targetUrl.isNotBlank()) {
                Log.d(TAG, "onRedirectRequested: launching browser for $targetUrl")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { onLaunchIntent(intent) }.onFailure { e ->
                    Log.e(TAG, "Failed to launch release url intent", e)
                }
            }
            publish(AppUpdateUiState.None)
        }
    }

    fun onSkip() {
        val version =
            when (val state = getCurrentState().updateState) {
                is AppUpdateUiState.Available -> state.update.versionName
                AppUpdateUiState.None -> null
            } ?: return
        scope.launch(ioDispatcher) { settingsRepository.setSkippedUpdateVersion(version) }
        publish(AppUpdateUiState.None)
    }

    fun onDismiss() {
        Log.d(TAG, "onDismiss: dismissing update banner for current session")
        publish(AppUpdateUiState.None)
    }

    private fun publish(state: AppUpdateUiState) {
        updateUiState { it.copy(updateState = state) }
    }
}
