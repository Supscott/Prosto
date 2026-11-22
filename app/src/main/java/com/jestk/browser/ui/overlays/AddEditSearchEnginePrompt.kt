package com.jestk.browser.ui.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jestk.browser.R
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.components.DockPromptAction
import com.jestk.browser.ui.components.DockPromptCard
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue

/**
 * Form for adding or editing a custom search provider, shown on the shared [DockPromptCard] (it rides
 * above the keyboard). Save stays disabled until the title and URL template are valid and the
 * shortcut is not used by another engine.
 */
@Composable
fun AddEditSearchEnginePrompt(
    isDark: Boolean,
    existingEngines: List<SearchEngine>,
    engineToEdit: SearchEngine? = null,
    onDismiss: () -> Unit,
    onSave: (id: String?, title: String, shortcut: String?, urlTemplate: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by remember { mutableStateOf(engineToEdit?.displayName.orEmpty()) }
    var shortcut by remember { mutableStateOf(engineToEdit?.shortcut.orEmpty()) }
    var urlTemplate by remember { mutableStateOf(engineToEdit?.searchUrlTemplate ?: URL_TEMPLATE_PREFILL) }
    val focusRequester = remember { FocusRequester() }
    val paste = rememberPasteFromClipboard { urlTemplate = it }

    val trimmedTitle = title.trim()
    val trimmedShortcut = shortcut.trim()
    val trimmedUrl = urlTemplate.trim()
    val isShortcutDuplicate = remember(trimmedShortcut, existingEngines, engineToEdit) {
        trimmedShortcut.isNotBlank() && existingEngines.any { engine ->
            engine.id != engineToEdit?.id && engine.shortcut?.equals(trimmedShortcut, ignoreCase = true) == true
        }
    }
    val isTemplateMissing = isUrlTemplatePlaceholderMissing(trimmedUrl)
    val isUrlValid = trimmedUrl.isNotBlank() && trimmedUrl.contains("%s") &&
        (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://") || trimmedUrl.contains("."))
    val isFormValid = trimmedTitle.isNotBlank() && isUrlValid && !isShortcutDuplicate
    val previewFaviconUrl = remember(trimmedUrl) { FaviconManager.extractFallbackFaviconUrl(trimmedUrl) }

    fun submit() {
        if (!isFormValid) return
        onSave(engineToEdit?.id, trimmedTitle, trimmedShortcut.takeIf { it.isNotBlank() }, trimmedUrl)
    }

    LaunchedEffect(Unit) { if (engineToEdit == null) focusRequester.requestFocus() }

    DockPromptCard(
        title = stringResource(
            if (engineToEdit != null) R.string.search_engine_dialog_edit_title else R.string.search_engine_dialog_add_title,
        ),
        isDark = isDark,
        onDismiss = onDismiss,
        modifier = modifier,
        actions = listOf(
            DockPromptAction(stringResource(R.string.cancel), onDismiss),
            DockPromptAction(
                label = stringResource(if (engineToEdit != null) R.string.search_engine_save_action else R.string.add_action),
                onClick = ::submit,
                primary = true,
                enabled = isFormValid,
            ),
        ),
        content = {
            PromptTextField(
                value = title,
                onValueChange = { title = it },
                isDark = isDark,
                label = stringResource(R.string.search_engine_title_label),
                placeholder = stringResource(R.string.search_engine_title_hint),
                leadingIcon = {
                    if (!previewFaviconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = previewFaviconUrl,
                            contentDescription = stringResource(R.string.favicon_content_description),
                            modifier = Modifier.size(22.dp).clip(CircleShape),
                        )
                    } else {
                        Icon(Icons.Default.Title, null, tint = ProstoMutedSlate, modifier = Modifier.size(20.dp))
                    }
                },
                trailingIcon = { if (title.isNotEmpty()) ClearButton { title = "" } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
            Spacer(modifier = Modifier.height(12.dp))
            PromptTextField(
                value = shortcut,
                onValueChange = { shortcut = it.filter { char -> !char.isWhitespace() } },
                isDark = isDark,
                label = stringResource(R.string.search_engine_shortcut_label),
                placeholder = stringResource(R.string.search_engine_shortcut_hint),
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.ShortText, null, tint = ProstoMutedSlate, modifier = Modifier.size(20.dp))
                },
                trailingIcon = { if (shortcut.isNotEmpty()) ClearButton { shortcut = "" } },
                errorText = if (isShortcutDuplicate) stringResource(R.string.search_engine_shortcut_in_use, trimmedShortcut) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            PromptTextField(
                value = urlTemplate,
                onValueChange = { urlTemplate = it },
                isDark = isDark,
                label = stringResource(R.string.search_engine_url_template_label),
                placeholder = stringResource(R.string.search_engine_url_template_hint),
                leadingIcon = {
                    Icon(Icons.Default.Link, null, tint = ProstoMutedSlate, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (urlTemplate.length > 8) {
                        ClearButton { urlTemplate = URL_TEMPLATE_PREFILL }
                    } else {
                        IconButton(onClick = paste) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = stringResource(R.string.paste_action),
                                tint = ProstoBrandPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                errorText = if (isTemplateMissing) stringResource(R.string.search_engine_url_template_placeholder_missing) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            TemplateHint(isDark)
        },
    )
}

/** What a new engine's URL field starts with (and returns to when cleared). */
internal const val URL_TEMPLATE_PREFILL = "https://"

/**
 * True when the "must contain %s" error should show: only after the user typed past the
 * [URL_TEMPLATE_PREFILL], so a fresh form opens without a red error (PRO-87).
 */
internal fun isUrlTemplatePlaceholderMissing(trimmedUrl: String): Boolean =
    trimmedUrl.isNotBlank() && trimmedUrl != URL_TEMPLATE_PREFILL && !trimmedUrl.contains("%s")

@Composable
private fun TemplateHint(isDark: Boolean) {
    val textColor = if (isDark) DarkOnSurface else ProstoDarkText
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) DarkSurfaceVariant.copy(alpha = 0.5f) else ProstoPaleBlue.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = ProstoBrandPrimary,
                modifier = Modifier.size(18.dp).padding(top = 2.dp),
            )
            Text(
                text = stringResource(R.string.search_engine_template_hint),
                style = LocalProstoTypography.current.bodySm,
                color = textColor.copy(alpha = 0.85f),
            )
        }
    }
}
