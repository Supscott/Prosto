package com.jestk.browser.data.mapper

/** Storage format of per-site settings: sites joined by ",", counts as "site=n" pairs joined by ",". */
internal fun serializeSiteSet(sites: Set<String>): String =
    sites.filter { it.isNotBlank() }.joinToString(",")

internal fun parseSiteSet(raw: String?): Set<String> =
    raw?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

internal fun serializeSiteCounts(counts: Map<String, Int>): String =
    counts.filter { it.key.isNotBlank() && it.value > 0 }.entries.joinToString(",") { "${it.key}=${it.value}" }

internal fun parseSiteCounts(raw: String?): Map<String, Int> =
    parseSiteSet(raw).mapNotNull { pair ->
        val site = pair.substringBeforeLast('=', "")
        val count = pair.substringAfterLast('=', "").toIntOrNull()
        if (site.isEmpty() || count == null || count <= 0) null else site to count
    }.toMap()
