package com.jestk.browser.engine

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Logs how long each stage of a page load takes, per tab, under the `PageLoadTiming` tag:
 * load request -> onPageStarted -> onPageCommitVisible (first visible frame) -> onPageFinished.
 *
 * Read it with `adb logcat -s PageLoadTiming`. The first-frame time is what the user perceives
 * as "the page appeared"; compare it before and after a loading change.
 */
object PageLoadTimer {
    private const val TAG = "PageLoadTiming"

    private class Timeline(val startMs: Long, var committed: Boolean = false)

    private val timelines = ConcurrentHashMap<String, Timeline>()

    /** The app asked the WebView to load [url] (address bar, restore, new tab). */
    fun onLoadRequested(tabId: String?, url: String) {
        if (tabId == null || url.isBlank() || url == "about:blank") return
        timelines[tabId] = Timeline(SystemClock.elapsedRealtime())
        Log.d(TAG, "tab=$tabId request $url")
    }

    fun onPageStarted(tabId: String, url: String) {
        // Page-initiated navigations (link taps, redirects) have no load request: start here.
        val timeline = timelines[tabId]
        if (timeline == null || timeline.committed) {
            timelines[tabId] = Timeline(SystemClock.elapsedRealtime())
            Log.d(TAG, "tab=$tabId started $url (page-initiated)")
            return
        }
        Log.d(TAG, "tab=$tabId started +${elapsed(timeline)}ms $url")
    }

    fun onFirstVisibleFrame(tabId: String, url: String) {
        val timeline = timelines[tabId] ?: return
        if (timeline.committed) return
        timeline.committed = true
        Log.d(TAG, "tab=$tabId first-frame +${elapsed(timeline)}ms $url")
    }

    fun onPageFinished(tabId: String, url: String) {
        val timeline = timelines.remove(tabId) ?: return
        Log.d(TAG, "tab=$tabId finished +${elapsed(timeline)}ms $url")
    }

    private fun elapsed(timeline: Timeline): Long = SystemClock.elapsedRealtime() - timeline.startMs
}
