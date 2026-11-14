package com.jestk.browser.engine.adblock

import java.net.URI

/**
 * Port of `preprocess()` in `tools/update_filterlists.py`: resolves uBlock Origin `!#include`
 * (relative to the including list, same origin only, depth-limited) and `!#if/!#else/!#endif`
 * blocks, because adblock-rust treats those directives as comments.
 *
 * Environment: `env_chromium`, `env_mobile` and `ext_ublock` are true, every other known flag is
 * false, an unknown flag fails the list. [fetch] returns the text of an include target.
 * Known difference: Python's `splitlines` also splits on a few exotic separators (VT, FF, NEL, U+2028);
 * filter lists do not contain them.
 */
internal class FilterListPreprocessor(
    private val requireHttps: Boolean = true,
    private val fetch: (String) -> String,
) {
    fun preprocess(text: String, baseUrl: String, depth: Int = 0): String {
        require(depth <= MAX_DEPTH) { "include nesting too deep" }
        val out = ArrayList<String>()
        val stack = ArrayList<BooleanArray>() // each frame: [branch_active]
        for (line in splitLines(text)) {
            val stripped = line.trim()
            val active = stack.all { it[0] }
            when {
                stripped.startsWith(IF) -> stack += booleanArrayOf(evaluate(stripped.substring(IF.length)))
                stripped == ELSE -> {
                    require(stack.isNotEmpty()) { "unbalanced !#else" }
                    stack.last()[0] = !stack.last()[0]
                }
                stripped == ENDIF -> {
                    require(stack.isNotEmpty()) { "unbalanced !#endif" }
                    stack.removeAt(stack.lastIndex)
                }
                !active -> Unit
                stripped.startsWith(INCLUDE) -> {
                    val target = resolve(baseUrl, stripped.substring(INCLUDE.length).trim())
                    out += preprocess(fetch(target), target, depth + 1).trimEnd('\n')
                }
                else -> out += line
            }
        }
        require(stack.isEmpty()) { "unbalanced !#if" }
        return out.joinToString("\n") + "\n"
    }

    private fun resolve(baseUrl: String, relative: String): String {
        val base = URI(baseUrl)
        val target = base.resolve(relative)
        require(!requireHttps || target.scheme == "https") { "include is not HTTPS: $target" }
        require(target.scheme == base.scheme && target.host == base.host && port(target) == port(base)) {
            "include is not same-origin: $target"
        }
        return target.toString()
    }

    private fun port(uri: URI): Int = if (uri.port >= 0) uri.port else if (uri.scheme == "https") HTTPS_PORT else HTTP_PORT

    private fun evaluate(condition: String): Boolean {
        val trimmed = condition.trim()
        val negate = trimmed.startsWith("!")
        val flag = trimmed.trimStart('!')
        require(flag in KNOWN_FLAGS) { "unknown preprocessor flag: $trimmed" }
        return (flag in TRUE_FLAGS) != negate
    }

    private fun splitLines(text: String): List<String> {
        val lines = text.lines()
        return if (lines.isNotEmpty() && lines.last().isEmpty()) lines.dropLast(1) else lines
    }

    companion object {
        private const val MAX_DEPTH = 5
        private const val HTTPS_PORT = 443
        private const val HTTP_PORT = 80
        private const val IF = "!#if "
        private const val ELSE = "!#else"
        private const val ENDIF = "!#endif"
        private const val INCLUDE = "!#include "
        private val TRUE_FLAGS = setOf("env_chromium", "env_mobile", "ext_ublock")
        private val KNOWN_FLAGS = TRUE_FLAGS + setOf(
            "env_firefox", "env_safari", "env_mv3", "ext_ubol", "ext_abp", "cap_html_filtering", "cap_ipaddress",
        )
    }
}
