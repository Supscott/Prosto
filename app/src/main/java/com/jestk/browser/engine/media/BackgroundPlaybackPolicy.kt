package com.jestk.browser.engine.media

/** What the page's `<video>` / `<audio>` elements are doing, as reported by [MediaPageScripts]. */
internal enum class MediaPageState {
    PLAYING,
    PAUSED,
    NONE,
    ;

    companion object {
        /** Parses the raw `evaluateJavascript` result, a JSON string such as `"playing"`. */
        fun parse(raw: String?): MediaPageState =
            when (raw?.trim('"')) {
                "playing" -> PLAYING
                "paused" -> PAUSED
                else -> NONE
            }
    }
}

/** How long a paused session keeps its notification before the service gives up. */
internal const val PAUSED_SESSION_TIMEOUT_MS = 5 * 60 * 1000L

/**
 * Whether the active tab should keep playing now that the app left the screen. Picture-in-Picture
 * keeps the Activity visible and a configuration change recreates the window, so neither counts.
 */
internal fun shouldKeepPlayingInBackground(
    enabled: Boolean,
    inPictureInPicture: Boolean,
    changingConfigurations: Boolean,
    hasActiveWebView: Boolean,
    pageState: MediaPageState,
): Boolean =
    enabled &&
        !inPictureInPicture &&
        !changingConfigurations &&
        hasActiveWebView &&
        pageState == MediaPageState.PLAYING

/** Whether a running background session has nothing left to keep alive. */
internal fun shouldStopBackgroundSession(
    pageState: MediaPageState,
    pausedForMs: Long,
): Boolean =
    pageState == MediaPageState.NONE ||
        (pageState == MediaPageState.PAUSED && pausedForMs >= PAUSED_SESSION_TIMEOUT_MS)
