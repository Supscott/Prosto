package com.jestk.browser.ui.views

/**
 * Decides when the pull-to-refresh spinner hides.
 *
 * The spinner belongs to the pull gesture only: ordinary page loads show their progress in the
 * tab pill, like Chrome. A pull starts a reload; the spinner stays until that reload has started
 * and then finished. When no load starts in time (for example the reload was a no-op), the
 * spinner hides after the start timeout.
 */
class PullRefreshIndicatorState {
    private var awaitingReloadStart = false
    private var isPageLoading = false

    /** The user released the pull past the threshold, so the layout now shows its spinner. */
    fun onPullRefresh() {
        awaitingReloadStart = true
    }

    /** Returns true when a spinner that is showing must hide now. */
    fun onPageLoadingChanged(isLoading: Boolean): Boolean {
        isPageLoading = isLoading
        if (isLoading) {
            awaitingReloadStart = false
            return false
        }
        return !awaitingReloadStart
    }

    /** The reload did not start in time. Returns true when the spinner must hide now. */
    fun onReloadStartTimeout(): Boolean {
        awaitingReloadStart = false
        return !isPageLoading
    }

    companion object {
        const val RELOAD_START_TIMEOUT_MS = 3_000L
    }
}
