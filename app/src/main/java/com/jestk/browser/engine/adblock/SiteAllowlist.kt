package com.jestk.browser.engine.adblock

import com.jestk.browser.domain.model.SiteAllowlistEntry
import com.jestk.browser.domain.model.SiteAllowlistHosts
import com.jestk.browser.util.UrlSecurityUtils
import java.util.concurrent.atomic.AtomicReference

/**
 * In-memory snapshot of the persisted per-site allowlist, read by the blocking paths on WebView
 * worker threads. Lookups never suspend and never touch the database: [update] swaps an
 * immutable map that the repository flow refreshes.
 *
 * A null workspace id means the caller does not know the workspace (service-worker requests);
 * only all-workspaces entries match then.
 */
class SiteAllowlist(
    private val hostOf: (String) -> String = UrlSecurityUtils::extractHost,
) {
    /** Host to the scopes it is allowlisted for; a null scope stands for all workspaces. */
    private val snapshot = AtomicReference<Map<String, Set<String?>>>(emptyMap())

    fun update(entries: List<SiteAllowlistEntry>) {
        snapshot.set(entries.groupBy({ it.host }, { it.workspaceId }).mapValues { it.value.toSet() })
    }

    /** Whether blocking is off for the site of [url] in [workspaceId]. Subdomains match their parent entry. */
    fun isAllowlisted(url: String?, workspaceId: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val map = snapshot.get()
        if (map.isEmpty()) return false
        return isHostAllowlisted(hostOf(url), workspaceId, map)
    }

    fun isHostAllowlisted(host: String, workspaceId: String?): Boolean =
        isHostAllowlisted(host, workspaceId, snapshot.get())

    private fun isHostAllowlisted(host: String, workspaceId: String?, map: Map<String, Set<String?>>): Boolean =
        SiteAllowlistHosts.candidates(host).any { candidate ->
            val scopes = map[candidate] ?: return@any false
            null in scopes || (workspaceId != null && workspaceId in scopes)
        }

    companion object {
        /** The process-wide snapshot the engine hooks read; kept current by [SiteAllowlistSync]. */
        val shared = SiteAllowlist()
    }
}
