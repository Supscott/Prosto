package com.jestk.browser.engine.adblock

import com.jestk.browser.engine.AdBlockerEngine
import com.jestk.browser.util.UrlSecurityUtils

/** One main-frame navigation of a tab, as `shouldOverrideUrlLoading` sees it. */
data class GuardedNavigation(
    val tabId: String,
    val url: String,
    /** The page the tab is on now (the opener URL for a fresh popup); null or `about:blank` when unknown. */
    val currentUrl: String?,
    /** The tap, or the opener's tap for a popup, behind this navigation. */
    val hasGesture: Boolean,
    val isServerRedirect: Boolean,
    /** The workspace of the tab (of the opener for a fresh popup); null when unknown. */
    val workspaceId: String? = null,
)

/** Which protections apply right now. */
data class GuardSettings(
    val adBlockActive: Boolean,
    val redirectProtectionActive: Boolean,
)

/** What the guard decided about a navigation. */
enum class GuardVerdict {
    ALLOW,

    /** The destination is on the ad list. */
    BLOCK_LISTED,

    /** A script jumped to another site without a gesture. */
    BLOCK_REDIRECT,

    /** The opener jumped away right after a tap opened a popup. */
    BLOCK_POPUNDER,
}

/**
 * Every decision about a main-frame navigation in one place: the ad host list, the cross-site
 * script redirect heuristic (`isAdRedirectProtectionEnabled`) and the popunder rule. On a site
 * the user allowlisted the ad list and the popunder rule are off; redirect protection is its
 * own setting and stays.
 * The caller does the side effects (stats, prompts).
 */
class NavigationGuard(
    private val popunderDetector: PopunderDetector,
    private val popupAllowlist: () -> PopupSiteAllowlist = { PopupSiteAllowlist.NONE },
    private val isSiteAllowlisted: (url: String?, workspaceId: String?) -> Boolean = { _, _ -> false },
    private val isListedAdUrl: (String) -> Boolean = AdBlockerEngine::shouldBlockNavigation,
    private val isSameSite: (String, String) -> Boolean = UrlSecurityUtils::isSameSite,
    private val extractHost: (String) -> String = UrlSecurityUtils::extractHost,
    private val isSearchHost: (String) -> Boolean = UrlSecurityUtils::isSearchEngineHost,
    private val isAuthHost: (String) -> Boolean = UrlSecurityUtils::isAuthOrSecurityHost,
    /**
     * Whether a listed URL that is let through gets the `$document` block page. True once the
     * native engine is loaded: the ad list check above is then the same `$document` check.
     */
    private val isBlockPageAvailable: () -> Boolean = AdBlockerEngine::isNativeReady,
) {
    fun evaluate(nav: GuardedNavigation, settings: GuardSettings): GuardVerdict {
        val url = nav.url
        if (!isWebUrl(url) || isStaticAsset(url)) return GuardVerdict.ALLOW
        val adBlockActive = settings.adBlockActive && !isSiteAllowlisted(nav.currentUrl, nav.workspaceId)
        if (!adBlockActive && !settings.redirectProtectionActive) return GuardVerdict.ALLOW

        // A tapped link to a listed host gets the block page, which explains and offers both
        // "Go back" and "Proceed anyway"; stopping it here as well would ask twice. A script jump
        // without a tap is still stopped here, so the user stays on the page they were reading.
        if (adBlockActive && isListedAdUrl(url) && !(nav.hasGesture && isBlockPageAvailable())) {
            return GuardVerdict.BLOCK_LISTED
        }

        val current = nav.currentUrl
        if (current.isNullOrBlank() || current == "about:blank") return GuardVerdict.ALLOW
        val currentHost = extractHost(current)
        val targetHost = extractHost(url)
        if (currentHost.isEmpty() || targetHost.isEmpty()) return GuardVerdict.ALLOW
        val crossSite = !isSameSite(current, url)

        // Sign-in and security hops are never ad hijacks; leaving a login page is part of the sign-in.
        val isAuth = isAuthHost(targetHost) || isLoginPage(current)

        if (adBlockActive && !nav.isServerRedirect && !isAuth &&
            !popupAllowlist().allows(current) &&
            popunderDetector.isPopunder(nav.tabId, crossSite, nav.hasGesture)
        ) {
            return GuardVerdict.BLOCK_POPUNDER
        }

        // A server redirect continues a navigation that was already allowed; only script-driven
        // cross-site jumps without a gesture are treated as ad hijacks.
        if (settings.redirectProtectionActive && !nav.hasGesture && !nav.isServerRedirect &&
            crossSite && !isSearchHost(currentHost) && !isAuth
        ) {
            return GuardVerdict.BLOCK_REDIRECT
        }
        return GuardVerdict.ALLOW
    }

    companion object {
        private val IDENTITY_PROVIDER_HOSTS = setOf(
            "accounts.google.com", "appleid.apple.com", "login.microsoftonline.com", "login.live.com",
            "oauth.vk.com", "id.vk.com", "passport.yandex.ru", "passport.yandex.com", "id.rambler.ru",
            "auth.mail.ru", "account.mail.ru",
        )

        private val STATIC_EXTENSIONS = setOf(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".ico", ".mp4", ".webm", ".mp3", ".ogg",
            ".m4a", ".m3u8", ".ts", ".css", ".js",
        )

        private val STATIC_PATH_PARTS = listOf("/image/", "/covers/", "/assets/")

        private fun isWebUrl(url: String): Boolean =
            url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

        private fun isStaticAsset(url: String): Boolean {
            val clean = url.substringBefore('?').substringBefore('#').lowercase()
            return STATIC_EXTENSIONS.any { clean.endsWith(it) } || STATIC_PATH_PARTS.any { clean.contains(it) }
        }

        /** Whether [url] is a sign-in page whose script redirect back to the site must not be blocked. */
        fun isLoginPage(url: String): Boolean {
            val rest = url.substringAfter("://", "")
            if (rest.isEmpty()) return false
            val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
            val host = authority.substringAfterLast('@').substringBefore(':').lowercase().removePrefix("www.")
            if (host.isEmpty()) return false
            if (host in IDENTITY_PROVIDER_HOSTS) return true
            val path = "/" + rest.substringAfter('/', "").substringBefore('?').substringBefore('#').lowercase()
            val isMultiPurposeHost = host == "github.com" || host == "facebook.com" || host == "m.facebook.com"
            return isMultiPurposeHost && (path.startsWith("/login") || path.contains("/oauth"))
        }
    }
}
