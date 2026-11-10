package com.jestk.browser.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabGridContextMenuBottomSheet(
    tab: Tab,
    isDark: Boolean,
    availableWorkspaces: List<Workspace>,
    onMoveToWorkspace: (workspaceId: String) -> Unit,
    onCloseTab: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showWorkspacePicker by remember { mutableStateOf(false) }
    val sheetBg = if (isDark) DarkSurface else Color.White
    val typography = LocalProstoTypography.current
    val dividerColor = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetBg,
        dragHandle = { FolderDragHandle() },
        modifier = modifier.testTag("tab_grid_context_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            // Header: Tab Title + Close Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tab.title.ifEmpty { tab.url },
                    style = typography.headlineSm,
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
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

            if (showWorkspacePicker) {
                // Back Button to Main Menu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(ProstoShapes.Pill)
                        .clickable { showWorkspacePicker = false }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.tab_context_move_to_workspace_title),
                        tint = ProstoMutedSlate,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.tab_context_move_to_workspace_title),
                        style = typography.bodyMd.copy(fontWeight = FontWeight.Medium),
                        color = if (isDark) DarkOnSurface else ProstoDarkText,
                    )
                }

                HorizontalDivider(color = dividerColor, thickness = 1.dp)

                availableWorkspaces.forEach { ws ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(ProstoShapes.Pill)
                            .clickable {
                                onMoveToWorkspace(ws.id)
                                onDismiss()
                            }
                            .testTag("tab_grid_pick_workspace_${ws.id}")
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(ws.color)),
                        )
                        Text(
                            text = ws.name,
                            style = typography.bodyMd.copy(fontWeight = FontWeight.Medium),
                            color = if (isDark) DarkOnSurface else ProstoDarkText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider(color = dividerColor, thickness = 1.dp)
                }
            } else {
                // Move to Workspace (if other workspaces exist)
                if (availableWorkspaces.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(ProstoShapes.Pill)
                            .clickable { showWorkspacePicker = true }
                            .testTag("tab_grid_context_move_to_workspace")
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.tab_context_move_to_workspace),
                            tint = ProstoMutedSlate,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.tab_context_move_to_workspace),
                            style = typography.bodyMd.copy(fontWeight = FontWeight.Medium),
                            color = if (isDark) DarkOnSurface else ProstoDarkText,
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 1.dp)
                }

                // Close Tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(ProstoShapes.Pill)
                        .clickable {
                            onCloseTab()
                            onDismiss()
                        }
                        .testTag("tab_grid_context_close_tab")
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close_tab),
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.close_tab),
                        style = typography.bodyMd.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFFEF4444),
                    )
                }
            }
        }
    }
}
