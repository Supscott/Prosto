package com.jestk.browser.engine.media

/** What the media notification shows. */
internal data class BackgroundPlaybackInfo(
    val title: String,
    val host: String,
    val isPlaying: Boolean,
)

/** Hand-off between [BackgroundPlaybackController] and [BackgroundPlaybackService]. */
internal object BackgroundPlaybackSession {
    /** Set by the controller while a session runs; invoked on the main thread. */
    @Volatile
    var onSetPlaying: ((Boolean) -> Unit)? = null
}
