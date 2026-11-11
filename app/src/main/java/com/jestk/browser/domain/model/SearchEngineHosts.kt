package com.jestk.browser.domain.model

import java.net.URI

/** The single answer to "is this a search engine host?" used by navigation and app-handoff logic. */
object SearchEngineHosts {

    private val DOMAINS = setOf(
        "duckduckgo.com",
        "brave.com",
        "kagi.com",
        "ecosia.org",
        "startpage.com",
        "bing.com",
        "yahoo.com",
        "baidu.com",
        "qwant.com",
        "ya.ru",
    )

    /** Brands that run one search domain per country: google.de, google.co.uk, yandex.kz, … */
    private val COUNTRY_BRANDS = setOf("google", "yandex")

    fun isSearchEngineHost(host: String): Boolean {
        val clean = normalize(host)
        if (clean.isEmpty()) return false
        return DOMAINS.any { isSameOrSubdomain(clean, it) } || COUNTRY_BRANDS.any { isBrandHost(clean, it) }
    }

    fun isSameOrSubdomain(host: String, domain: String): Boolean {
        val clean = normalize(host)
        return clean == domain || clean.endsWith(".$domain")
    }

    /**
     * True for `brand.<tld>` and its subdomains, where the suffix is a public TLD such as `com`,
     * `ru`, `co.uk` or `com.br` (one label, or a short label plus a country code).
     */
    fun isBrandHost(host: String, brand: String): Boolean {
        val labels = normalize(host).split('.')
        val index = labels.lastIndexOf(brand)
        if (index < 0) return false
        val suffix = labels.subList(index + 1, labels.size)
        return when (suffix.size) {
            1 -> suffix[0].isNotEmpty()
            2 -> suffix[0].length in 2..3 && suffix[1].length == 2
            else -> false
        }
    }

    fun hostOf(url: String): String? =
        try {
            URI(url.trim()).host?.let(::normalize)?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }

    private fun normalize(host: String): String = host.trim().lowercase().removePrefix("www.")
}
