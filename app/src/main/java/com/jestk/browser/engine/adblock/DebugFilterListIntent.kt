package com.jestk.browser.engine.adblock

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * `prosto-debug://add-list?url=<encoded>` loads one extra filter list (the ad-block fixture's
 * `fixture-filters.txt`); an empty `url` removes it (PRO-56). Handled only when the app is
 * debuggable: a release build consumes the intent and does nothing.
 */
internal object DebugFilterListIntent {
    private const val TAG = "DebugFilterList"
    private const val SCHEME = "prosto-debug"
    private const val HOST = "add-list"

    fun matches(intent: Intent?): Boolean = intent?.data?.let { it.scheme == SCHEME && it.host == HOST } == true

    /** Returns true when the list set was changed (or the change was a no-op) and the engines are consistent. */
    suspend fun handle(
        context: Context,
        intent: Intent,
        controller: FilterListController = FilterListServices.controller(context),
    ): Boolean {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return false
        val url = intent.data?.getQueryParameter("url").orEmpty()
        val ok = controller.setDebugList(url)
        Log.i(TAG, "url=${url.ifEmpty { "(removed)" }} ok=$ok")
        return ok
    }
}
