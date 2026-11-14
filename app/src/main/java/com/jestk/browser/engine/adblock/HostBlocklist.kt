package com.jestk.browser.engine.adblock

import java.util.concurrent.ConcurrentHashMap

/**
 * Blocklist of hostnames that supports allocation-free suffix matching.
 *
 * Exact lookups use a plain hash set. Matching a subdomain ("ads.example.com" against the blocked
 * host "example.com") needs to test every suffix of a host, which used to allocate one substring
 * per candidate. To avoid that, the hosts are additionally bucketed by their own
 * [String.hashCode]: a candidate suffix is hashed over its character range with the very same
 * algorithm [String.hashCode] uses, so the bucket can be found without allocating a string, and
 * the few candidates of that bucket are then compared in place with [String.regionMatches].
 *
 * The instance is safe for concurrent use: rules are loaded on a background thread while the
 * matching runs on the main thread.
 */
internal sealed interface HostBucket {
    fun matches(host: String, fromIndex: Int, length: Int): Boolean
    fun withHost(newHost: String): HostBucket

    class Single(val host: String) : HostBucket {
        override fun matches(host: String, fromIndex: Int, length: Int): Boolean =
            this.host.length == length && host.regionMatches(fromIndex, this.host, 0, length)

        override fun withHost(newHost: String): HostBucket =
            Multi(arrayOf(host, newHost))
    }

    class Multi(val hosts: Array<String>) : HostBucket {
        override fun matches(host: String, fromIndex: Int, length: Int): Boolean {
            for (h in hosts) {
                if (h.length == length && host.regionMatches(fromIndex, h, 0, length)) {
                    return true
                }
            }
            return false
        }

        override fun withHost(newHost: String): HostBucket {
            val newArr = java.util.Arrays.copyOf(hosts, hosts.size + 1)
            newArr[hosts.size] = newHost
            return Multi(newArr)
        }
    }
}

internal class HostBlocklist {
    private val exactHosts = ConcurrentHashMap.newKeySet<String>(65536)
    private val hashBuckets = ConcurrentHashMap<Int, HostBucket>(65536)

    val size: Int get() = exactHosts.size

    fun add(host: String) {
        if (!exactHosts.add(host)) return
        hashBuckets.compute(host.hashCode()) { _, existing ->
            existing?.withHost(host) ?: HostBucket.Single(host)
        }
    }

    fun addAll(hosts: Collection<String>) {
        for (host in hosts) {
            add(host)
        }
    }

    fun clear() {
        exactHosts.clear()
        hashBuckets.clear()
    }

    /** Returns true when [host] itself is blocked. */
    fun contains(host: String): Boolean = exactHosts.contains(host)

    /**
     * Returns the index in [host] where the blocked entry that covers it starts (0 for an exact
     * match, the start of a parent domain for a subdomain match), or -1 when nothing matches.
     * Candidate suffixes are compared in place, so the common non-matching case allocates nothing.
     */
    fun matchedSuffixStart(host: String): Int {
        if (exactHosts.contains(host)) return 0
        var suffixStart = 0
        while (true) {
            val dotIndex = host.indexOf('.', suffixStart)
            // A trailing dot terminates the chain: there is no further suffix left to test.
            if (dotIndex == -1 || dotIndex == host.length - 1) return -1
            suffixStart = dotIndex + 1
            if (containsSuffix(host, suffixStart)) return suffixStart
        }
    }

    /**
     * Returns true when the region of [host] that starts at [fromIndex] is a blocked host.
     * The region is compared in place, so no substring is allocated.
     */
    fun containsSuffix(
        host: String,
        fromIndex: Int,
    ): Boolean {
        if (fromIndex < 0 || fromIndex >= host.length) return false
        val bucket = hashBuckets[hashRegion(host, fromIndex)] ?: return false
        return bucket.matches(host, fromIndex, host.length - fromIndex)
    }

    /**
     * Hash of the region of [host] that starts at [fromIndex], computed exactly like
     * [String.hashCode] of that substring, so a region hash always matches the hash of the equal
     * string stored in the blocklist.
     */
    private fun hashRegion(
        host: String,
        fromIndex: Int,
    ): Int {
        var hash = 0
        for (index in fromIndex until host.length) {
            hash = 31 * hash + host[index].code
        }
        return hash
    }
}
