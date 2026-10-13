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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.Title
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.jestk.browser.R
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalSereneTypography
import com.jestk.browser.ui.theme.SereneBorders
import com.jestk.browser.ui.theme.SereneBrandPrimary
import com.jestk.browser.ui.theme.SereneCanvasFloor
import com.jestk.browser.ui.theme.SereneDarkText
import com.jestk.browser.ui.theme.SereneMutedSlate
import com.jestk.browser.ui.theme.SerenePaleBlue
import com.jestk.browser.ui.theme.SereneShapes

@Composable
fun AddEditSearchEngineDialog(
    isDark: Boolean,
    existingEngines: List<SearchEngine>,
    engineToEdit: SearchEngine? = null,
    onDismiss: () -> Unit,
    onSave: (id: String?, title: String, shortcut: String?, urlTemplate: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AddEditSearchEngineDialogContent(
            isDark = isDark,
            existingEngines = existingEngines,
            engineToEdit = engineToEdit,
            onDismiss = onDismiss,
            onSave = onSave,
            modifier = modifier,
        )
    }
}

@Composable
fun AddEditSearchEngineDialogContent(
    isDark: Boolean,
    existingEngines: List<SearchEngine>,
    engineToEdit: SearchEngine? = null,
    onDismiss: () -> Unit,
    onSave: (id: String?, title: String, shortcut: String?, urlTemplate: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by remember { mutableStateOf(engineToEdit?.displayName.orEmpty()) }
    var shortcut by remember { mutableStateOf(engineToEdit?.shortcut.orEmpty()) }
    var urlTemplate by remember { mutableStateOf(engineToEdit?.searchUrlTemplate ?: "https://") }

    val focusRequester = remember { FocusRequester() }
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    val dialogBg = if (isDark) DarkSurface else Color.White
    val inputBg = if (isDark) DarkSurfaceVariant else SereneCanvasFloor
    val textColor = if (isDark) DarkOnSurface else SereneDarkText
    val fieldBorderColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val errorColor = Color(0xFFEF4444)

    // Validation
    val trimmedTitle = title.trim()
    val trimmedShortcut = shortcut.trim()
    val trimmedUrl = urlTemplate.trim()

    val isShortcutDuplicate = remember(trimmedShortcut, existingEngines, engineToEdit) {
        if (trimmedShortcut.isBlank()) {
            false
        } else {
            existingEngines.any { engine ->
                engine.id != engineToEdit?.id &&
                    engine.shortcut?.equals(trimmedShortcut, ignoreCase = true) == true
            }
        }
    }

    val isUrlValid = trimmedUrl.isNotBlank() && trimmedUrl.contains("%s") &&
        (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://") || trimmedUrl.contains("."))

    val isFormValid = trimmedTitle.isNotBlank() && isUrlValid && !isShortcutDuplicate

    val previewFaviconUrl = remember(trimmedUrl) {
        FaviconManager.extractFallbackFaviconUrl(trimmedUrl)
    }

    fun submit() {
        if (!isFormValid) return
        onSave(
            engineToEdit?.id,
            trimmedTitle,
            trimmedShortcut.takeIf { it.isNotBlank() },
            trimmedUrl
        )
    }

    LaunchedEffect(Unit) {
        if (engineToEdit == null) {
            focusRequester.requestFocus()
        }
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header icon
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (isDark) DarkSurfaceVariant else SerenePaleBlue),
                contentAlignment = Alignment.Center,
            ) {
                if (!previewFaviconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = previewFaviconUrl,
                        contentDescription = "Favicon",
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = if (engineToEdit != null) Icons.Default.Search else Icons.Default.Public,
                        contentDescription = null,
                        tint = SereneBrandPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Title
            Text(
                text = if (engineToEdit != null) "Edit Search Provider" else "Add Search Provider",
                style = LocalSereneTypography.current.headlineMd.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                ),
                color = textColor,
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Field 1: Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.search_engine_title_label)) },
                placeholder = { Text("e.g. YouTube, Wikipedia") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Title,
                        contentDescription = null,
                        tint = SereneMutedSlate,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (title.isNotEmpty()) {
                        IconButton(onClick = { title = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = SereneMutedSlate,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = inputBg,
                    unfocusedContainerColor = inputBg,
                    focusedBorderColor = SereneBrandPrimary,
                    unfocusedBorderColor = fieldBorderColor,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Field 2: Shortcut (optional)
            OutlinedTextField(
                value = shortcut,
                onValueChange = { shortcut = it.filter { char -> !char.isWhitespace() } },
                label = { Text(stringResource(R.string.search_engine_shortcut_label)) },
                placeholder = { Text("e.g. y, yt, w") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShortText,
                        contentDescription = null,
                        tint = SereneMutedSlate,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (shortcut.isNotEmpty()) {
                        IconButton(onClick = { shortcut = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = SereneMutedSlate,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                isError = isShortcutDuplicate,
                supportingText = {
                    if (isShortcutDuplicate) {
                        Text(
                            text = "Shortcut '$trimmedShortcut' is already in use",
                            color = errorColor,
                            style = LocalSereneTypography.current.labelSm,
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = inputBg,
                    unfocusedContainerColor = inputBg,
                    focusedBorderColor = SereneBrandPrimary,
                    unfocusedBorderColor = fieldBorderColor,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Field 3: URL Template
            OutlinedTextField(
                value = urlTemplate,
                onValueChange = { urlTemplate = it },
                label = { Text(stringResource(R.string.search_engine_url_template_label)) },
                placeholder = { Text("https://example.com/search?q=%s") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = SereneMutedSlate,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        if (urlTemplate.length > 8) {
                            IconButton(onClick = { urlTemplate = "https://" }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = SereneMutedSlate,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val clip = clipboard.getClipEntry()
                                    val clipText = clip?.clipData?.let {
                                        if (it.itemCount > 0) it.getItemAt(0).text?.toString() else null
                                    }
                                    if (!clipText.isNullOrBlank()) {
                                        urlTemplate = clipText.trim()
                                    }
                                }
                            },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste",
                                tint = SereneBrandPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                isError = trimmedUrl.isNotBlank() && !trimmedUrl.contains("%s"),
                supportingText = {
                    if (trimmedUrl.isNotBlank() && !trimmedUrl.contains("%s")) {
                        Text(
                            text = "URL must contain %s to replace with the search query",
                            color = errorColor,
                            style = LocalSereneTypography.current.labelSm,
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = inputBg,
                    unfocusedContainerColor = inputBg,
                    focusedBorderColor = SereneBrandPrimary,
                    unfocusedBorderColor = fieldBorderColor,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Hint Text Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) DarkSurfaceVariant.copy(alpha = 0.5f) else SerenePaleBlue.copy(alpha = 0.5f),
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
                        tint = SereneBrandPrimary,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 2.dp),
                    )
                    Text(
                        text = "Write the search url and replace the query with %s.\n\nFor example: https://youtube.com/search?q=%s",
                        style = LocalSereneTypography.current.bodySm.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = if (isDark) DarkOnSurface.copy(alpha = 0.85f) else SereneDarkText.copy(alpha = 0.85f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isDark) DarkOnSurface else SereneDarkText,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, fieldBorderColor),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                ) {
                    Text(
                        text = "Cancel",
                        style = LocalSereneTypography.current.labelMd.copy(fontWeight = FontWeight.SemiBold),
                    )
                }

                Button(
                    onClick = { submit() },
                    enabled = isFormValid,
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SereneBrandPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1),
                        disabledContentColor = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                ) {
                    Text(
                        text = if (engineToEdit != null) "Save" else "Add",
                        style = LocalSereneTypography.current.labelMd.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Preview(name = "Add Provider Light")
@Composable
internal fun AddEditSearchEngineDialogLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        Box(
            modifier = Modifier
                .background(Color(0xFFE2E8F0))
                .padding(16.dp),
        ) {
            AddEditSearchEngineDialogContent(
                isDark = false,
                existingEngines = SearchEngine.DEFAULT_ENGINES,
                onDismiss = {},
                onSave = { _, _, _, _ -> },
            )
        }
    }
}

@Preview(name = "Add Provider Dark")
@Composable
internal fun AddEditSearchEngineDialogDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .background(Color(0xFF0F172A))
                .padding(16.dp),
        ) {
            AddEditSearchEngineDialogContent(
                isDark = true,
                existingEngines = SearchEngine.DEFAULT_ENGINES,
                onDismiss = {},
                onSave = { _, _, _, _ -> },
            )
        }
    }
}
