package com.jestk.browser.util

import android.content.Context
import com.jestk.browser.ui.viewmodel.BackgroundStamp

/** Remembers when the app went to the background so the time away survives process death. */
class BackgroundStampStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(stamp: BackgroundStamp) {
        prefs.edit().putLong(KEY_AT, stamp.atMs).putBoolean(KEY_MEDIA, stamp.mediaActive).apply()
    }

    /** Returns the saved stamp and forgets it, so one absence triggers at most one new tab. */
    fun take(): BackgroundStamp? {
        if (!prefs.contains(KEY_AT)) return null
        val stamp = BackgroundStamp(prefs.getLong(KEY_AT, 0L), prefs.getBoolean(KEY_MEDIA, false))
        prefs.edit().clear().apply()
        return stamp
    }

    private companion object {
        const val PREFS_NAME = "background_stamp"
        const val KEY_AT = "at_ms"
        const val KEY_MEDIA = "media_active"
    }
}
