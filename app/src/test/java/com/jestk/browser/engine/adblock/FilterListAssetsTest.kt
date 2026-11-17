package com.jestk.browser.engine.adblock

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** Guards the bundled filter lists against empty files and HTML error pages saved as lists. */
class FilterListAssetsTest {
    private val filtersRoot = File("src/main/assets/filters")
    // Plain JVM test: android's org.json is a stub here, and both files are flat lists of objects.
    private fun entries(path: String): List<String> = Regex("\\{[^{}]*\\}").findAll(File(path).readText()).map { it.value }.toList()
    private fun id(entry: String): String = Regex("\"id\":\\s*\"([^\"]+)\"").find(entry)!!.groupValues[1]

    /** Prosto's own hand-edited lists (`"local": true`): small by nature, never downloaded. */
    private val localIds: Set<String> =
        entries("../tools/filterlists/lists.json").filter { Regex("\"local\":\\s*true").containsMatchIn(it) }.map(::id).toSet()

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
            if (list.nameWithoutExtension !in localIds) assertThat(list.length()).isGreaterThan(1_000L)
            val first = firstLine(list)
            assertThat(first.startsWith("[Adblock") || first.startsWith("!")).isTrue()
        }
    }

    @Test
    fun localListsShipInTheApkButNotInTheRuntimeCatalog() {
        assertThat(localIds).contains("prosto")
        val catalogIds = entries("src/main/assets/adblock/lists.json").map(::id)
        for (id in localIds) {
            assertThat(File(filtersRoot, "standard/ads/$id.txt").exists()).isTrue()
            assertThat(catalogIds).doesNotContain(id)
        }
    }

    @Test
    fun strictListsAreNotBundled() {
        assertThat(File(filtersRoot, "strict").exists()).isFalse()
    }
}
