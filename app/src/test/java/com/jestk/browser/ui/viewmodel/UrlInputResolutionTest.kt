package com.jestk.browser.ui.viewmodel

import com.google.common.truth.Truth.assertWithMessage
import com.jestk.browser.domain.model.SearchEngine
import org.junit.Test

class UrlInputResolutionTest {
    private val engines = SearchEngine.DEFAULT_ENGINES
    private val default = SearchEngine.GOOGLE

    @Test
    fun resolveUrlInput_table() {
        val cases = listOf(
            "" to ResolvedUrlInput(""),
            "  about:blank " to ResolvedUrlInput(""),
            "https://example.com/a" to ResolvedUrlInput("https://example.com/a"),
            " github.com " to ResolvedUrlInput("https://github.com"),
            "d kotlin flow" to ResolvedUrlInput(SearchEngine.DUCKDUCKGO.buildSearchUrl("kotlin flow"), SearchEngine.DUCKDUCKGO),
            "D kotlin" to ResolvedUrlInput(SearchEngine.DUCKDUCKGO.buildSearchUrl("kotlin"), SearchEngine.DUCKDUCKGO),
            "zz kotlin" to ResolvedUrlInput(default.buildSearchUrl("zz kotlin")),
            "kotlin" to ResolvedUrlInput(default.buildSearchUrl("kotlin")),
        )
        for ((input, expected) in cases) {
            assertWithMessage("input '$input'").that(resolveUrlInput(input, engines, default)).isEqualTo(expected)
        }
    }
}
