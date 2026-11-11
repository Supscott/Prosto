package com.jestk.browser.engine

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context

/**
 * How many live WebViews the pool keeps. A live tab switches instantly; an evicted one comes back
 * from its snapshot and saved history but reloads, so devices with spare memory keep more.
 */
internal object WebViewPoolSizing {
    const val DEFAULT_MAX_WEBVIEWS = 5
    private const val GIB = 1024L * 1024L * 1024L

    fun maxWebViews(totalMemoryBytes: Long, isLowRamDevice: Boolean): Int = when {
        isLowRamDevice -> 2
        totalMemoryBytes <= 0L -> DEFAULT_MAX_WEBVIEWS
        totalMemoryBytes < 3 * GIB -> 3
        totalMemoryBytes < 6 * GIB -> DEFAULT_MAX_WEBVIEWS
        else -> 8
    }

    fun maxWebViews(context: Context): Int {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return DEFAULT_MAX_WEBVIEWS
        val info = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        return maxWebViews(info.totalMem, activityManager.isLowRamDevice)
    }

    /**
     * Whether a trim level means real memory pressure. Going to the background (UI_HIDDEN,
     * BACKGROUND) is not: dropping tabs then would reload them every time the user switches apps.
     */
    fun isMemoryPressure(level: Int): Boolean =
        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE
}
