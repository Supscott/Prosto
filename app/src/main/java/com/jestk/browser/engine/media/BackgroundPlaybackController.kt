package com.jestk.browser.engine.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.engine.NestedWebView
import com.jestk.browser.engine.WebViewPool

/**
 * Keeps the active tab's media playing while the app is off screen (setting "Background
 * playback"). The Activity reports its lifecycle; the controller probes the page, starts
 * [BackgroundPlaybackService] when media is playing and polls the page until it stops.
 * All methods run on the main thread.
 */
internal class BackgroundPlaybackController(
    context: Context,
    private val webViewPool: WebViewPool,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private val appContext = context.applicationContext
    private var probeGeneration = 0
    private var candidate: Candidate? = null
    private var activityStopped = false
    private var session: Session? = null

    /** The WebView whose page is kept "visible" while the app is off screen; see [NestedWebView.keepPageVisible]. */
    private var keptVisible: NestedWebView? = null
    private val poll = Runnable { pollPage() }

    /** True while the app plays media in the background; cleared when the app returns on screen. */
    val isSessionActive: Boolean get() = session != null

    private class Candidate(val tabId: String, val title: String, val host: String)

    private class Session(val candidate: Candidate) {
        var isPlaying = true
        var pausedSince = 0L
    }

    /** The Activity left the resumed state; decides whether the tab keeps playing. */
    fun onActivityPaused(
        enabled: Boolean,
        inPictureInPicture: Boolean,
        changingConfigurations: Boolean,
        tab: Tab?,
    ) {
        val generation = ++probeGeneration
        candidate = null
        val webView = tab?.let { webViewPool.getWebView(it.id) }
        if (!enabled || webView == null || tab == null) return
        if (inPictureInPicture || changingConfigurations) return
        // The screen can turn off before the probe answers; keep the page visible from the start
        // and give it back its real visibility below when nothing is playing.
        keepVisible(webView as? NestedWebView)
        webView.evaluateJavascript(MediaPageScripts.PROBE) { raw ->
            if (generation != probeGeneration) return@evaluateJavascript
            val keep =
                shouldKeepPlayingInBackground(
                    enabled = enabled,
                    inPictureInPicture = inPictureInPicture,
                    changingConfigurations = changingConfigurations,
                    hasActiveWebView = true,
                    pageState = MediaPageState.parse(raw),
                )
            if (keep) {
                candidate = Candidate(tab.id, tab.title, hostOf(tab.url))
                startIfReady()
            } else {
                keepVisible(null)
            }
        }
    }

    /** The Activity is no longer visible; a short pause (dialog, permission prompt) never gets here. */
    fun onActivityStopped() {
        activityStopped = true
        startIfReady()
    }

    /** The app is back in the foreground. */
    fun onActivityResumed() {
        activityStopped = false
        probeGeneration++
        candidate = null
        stop(appInForeground = true)
    }

    /** Ends the session; [appInForeground] when the app is coming back on screen. */
    fun stop(appInForeground: Boolean = false) {
        handler.removeCallbacks(poll)
        candidate = null
        keepVisible(null, appInForeground)
        if (session == null) return
        session = null
        BackgroundPlaybackSession.onSetPlaying = null
        BackgroundPlaybackService.stop(appContext)
    }

    private fun startIfReady() {
        val ready = candidate ?: return
        if (!activityStopped || session != null) return
        candidate = null
        session = Session(ready)
        BackgroundPlaybackSession.onSetPlaying = ::setPlaying
        showNotification()
        handler.postDelayed(poll, POLL_INTERVAL_MS)
    }

    private fun setPlaying(playing: Boolean) {
        val current = session ?: return
        val webView = webViewOf(current) ?: return stop()
        webView.evaluateJavascript(if (playing) MediaPageScripts.PLAY else MediaPageScripts.PAUSE, null)
        handler.removeCallbacks(poll)
        handler.postDelayed(poll, SETTLE_DELAY_MS)
    }

    private fun pollPage() {
        val current = session ?: return
        val webView = webViewOf(current) ?: return stop()
        webView.evaluateJavascript(MediaPageScripts.PROBE) { raw ->
            if (session !== current) return@evaluateJavascript
            val state = MediaPageState.parse(raw)
            val playing = state == MediaPageState.PLAYING
            if (!playing && current.isPlaying) current.pausedSince = elapsedRealtime()
            val pausedFor = if (state == MediaPageState.PAUSED) elapsedRealtime() - current.pausedSince else 0L
            if (shouldStopBackgroundSession(state, pausedFor)) {
                stop()
                return@evaluateJavascript
            }
            if (playing != current.isPlaying) {
                current.isPlaying = playing
                showNotification()
            }
            handler.postDelayed(poll, POLL_INTERVAL_MS)
        }
    }

    private fun showNotification() {
        val current = session ?: return
        BackgroundPlaybackService.show(
            appContext,
            BackgroundPlaybackInfo(current.candidate.title, current.candidate.host, current.isPlaying),
        )
    }

    private fun keepVisible(webView: NestedWebView?, appInForeground: Boolean = false) {
        if (keptVisible !== webView) keptVisible?.setKeepPageVisible(false, appInForeground)
        keptVisible = webView
        webView?.setKeepPageVisible(true, appInForeground)
    }

    private fun webViewOf(current: Session): WebView? = webViewPool.getWebView(current.candidate.tabId)

    private fun hostOf(url: String): String = runCatching { android.net.Uri.parse(url).host }.getOrNull().orEmpty()

    private companion object {
        const val POLL_INTERVAL_MS = 3_000L
        const val SETTLE_DELAY_MS = 400L
    }
}
