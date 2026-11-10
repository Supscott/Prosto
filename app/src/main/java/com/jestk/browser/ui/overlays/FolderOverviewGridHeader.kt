package com.jestk.browser.ui.overlays

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.navigation.getContrastingFolderContentColor
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalProstoTypography

/**
 * Lightweight bounds representation for a grid item, allowing decoupled testing and layout calculations.
 */
internal data class GridItemBounds(
    val key: Any,
    val offset: androidx.compose.ui.unit.IntOffset,
    val size: androidx.compose.ui.unit.IntSize,
)

/**
 * State representing the active floating sticky header overlay.
 */
internal data class StickyHeaderState(
    val visible: Boolean = false,
    val folder: TabFolder? = null,
    val tabCount: Int = 0,
    val translationY: Float = 0f,
)

/**
 * Bounding box for the translucent underlay drawn behind an expanded folder's member tabs.
 */
internal data class FolderUnderlayBounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
)

/**
 * Calculates whether the floating sticky header should be shown, and its vertical translation.
 */
internal fun computeStickyHeaderState(
    expandedFolders: List<TabFolder>,
    folderMemberTabsMap: Map<String, List<Tab>>,
    visibleItems: List<LazyGridItemInfo>,
    headerHeightPx: Float,
): StickyHeaderState = computeStickyHeaderStateFromBounds(
    expandedFolders = expandedFolders,
    folderMemberTabsMap = folderMemberTabsMap,
    visibleItems = visibleItems.map { GridItemBounds(it.key, it.offset, it.size) },
    headerHeightPx = headerHeightPx,
)

/**
 * Pure calculation of sticky header state from item bounds.
 */
internal fun computeStickyHeaderStateFromBounds(
    expandedFolders: List<TabFolder>,
    folderMemberTabsMap: Map<String, List<Tab>>,
    visibleItems: List<GridItemBounds>,
    headerHeightPx: Float,
): StickyHeaderState {
    if (expandedFolders.isEmpty() || visibleItems.isEmpty()) {
        return StickyHeaderState()
    }

    for (folder in expandedFolders) {
        val headerItem = visibleItems.firstOrNull { it.key == "folder_header_${folder.id}" }
        val memberTabs = folderMemberTabsMap[folder.id] ?: emptyList()
        val memberKeys = memberTabs.map { "tab_${it.id}" }.toSet()
        val memberItems = visibleItems.filter { it.key in memberKeys }

        val headerScrolledPast = (headerItem == null || headerItem.offset.y < 0)
        if (headerScrolledPast && memberItems.isNotEmpty()) {
            val folderBottom = memberItems.maxOf { it.offset.y + it.size.height }.toFloat()
            if (folderBottom > 0f) {
                val translationY = if (folderBottom < headerHeightPx) {
                    folderBottom - headerHeightPx
                } else {
                    0f
                }
                return StickyHeaderState(
                    visible = true,
                    folder = folder,
                    tabCount = memberTabs.size,
                    translationY = translationY,
                )
            }
        }
    }

    return StickyHeaderState()
}

/**
 * Computes bounding rectangle of an expanded folder's member items for drawing the underlay.
 */
internal fun calculateFolderUnderlayBounds(
    visibleItems: List<LazyGridItemInfo>,
    folderTabKeys: Set<String>,
    gridWidth: Float,
    horizontalMarginPx: Float = 0f,
    verticalPaddingPx: Float = 0f,
): FolderUnderlayBounds? = calculateFolderUnderlayBoundsFromBounds(
    visibleItems = visibleItems.map { GridItemBounds(it.key, it.offset, it.size) },
    folderTabKeys = folderTabKeys,
    gridWidth = gridWidth,
    horizontalMarginPx = horizontalMarginPx,
    verticalPaddingPx = verticalPaddingPx,
)

internal fun calculateFolderUnderlayBoundsFromBounds(
    visibleItems: List<GridItemBounds>,
    folderTabKeys: Set<String>,
    gridWidth: Float,
    horizontalMarginPx: Float = 0f,
    verticalPaddingPx: Float = 0f,
): FolderUnderlayBounds? {
    val memberItems = visibleItems.filter { it.key in folderTabKeys }
    if (memberItems.isEmpty()) return null

    val minX = horizontalMarginPx
    val maxX = gridWidth - horizontalMarginPx
    val minY = memberItems.minOf { it.offset.y } - verticalPaddingPx
    val maxY = memberItems.maxOf { it.offset.y + it.size.height } + verticalPaddingPx
    return FolderUnderlayBounds(minX = minX, minY = minY, maxX = maxX, maxY = maxY)
}

