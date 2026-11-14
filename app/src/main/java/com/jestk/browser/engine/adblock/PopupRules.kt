package com.jestk.browser.engine.adblock

/**
 * adblock-rust has no popup request type, so `$popup` rules never match. They are compiled into a
 * separate small engine instead: every rule whose options name `popup` is rewritten to `$document`
 * (its other type options dropped, everything else kept), and a popup's target URL is checked
 * there as a `DOCUMENT` request with the opener as source. Path rules, `domain=` and `@@`
 * exceptions keep working, and so do rules from downloaded lists.
 */
internal object PopupRules {
    private val TYPE_OPTIONS = setOf(
        "document", "doc", "subdocument", "frame", "script", "image", "stylesheet", "css", "font",
        "media", "object", "xmlhttprequest", "xhr", "websocket", "ping", "beacon", "other", "all",
        "popunder",
    )

    /** Modifiers that make no sense for a document check; such rules are left out. */
    private val UNSUPPORTED = listOf("redirect", "redirect-rule", "rewrite", "csp", "removeparam", "replace", "permissions", "header", "urltransform")

    fun extract(lists: List<String>): List<String> {
        val out = StringBuilder()
        for (text in lists) {
            text.lineSequence().forEach { raw -> rewrite(raw.trim())?.let { out.append(it).append('\n') } }
        }
        return if (out.isEmpty()) emptyList() else listOf(out.toString())
    }

    /** The `$document` form of one `$popup` rule, or null when the line is not a usable popup rule. */
    fun rewrite(line: String): String? {
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return null
        if (line.contains("##") || line.contains("#@#") || line.contains("#?#") || line.contains("#$#")) return null
        val dollar = line.lastIndexOf('$')
        if (dollar <= 0) return null
        val pattern = line.substring(0, dollar)
        val options = line.substring(dollar + 1).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (options.none { it.equals("popup", ignoreCase = true) }) return null
        if (options.any { option -> UNSUPPORTED.any { option.lowercase().startsWith(it) } }) return null
        val kept = options.filter { option ->
            val name = option.lowercase().removePrefix("~")
            name != "popup" && name !in TYPE_OPTIONS
        }
        return pattern + "$" + (listOf("document") + kept).joinToString(",")
    }
}
