package com.jestk.browser.engine.adblock

/**
 * Spots popunders: a tap opens a popup and, a moment later, the page that was tapped jumps to
 * another site without a gesture of its own. The user sees the page they tapped, never the ad
 * that took over the opener.
 *
 * Pure Kotlin with an injected [clock]. Per-tab state, thread-safe: the popup is registered from
 * the main thread, the navigation is checked from `shouldOverrideUrlLoading`.
 *
 * Every decision is reported to [log] with the measured delta so [DEFAULT_THRESHOLD_MS] can be
 * tuned from real sites (the caller wires [log] to logcat in debug builds).
 */
class PopunderDetector(
    private val clock: () -> Long,
    private val thresholdMs: Long = DEFAULT_THRESHOLD_MS,
    private val log: (String) -> Unit = {},
) {
    private val popupOpenedAt = HashMap<String, Long>()

    /** A tap in [openerTabId] opened a popup. Only the latest popup of a tab matters. */
    fun onPopupOpened(openerTabId: String) {
        val now = clock()
        synchronized(popupOpenedAt) {
            popupOpenedAt.entries.removeAll { now - it.value > thresholdMs }
            popupOpenedAt[openerTabId] = now
        }
    }

    /**
     * Whether a main-frame navigation of [tabId] is the second half of a popunder. Only a
     * cross-site jump without a gesture can be one; same-site hops and taps never are.
     */
    fun isPopunder(tabId: String, isCrossSite: Boolean, hasGesture: Boolean): Boolean {
        if (!isCrossSite || hasGesture) return false
        val openedAt = synchronized(popupOpenedAt) { popupOpenedAt[tabId] } ?: return false
        val delta = clock() - openedAt
        val blocked = delta in 0 until thresholdMs
        log("popunder tab=$tabId delta=${delta}ms threshold=${thresholdMs}ms -> ${if (blocked) "BLOCK" else "allow"}")
        return blocked
    }

    /** Forgets a closed tab. */
    fun forget(tabId: String) {
        synchronized(popupOpenedAt) { popupOpenedAt.remove(tabId) }
    }

    companion object {
        /** Opener jumps cross-site this soon after its popup opened: popunder. Tune from the logged deltas. */
        const val DEFAULT_THRESHOLD_MS = 1000L
    }
}
