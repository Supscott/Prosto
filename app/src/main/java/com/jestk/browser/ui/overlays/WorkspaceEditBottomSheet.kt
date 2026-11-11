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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.components.workspaceDisplayName
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes

private const val MAX_WORKSPACE_NAME_LENGTH = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceEditBottomSheet(
    isDark: Boolean,
    workspace: Workspace?,
    isLastWorkspace: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, color: Int) -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val initialName = workspace?.let { workspaceDisplayName(it) }.orEmpty()
    var name by remember(workspace) {
        mutableStateOf(initialName)
    }
    var selectedColor by remember(workspace) {
        mutableIntStateOf(workspace?.color ?: TabFolder.PALETTE.first())
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val isEditing = workspace != null
    val sheetBg = if (isDark) DarkSurface else Color.White
    val typography = LocalProstoTypography.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetBg,
        dragHandle = { FolderDragHandle() },
        modifier = modifier.imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            // Header: Title + Close Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(if (isEditing) R.string.workspace_edit else R.string.workspace_add),
                    style = typography.headlineSm,
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.folder_close),
                        tint = ProstoMutedSlate,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Workspace Preview Card Pill
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.folder_preview_label),
                    style = typography.labelSm,
                    color = ProstoMutedSlate,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(bottom = 6.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(Color(selectedColor), CircleShape),
                        )
                        Text(
                            text = name.ifBlank { stringResource(R.string.workspace_name_placeholder) },
                            style = typography.headlineSm,
                            color = Color(selectedColor),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                        value = name,
                        onValueChange = { if (it.length <= MAX_WORKSPACE_NAME_LENGTH) name = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .testTag("workspace_name_input"),
                        singleLine = true,
                        textStyle = typography.bodyMd.copy(
                            color = if (isDark) DarkOnSurface else ProstoDarkText,
                        ),
                        cursorBrush = SolidColor(ProstoBrandPrimary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                if (name.isNotBlank()) {
                                    onSave(name.trim(), selectedColor)
                                }
                            },
                        ),
                        decorationBox = { innerTextField ->
                            if (name.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.workspace_name_placeholder),
                                    style = typography.bodyMd,
                                    color = ProstoMutedSlate,
                                )
                            }
                            innerTextField()
                        },
                    )

                    if (name.isNotEmpty()) {
                        IconButton(
                            onClick = { name = "" },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.workspace_clear_name),
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
                            .testTag("workspace_color_picker_$colorInt"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 36.dp else 26.dp)
                                .background(
                                    color = if (isSelected) color.copy(alpha = 0.25f) else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) color else Color.Transparent,
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(color = color, shape = CircleShape),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons: Save/Create + Delete (if editing)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        keyboardController?.hide()
                        // Keep a default name blank so it stays localized; store only what the user typed.
                        val typed = name.trim()
                        val finalName = if (workspace?.name?.isBlank() == true && typed == initialName) "" else typed
                        onSave(finalName, selectedColor)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("workspace_save_button"),
                    shape = ProstoShapes.Pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProstoBrandPrimary,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = stringResource(if (isEditing) R.string.workspace_save else R.string.workspace_create),
                        style = typography.labelMd.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (isEditing && onDelete != null) {
                    if (isLastWorkspace) {
                        Text(
                            text = stringResource(R.string.workspace_cannot_delete_last),
                            style = typography.bodySm,
                            color = ProstoMutedSlate,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    } else {
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("workspace_delete_button"),
                            shape = ProstoShapes.Pill,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFEF4444),
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.workspace_delete),
                                style = typography.labelMd.copy(fontSize = 15.sp),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}
