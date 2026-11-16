package com.jestk.browser.domain.model

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import kotlin.random.Random

/** The number of ungrouped tabs of one site that makes the browser offer a folder for them. */
const val DOMAIN_GROUP_MIN_TABS = 3

/** Identifies a site within a workspace; a dismissal of "youtube.com" in one workspace does not hide it in another. */
data class DomainGroupKey(val workspaceId: String, val domain: String)

/** A folder the browser offers to create: [tabIds] are the ungrouped tabs of [domain], in dock order. */
data class DomainGroupSuggestion(
    val workspaceId: String,
    /** The registrable domain (eTLD+1), e.g. "youtube.com" for music.youtube.com. */
    val domain: String,
    /** The proposed folder name, e.g. "Youtube". */
    val folderTitle: String,
    val tabIds: List<String>,
) {
    val key: DomainGroupKey get() = DomainGroupKey(workspaceId, domain)
}

/**
 * The registrable domain (eTLD+1) of an http(s) page URL, or null for anything else: blank or
 * about:blank, other schemes, IP hosts and hosts without a private domain such as "localhost".
 * Subdomains collapse into the registrable domain, so music.youtube.com and youtube.com match.
 */
fun registrableDomain(url: String): String? = url.toHttpUrlOrNull()?.topPrivateDomain()

/**
 * The folder name for a registrable domain: its first label with a capital letter. A registrable
 * domain is one label plus the public suffix, so the first label is the name without the suffix
 * ("youtube.com" -> "Youtube", "bbc.co.uk" -> "Bbc").
 */
fun folderTitleForDomain(domain: String): String =
    domain.substringBefore('.').replaceFirstChar { it.titlecase(Locale.ROOT) }

/** The tabs of [workspaceId] that sit in no folder, grouped by registrable domain. */
fun ungroupedTabsByDomain(tabs: List<Tab>, workspaceId: String): Map<String, List<Tab>> =
    tabs
        .filter { it.workspaceId == workspaceId && it.folderId == null }
        .mapNotNull { tab -> registrableDomain(tab.url)?.let { it to tab } }
        .groupBy({ it.first }, { it.second })

/**
 * The site to offer a folder for, or null. A site qualifies with [DOMAIN_GROUP_MIN_TABS] or more
 * ungrouped tabs in [workspaceId]. A dismissed site stays hidden until its ungrouped tab count
 * exceeds the count recorded in [dismissed] when it was dismissed. With several candidates the one
 * with the most tabs wins; a tie goes to the site whose tab was active most recently.
 */
fun suggestDomainGroup(
    tabs: List<Tab>,
    workspaceId: String,
    dismissed: Map<DomainGroupKey, Int> = emptyMap(),
): DomainGroupSuggestion? {
    val best = ungroupedTabsByDomain(tabs, workspaceId)
        .filter { (domain, group) ->
            group.size >= DOMAIN_GROUP_MIN_TABS &&
                group.size > (dismissed[DomainGroupKey(workspaceId, domain)] ?: 0)
        }.entries
        .maxWithOrNull(
            compareBy<Map.Entry<String, List<Tab>>> { it.value.size }
                .thenBy { entry -> entry.value.maxOf { it.lastActiveTimestamp } },
        ) ?: return null
    return DomainGroupSuggestion(
        workspaceId = workspaceId,
        domain = best.key,
        folderTitle = folderTitleForDomain(best.key),
        tabIds = best.value.map { it.id },
    )
}

/** A random palette colour, preferring one no folder of [workspaceId] uses yet. */
fun pickFolderColor(
    folders: List<TabFolder>,
    workspaceId: String,
    random: Random = Random.Default,
): Int {
    val used = folders.filter { it.workspaceId == workspaceId }.map { it.color }.toSet()
    val unused = TabFolder.PALETTE.filter { it !in used }
    return unused.ifEmpty { TabFolder.PALETTE }.random(random)
}
