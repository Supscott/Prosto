package com.jestk.browser.ui.overlays

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.ui.components.SiteFavicon
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes
import java.io.File
import kotlin.math.roundToInt

/**
 * Floating ghost that follows the finger while a card is being dragged.
 *
 * It is the only thing that moves: the dragged item itself is drawn invisible inside the grid, so
 * its cell reads as a free slot for the whole gesture. The ghost carries the exact size of the
 * card that was picked up and keeps the grab offset, so it never jumps at lift-off.
 */
@Composable
internal fun TabOverviewDragGhost(
    reorderState: TabOverviewReorderState,
    tabs: List<Tab>,
    folders: List<TabFolder>,
    isDark: Boolean,
) {
    val draggedKey = reorderState.draggedKey
    val ghostSize = reorderState.ghostSize
    if (!reorderState.isDragging || draggedKey == null) return
    if (ghostSize.width <= 0 || ghostSize.height <= 0) return

    val density = LocalDensity.current
    val isGrouping = reorderState.hoverGroupingKey != null
    val scale by animateFloatAsState(
        targetValue = if (isGrouping) 0.92f else 1.04f,
        animationSpec = tween(140),
        label = "dragGhostScale",
    )

    Box(
        modifier =
            Modifier
                .offset {
                    IntOffset(
                        x = reorderState.ghostTopLeft.x.roundToInt(),
                        y = reorderState.ghostTopLeft.y.roundToInt(),
                    )
                }
                .size(
                    width = with(density) { ghostSize.width.toDp() },
                    height = with(density) { ghostSize.height.toDp() },
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin.Center
                }
                .testTag("drag_proxy"),
    ) {
        val draggedTab =
            if (draggedKey.startsWith("tab_")) {
                tabs.find { it.id == draggedKey.removePrefix("tab_") }
            } else {
                null
            }

        when {
            draggedTab != null -> TabDragGhost(tab = draggedTab, isDark = isDark)
            else -> {
                val folderId =
                    when {
                        draggedKey.startsWith("folder_header_") -> draggedKey.removePrefix("folder_header_")
                        draggedKey.startsWith("folder_") -> draggedKey.removePrefix("folder_")
                        else -> null
                    }
                folders.find { it.id == folderId }?.let { FolderDragGhost(folder = it) }
            }
        }
    }
}

@Composable
private fun TabDragGhost(
    tab: Tab,
    isDark: Boolean,
) {
    Surface(
        shape = ProstoShapes.Card,
        color = if (isDark) DarkSurface else Color.White,
        border = BorderStroke(2.dp, ProstoBrandPrimary),
        shadowElevation = 12.dp,
        modifier =
            Modifier
                .fillMaxSize()
                .clip(ProstoShapes.Card),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(if (isDark) DarkSurfaceVariant else ProstoCanvasFloor)
                        .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SiteFavicon(
                    faviconUrl = tab.faviconUrl,
                    imageSize = 12.dp,
                    containerSize = 14.dp,
                    shape = CircleShape,
                    placeholder = {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = ProstoMutedSlate.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp),
                        )
                    },
                )
                Text(
                    text = tab.title.ifEmpty { "Tab" },
                    style = LocalProstoTypography.current.labelMd.copy(fontSize = 10.sp),
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(if (isDark) DarkBackground else ProstoCanvasFloor),
                contentAlignment = Alignment.Center,
            ) {
                val file = tab.snapshotPath?.let { File(it) }
                if (tab.isHomeTab) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = ProstoMutedSlate.copy(alpha = 0.45f),
                        modifier = Modifier.size(28.dp),
                    )
                } else if (file != null && file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = tab.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = ProstoMutedSlate.copy(alpha = 0.40f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderDragGhost(folder: TabFolder) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        shape = shape,
        color = Color(folder.color),
        border = BorderStroke(2.dp, Color.White),
        shadowElevation = 12.dp,
        modifier =
            Modifier
                .fillMaxSize()
                .clip(shape),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = folder.title.ifBlank { "Folder" },
                style =
                    LocalProstoTypography.current.labelMd.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
