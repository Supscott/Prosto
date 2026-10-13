package com.jestk.browser.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class DomainArchitectureTest {

    @Test
    fun domainLayer_doesNotImportUiFrameworks() {
        // Resolve project root or domain directory
        val domainDir = File("src/main/java/com/jestk/browser/domain")
            .takeIf { it.exists() }
            ?: File("app/src/main/java/com/jestk/browser/domain")

        assertThat(domainDir.exists()).isTrue()

        val forbiddenImportPrefixes = listOf(
            "import android.view.",
            "import android.widget.",
            "import android.app.",
            "import androidx.compose."
        )

        val violations = mutableListOf<String>()

        domainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    forbiddenImportPrefixes.forEach { forbidden ->
                        if (trimmed.startsWith(forbidden)) {
                            violations.add("${file.name}:${index + 1}: $trimmed")
                        }
                    }
                }
            }

        assertThat(violations).isEmpty()
    }
}
