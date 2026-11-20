package com.jestk.browser.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * `prosto-debug://secure?enabled=0|1` lets adb drop FLAG_SECURE during incognito so
 * `tools/device.sh shot` can capture the incognito UI (PRO-82). Every read and write is gated by
 * FLAG_DEBUGGABLE: a release build ignores the intent and [disabled] is always false.
 */
internal object DebugSecureWindowSwitch {
    private const val TAG = "DebugSecureWindow"
    private const val SCHEME = "prosto-debug"
    private const val HOST = "secure"
    internal const val PREFS = "debug_flags"
    internal const val KEY = "secure_window_disabled"

    private val state = MutableStateFlow(false)
    private var loaded = false

    fun matches(intent: Intent?): Boolean = intent?.data?.let { it.scheme == SCHEME && it.host == HOST } == true

    private fun isDebuggable(context: Context) = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** True while adb has turned FLAG_SECURE off; always false in a non-debuggable build. */
    @Synchronized
    fun disabled(context: Context): StateFlow<Boolean> {
        if (!isDebuggable(context)) return MutableStateFlow(false)
        if (!loaded) {
            state.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
            loaded = true
        }
        return state
    }

    /** Applies the intent when the app is debuggable; returns true when it was a switch intent. */
    @Synchronized
    fun handle(context: Context, intent: Intent?): Boolean {
        if (!matches(intent)) return false
        if (!isDebuggable(context)) return true
        val disable = intent?.data?.getQueryParameter("enabled") == "0"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, disable).apply()
        loaded = true
        state.value = disable
        Log.i(TAG, "secure window ${if (disable) "off" else "on"}")
        return true
    }
}

/** FLAG_SECURE rule: incognito is always secure unless the debuggable-only switch turned it off. */
internal fun shouldBeSecure(incognito: Boolean, debugDisabled: Boolean, debuggable: Boolean): Boolean =
    incognito && !(debuggable && debugDisabled)
