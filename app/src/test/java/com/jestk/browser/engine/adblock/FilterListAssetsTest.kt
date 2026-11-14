package com.jestk.browser.engine.adblock

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** Guards the bundled filter lists against empty files and HTML error pages saved as lists. */
class FilterListAssetsTest {
    private val filtersRoot = File("src/main/assets/filters")

    private fun listsIn(preset: String): List<File> =
        File(filtersRoot, preset).walkTopDown().filter { it.isFile && it.extension == "txt" }.toList()

    private fun firstLine(file: File): String =
        file.bufferedReader().use { reader -> reader.readLine().orEmpty() }

    @Test
    fun standardListsExistForBothEngines() {
        val engines = File(filtersRoot, "standard").listFiles().orEmpty().map { it.name }
        assertThat(engines).containsAtLeast("ads", "privacy")
    }

    @Test
    fun everyStandardListIsNonEmptyAndStartsWithAHeaderOrComment() {
        val lists = listsIn("standard")
        assertThat(lists).isNotEmpty()
        for (list in lists) {
            assertThat(list.length()).isGreaterThan(1_000L)
            val first = firstLine(list)
            assertThat(first.startsWith("[Adblock") || first.startsWith("!")).isTrue()
        }
    }

    @Test
    fun strictListsAreNotBundled() {
        assertThat(File(filtersRoot, "strict").exists()).isFalse()
    }
}
