package com.jestk.browser.ui.overlays

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoDestructive
import com.jestk.browser.ui.theme.ProstoMutedSlate

private val FieldErrorColor = ProstoDestructive

/** Returns a callback that reads the clipboard text and passes the trimmed, non-blank text to [onText]. */
@Composable
internal fun rememberPasteFromClipboard(onText: (String) -> Unit): () -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(onText)
    return {
        scope.launch {
            val clipText = clipboard.getClipEntry()?.clipData?.let {
                if (it.itemCount > 0) it.getItemAt(0).text?.toString() else null
            }
            if (!clipText.isNullOrBlank()) latest.value(clipText.trim())
        }
    }
}

/**
 * Single-line field used by the forms on the dock prompt card. Label, placeholder and text colours
 * are set explicitly so they stay readable on the dark card surface.
 */
@Composable
internal fun PromptTextField(
    value: String,
    onValueChange: (String) -> Unit,
    isDark: Boolean,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    errorText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val typography = LocalProstoTypography.current
    val inputBg = if (isDark) DarkSurfaceVariant else ProstoCanvasFloor
    val textColor = if (isDark) DarkOnSurface else ProstoDarkText
    val hintColor = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate
    val borderColor = if (isDark) Color(0xFF475569) else Color(0xFFE2E8F0)
    Column(modifier = Modifier.fillMaxWidth()) {
    if (label != null) {
        Text(
            text = label,
            style = typography.bodySm,
            color = hintColor,
            modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
        )
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = typography.bodyMd,
        placeholder = { Text(text = placeholder, style = typography.bodyMd, color = hintColor) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = errorText != null,
        supportingText = errorText?.let { { Text(text = it, color = FieldErrorColor, style = typography.labelSm) } },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = inputBg,
            unfocusedContainerColor = inputBg,
            focusedBorderColor = ProstoBrandPrimary,
            unfocusedBorderColor = borderColor,
            focusedTextColor = textColor,
            unfocusedTextColor = textColor,
            focusedPlaceholderColor = hintColor,
            unfocusedPlaceholderColor = hintColor,
        ),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier,
    )
    }
}
