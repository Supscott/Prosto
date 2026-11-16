package com.jestk.browser.ui.viewmodel

import com.jestk.browser.util.UrlSecurityUtils

/**
 * Hosts a person chose to open despite a `$document` rule ("Proceed anyway"), per tab. Kept in
 * memory only: a host is allowed in that tab until it is closed or the app restarts, and never in
 * another tab. Separate from the app-wide allowance of the blocked-ad-navigation dialog.
 */
internal fun Map<String, Set<String>>.allowsDocument(tabId: String, url: String): Boolean {
    val host = UrlSecurityUtils.extractHost(url)
    return host.isNotEmpty() && this[tabId]?.contains(host) == true
}

internal fun Map<String, Set<String>>.withDocumentAllowed(tabId: String, url: String): Map<String, Set<String>> {
    val host = UrlSecurityUtils.extractHost(url)
    return if (host.isEmpty()) this else this + (tabId to (this[tabId].orEmpty() + host))
}
