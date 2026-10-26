package com.jestk.browser.data.repository

import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsBlockedRequestStatsTest {

    private class FakeSettingsRepository : SettingsRepository {
        val totalAdsIncremented = AtomicLong(0)
        val totalTrackersIncremented = AtomicLong(0)
        val incrementCallsCount = AtomicLong(0)

        override fun getSettings() = emptyFlow<BrowserSettings>()
        override suspend fun getCurrentSettings() = BrowserSettings()
        override fun getBlockedAdsCount() = emptyFlow<Long>()
        override fun getBlockedTrackersCount() = emptyFlow<Long>()

        override suspend fun incrementBlockedAds(count: Long) {
            totalAdsIncremented.addAndGet(count)
            incrementCallsCount.incrementAndGet()
        }

        override suspend fun incrementBlockedTrackers(count: Long) {
            totalTrackersIncremented.addAndGet(count)
            incrementCallsCount.incrementAndGet()
        }

        override suspend fun setThemeMode(mode: com.jestk.browser.domain.model.ThemeMode) {}
        override suspend fun setSearchEngine(engine: com.jestk.browser.domain.model.SearchEngine) {}
        override suspend fun setAdBlockerEnabled(enabled: Boolean) {}
        override suspend fun setTrackingProtectionEnabled(enabled: Boolean) {}
        override suspend fun setOpenInAppsEnabled(enabled: Boolean) {}
        override suspend fun setAutoUpdateEnabled(enabled: Boolean) {}
        override suspend fun setVibrationEnabled(enabled: Boolean) {}
        override suspend fun setGrayUnloadedTabsEnabled(enabled: Boolean) {}
        override suspend fun setHiddenSearchPageEngineIds(hiddenEngineIds: Set<String>) {}
        override suspend fun setSkippedUpdateVersion(version: String?) {}
    }

    @Test
    fun batchesMultipleRapidBlockedRequestsIntoSingleDatabaseIncrement() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repo = FakeSettingsRepository()
        val stats = SettingsBlockedRequestStats(
            settingsRepository = repo,
            ioDispatcher = testDispatcher,
            flushDelayMs = 150L
        )

        // 10 ads and 5 trackers blocked rapidly
        repeat(10) { stats.recordBlockedRequest(isTracker = false) }
        repeat(5) { stats.recordBlockedRequest(isTracker = true) }

        // Before debounce timer fires: no DB writes
        testScheduler.advanceTimeBy(50)
        assertThat(repo.totalAdsIncremented.get()).isEqualTo(0L)
        assertThat(repo.totalTrackersIncremented.get()).isEqualTo(0L)

        // Advance time past the flush delay
        testScheduler.advanceTimeBy(150)
        assertThat(repo.totalAdsIncremented.get()).isEqualTo(10L)
        assertThat(repo.totalTrackersIncremented.get()).isEqualTo(5L)
        assertThat(repo.incrementCallsCount.get()).isEqualTo(2L) // Exactly 1 call for ads, 1 for trackers
    }

    @Test
    fun explicitFlushFlushesImmediately() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repo = FakeSettingsRepository()
        val stats = SettingsBlockedRequestStats(
            settingsRepository = repo,
            ioDispatcher = testDispatcher,
            flushDelayMs = 5000L
        )

        repeat(7) { stats.recordBlockedRequest(isTracker = false) }
        repeat(3) { stats.recordBlockedRequest(isTracker = true) }

        stats.flush()

        assertThat(repo.totalAdsIncremented.get()).isEqualTo(7L)
        assertThat(repo.totalTrackersIncremented.get()).isEqualTo(3L)
    }
}
