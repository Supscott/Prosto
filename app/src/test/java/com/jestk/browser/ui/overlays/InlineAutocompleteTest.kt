package com.jestk.browser.ui.overlays

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InlineAutocompleteTest {

    private val match: (String) -> String? = { q -> "github.com".takeIf { it.startsWith(q, ignoreCase = true) } }

    private fun typed(text: String) = TextFieldValue(text, TextRange(text.length))

    private fun typeEachChar(word: String): List<InlineAutocompleteInput> {
        var input = InlineAutocompleteInput(TextFieldValue(""))
        return word.indices.map { i ->
            input = input.onEdit(typed(word.substring(0, i + 1)), match)
            input
        }
    }

    @Test
    fun typing_neverWritesTheCompletionIntoTheField() {
        // The IME only ever sees what was typed, so Compose never restarts its input connection.
        val steps = typeEachChar("github")
        steps.forEachIndexed { i, step ->
            assertThat(step.value.text).isEqualTo("github".substring(0, i + 1))
            assertThat(step.completion).isEqualTo("github.com")
        }
        assertThat(steps.last().suffix).isEqualTo(".com")
        assertThat(steps.last().submitText).isEqualTo("github.com")
    }

    @Test
    fun typingPastTheMatch_dropsTheCompletion() {
        val input = typeEachChar("gix").last()
        assertThat(input.completion).isNull()
        assertThat(input.submitText).isEqualTo("gix")
    }

    @Test
    fun backspace_dropsTheCompletionAndDeletesOneCharacter() {
        val afterGit = typeEachChar("git").last()
        val afterBackspace = afterGit.onEdit(typed("gi"), match)
        assertThat(afterBackspace.value.text).isEqualTo("gi")
        assertThat(afterBackspace.completion).isNull()
        // Typing forward again brings the completion back.
        assertThat(afterBackspace.onEdit(typed("git"), match).completion).isEqualTo("github.com")
    }

    @Test
    fun cursorMovedAwayFromTheEnd_dropsTheCompletion() {
        val afterGit = typeEachChar("git").last()
        val moved = afterGit.onEdit(TextFieldValue("git", TextRange(1)), match)
        assertThat(moved.completion).isNull()
        val stillAtEnd = afterGit.onEdit(TextFieldValue("git", TextRange(3)), match)
        assertThat(stillAtEnd.completion).isEqualTo("github.com")
    }

    @Test
    fun typingOverASelectedPrefill_completes() {
        val prefilled = InlineAutocompleteInput(TextFieldValue("example.org", TextRange(0, 11)))
        val replaced = prefilled.onEdit(typed("g"), match)
        assertThat(replaced.completion).isEqualTo("github.com")
    }

    @Test
    fun matcherResultThatDoesNotExtendTheQuery_isIgnored() {
        val input = InlineAutocompleteInput(TextFieldValue("")).onEdit(typed("hub")) { "github.com" }
        assertThat(input.completion).isNull()
    }

    @Test
    fun transformation_appendsTheSuffixAndKeepsTheCursorInTheTypedText() {
        val transformed = InlineCompletionTransformation(".com", Color.Blue).filter(AnnotatedString("github"))
        assertThat(transformed.text.text).isEqualTo("github.com")
        assertThat(transformed.offsetMapping.originalToTransformed(6)).isEqualTo(6)
        assertThat(transformed.offsetMapping.transformedToOriginal(10)).isEqualTo(6)
    }
}
