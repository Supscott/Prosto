package com.jestk.browser.engine

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature

private const val TAG = "ProfileCleanup"

/**
 * Runs [action] on the main thread: `ProfileStore`, `Profile` and `WebView` are @UiThread, and
 * their callers catch what a call from another thread throws, so a call from an IO coroutine
 * would fail silently.
 */
internal fun onMainThread(action: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) action() else Handler(Looper.getMainLooper()).post(action)
}

private fun isMultiProfileSupported(): Boolean =
    runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false)

/** Clears the cookies and web storage of a profile this process has loaded. Main thread only. */
internal fun wipeProfile(profileName: String) {
    runCatching {
        if (!isMultiProfileSupported()) return@runCatching
        val profile = ProfileStore.getInstance().getProfile(profileName) ?: return@runCatching
        profile.cookieManager.removeAllCookies(null)
        profile.cookieManager.flush()
        profile.webStorage.deleteAllData()
    }.onFailure { Log.w(TAG, "Failed to wipe profile $profileName", it) }
}

/**
 * The profiles left at the start of a process that must go: every incognito profile and every
 * profile an earlier process could not delete ([pending]). The default profile is never deleted.
 */
internal fun leftoverProfiles(allNames: List<String>, pending: Set<String>): List<String> =
    allNames.filter { it != Profile.DEFAULT_PROFILE_NAME && (IncognitoProfile.isIncognito(it) || it in pending) }

/**
 * Deletes WebView profiles that are no longer used.
 *
 * Chromium refuses to delete a profile this process has loaded ("Cannot delete in-use profile",
 * even after every WebView of it is destroyed; see `ProfileStore.deleteProfile`). So a profile
 * that cannot go now is wiped at once and its name is kept in SharedPreferences; [start] deletes
 * it on the next app start, before any WebView can load it.
 */
object ProfileCleanup {
    private const val PREFS = "profile_cleanup"
    private const val KEY_PENDING = "pending_profile_deletes"

    @Volatile
    private var prefs: SharedPreferences? = null

    /** Call once from Application.onCreate. */
    fun start(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        onMainThread(::deleteLeftovers)
    }

    /**
     * Destroys the WebViews of [tabIds] (the deleted workspace's tabs) and deletes [profileName],
     * or wipes it and deletes it on the next start when Chromium still holds it.
     */
    fun deleteWorkspaceProfile(pool: WebViewPool, profileName: String, tabIds: List<String>) {
        onMainThread {
            tabIds.forEach { pool.removeWebView(it, keepState = false) }
            if (profileName == Profile.DEFAULT_PROFILE_NAME || pool.deleteProfile(profileName)) return@onMainThread
            if (!isMultiProfileSupported()) return@onMainThread
            wipeProfile(profileName)
            updatePending { it + profileName }
            Log.i(TAG, "Profile $profileName is in use; wiped now, deleted on the next start")
        }
    }

    private fun deleteLeftovers() {
        runCatching {
            if (!isMultiProfileSupported()) return@runCatching
            val store = ProfileStore.getInstance()
            val pending = prefs?.getStringSet(KEY_PENDING, null).orEmpty()
            val failed = leftoverProfiles(store.allProfileNames, pending).filterNot { name ->
                runCatching { store.deleteProfile(name) }
                    .onFailure { Log.w(TAG, "Failed to delete leftover profile $name", it) }
                    .isSuccess
            }
            // A name that no longer exists is done too; keep only the ones that failed.
            updatePending { failed.filter { it in pending }.toSet() }
        }.onFailure { Log.w(TAG, "Failed to delete leftover profiles", it) }
    }

    private fun updatePending(transform: (Set<String>) -> Set<String>) {
        val p = prefs ?: return
        val next = transform(p.getStringSet(KEY_PENDING, null).orEmpty().toSet())
        p.edit().putStringSet(KEY_PENDING, next).apply()
    }
}
