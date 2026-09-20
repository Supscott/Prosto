package com.jestk.browser.ui.overlays

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jestk.browser.R
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LiquidBorders
import com.jestk.browser.ui.theme.LiquidShapes
import com.jestk.browser.ui.theme.PastelBlue

/**
 * Modal dialog for adding a new site to Favorites.
 * Follows the Liquid Pastel Sunset design system:
 * - Floating capsule container (LiquidShapes.Capsule) with specular rim border
 * - Quick-paste action for website URL
 * - Automatic protocol normalization (prepends https:// when missing)
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

    val dialogBg = if (isDark) Color(0xFF242021) else Color(0xFFFFF8F8)
    val inputBg = if (isDark) Color(0xFF2E292A) else Color(0xFFF3ECED)

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
        // Focus the URL field if empty, or title if URL is already populated
        focusRequester.requestFocus()
    }
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = LiquidShapes.Capsule,
            color = dialogBg,
            border = LiquidBorders.specularBorder(isDark = isDark, width = 1.dp),
            shadowElevation = 14.dp,
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
                                color = if (isDark) Color(0xFF384353) else PastelBlue.copy(alpha = 0.25f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.add_favorite_dialog_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Input Field 1: Website Name
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.add_favorite_name_hint),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.outline,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        if (title.isNotEmpty()) {
                            IconButton(onClick = { title = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = inputBg,
                        unfocusedContainerColor = inputBg,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Input Field 2: Website URL (Required)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.add_favorite_url_hint),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.outline,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        if (url.isNotEmpty()) {
                            IconButton(onClick = { url = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.outline,
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
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = inputBg,
                        unfocusedContainerColor = inputBg,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
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
                        shape = LiquidShapes.ButtonPill,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Button(
                        onClick = { submit() },
                        enabled = url.isNotBlank(),
                        shape = LiquidShapes.ButtonPill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.add_favorite_add_button),
                            fontWeight = FontWeight.Bold,
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
