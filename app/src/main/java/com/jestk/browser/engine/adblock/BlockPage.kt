package com.jestk.browser.engine.adblock

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * The minimal local page served when a `$document` rule blocks a top-level navigation.
 * No script and no external resources: only inline CSS and escaped text. `no-store` keeps it out
 * of the back/forward cache, so going forward to it asks the policy again.
 */
internal object BlockPage {
    fun response(title: String, body: String, host: String): WebResourceResponse =
        WebResourceResponse(
            "text/html",
            "UTF-8",
            200,
            "OK",
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(html(title, body, host).toByteArray(Charsets.UTF_8)),
        )

    internal fun html(title: String, body: String, host: String): String =
        "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>${escape(title)}</title><style>$CSS</style></head><body><main>" +
            "<h1>${escape(title)}</h1><p>${escape(body)}</p><p class=\"h\">${escape(host)}</p>" +
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
            "@media(prefers-color-scheme:dark){body{background:#121212;color:#e6e1e5}.h{color:#9a9a9a}}"
}
