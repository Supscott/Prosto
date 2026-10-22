package com.jestk.browser.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jestk.browser.R
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue
import com.jestk.browser.ui.theme.ProstoShapes

/**
 * Modal dialog for adding a new site to Favorites.
 * Follows the Prosto Canvas design system:
 * - Level 2 floating card container (ProstoShapes.Card = 24dp)
 * - Quick-paste action for website URL
 * - Automatic protocol normalization (prepends https:// when missing)
 * - Crisp solid surfaces with NO BLUR.
 */
@Composable
fun AddFavoriteDialog(
    isDark: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (title: String, url: String) -> Unit,
    initialUrl: String = "",
    initialTitle: String = "",
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AddFavoriteDialogContent(
            isDark = isDark,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            initialUrl = initialUrl,
            initialTitle = initialTitle,
            modifier = modifier,
        )
    }
}

@Composable
fun AddFavoriteDialogContent(
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
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    val dialogBg = if (isDark) DarkSurface else Color.White
    val inputBg = if (isDark) DarkSurfaceVariant else ProstoCanvasFloor
    val textColor = if (isDark) DarkOnSurface else ProstoDarkText
    val fieldBorderColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .padding(vertical = 24.dp),
        shape = ProstoShapes.Card,
        color = dialogBg,
        border = ProstoBorders.floatingBorder(isDark),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header: Icon + Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (isDark) DarkSurfaceVariant else ProstoPaleBlue,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = ProstoBrandPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.add_favorite_dialog_title),
                        style = LocalProstoTypography.current.headlineSm,
                        color = textColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Input Field 1: Website Name
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = LocalProstoTypography.current.bodyMd,
                placeholder = {
                    Text(
                        text = stringResource(R.string.add_favorite_name_hint),
                        style = LocalProstoTypography.current.bodyMd,
                        color = ProstoMutedSlate,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = ProstoMutedSlate,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (title.isNotEmpty()) {
                        IconButton(onClick = { title = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.clear_action),
                                tint = ProstoMutedSlate,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = inputBg,
                    unfocusedContainerColor = inputBg,
                    focusedBorderColor = fieldBorderColor,
                    unfocusedBorderColor = fieldBorderColor,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Input Field 2: Website URL (Required)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                textStyle = LocalProstoTypography.current.bodyMd,
                placeholder = {
                    Text(
                        text = stringResource(R.string.add_favorite_url_hint),
                        style = LocalProstoTypography.current.bodyMd,
                        color = ProstoMutedSlate,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = ProstoBrandPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (url.isNotEmpty()) {
                        IconButton(onClick = { url = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.clear_action),
                                tint = ProstoMutedSlate,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val clip = clipboard.getClipEntry()
                                    val clipText = clip?.clipData?.let {
                                        if (it.itemCount > 0) it.getItemAt(0).text?.toString() else null
                                    }
                                    if (!clipText.isNullOrBlank()) {
                                        url = clipText.trim()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = stringResource(R.string.paste_action),
                                tint = ProstoBrandPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = inputBg,
                    unfocusedContainerColor = inputBg,
                    focusedBorderColor = fieldBorderColor,
                    unfocusedBorderColor = fieldBorderColor,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { submit() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = ProstoShapes.Pill,
                    border = BorderStroke(1.dp, fieldBorderColor),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = LocalProstoTypography.current.labelMd.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = ProstoMutedSlate,
                    )
                }

                Button(
                    onClick = { submit() },
                    enabled = url.isNotBlank(),
                    shape = ProstoShapes.Pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProstoBrandPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = if (isDark) DarkSurfaceVariant else Color(0xFFE2E8F0),
                        disabledContentColor = ProstoMutedSlate,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.add_favorite_add_button),
                        style = LocalProstoTypography.current.labelMd.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (url.isNotBlank()) Color.White else ProstoMutedSlate,
                    )
                }
            }
        }
    }
}

@Preview(name = "AddFavoriteDialog Light", showBackground = true)
@Composable
fun AddFavoriteDialogLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        AddFavoriteDialogContent(
            isDark = false,
            onDismiss = {},
            onConfirm = { _, _ -> },
            initialTitle = "GitHub",
            initialUrl = "https://github.com",
        )
    }
}

@Preview(name = "AddFavoriteDialog Dark", showBackground = true)
@Composable
fun AddFavoriteDialogDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        AddFavoriteDialogContent(
            isDark = true,
            onDismiss = {},
            onConfirm = { _, _ -> },
            initialTitle = "GitHub",
            initialUrl = "https://github.com",
        )
    }
}
