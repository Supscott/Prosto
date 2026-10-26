package com.jestk.browser.data.repository

import com.jestk.browser.domain.repository.BlockedRequestStats
import com.jestk.browser.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Persists the ad-blocker counters through the settings repository with in-memory buffering.
 *
 * Incoming blocked request counts are aggregated in-memory and flushed to the database in
 * debounced batches to prevent disk I/O thrashing and continuous Compose UI recomposition
 * during page loading.
 */
class SettingsBlockedRequestStats(
    private val settingsRepository: SettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val flushDelayMs: Long = 100L,
) : BlockedRequestStats {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val pendingAds = AtomicLong(0)
    private val pendingTrackers = AtomicLong(0)
    private val scheduledFlush = AtomicReference<Job?>(null)

    override suspend fun recordBlockedRequest(isTracker: Boolean) {
        if (isTracker) {
            pendingTrackers.incrementAndGet()
        } else {
            pendingAds.incrementAndGet()
        }
        scheduleFlush()
    }

    private fun scheduleFlush() {
        val existing = scheduledFlush.get()
        if (existing != null && existing.isActive) return

        val newJob = scope.launch {
            delay(flushDelayMs)
            flush()
        }
        if (!scheduledFlush.compareAndSet(existing, newJob)) {
            newJob.cancel()
        }
    }

    suspend fun flush() {
        val ads = pendingAds.getAndSet(0)
        val trackers = pendingTrackers.getAndSet(0)
        if (ads > 0) {
            settingsRepository.incrementBlockedAds(ads)
        }
        if (trackers > 0) {
            settingsRepository.incrementBlockedTrackers(trackers)
        }
    }
}
