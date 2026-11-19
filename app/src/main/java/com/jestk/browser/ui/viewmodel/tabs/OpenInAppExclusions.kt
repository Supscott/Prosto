package com.jestk.browser.ui.viewmodel.tabs

import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.engine.ExternalAppResolver

/** Explicit dismissals of a site's open-in-app pill after which the pill asks to stop offering. */
internal const val OPEN_IN_APP_DISMISSALS_BEFORE_ASK = 3

/**
 * Per-site "don't offer to open in the app" bookkeeping on [BrowserSettings]. Sites match through
 * [ExternalAppResolver.areDomainAliases], so `www.` and known aliases share one entry.
 */
internal object OpenInAppExclusions {
    fun isExcluded(settings: BrowserSettings, domain: String): Boolean =
        settings.openInAppExcludedSites.any { same(it, domain) }

    fun dismissalCount(settings: BrowserSettings, domain: String): Int =
        settings.openInAppDismissCounts.entries.filter { same(it.key, domain) }.sumOf { it.value }

    /** True when the pill for [domain] should be replaced by the "don't offer" question. */
    fun shouldAsk(settings: BrowserSettings, domain: String): Boolean =
        !isExcluded(settings, domain) && dismissalCount(settings, domain) >= OPEN_IN_APP_DISMISSALS_BEFORE_ASK

    /** One more swipe-away of the pill; timeouts are never recorded. */
    fun recordDismissal(settings: BrowserSettings, domain: String): BrowserSettings {
        if (domain.isBlank() || isExcluded(settings, domain)) return settings
        val key = settings.openInAppDismissCounts.keys.firstOrNull { same(it, domain) } ?: normalize(domain)
        val counts = settings.openInAppDismissCounts
        return settings.copy(openInAppDismissCounts = counts + (key to (counts[key] ?: 0) + 1))
    }

    fun resetDismissals(settings: BrowserSettings, domain: String): BrowserSettings =
        settings.copy(openInAppDismissCounts = settings.openInAppDismissCounts.filterKeys { !same(it, domain) })

    fun exclude(settings: BrowserSettings, domain: String): BrowserSettings {
        val cleared = resetDismissals(settings, domain)
        if (domain.isBlank() || isExcluded(cleared, domain)) return cleared
        return cleared.copy(openInAppExcludedSites = cleared.openInAppExcludedSites + normalize(domain))
    }

    fun include(settings: BrowserSettings, site: String): BrowserSettings =
        settings.copy(openInAppExcludedSites = settings.openInAppExcludedSites.filterNot { same(it, site) }.toSet())

    private fun normalize(domain: String): String = domain.trim().removePrefix("www.").lowercase()

    private fun same(a: String, b: String): Boolean = a == b || ExternalAppResolver.areDomainAliases(a, b)
}
