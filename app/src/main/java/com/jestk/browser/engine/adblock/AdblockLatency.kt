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
    private val cpuBuckets = AtomicLongArray(BUCKETS)
    private val total = AtomicInteger()

    /**
     * Records one `decide`: [nanos] of wall time and [cpuNanos] of thread CPU time. Wall time also
     * counts the time the WebView IO thread waits to be scheduled while pages render; the CPU time
     * is the work itself.
     */
    fun record(nanos: Long, cpuNanos: Long = -1) {
        buckets.incrementAndGet(bucketOf(nanos))
        if (cpuNanos >= 0) cpuBuckets.incrementAndGet(bucketOf(cpuNanos))
        if (total.incrementAndGet() % DUMP_EVERY == 0) Log.d(TAG, "wall ${summary(buckets)} | cpu ${summary(cpuBuckets)}")
    }

    private fun bucketOf(nanos: Long): Int {
        val micros = (nanos / 1000).coerceAtLeast(1)
        return (63 - java.lang.Long.numberOfLeadingZeros(micros)).coerceAtMost(BUCKETS - 1)
    }

    /** Sample count, p50/p99 upper bounds and the non-empty buckets of the wall-time histogram. */
    fun summary(): String = summary(buckets)

    private fun summary(source: AtomicLongArray): String {
        val counts = LongArray(BUCKETS) { source.get(it) }
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
        for (i in 0 until BUCKETS) {
            buckets.set(i, 0)
            cpuBuckets.set(i, 0)
        }
        total.set(0)
    }
}
