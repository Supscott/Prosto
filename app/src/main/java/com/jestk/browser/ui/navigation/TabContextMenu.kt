package com.jestk.browser.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.jestk.browser.R
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.components.workspaceDisplayName
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoCardSurface
import com.jestk.browser.ui.theme.ProstoShapes

/**
 * Contextual dropdown menu on tabs (active or inactive); every action applies to that tab.
 * Layout, top to bottom: an icon row (Copy link, Share, Favorite toggle, Reload), the Site group
 * (blocking toggle), the Organize group (folders, workspace) and Close tab in red.
 * A null callback hides its item (Reload on home pages, Favorite and blocking on non-web URLs).
 * Adheres to Prosto Canvas:
 * - Floating card with 24px radius (ProstoShapes.Card), pure solid surface.
 * - Rim border and Level 2 elevation shadow.
 * - 48px touch targets for menu rows and icon buttons.
 * - Hairline dividers 1px solid #F1F5F9 (Dark: 1px solid #334155).
 * - NO BLUR.
 */
@Composable
fun TabContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isDark: Boolean,
    onCloseTab: () -> Unit,
    onCopyUrl: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    isSiteAllowlisted: Boolean = false,
    onToggleBlocking: (() -> Unit)? = null,
    onShareLink: (() -> Unit)? = null,
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

    val density = LocalDensity.current
    val popupPositionProvider = remember(density) { TabContextMenuPositionProvider(density) }

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
            TabContextMenuCard(
                onDismissRequest = onDismissRequest,
                isDark = isDark,
                onCloseTab = onCloseTab,
                onCopyUrl = onCopyUrl,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                isSiteAllowlisted = isSiteAllowlisted,
                onToggleBlocking = onToggleBlocking,
                onShareLink = onShareLink,
                onReload = onReload,
                folderId = folderId,
                availableFolders = availableFolders,
                onCreateFolder = onCreateFolder,
                onAddToFolder = onAddToFolder,
                onRemoveFromFolder = onRemoveFromFolder,
                availableWorkspaces = availableWorkspaces,
                onMoveToWorkspace = onMoveToWorkspace,
            )
        }
    }
}

/** The menu card without the popup window, so previews can render it inline. */
@Composable
internal fun TabContextMenuCard(
    onDismissRequest: () -> Unit,
    isDark: Boolean,
    onCloseTab: () -> Unit,
    onCopyUrl: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    isSiteAllowlisted: Boolean,
    onToggleBlocking: (() -> Unit)?,
    onShareLink: (() -> Unit)?,
    onReload: (() -> Unit)?,
    folderId: String?,
    availableFolders: List<TabFolder>,
    onCreateFolder: (() -> Unit)?,
    onAddToFolder: ((folderId: String) -> Unit)?,
    onRemoveFromFolder: (() -> Unit)?,
    availableWorkspaces: List<Workspace>,
    onMoveToWorkspace: ((workspaceId: String) -> Unit)?,
) {
    var showFolderPicker by remember { mutableStateOf(false) }
    var showWorkspacePicker by remember { mutableStateOf(false) }

    Surface(
        shape = ProstoShapes.Card,
        color = if (isDark) DarkSurface else ProstoCardSurface,
        shadowElevation = 12.dp,
        border = ProstoBorders.floatingBorder(isDark),
        modifier = Modifier.widthIn(min = ACTION_MENU_MIN_WIDTH, max = ACTION_MENU_MAX_WIDTH).width(IntrinsicSize.Max),
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
                TabMenuHairline(isDark)
                availableWorkspaces.forEach { ws ->
                    TabMenuPickerRow(ws.color, workspaceDisplayName(ws), isDark, "tab_context_pick_workspace_${ws.id}") {
                        onDismissRequest()
                        onMoveToWorkspace?.invoke(ws.id)
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
                TabMenuHairline(isDark)
                availableFolders.forEach { folder ->
                    TabMenuPickerRow(
                        color = folder.color,
                        title = folder.title.ifBlank { stringResource(R.string.folder_new_title) },
                        isDark = isDark,
                        testTag = "tab_context_pick_folder_${folder.id}",
                    ) {
                        onDismissRequest()
                        onAddToFolder?.invoke(folder.id)
                    }
                }
            } else {
                TabMenuActionRow(
                    isDark = isDark,
                    onCopyUrl = {
                        onDismissRequest()
                        onCopyUrl()
                    },
                    onReload = onReload?.let { reload -> { onDismissRequest(); reload() } },
                    onShareLink = onShareLink?.let { share -> { onDismissRequest(); share() } },
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite?.let { toggle -> { onDismissRequest(); toggle() } },
                )

                if (onToggleBlocking != null) {
                    TabMenuHairline(isDark)
                    PopupMenuItem(
                        icon = Icons.Default.Shield,
                        label = stringResource(if (isSiteAllowlisted) R.string.menu_enable_blocking else R.string.menu_disable_blocking),
                        isActive = isSiteAllowlisted,
                        isDark = isDark,
                        activeTile = false,
                        onClick = {
                            onDismissRequest()
                            onToggleBlocking()
                        },
                        modifier = Modifier.testTag("tab_context_blocking"),
                    )
                }

                val canMove = availableWorkspaces.isNotEmpty() && onMoveToWorkspace != null
                val canNewFolder = folderId == null && onCreateFolder != null
                val canAddToFolder = folderId == null && availableFolders.isNotEmpty() && onAddToFolder != null
                val canRemoveFromFolder = folderId != null && onRemoveFromFolder != null
                if (canMove || canNewFolder || canAddToFolder || canRemoveFromFolder) {
                    TabMenuGroupGap(isDark)
                    if (canNewFolder) {
                        PopupMenuItem(
                            icon = Icons.Default.Add,
                            label = stringResource(R.string.tab_context_new_folder),
                            isActive = false,
                            isDark = isDark,
                            onClick = {
                                onDismissRequest()
                                onCreateFolder?.invoke()
                            },
                            modifier = Modifier.testTag("tab_context_new_folder"),
                        )
                    }
                    if (canAddToFolder) {
                        PopupMenuItem(
                            icon = Icons.Default.FilterNone,
                            label = stringResource(R.string.tab_context_add_to_folder),
                            isActive = false,
                            isDark = isDark,
                            onClick = { showFolderPicker = true },
                            modifier = Modifier.testTag("tab_context_add_to_folder"),
                        )
                    }
                    if (canRemoveFromFolder) {
                        PopupMenuItem(
                            icon = Icons.Default.Close,
                            label = stringResource(R.string.tab_context_remove_from_folder),
                            isActive = false,
                            isDark = isDark,
                            onClick = {
                                onDismissRequest()
                                onRemoveFromFolder?.invoke()
                            },
                            modifier = Modifier.testTag("tab_context_remove_from_folder"),
                        )
                    }
                    if (canMove) {
                        PopupMenuItem(
                            icon = Icons.AutoMirrored.Filled.ArrowForward,
                            label = stringResource(R.string.tab_context_move_to_workspace),
                            isActive = false,
                            isDark = isDark,
                            onClick = { showWorkspacePicker = true },
                            modifier = Modifier.testTag("tab_context_move_to_workspace"),
                        )
                    }
                }

                TabMenuGroupGap(isDark)
                PopupMenuItem(
                    icon = Icons.Default.Close,
                    label = stringResource(R.string.a11y_close_tab),
                    isActive = false,
                    isDark = isDark,
                    destructive = true,
                    onClick = {
                        onDismissRequest()
                        onCloseTab()
                    },
                    modifier = Modifier.testTag("tab_context_close"),
                )
            }
        }
    }
}
