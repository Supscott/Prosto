package com.jestk.browser.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.domain.model.TabFolder
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
import com.jestk.browser.ui.theme.ProstoShapes

/**
 * Drag handle pill matching Prosto Canvas aesthetic: 36.dp x 4.dp with ProstoShapes.Pill shape.
 */
@Composable
fun FolderDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 12.dp, bottom = 8.dp)
            .size(width = 36.dp, height = 4.dp)
            .background(
                color = ProstoMutedSlate.copy(alpha = 0.4f),
                shape = ProstoShapes.Pill,
            ),
    )
}

/**
 * Modal BottomSheet for creating and editing tab folders with live indicator preview,
 * text input with auto-focus and clear button, and an 8-color palette selector.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderEditBottomSheet(
    isDark: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, color: Int) -> Unit,
    modifier: Modifier = Modifier,
    initialTitle: String = "",
    initialColor: Int = TabFolder.PALETTE.first(),
    isEditing: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = ProstoShapes.BottomSheet,
        containerColor = if (isDark) DarkSurface else Color.White,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = { FolderDragHandle() },
        modifier = modifier.border(
            border = ProstoBorders.cardBorder(isDark),
            shape = ProstoShapes.BottomSheet,
        ),
    ) {
        FolderEditBottomSheetContent(
            isDark = isDark,
            initialTitle = initialTitle,
            initialColor = initialColor,
            isEditing = isEditing,
            onDismiss = onDismiss,
            onSave = onSave,
        )
    }
}

/**
 * Content container of FolderEditBottomSheet, extracted for reusability and preview/unit testing.
 */
@Composable
fun FolderEditBottomSheetContent(
    isDark: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, color: Int) -> Unit,
    modifier: Modifier = Modifier,
    initialTitle: String = "",
    initialColor: Int = TabFolder.PALETTE.first(),
    isEditing: Boolean = false,
    showDragHandle: Boolean = false,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var selectedColor by remember(initialColor) { mutableIntStateOf(initialColor) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showDragHandle) {
            FolderDragHandle()
        }

        // Header: Title and Close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(if (isEditing) R.string.folder_edit_title else R.string.folder_new_title),
                style = LocalProstoTypography.current.headlineSm.copy(fontWeight = FontWeight.Bold),
                color = if (isDark) DarkOnSurface else ProstoDarkText,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = ProstoMutedSlate,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Live Preview Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.folder_preview_label),
                style = LocalProstoTypography.current.labelSm,
                color = ProstoMutedSlate,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 6.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .background(
                        color = if (isDark) DarkSurfaceVariant.copy(alpha = 0.4f) else ProstoCanvasFloor,
                        shape = ProstoShapes.Md,
                    )
                    .border(
                        border = ProstoBorders.hairlineBorder(isDark),
                        shape = ProstoShapes.Md,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                FolderLivePreview(
                    title = title,
                    color = selectedColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Text Field Input
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    color = if (isDark) DarkSurfaceVariant.copy(alpha = 0.5f) else ProstoCanvasFloor,
                    shape = ProstoShapes.Pill,
                )
                .border(
                    border = ProstoBorders.hairlineBorder(isDark),
                    shape = ProstoShapes.Pill,
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = LocalProstoTypography.current.bodyMd.copy(
                        color = if (isDark) DarkOnSurface else ProstoDarkText,
                    ),
                    cursorBrush = SolidColor(ProstoBrandPrimary),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            onSave(title.trim(), selectedColor)
                        },
                    ),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (title.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.folder_name_placeholder),
                                    style = LocalProstoTypography.current.bodyMd,
                                    color = ProstoMutedSlate,
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                if (title.isNotEmpty()) {
                    IconButton(
                        onClick = { title = "" },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.folder_clear_name),
                            tint = ProstoMutedSlate,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 8-Color Circle Picker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val selectColorDescription = stringResource(R.string.folder_select_color)
            TabFolder.PALETTE.forEach { colorInt ->
                val isSelected = colorInt == selectedColor
                val color = Color(colorInt)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { selectedColor = colorInt },
                        )
                        .semantics {
                            contentDescription = selectColorDescription
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .border(
                                    width = 2.dp,
                                    color = if (isDark) Color.White else ProstoDarkText,
                                    shape = CircleShape,
                                ),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 26.dp else 30.dp)
                            .background(color = color, shape = CircleShape),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Button ("Create" or "Save")
        Button(
            onClick = {
                keyboardController?.hide()
                onSave(title.trim(), selectedColor)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = ProstoShapes.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = ProstoBrandPrimary,
                contentColor = Color.White,
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 2.dp,
                pressedElevation = 0.dp,
            ),
        ) {
            Text(
                text = stringResource(if (isEditing) R.string.folder_save_button else R.string.folder_create_button),
                style = LocalProstoTypography.current.labelMd.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                ),
            )
        }
    }
}

/**
 * Live preview of the folder indicator as it will appear in the dock:
 * - When [title] is blank: 38dp circular indicator with arrow pointing left (`←`), filled with folder color.
 * - When [title] is not blank: rounded chip (pill shape) with folder color background, title text in contrasting
 *   color, and arrow pointing left (`←`) immediately to the right of the title text (`[ Title ← ]`).
 */
@Composable
fun FolderLivePreview(
    title: String,
    color: Int,
    modifier: Modifier = Modifier,
) {
    val folderColor = Color(color)
    val contentColor = if (folderColor.luminance() > 0.45f) Color(0xFF16201E) else Color.White

    if (title.isBlank()) {
        Box(
            modifier = modifier
                .size(38.dp)
                .background(folderColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
        }
    } else {
        Row(
            modifier = modifier
                .height(38.dp)
                .background(folderColor, CircleShape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = LocalProstoTypography.current.labelMd.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Preview(name = "Folder Edit BottomSheet - Light")
@Composable
fun FolderEditBottomSheetLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        FolderEditBottomSheetContent(
            isDark = false,
            initialTitle = "Work",
            initialColor = TabFolder.PALETTE[0],
            isEditing = false,
            onDismiss = {},
            onSave = { _, _ -> },
            showDragHandle = true,
        )
    }
}

@Preview(name = "Folder Edit BottomSheet - Dark")
@Composable
fun FolderEditBottomSheetDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        FolderEditBottomSheetContent(
            isDark = true,
            initialTitle = "",
            initialColor = TabFolder.PALETTE[2],
            isEditing = true,
            onDismiss = {},
            onSave = { _, _ -> },
            showDragHandle = true,
        )
    }
}