internal fun shouldRemoveTabFromFolderOnDropFromBounds(
    sourceFolderId: String?,
    dropTargetKey: String?,
    dropPosition: Offset,
    tabs: List<Tab>,
    visibleItems: List<GridItemBounds>,
): Boolean {
    if (sourceFolderId == null) return false

    if (dropTargetKey != null && dropTargetKey.startsWith("tab_")) {
        val targetTabId = dropTargetKey.removePrefix("tab_")
        val targetTab = tabs.find { it.id == targetTabId }
        if (targetTab?.folderId == sourceFolderId) {
            return false
        }
        return true
    }

    if (dropTargetKey != null && (dropTargetKey.startsWith("folder_") || dropTargetKey.startsWith("folder_header_"))) {
        val targetFolderId = dropTargetKey.removePrefix("folder_header_").removePrefix("folder_")
        if (targetFolderId != sourceFolderId) {
            return true
        }
    }

    val folderItems = visibleItems.filter { item ->
        item.key == "folder_header_$sourceFolderId" ||
            tabs.find { "tab_${it.id}" == item.key }?.folderId == sourceFolderId ||
            item.key.toString().startsWith("folder_spacer_${sourceFolderId}_")
    }

    if (folderItems.isEmpty()) {
        return true
    }

    val minY = folderItems.minOf { it.offset.y }
    val maxY = folderItems.maxOf { it.offset.y + it.size.height }
    return dropPosition.y < minY || dropPosition.y > maxY
}

/**
 * Header strip for an expanded folder in TabOverviewGrid.
 *
 * Rendered both as:
 * 1. In-grid full-span (GridItemSpan(3)) item capping the member cards.
 * 2. Floating sticky header overlay pinned at the top of the grid viewport during scroll.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderOverviewGridHeader(
    folder: TabFolder,
    tabCount: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isSticky: Boolean = false,
    onOpenEditSheet: () -> Unit = {},
) {
    val folderColor = Color(folder.color)
    val contentColor = getContrastingFolderContentColor(folderColor)
    val displayTitle = folder.title.ifBlank {
        pluralStringResource(R.plurals.folder_tabs_count, tabCount, tabCount)
    }

    val shape = if (isSticky) {
        RoundedCornerShape(12.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    }
    val elevation = if (isSticky) 6.dp else 2.dp
    val tag = if (isSticky) "folder_sticky_header_${folder.id}" else "folder_in_grid_header_${folder.id}"
    val titleTag = if (isSticky) "folder_sticky_header_title_${folder.id}" else "folder_header_title_${folder.id}"
    val arrowTag = if (isSticky) "folder_sticky_header_arrow_${folder.id}" else "folder_header_arrow_${folder.id}"
    val editTag = if (isSticky) "folder_sticky_header_edit_${folder.id}" else "folder_header_edit_${folder.id}"

    Surface(
        shape = shape,
        color = folderColor,
        shadowElevation = elevation,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .testTag(tag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = displayTitle,
                style = LocalProstoTypography.current.labelMd.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .testTag(titleTag),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = onOpenEditSheet,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag(editTag),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.folder_edit_action),
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.folder_collapse),
                    tint = contentColor,
                    modifier = Modifier
                        .size(22.dp)
                        .testTag(arrowTag),
                )
            }
        }
    }
}

@Preview(name = "FolderOverviewGridHeader Light", showBackground = true, widthDp = 380, heightDp = 60)
@Composable
fun FolderOverviewGridHeaderLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        Box(modifier = Modifier.padding(8.dp)) {
            FolderOverviewGridHeader(
                folder = TabFolder(
                    id = "f1",
                    title = "Work",
                    color = 0xFF2196F3.toInt(),
                ),
                tabCount = 5,
                onClick = {},
                onLongClick = {},
            )
        }
    }
}

@Preview(name = "FolderOverviewGridHeader Dark Sticky", showBackground = true, widthDp = 380, heightDp = 60)
@Composable
fun FolderOverviewGridHeaderDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.padding(8.dp)) {
            FolderOverviewGridHeader(
                folder = TabFolder(
                    id = "f2",
                    title = "Research",
                    color = 0xFF8B5CF6.toInt(),
                ),
                tabCount = 3,
                isSticky = true,
                onClick = {},
                onLongClick = {},
            )
        }
    }
}
