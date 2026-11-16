package com.jestk.browser.engine.adblock

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/** The localised strings of the block page. */
internal class BlockPageText(val title: String, val body: String, val goBack: String, val proceed: String)

/**
 * The minimal local page served when a `$document` rule blocks a top-level navigation.
 * No script and no external resources: only inline CSS, escaped text and two links ("Go back",
 * "Proceed anyway") to [BlockPageActions] URLs that the WebView client intercepts. `no-store`
 * keeps it out of the back/forward cache, so going forward to it asks the policy again.
 *
 * Its `<title>` ends with [TITLE_MARKER], the only way to recognise a block page in the WebView
 * back/forward list (a history item exposes just URL and title); see [BlockPageHistory].
 */
internal object BlockPage {
    /** Invisible suffix of the block page title: neither collapsed nor stripped by the HTML title rules. */
    const val TITLE_MARKER = "⁠​"

    fun isBlockPageTitle(title: String?): Boolean = title?.endsWith(TITLE_MARKER) == true

    /** The title as people read it, without [TITLE_MARKER]. */
    fun displayTitle(title: String): String = title.removeSuffix(TITLE_MARKER)

    fun response(text: BlockPageText, host: String, token: String): WebResourceResponse =
        WebResourceResponse(
            "text/html",
            "UTF-8",
            200,
            "OK",
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(
                html(
                    text,
                    host,
                    goBackUrl = BlockPageActions.url(BlockPageAction.GO_BACK, token),
                    proceedUrl = BlockPageActions.url(BlockPageAction.PROCEED, token),
                ).toByteArray(Charsets.UTF_8),
            ),
        )

    internal fun html(text: BlockPageText, host: String, goBackUrl: String, proceedUrl: String): String =
        "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>${escape(text.title)}$TITLE_MARKER</title><style>$CSS</style></head><body><main>" +
            "<h1>${escape(text.title)}</h1><p>${escape(text.body)}</p><p class=\"h\">${escape(host)}</p>" +
            "<a class=\"b p\" href=\"${escape(goBackUrl)}\">${escape(text.goBack)}</a>" +
            "<a class=\"b s\" href=\"${escape(proceedUrl)}\">${escape(text.proceed)}</a>" +
            "</main></body></html>"

    private fun escape(text: String): String =
        buildString(text.length) {
            for (c in text) {
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(c)
                }
            }
        }

    private const val CSS =
        "body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;" +
            "font:16px sans-serif;background:#fff;color:#1c1b1f}" +
            "main{padding:24px;max-width:420px;text-align:center}h1{font-size:22px}" +
            ".h{color:#6b6b6b;word-break:break-all}" +
            ".b{display:block;margin:12px auto 0;padding:12px 20px;max-width:280px;border-radius:12px;" +
            "font-weight:600;text-decoration:none;box-sizing:border-box}" +
            ".p{background:#087e78;color:#fff}.s{color:#6b6b6b}" +
            "@media(prefers-color-scheme:dark){body{background:#121212;color:#e6e1e5}.h,.s{color:#9a9a9a}}"
}
