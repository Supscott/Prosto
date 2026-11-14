package com.jestk.browser.engine.adblock

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class FilterListPreprocessorTest {
    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader!!.getResourceAsStream("filterlists/$name")).use { String(it.readBytes()) }

    private val parityFiles = mapOf(
        "https://lists.example/dir/parts/extra.txt" to "parity-extra.txt",
        "https://lists.example/dir/parts/deeper.txt" to "parity-deeper.txt",
        "https://lists.example/dir/https-root-relative.txt" to "parity-root.txt",
    )

    @Test
    fun output_matchesThePythonPreprocessorOnTheSample() {
        val preprocessor = FilterListPreprocessor { url -> resource(parityFiles.getValue(url)) }

        val out = preprocessor.preprocess(resource("parity-input.txt"), "https://lists.example/dir/list.txt")

        // parity-expected.txt is the output of tools/update_filterlists.py preprocess() on the same files.
        assertThat(out).isEqualTo(resource("parity-expected.txt"))
    }

    @Test
    fun unknownFlag_failsTheList() {
        val preprocessor = FilterListPreprocessor { "" }

        assertThrows(IllegalArgumentException::class.java) {
            preprocessor.preprocess("!#if some_new_flag\n||a^\n!#endif\n", "https://x.example/l.txt")
        }
    }

    @Test
    fun unbalancedIf_failsTheList() {
        val preprocessor = FilterListPreprocessor { "" }

        assertThrows(IllegalArgumentException::class.java) {
            preprocessor.preprocess("!#if env_chromium\n||a^\n", "https://x.example/l.txt")
        }
    }

    @Test
    fun crossOriginInclude_isRejected() {
        val preprocessor = FilterListPreprocessor { "||x^\n" }

        assertThrows(IllegalArgumentException::class.java) {
            preprocessor.preprocess("!#include https://evil.example/x.txt\n", "https://x.example/l.txt")
        }
    }

    @Test
    fun httpInclude_isRejectedWhenHttpsIsRequired() {
        val preprocessor = FilterListPreprocessor { "||x^\n" }

        assertThrows(IllegalArgumentException::class.java) {
            preprocessor.preprocess("!#include http://x.example/x.txt\n", "https://x.example/l.txt")
        }
    }

    @Test
    fun includeChain_isDepthLimited() {
        val preprocessor = FilterListPreprocessor { "!#include again.txt\n" }

        assertThrows(IllegalArgumentException::class.java) {
            preprocessor.preprocess("!#include again.txt\n", "https://x.example/l.txt")
        }
    }

    @Test
    fun inactiveIncludes_areNotFetched() {
        val fetched = mutableListOf<String>()
        val preprocessor = FilterListPreprocessor { fetched += it; "" }

        preprocessor.preprocess("!#if env_firefox\n!#include a.txt\n!#endif\n", "https://x.example/l.txt")

        assertThat(fetched).isEmpty()
    }
}
