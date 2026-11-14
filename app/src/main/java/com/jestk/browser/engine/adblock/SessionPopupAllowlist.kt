package com.jestk.browser.engine.adblock

import com.jestk.browser.util.UrlSecurityUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * Opener hosts whose popups the user allowed from the "Popup blocked" pill, kept for the session
 * only. CB-14 replaces it behind [PopupSiteAllowlist] with the persisted per-site allowlist.
 */
class SessionPopupAllowlist(
    private val hostOf: (String) -> String = UrlSecurityUtils::extractHost,
) : PopupSiteAllowlist {
    private val hosts: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun allow(url: String) {
        val host = normalize(hostOf(url))
        if (host.isNotEmpty()) hosts += host
    }

    override fun allows(openerUrl: String?): Boolean {
        if (openerUrl.isNullOrBlank()) return false
        return normalize(hostOf(openerUrl)) in hosts
    }

    private fun normalize(host: String) = host.lowercase().removePrefix("www.")
}
