package com.jestk.browser.ui.viewmodel

/** When the app last went off screen, and whether media was playing or in PiP at that moment. */
data class BackgroundStamp(
    val atMs: Long,
    val mediaActive: Boolean,
)

/**
 * Decides whether returning to the app should open a fresh Home tab: the app was away for at least
 * [thresholdMs], and the return is not a continuation of something else (a link, media, an already
 * empty Home tab, no tabs yet).
 */
class ResumeNewTabPolicy(
    private val thresholdMs: Long = DEFAULT_THRESHOLD_MS,
) {
    fun shouldOpenNewTab(
        stamp: BackgroundStamp?,
        nowMs: Long,
        hasTabs: Boolean,
        activeTabIsHome: Boolean,
        returnedWithLink: Boolean,
        mediaActiveNow: Boolean,
    ): Boolean {
        if (stamp == null || !hasTabs || activeTabIsHome) return false
        if (returnedWithLink || stamp.mediaActive || mediaActiveNow) return false
        return nowMs - stamp.atMs >= thresholdMs
    }

    companion object {
        const val DEFAULT_THRESHOLD_MS = 30L * 60L * 1000L
    }
}
