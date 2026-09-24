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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
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
import com.jestk.browser.ui.theme.LocalSereneTypography
import com.jestk.browser.ui.theme.SereneAccent
import com.jestk.browser.ui.theme.SereneBorders
import com.jestk.browser.ui.theme.SereneBrandPrimary
import com.jestk.browser.ui.theme.SereneCanvasFloor
import com.jestk.browser.ui.theme.SereneDarkText
import com.jestk.browser.ui.theme.SereneMutedSlate
import com.jestk.browser.ui.theme.SerenePaleBlue
import com.jestk.browser.ui.theme.SereneShapes

/**
 * Modal dialog for adding a new site to Favorites.
 * Follows the Serene Canvas design system:
 * - Level 2 floating card container (SereneShapes.Card = 24dp)
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
    val clipboardManager = LocalClipboardManager.current

    val dialogBg = if (isDark) DarkSurface else Color.White
    val inputBg = if (isDark) DarkSurfaceVariant else SereneCanvasFloor
    val textColor = if (isDark) DarkOnSurface else SereneDarkText
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
        shape = SereneShapes.Card,
        color = dialogBg,
        border = SereneBorders.floatingBorder(isDark),
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
                            color = if (isDark) DarkSurfaceVariant else SerenePaleBlue,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = SereneBrandPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.add_favorite_dialog_title),
                        style = LocalSereneTypography.current.headlineSm,
                        color = textColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Input Field 1: Website Name
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = LocalSereneTypography.current.bodyMd,
                placeholder = {
                    Text(
                        text = stringResource(R.string.add_favorite_name_hint),
                        style = LocalSereneTypography.current.bodyMd,
                        color = SereneMutedSlate,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = SereneMutedSlate,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (title.isNotEmpty()) {
                        IconButton(onClick = { title = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = SereneMutedSlate,
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
                    focusedBorderColor = SereneAccent,
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
                textStyle = LocalSereneTypography.current.bodyMd,
                placeholder = {
                    Text(
                        text = stringResource(R.string.add_favorite_url_hint),
                        style = LocalSereneTypography.current.bodyMd,
                        color = SereneMutedSlate,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = SereneBrandPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (url.isNotEmpty()) {
                        IconButton(onClick = { url = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = SereneMutedSlate,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        val clipText = clipboardManager.getText()?.text
                        if (!clipText.isNullOrBlank()) {
                            IconButton(onClick = { url = clipText.trim() }) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = SereneBrandPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = inputBg,
                    unfocusedContainerColor = inputBg,
                    focusedBorderColor = SereneAccent,
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
                    shape = SereneShapes.Pill,
                    border = BorderStroke(1.dp, fieldBorderColor),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = LocalSereneTypography.current.labelMd.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = SereneMutedSlate,
                    )
                }

                Button(
                    onClick = { submit() },
                    enabled = url.isNotBlank(),
                    shape = SereneShapes.Pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SereneBrandPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = if (isDark) DarkSurfaceVariant else Color(0xFFE2E8F0),
                        disabledContentColor = SereneMutedSlate,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.add_favorite_add_button),
                        style = LocalSereneTypography.current.labelMd.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (url.isNotBlank()) Color.White else SereneMutedSlate,
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
