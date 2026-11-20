package com.jestk.browser.util

import android.content.Context
import com.jestk.browser.domain.model.AppUpdate

/** Persistent state of the GitHub update check: throttle, failure backoff, ETag and the last known release. */
interface UpdateCheckStore {
    /** Epoch ms of the last successful check (200 or 304); 0 when none happened yet. */
    var lastSuccessAt: Long

    /** Epoch ms before which no request may be made (failure backoff); 0 when unrestricted. */
    var nextAllowedAt: Long
    var failureCount: Int
    var etag: String?

    /** The last release fetched successfully, kept so a known update survives failed checks. */
    var cachedRelease: AppUpdate?
}

class SharedPrefsUpdateCheckStore(context: Context) : UpdateCheckStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var lastSuccessAt: Long
        get() = prefs.getLong(KEY_LAST_SUCCESS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SUCCESS, value).apply()

    override var nextAllowedAt: Long
        get() = prefs.getLong(KEY_NEXT_ALLOWED, 0L)
        set(value) = prefs.edit().putLong(KEY_NEXT_ALLOWED, value).apply()

    override var failureCount: Int
        get() = prefs.getInt(KEY_FAILURES, 0)
        set(value) = prefs.edit().putInt(KEY_FAILURES, value).apply()

    override var etag: String?
        get() = prefs.getString(KEY_ETAG, null)
        set(value) = prefs.edit().putString(KEY_ETAG, value).apply()

    override var cachedRelease: AppUpdate?
        get() {
            val version = prefs.getString(KEY_VERSION, null) ?: return null
            return AppUpdate(
                versionName = version,
                releaseUrl = prefs.getString(KEY_URL, "").orEmpty(),
            )
        }
        set(value) {
            prefs.edit()
                .putString(KEY_VERSION, value?.versionName)
                .putString(KEY_URL, value?.releaseUrl)
                .apply()
        }

    private companion object {
        const val PREFS_NAME = "update_check"
        const val KEY_LAST_SUCCESS = "last_success_at"
        const val KEY_NEXT_ALLOWED = "next_allowed_at"
        const val KEY_FAILURES = "failure_count"
        const val KEY_ETAG = "etag"
        const val KEY_VERSION = "release_version"
        const val KEY_URL = "release_url"
    }
}
