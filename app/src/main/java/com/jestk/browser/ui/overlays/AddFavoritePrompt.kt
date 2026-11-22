package com.jestk.browser.ui.overlays

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.ui.components.DockPromptAction
import com.jestk.browser.ui.components.DockPromptCard
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoMutedSlate

/**
 * Form for adding a new site to Favorites, shown on the shared [DockPromptCard] (it rides above the
 * keyboard). A missing protocol is normalised to https://, an empty name is derived from the host.
 */
@Composable
fun AddFavoritePrompt(
    isDark: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (title: String, url: String) -> Unit,
    initialUrl: String = "",
    initialTitle: String = "",
    modifier: Modifier = Modifier,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var url by remember { mutableStateOf(initialUrl) }
    val focusRequester = remember { FocusRequester() }
    val paste = rememberPasteFromClipboard { url = it }

    fun submit() {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return
        val normalizedUrl = when {
            trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://") -> trimmedUrl
            else -> "https://$trimmedUrl"
        }
        val derivedTitle = title.trim().ifBlank {
            normalizedUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')
        }
        onConfirm(derivedTitle, normalizedUrl)
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    DockPromptCard(
        title = stringResource(R.string.add_favorite_dialog_title),
        isDark = isDark,
        onDismiss = onDismiss,
        modifier = modifier,
        actions = listOf(
            DockPromptAction(stringResource(R.string.cancel), onDismiss),
            DockPromptAction(
                label = stringResource(R.string.add_favorite_add_button),
                onClick = ::submit,
                primary = true,
                enabled = url.isNotBlank(),
            ),
        ),
        content = {
            PromptTextField(
                value = title,
                onValueChange = { title = it },
                isDark = isDark,
                placeholder = stringResource(R.string.add_favorite_name_hint),
                leadingIcon = {
                    Icon(Icons.Default.BookmarkBorder, null, tint = ProstoMutedSlate, modifier = Modifier.size(20.dp))
                },
                trailingIcon = { if (title.isNotEmpty()) ClearButton { title = "" } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            PromptTextField(
                value = url,
                onValueChange = { url = it },
                isDark = isDark,
                placeholder = stringResource(R.string.add_favorite_url_hint),
                leadingIcon = {
                    Icon(Icons.Default.Language, null, tint = ProstoBrandPrimary, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (url.isNotEmpty()) {
                        ClearButton { url = "" }
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
    )
}

@Composable
internal fun ClearButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.Clear,
            contentDescription = stringResource(R.string.clear_action),
            tint = ProstoMutedSlate,
            modifier = Modifier.size(18.dp),
        )
    }
}
