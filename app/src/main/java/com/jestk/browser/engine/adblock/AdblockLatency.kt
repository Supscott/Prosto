package com.jestk.browser.engine.adblock

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLongArray

/**
 * Lock-free histogram of `decide` durations (log2 buckets of microseconds). Debug builds only:
 * [enabled] stays false in release, so the hot path costs one volatile read.
 */
internal object AdblockLatency {
    private const val TAG = "AdblockLatency"
    private const val BUCKETS = 24
    private const val DUMP_EVERY = 500

    @Volatile
    var enabled = false

    private val buckets = AtomicLongArray(BUCKETS)
    private val total = AtomicInteger()

    fun record(nanos: Long) {
        val micros = (nanos / 1000).coerceAtLeast(1)
        val bucket = (63 - java.lang.Long.numberOfLeadingZeros(micros)).coerceAtMost(BUCKETS - 1)
        buckets.incrementAndGet(bucket)
        if (total.incrementAndGet() % DUMP_EVERY == 0) Log.d(TAG, summary())
    }

    /** Sample count, p50/p99 upper bounds and the non-empty buckets. */
    fun summary(): String {
        val counts = LongArray(BUCKETS) { buckets.get(it) }
        val sum = counts.sum()
        if (sum == 0L) return "no samples"
        fun percentile(p: Double): Long {
            var seen = 0L
            for (i in counts.indices) {
                seen += counts[i]
                if (seen >= sum * p) return 1L shl (i + 1)
            }
            return 1L shl BUCKETS
        }
        val spread = counts.indices.filter { counts[it] > 0 }.joinToString(" ") { "<${1L shl (it + 1)}us:${counts[it]}" }
        return "n=$sum p50<${percentile(0.5)}us p99<${percentile(0.99)}us $spread"
    }

    fun reset() {
        for (i in 0 until BUCKETS) buckets.set(i, 0)
        total.set(0)
    }
}
