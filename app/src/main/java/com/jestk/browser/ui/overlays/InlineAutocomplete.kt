package com.jestk.browser.ui.overlays

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle

/**
 * Address bar input with an inline completion (`gi` + `thub.com`).
 *
 * The completion is never written into the text field: [value] holds exactly what the keyboard
 * typed and the completion is only drawn after it ([InlineCompletionTransformation]). Writing it
 * into the field made Compose restart the IME input connection on every keystroke, and a key
 * pressed during the restart was lost (`github.com` became `gihub.com`).
 *
 * @property completion the full completed text (starts with the typed text, case-insensitively),
 *   or `null` when nothing is suggested.
 */
internal data class InlineAutocompleteInput(
    val value: TextFieldValue,
    val completion: String? = null,
) {
    /** What Go submits: the completion when one is shown, otherwise the typed text. */
    val submitText: String get() = completion ?: value.text

    /** The part drawn after the typed text. */
    val suffix: String get() = completion?.substring(value.text.length).orEmpty()
}

/**
 * Applies one edit from the text field. Typing forward with the cursor at the end looks up a new
 * completion with [findMatch]; deleting or moving the cursor away from the end drops it.
 */
internal fun InlineAutocompleteInput.onEdit(
    newValue: TextFieldValue,
    findMatch: (String) -> String?,
): InlineAutocompleteInput {
    val oldValue = value
    val newText = newValue.text
    val cursorAtEnd = newValue.selection.collapsed && newValue.selection.end == newText.length
    if (newText == oldValue.text) {
        return InlineAutocompleteInput(newValue, completion.takeIf { cursorAtEnd })
    }
    val isDeletion = if (oldValue.selection.collapsed) {
        newText.length < oldValue.text.length
    } else {
        newText == oldValue.text.removeRange(oldValue.selection.min, oldValue.selection.max)
    }
    if (isDeletion || !cursorAtEnd) return InlineAutocompleteInput(newValue)
    val match = findMatch(newText)?.takeIf {
        it.length > newText.length && it.startsWith(newText, ignoreCase = true)
    }
    return InlineAutocompleteInput(newValue, match)
}

/** Draws [suffix] after the typed text, highlighted like a selection; the cursor stays before it. */
internal class InlineCompletionTransformation(
    private val suffix: String,
    private val highlight: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (suffix.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val typedLength = text.length
        val shown = buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(background = highlight)) { append(suffix) }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset
            override fun transformedToOriginal(offset: Int): Int = offset.coerceAtMost(typedLength)
        }
        return TransformedText(shown, mapping)
    }

    override fun equals(other: Any?): Boolean =
        other is InlineCompletionTransformation && other.suffix == suffix && other.highlight == highlight

    override fun hashCode(): Int = 31 * suffix.hashCode() + highlight.hashCode()
}
