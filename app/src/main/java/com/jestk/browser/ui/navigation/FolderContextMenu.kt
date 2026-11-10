package com.jestk.browser.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.jestk.browser.R
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkTextHighContrast
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoCardSurface
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoShapes

/**
 * Context menu for tab folders in the bottom dock.
 * Adheres to Prosto Canvas design system:
 * - Floating card surface, Level 2 elevation shadow, rim border.
 * - 48px touch targets for menu rows.
 * - Hairline dividers.
 * - NO BLUR.
 */
@Composable
fun FolderContextMenu(
    folder: TabFolder,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isDark: Boolean,
    onNewTabInFolder: () -> Unit,
    onEditFolder: () -> Unit,
    onUngroupFolder: () -> Unit,
    onCloseFolder: () -> Unit,
) {
    if (!expanded) return

    BackHandler(enabled = true, onBack = onDismissRequest)

    val menuBg = if (isDark) DarkSurface else ProstoCardSurface
    val menuBorder = ProstoBorders.floatingBorder(isDark)
    val dividerColor = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)
    val density = LocalDensity.current
    val typography = LocalProstoTypography.current
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
            modifier = Modifier
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
                }
                .testTag("folder_context_menu_${folder.id}"),
        ) {
            Surface(
                shape = ProstoShapes.Card,
                color = menuBg,
                shadowElevation = 12.dp,
                border = menuBorder,
                modifier = Modifier.width(200.dp),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Header with color dot and folder title
                    val folderTitle = folder.title.ifBlank {
                        stringResource(R.string.folder_new_title)
                    }
                    val headerTextColor = if (isDark) DarkTextHighContrast else ProstoDarkText

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(folder.color)),
                        )
                        Text(
                            text = folderTitle,
                            style = typography.labelMd.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            ),
                            color = headerTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                    // New tab in folder
                    PopupMenuItem(
                        icon = Icons.Default.Add,
                        label = stringResource(R.string.folder_new_tab),
                        isActive = false,
                        isDark = isDark,
                        onClick = {
                            onDismissRequest()
                            onNewTabInFolder()
                        },
                        modifier = Modifier.testTag("folder_menu_new_tab"),
                    )

                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                    // Edit name and color
                    PopupMenuItem(
                        icon = Icons.Default.Edit,
                        label = stringResource(R.string.folder_edit_name_color),
                        isActive = false,
                        isDark = isDark,
                        onClick = {
                            onDismissRequest()
                            onEditFolder()
                        },
                        modifier = Modifier.testTag("folder_menu_edit"),
                    )

                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                    // Ungroup
                    PopupMenuItem(
                        icon = Icons.Default.FilterNone,
                        label = stringResource(R.string.folder_ungroup),
                        isActive = false,
                        isDark = isDark,
                        onClick = {
                            onDismissRequest()
                            onUngroupFolder()
                        },
                        modifier = Modifier.testTag("folder_menu_ungroup"),
                    )

                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                    // Close folder
                    PopupMenuItem(
                        icon = Icons.Default.Close,
                        label = stringResource(R.string.folder_close),
                        isActive = false,
                        isDark = isDark,
                        onClick = {
                            onDismissRequest()
                            onCloseFolder()
                        },
                        modifier = Modifier.testTag("folder_menu_close"),
                    )
                }
            }
        }
    }
}
