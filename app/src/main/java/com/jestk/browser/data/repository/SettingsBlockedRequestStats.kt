package com.jestk.browser.data.repository

import com.jestk.browser.domain.repository.BlockedRequestStats
import com.jestk.browser.domain.repository.SettingsRepository

/**
 * Persists the ad-blocker counters through the settings repository.
 *
 * This is the only place that knows how a suppressed request maps onto the stored counters; the
 * engine just reports whether the request was a tracker.
 */
class SettingsBlockedRequestStats(
    private val settingsRepository: SettingsRepository,
) : BlockedRequestStats {

    override suspend fun recordBlockedRequest(isTracker: Boolean) {
        if (isTracker) {
            settingsRepository.incrementBlockedTrackers()
        } else {
            settingsRepository.incrementBlockedAds()
        }
    }
}
