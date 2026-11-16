package com.jestk.browser.engine.adblock

import android.webkit.WebResourceRequest
import android.webkit.WebView
import java.security.MessageDigest
import java.security.SecureRandom

/** What the buttons of the block page ask for. */
enum class BlockPageAction(val path: String) {
    GO_BACK("back"),
    PROCEED("proceed"),
}

/**
 * The channel from the block page buttons to the app. Each button is a plain link to
 * `prosto-block://<action>/<token>`; the WebView client swallows every such URL in
 * `shouldOverrideUrlLoading`, so the page needs no script, no bridge object and no window global,
 * and normal pages see nothing. A navigation to the scheme acts only when it carries the
 * per-process random token (a site cannot guess it) and the WebView is showing a block page.
 */
internal object BlockPageActions {
    private const val PREFIX = "prosto-block://"

    private val processToken: String by lazy {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        bytes.joinToString("") { "%02x".format(it) }
    }

    fun url(action: BlockPageAction, token: String = processToken): String = "$PREFIX${action.path}/$token"

    /** True for every URL of the reserved scheme, valid or forged; such a navigation must never load. */
    fun isActionUrl(url: String): Boolean = url.startsWith(PREFIX, ignoreCase = true)

    /** The action of [url], or null when it is not an action URL or its token is wrong. */
    fun parse(url: String, token: String = processToken): BlockPageAction? {
        if (!isActionUrl(url)) return null
        val parts = url.substring(PREFIX.length).trimEnd('/').split('/')
        if (parts.size != 2) return null
        val action = BlockPageAction.entries.firstOrNull { it.path == parts[0].lowercase() } ?: return null
        val given = parts[1].toByteArray(Charsets.UTF_8)
        return if (MessageDigest.isEqual(given, token.toByteArray(Charsets.UTF_8))) action else null
    }

    /**
     * The action a main-frame navigation to [request] triggers, with the blocked URL it applies to,
     * or null (forged token, subframe, or the WebView is not showing a block page). Main thread only.
     */
    fun resolve(view: WebView?, request: WebResourceRequest): Pair<BlockPageAction, String>? {
        val action = parse(request.url?.toString().orEmpty()) ?: return null
        if (!request.isForMainFrame || view == null || !BlockPage.isBlockPageTitle(view.title)) return null
        return action to (view.url ?: return null)
    }
}
