package com.jestk.browser.engine.adblock

import com.jestk.browser.util.UrlSecurityUtils

/** Lets CB-12 and CB-14 exempt the opener site from popup dropping. */
fun interface PopupSiteAllowlist {
    fun allows(openerUrl: String?): Boolean

    companion object {
        val NONE = PopupSiteAllowlist { false }
    }
}

/** How a pending popup was resolved. */
enum class PopupOutcome { REVEALED, DROPPED }

/**
 * Decides whether a popup window may become a visible tab. `onCreateWindow` does not know the
 * target URL, so a popup stays pending until its first main-frame URL is seen in the popup
 * WebView (or [timeoutMs] passes, which covers `window.open('')` followed by a later
 * `location` assignment, as OAuth flows do).
 *
 * Pure Kotlin with an injected [clock]; thread-safe, because the first URL usually arrives on a
 * WebView worker thread. The `onReveal` / `onDrop` callbacks run on the calling thread, outside
 * the lock; the caller is responsible for hopping to the main thread.
 */
class PopupGate(
    private val clock: () -> Long,
    private val isAdPopup: (url: String) -> Boolean,
    var allowlist: PopupSiteAllowlist = PopupSiteAllowlist.NONE,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val isAuthUrl: (url: String) -> Boolean = ::isAuthDomainUrl,
    private val isSameSite: (url: String, openerUrl: String) -> Boolean = UrlSecurityUtils::isSameSite,
) {
    private class Pending(
        val openerUrl: String?,
        val createdAt: Long,
        val onReveal: () -> Unit,
        val onDrop: (url: String?) -> Unit,
    )

    private val pending = HashMap<String, Pending>()

    fun register(tabId: String, openerUrl: String?, onReveal: () -> Unit, onDrop: (url: String?) -> Unit) {
        synchronized(pending) { pending[tabId] = Pending(openerUrl, clock(), onReveal, onDrop) }
    }

    fun isPending(tabId: String): Boolean = synchronized(pending) { tabId in pending }

    /**
     * Feeds a main-frame URL seen in the popup WebView. Returns the decision it caused, or null
     * when the popup is unknown, already decided, or the URL says nothing yet (`about:blank`).
     */
    fun onUrl(tabId: String, url: String?): PopupOutcome? {
        if (url.isNullOrBlank() || url == "about:blank") return null
        val entry = synchronized(pending) { pending.remove(tabId) } ?: return null
        val drop = shouldDrop(entry, url)
        if (drop) entry.onDrop(url) else entry.onReveal()
        return if (drop) PopupOutcome.DROPPED else PopupOutcome.REVEALED
    }

    /** Reveals every popup that has waited for [timeoutMs] without a URL. */
    fun sweep() {
        val now = clock()
        val due = synchronized(pending) {
            val keys = pending.filterValues { now - it.createdAt >= timeoutMs }.keys.toList()
            keys.mapNotNull { pending.remove(it) }
        }
        due.forEach { it.onReveal() }
    }

    /** Forgets a popup whose tab went away before a decision. */
    fun forget(tabId: String) {
        synchronized(pending) { pending.remove(tabId) }
    }

    private fun shouldDrop(entry: Pending, url: String): Boolean {
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return false
        if (isAuthUrl(url)) return false
        val opener = entry.openerUrl
        if (!opener.isNullOrBlank() && isSameSite(url, opener)) return false
        if (allowlist.allows(opener)) return false
        return isAdPopup(url)
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 1500L

        private fun isAuthDomainUrl(url: String): Boolean =
            AuthDomainAllowlist.isAllowlistedAuthDomain(UrlSecurityUtils.extractHost(url))
    }
}
