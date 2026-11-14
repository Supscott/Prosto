package com.jestk.browser.domain.model

/**
 * One site on which content blocking is switched off. [host] is normalized (see
 * [SiteAllowlistHosts.normalize]) and also covers its subdomains; [workspaceId] is the
 * [Workspace.id] the entry applies to, or null for all workspaces.
 */
data class SiteAllowlistEntry(
    val host: String,
    val workspaceId: String?,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** Whether this entry covers [pageHost] when browsing in [inWorkspaceId]. */
    fun covers(pageHost: String, inWorkspaceId: String?): Boolean =
        (workspaceId == null || workspaceId == inWorkspaceId) && host in SiteAllowlistHosts.candidates(pageHost)
}

/** Host handling shared by the repository, the in-memory snapshot and the UI. Pure Kotlin. */
object SiteAllowlistHosts {
    /** Lowercase, trimmed, no trailing dot and no leading `www.`; empty for a blank host. */
    fun normalize(host: String): String =
        host.trim().lowercase().trimEnd('.').removePrefix("www.")

    /**
     * [host] followed by its parent domains, down to the last two labels: for `a.b.example.com`
     * this is `a.b.example.com`, `b.example.com`, `example.com`. An entry for `example.com`
     * therefore covers every subdomain, while an entry for `news.example.com` does not cover
     * `example.com`.
     */
    fun candidates(host: String): List<String> {
        val normalized = normalize(host)
        if (normalized.isEmpty()) return emptyList()
        val labels = normalized.split('.')
        if (labels.size <= 2) return listOf(normalized)
        return (0..labels.size - 2).map { labels.drop(it).joinToString(".") }
    }
}

/** Whether any entry disables blocking for [host] in [workspaceId]. */
fun List<SiteAllowlistEntry>.covers(host: String, workspaceId: String?): Boolean =
    any { it.covers(host, workspaceId) }
