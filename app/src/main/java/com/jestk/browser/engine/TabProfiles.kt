package com.jestk.browser.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.Workspace

private const val TAG = "TabProfiles"

/**
 * Runs [action] on the main thread: `ProfileStore`, `Profile` and `WebView.destroy()` are
 * @UiThread, and the callers catch what a call from another thread throws, so a call from an IO
 * coroutine would fail silently.
 */
private fun onMainThread(action: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) action() else Handler(Looper.getMainLooper()).post(action)
}

/** [WebViewPool.deleteProfile] on the main thread. */
fun WebViewPool.deleteProfileOnMainThread(profileName: String) {
    onMainThread { deleteProfile(profileName) }
}

/**
 * The WebView profiles of incognito tabs, one per incognito session.
 *
 * Chromium cannot delete a profile that this process has loaded ("Cannot delete in-use profile"
 * even after every WebView of it is destroyed; see `ProfileStore.deleteProfile`). So when the last
 * incognito tab closes, [endSession] wipes the session's cookies and web storage at once and
 * moves to a fresh profile name, so a later incognito tab never sees the earlier session's data.
 * The profile directories themselves are deleted on the next app start ([scheduleLeftoverCleanup]),
 * before any WebView loads them. [WebViewPool] never keeps a spare WebView or warms a renderer for
 * an incognito profile.
 */
object IncognitoProfile {
    // Also matches the plain "incognito" profile of early debug builds, so their cleanup deletes it.
    private const val PREFIX = "incognito"

    @Volatile
    private var session = 0

    /** The profile of the current incognito session. */
    val name: String get() = "$PREFIX-$session"

    fun isIncognito(profileName: String?): Boolean = profileName?.startsWith(PREFIX) == true

    /** Ends the current session: its data is wiped now and the next incognito tab gets a new profile. */
    fun endSession() {
        val ended = name
        session++
        onMainThread { wipe(ended) }
    }

    /** Deletes the incognito profiles a previous process left on disk. Call it once on app start. */
    fun scheduleLeftoverCleanup() {
        onMainThread {
            runCatching {
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return@runCatching
                val store = ProfileStore.getInstance()
                store.allProfileNames.filter(::isIncognito).forEach { store.deleteProfile(it) }
            }.onFailure { Log.w(TAG, "Failed to delete leftover incognito profiles", it) }
        }
    }

    private fun wipe(profileName: String) {
        runCatching {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return@runCatching
            val profile = ProfileStore.getInstance().getProfile(profileName) ?: return@runCatching
            profile.cookieManager.removeAllCookies(null)
            profile.cookieManager.flush()
            profile.webStorage.deleteAllData()
        }.onFailure { Log.w(TAG, "Failed to wipe incognito profile $profileName", it) }
    }
}

/**
 * The WebView profile a tab must use: [IncognitoProfile.name] for an incognito [tab], otherwise
 * the profile of the workspace [workspaceId].
 */
fun profileNameFor(tab: Tab?, workspaces: List<Workspace>, workspaceId: String): String =
    if (tab?.isIncognito == true) {
        IncognitoProfile.name
    } else {
        workspaces.find { it.id == workspaceId }?.profileName ?: workspaceId
    }
