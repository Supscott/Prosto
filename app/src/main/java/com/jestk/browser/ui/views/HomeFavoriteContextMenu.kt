package com.jestk.browser.ui.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.jestk.browser.R
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.components.workspaceDisplayName
import com.jestk.browser.ui.overlays.ContextMenuActionRow
import com.jestk.browser.ui.overlays.FloatingContextMenuContainer
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue

/**
 * Long-press menu of a home favorite, drawn in the app's floating context menu style (same card
 * as the link and image menus). Choosing "Move to workspace" swaps the rows for the workspace
 * list inside the same card.
 *
 * [anchor] is in the coordinates of the area this composable fills.
 */
@Composable
internal fun HomeFavoriteContextMenu(
    anchor: Offset?,
    availableWorkspaces: List<Workspace>,
    canMove: Boolean,
    canDelete: Boolean,
    onCopyUrl: () -> Unit,
    onMoveToWorkspace: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialWorkspacePicker: Boolean = false,
    animateIn: Boolean = true,
) {
    FloatingContextMenuContainer(
        anchor = anchor,
        onDismiss = onDismiss,
        modifier = modifier,
        animateIn = animateIn,
    ) {
        HomeFavoriteContextMenuContent(
            availableWorkspaces = availableWorkspaces,
            canMove = canMove,
            canDelete = canDelete,
            onCopyUrl = onCopyUrl,
            onMoveToWorkspace = onMoveToWorkspace,
            onDelete = onDelete,
            initialWorkspacePicker = initialWorkspacePicker,
        )
    }
}

/**
 * Hosts [HomeFavoriteContextMenu] in a window-sized popup so the anchor can be a window position
 * (tile position plus the press offset) and the scrim covers the whole screen.
 */
@Composable
internal fun HomeFavoriteContextMenuPopup(
    anchorInWindow: Offset,
    availableWorkspaces: List<Workspace>,
    canMove: Boolean,
    canDelete: Boolean,
    onCopyUrl: () -> Unit,
    onMoveToWorkspace: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        popupPositionProvider = WindowOriginPositionProvider,
        onDismissRequest = onDismiss,
        properties =
            PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                clippingEnabled = false,
                usePlatformDefaultWidth = false,
            ),
    ) {
        HomeFavoriteContextMenu(
            anchor = anchorInWindow,
            availableWorkspaces = availableWorkspaces,
            canMove = canMove,
            canDelete = canDelete,
            onCopyUrl = onCopyUrl,
            onMoveToWorkspace = onMoveToWorkspace,
            onDelete = onDelete,
            onDismiss = onDismiss,
        )
    }
}

private object WindowOriginPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

@Composable
internal fun HomeFavoriteContextMenuContent(
    availableWorkspaces: List<Workspace>,
    canMove: Boolean,
    canDelete: Boolean,
    onCopyUrl: () -> Unit,
    onMoveToWorkspace: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    initialWorkspacePicker: Boolean = false,
) {
    val isDark = LocalIsDarkTheme.current
    val textColor = if (isDark) DarkOnSurface else ProstoDarkText
    val iconBg = if (isDark) DarkSurfaceVariant else ProstoPaleBlue
    val neutralBg = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9)
    var showWorkspacePicker by remember { mutableStateOf(initialWorkspacePicker) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        if (showWorkspacePicker) {
            ContextMenuActionRow(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                iconTint = ProstoMutedSlate,
                iconBg = neutralBg,
                title = stringResource(R.string.tab_context_move_to_workspace_title),
                titleColor = textColor,
                onClick = { showWorkspacePicker = false },
            )
            availableWorkspaces.forEach { ws ->
                ContextMenuActionRow(
                    icon = Icons.Default.Circle,
                    iconTint = Color(ws.color),
                    iconBg = iconBg,
                    title = workspaceDisplayName(ws),
                    titleColor = textColor,
                    onClick = { onMoveToWorkspace(ws.id) },
                )
            }
        } else {
            ContextMenuActionRow(
                icon = Icons.Default.ContentCopy,
                iconTint = ProstoMutedSlate,
                iconBg = neutralBg,
                title = stringResource(R.string.copy_url_action),
                titleColor = textColor,
                onClick = onCopyUrl,
            )
            if (canMove && availableWorkspaces.isNotEmpty()) {
                ContextMenuActionRow(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    iconTint = ProstoBrandPrimary,
                    iconBg = iconBg,
                    title = stringResource(R.string.move_to_workspace),
                    titleColor = textColor,
                    onClick = { showWorkspacePicker = true },
                )
            }
            if (canDelete) {
                val error = MaterialTheme.colorScheme.error
                ContextMenuActionRow(
                    icon = Icons.Default.Delete,
                    iconTint = error,
                    iconBg = error.copy(alpha = 0.12f),
                    title = stringResource(R.string.delete_bookmark_action),
                    titleColor = error,
                    onClick = onDelete,
                )
            }
        }
    }
}
