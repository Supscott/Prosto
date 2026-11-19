package com.jestk.browser.ui.viewmodel

import android.content.Intent
import android.net.Uri
import android.util.Log
import com.jestk.browser.domain.model.AppUpdateUiState
import com.jestk.browser.domain.model.compareVersions
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.engine.update.AppUpdateManager
import com.jestk.browser.engine.update.UpdateFetchResult
import com.jestk.browser.util.UpdateCheckStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "UpdateCoordinator"
private const val MIN_SUCCESS_INTERVAL_MS = 6 * 60 * 60 * 1000L
private val BACKOFF_STEPS_MS = longArrayOf(5 * 60_000L, 15 * 60_000L, 60 * 60_000L, 6 * 60 * 60_000L)

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
    var onLaunchIntent: (Intent) -> Unit = {},
) {
    fun onAppStart() {
        Log.d(TAG, "onAppStart: scheduling update check")
        scope.launch(ioDispatcher) {
            publishCachedRelease()
            checkForUpdate(manual = false)
        }
    }

    fun onAppResume() {
        scope.launch(ioDispatcher) { checkForUpdate(manual = false) }
    }

    fun onAutoUpdateEnabled() {
        Log.d(TAG, "onAutoUpdateEnabled: check bypassing the interval")
        scope.launch(ioDispatcher) { checkForUpdate(manual = false, bypassInterval = true) }
    }

    fun onAutoUpdateDisabled() {
        Log.d(TAG, "onAutoUpdateDisabled: clearing update notification")
        publish(AppUpdateUiState.None)
    }

    /** Shows the release remembered from an earlier check without touching the network. */
    private suspend fun publishCachedRelease() {
        val manager = appUpdateManager ?: return
        val cached = manager.store.cachedRelease ?: return
        val settings = settingsRepository.getCurrentSettings()
        if (!settings.isAutoUpdateEnabled) return
        if (compareVersions(cached.versionName, manager.currentVersion()) <= 0) return
        if (cached.versionName == settings.skippedUpdateVersion) return
        Log.d(TAG, "update check: cached release ${cached.versionName}, no request")
        publish(AppUpdateUiState.Available(cached))
    }

    /**
     * Checks GitHub for a newer release. The failure backoff always applies; the
     * [MIN_SUCCESS_INTERVAL_MS] interval is skipped for [manual] checks and [bypassInterval].
     */
    /**
     * One check at a time: a cold start fires onAppStart and onAppResume together, and both would
     * pass the interval and backoff gates before either stores its result (two requests).
     */
    private val checkMutex = Mutex()

    suspend fun checkForUpdate(manual: Boolean = false, bypassInterval: Boolean = manual) =
        checkMutex.withLock { checkLocked(manual, bypassInterval) }

    private suspend fun checkLocked(manual: Boolean, bypassInterval: Boolean) {
        val manager = appUpdateManager ?: return
        val settings = settingsRepository.getCurrentSettings()
        if (!manual && !settings.isAutoUpdateEnabled) {
            Log.d(TAG, "update check skipped: auto-update disabled in settings")
            return
        }

        val store = manager.store
        val now = clock()
        if (now < store.nextAllowedAt) {
            Log.d(TAG, "update check skipped: backoff until ${store.nextAllowedAt} (failures=${store.failureCount})")
            return
        }
        val sinceSuccess = now - store.lastSuccessAt
        if (!bypassInterval && store.lastSuccessAt > 0 && sinceSuccess < MIN_SUCCESS_INTERVAL_MS) {
            Log.d(TAG, "update check skipped: interval (${sinceSuccess / 1000}s since last success)")
            return
        }

        val release =
            when (val result = manager.fetchLatestRelease(etag = store.etag?.takeIf { store.cachedRelease != null })) {
                is UpdateFetchResult.Fresh -> {
                    Log.d(TAG, "update check: 200, release ${result.release.versionName}")
                    store.etag = result.etag
                    store.cachedRelease = result.release
                    recordSuccess(store, now)
                    result.release
                }
                UpdateFetchResult.NotModified -> {
                    Log.d(TAG, "update check: 304 not modified")
                    recordSuccess(store, now)
                    store.cachedRelease
                }
                is UpdateFetchResult.Failed -> {
                    val failures = store.failureCount + 1
                    val backoffUntil = now + backoffMs(failures)
                    store.failureCount = failures
                    store.nextAllowedAt = maxOf(backoffUntil, result.retryAtMs ?: 0L)
                    Log.w(TAG, "update check failed: HTTP ${result.code}, retry after ${store.nextAllowedAt}")
                    return
                }
            } ?: return

        val currentVersion = manager.currentVersion()
        if (compareVersions(release.versionName, currentVersion) > 0) {
            if (!manual && release.versionName == settings.skippedUpdateVersion) {
                Log.d(TAG, "Version ${release.versionName} was skipped by user")
                return
            }
            Log.d(TAG, "Publishing update available for ${release.versionName}")
            publish(AppUpdateUiState.Available(release))
        } else {
            Log.d(TAG, "Current version is up to date ($currentVersion >= ${release.versionName})")
        }
    }

    private fun recordSuccess(store: UpdateCheckStore, now: Long) {
        store.lastSuccessAt = now
        store.failureCount = 0
        store.nextAllowedAt = 0L
    }

    private fun backoffMs(failures: Int): Long = BACKOFF_STEPS_MS[minOf(failures, BACKOFF_STEPS_MS.size) - 1]

    fun onRedirectRequested() {
        val state = getCurrentState().updateState
        if (state is AppUpdateUiState.Available) {
            val targetUrl = state.update.releaseUrl
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
