package com.jestk.browser.domain.repository

/**
 * Receives statistics about the requests the ad blocker suppressed.
 *
 * The engine layer must not depend on the Room-backed data layer, so it only sees this narrow
 * contract instead of a [SettingsRepository]. Whoever owns the persistence decides how the
 * counters are stored.
 */
interface BlockedRequestStats {

    /**
     * Records a single suppressed request.
     *
     * @param isTracker `true` when the suppressed request belonged to a known tracker rather than
     * to a known ad host.
     */
    suspend fun recordBlockedRequest(isTracker: Boolean)

    /**
     * Flushes any in-memory buffered counts to persistent storage.
     */
    suspend fun flush() {}
}
