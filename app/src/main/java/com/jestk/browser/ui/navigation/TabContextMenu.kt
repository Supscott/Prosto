package com.jestk.browser.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.jestk.browser.R
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.components.workspaceDisplayName
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoCardSurface
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoShapes
import kotlinx.coroutines.isActive

/**
 * Contextual dropdown menu on tabs (active or inactive).
 * Adheres to Prosto Canvas:
 * - Floating card with 24px radius (ProstoShapes.Card), pure solid surface.
 * - Rim border and Level 2 elevation shadow.
 * - 48px touch targets for menu rows.
 * - Hairline dividers 1px solid #F1F5F9 (Dark: 1px solid #334155).
 * - Clean Manrope title typography.
 * - NO BLUR.
 */
@Composable
fun TabContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isDark: Boolean,
    onCloseTab: () -> Unit,
    onCopyUrl: () -> Unit,
    onAddFavorite: () -> Unit,
    onShareLink: (() -> Unit)? = null,
    isReloadVisible: Boolean = false,
    onReload: (() -> Unit)? = null,
    folderId: String? = null,
    availableFolders: List<TabFolder> = emptyList(),
    onCreateFolder: (() -> Unit)? = null,
    onAddToFolder: ((folderId: String) -> Unit)? = null,
    onRemoveFromFolder: (() -> Unit)? = null,
    availableWorkspaces: List<Workspace> = emptyList(),
    onMoveToWorkspace: ((workspaceId: String) -> Unit)? = null,
) {
    if (!expanded) return

    BackHandler(enabled = true, onBack = onDismissRequest)

    val menuBg = if (isDark) DarkSurface else ProstoCardSurface
    val menuBorder = ProstoBorders.floatingBorder(isDark)
    val dividerColor = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)
    val density = LocalDensity.current
    val typography = LocalProstoTypography.current
    var showFolderPicker by remember { mutableStateOf(false) }
    var showWorkspacePicker by remember { mutableStateOf(false) }

    val popupPositionProvider = remember(density) {
        TabContextMenuPositionProvider(density)
    }

    Popup(
        onDismissRequest = onDismissRequest,
        popupPositionProvider = popupPositionProvider,
        properties = PopupProperties(focusable = true, dismissOnClickOutside = true, dismissOnBackPress = true),
    ) {
        val dockGapPx = with(density) { 10.dp.toPx() }
        val overflowPx = with(density) { 32.dp.toPx() }

        Box(
            modifier =
                Modifier
                    .graphicsLayer {
                        shape = DockShadowUnderClipShape(dockGapPx, overflowPx)
                        clip = true
                    }
                    .drawWithContent {
                        clipRect(
                            left = -overflowPx,
                            top = -overflowPx,
                            right = size.width + overflowPx,
                            bottom = size.height + dockGapPx,
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    },
        ) {
            Surface(
                shape = ProstoShapes.Card,
                color = menuBg,
                shadowElevation = 12.dp,
                border = menuBorder,
                modifier = Modifier.width(188.dp),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (showWorkspacePicker) {
                        PopupMenuItem(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            label = stringResource(R.string.tab_context_move_to_workspace_title),
                            isActive = false,
                            isDark = isDark,
                            onClick = { showWorkspacePicker = false },
                            modifier = Modifier.testTag("tab_context_workspace_back"),
                        )

                        HorizontalDivider(color = dividerColor, thickness = 1.dp)

                        availableWorkspaces.forEachIndexed { idx, ws ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onDismissRequest()
                                        onMoveToWorkspace?.invoke(ws.id)
                                    }
                                    .testTag("tab_context_pick_workspace_${ws.id}"),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color(ws.color)),
                                    )
                                    Text(
                                        text = workspaceDisplayName(ws),
                                        style = typography.bodyMd.copy(fontWeight = FontWeight.Medium),
                                        color = if (isDark) Color(0xFFF1F5F9) else ProstoDarkText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (idx < availableWorkspaces.lastIndex) {
                                HorizontalDivider(color = dividerColor, thickness = 1.dp)
                            }
                        }
                    } else if (showFolderPicker) {
                        PopupMenuItem(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            label = stringResource(R.string.tab_context_add_to_folder),
                            isActive = false,
                            isDark = isDark,
                            onClick = { showFolderPicker = false },
                            modifier = Modifier.testTag("tab_context_folder_back"),
                        )

                        HorizontalDivider(color = dividerColor, thickness = 1.dp)

                        availableFolders.forEachIndexed { idx, folder ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onDismissRequest()
                                        onAddToFolder?.invoke(folder.id)
                                    }
                                    .testTag("tab_context_pick_folder_${folder.id}"),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color(folder.color)),
                                    )
                                    Text(
                                        text = folder.title.ifBlank { stringResource(R.string.folder_new_title) },
                                        style = typography.bodyMd.copy(fontWeight = FontWeight.Medium),
                                        color = if (isDark) Color(0xFFF1F5F9) else ProstoDarkText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (idx < availableFolders.lastIndex) {
                                HorizontalDivider(color = dividerColor, thickness = 1.dp)
                            }
                        }
                    } else {
                        // Reload (First item when isReloadVisible is true)
                        if (isReloadVisible && onReload != null) {
                            PopupMenuItem(
                                icon = Icons.Default.Refresh,
                                label = stringResource(R.string.reload_tab),
                                isActive = false,
                                isDark = isDark,
                                onClick = {
                                    onDismissRequest()
                                    onReload()
                                },
                            )

                            HorizontalDivider(color = dividerColor, thickness = 1.dp)
                        }

                        // Close Tab
                        PopupMenuItem(
                            icon = Icons.Default.Close,
                            label = stringResource(R.string.a11y_close_tab),
                            isActive = false,
                            isDark = isDark,
                            onClick = {
                                onDismissRequest()
                                onCloseTab()
                            },
                        )

                        HorizontalDivider(color = dividerColor, thickness = 1.dp)

                        // Move to Workspace
                        if (availableWorkspaces.isNotEmpty() && onMoveToWorkspace != null) {
                            PopupMenuItem(
                                icon = Icons.AutoMirrored.Filled.ArrowForward,
                                label = stringResource(R.string.tab_context_move_to_workspace),
                                isActive = false,
                                isDark = isDark,
                                onClick = {
                                    showWorkspacePicker = true
                                },
                                modifier = Modifier.testTag("tab_context_move_to_workspace"),
                            )

                            HorizontalDivider(color = dividerColor, thickness = 1.dp)
                        }

                        // Folder options
                        if (folderId == null) {
                            if (onCreateFolder != null) {
                                PopupMenuItem(
                                    icon = Icons.Default.Add,
                                    label = stringResource(R.string.tab_context_new_folder),
                                    isActive = false,
                                    isDark = isDark,
                                    onClick = {
                                        onDismissRequest()
                                        onCreateFolder()
                                    },
                                    modifier = Modifier.testTag("tab_context_new_folder"),
                                )

                                HorizontalDivider(color = dividerColor, thickness = 1.dp)
                            }

                            if (availableFolders.isNotEmpty() && onAddToFolder != null) {
                                PopupMenuItem(
                                    icon = Icons.Default.FilterNone,
                                    label = stringResource(R.string.tab_context_add_to_folder),
                                    isActive = false,
                                    isDark = isDark,
                                    onClick = {
                                        showFolderPicker = true
                                    },
                                    modifier = Modifier.testTag("tab_context_add_to_folder"),
                                )

                                HorizontalDivider(color = dividerColor, thickness = 1.dp)
                            }
                        } else if (onRemoveFromFolder != null) {
                            PopupMenuItem(
                                icon = Icons.Default.Close,
                                label = stringResource(R.string.tab_context_remove_from_folder),
                                isActive = false,
                                isDark = isDark,
                                onClick = {
                                    onDismissRequest()
                                    onRemoveFromFolder()
                                },
                                modifier = Modifier.testTag("tab_context_remove_from_folder"),
                            )

                            HorizontalDivider(color = dividerColor, thickness = 1.dp)
                        }

                        // Copy URL
                        PopupMenuItem(
                            icon = Icons.Default.ContentCopy,
                            label = stringResource(R.string.copy_link_url),
                            isActive = false,
                            isDark = isDark,
                            onClick = {
                                onDismissRequest()
                                onCopyUrl()
                            },
                        )

                        HorizontalDivider(color = dividerColor, thickness = 1.dp)

                        // Add to Favorites
                        PopupMenuItem(
                            icon = Icons.Default.Bookmark,
                            label = stringResource(R.string.add_favorite_dialog_title),
                            isActive = false,
                            isDark = isDark,
                            onClick = {
                                onDismissRequest()
                                onAddFavorite()
                            },
                        )

                        // Share Link
                        if (onShareLink != null) {
                            HorizontalDivider(color = dividerColor, thickness = 1.dp)

                            PopupMenuItem(
                                icon = Icons.Default.Share,
                                label = stringResource(R.string.share_link),
                                isActive = false,
                                isDark = isDark,
                                onClick = {
                                    onDismissRequest()
                                    onShareLink()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
